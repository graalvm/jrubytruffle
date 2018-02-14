/*
 * Copyright (c) 2014, 2018 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 *
 * Some of the code in this class is modified from org.jruby.runtime.encoding.EncodingService,
 * licensed under the same EPL1.0/GPL 2.0/LGPL 2.1 used throughout.
 */
package org.truffleruby.core.encoding;

import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.object.DynamicObject;
import org.jcodings.Encoding;
import org.jcodings.EncodingDB;
import org.jcodings.EncodingDB.Entry;
import org.jcodings.specific.USASCIIEncoding;
import org.jcodings.util.CaseInsensitiveBytesHash;
import org.jcodings.util.CaseInsensitiveBytesHash.CaseInsensitiveBytesHashEntry;
import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.core.rope.CodeRange;
import org.truffleruby.core.rope.Rope;
import org.truffleruby.core.rope.RopeOperations;
import org.truffleruby.core.string.EncodingUtils;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.platform.NativeConfiguration;
import org.truffleruby.platform.TruffleNFIPlatform;
import org.truffleruby.platform.TruffleNFIPlatform.NativeFunction;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EncodingManager {

    private static final int INITIAL_NUMBER_OF_ENCODINGS = EncodingDB.getEncodings().size();

    private final List<DynamicObject> ENCODING_LIST_BY_ENCODING_LIST_INDEX = new ArrayList<>(INITIAL_NUMBER_OF_ENCODINGS);
    private final List<DynamicObject> ENCODING_LIST_BY_ENCODING_INDEX = new ArrayList<>(INITIAL_NUMBER_OF_ENCODINGS);
    private final Map<String, DynamicObject> LOOKUP = new ConcurrentHashMap<>();

    private final RubyContext context;

    @CompilationFinal private Encoding localeEncoding;
    private Encoding defaultExternalEncoding;
    private Encoding defaultInternalEncoding;


    public EncodingManager(RubyContext context) {
        this.context = context;
    }

    public void defineEncodings() {
        final DynamicObject encodingClass = context.getCoreLibrary().getEncodingClass();
        initializeEncodings(encodingClass);
        initializeEncodingAliases(encodingClass);
    }

    private void initializeEncodings(DynamicObject encodingClass) {
        final CaseInsensitiveBytesHash<EncodingDB.Entry>.CaseInsensitiveBytesHashEntryIterator hei = EncodingDB.getEncodings().entryIterator();

        while (hei.hasNext()) {
            final CaseInsensitiveBytesHashEntry<EncodingDB.Entry> e = hei.next();
            final EncodingDB.Entry encodingEntry = e.value;
            final DynamicObject rubyEncoding = defineEncoding(encodingEntry, e.bytes, e.p, e.end);

            for (String constName : EncodingUtils.encodingNames(e.bytes, e.p, e.end)) {
                Layouts.MODULE.getFields(encodingClass).setConstant(context, null, constName, rubyEncoding);
            }
        }
    }

    private void initializeEncodingAliases(DynamicObject encodingClass) {
        final CaseInsensitiveBytesHash<EncodingDB.Entry>.CaseInsensitiveBytesHashEntryIterator hei = EncodingDB.getAliases().entryIterator();

        while (hei.hasNext()) {
            final CaseInsensitiveBytesHashEntry<EncodingDB.Entry> e = hei.next();
            final EncodingDB.Entry encodingEntry = e.value;

            // The alias name should be exactly the one in the encodings DB.
            final DynamicObject rubyEncoding = defineAlias(encodingEntry.getIndex(), new String(e.bytes, e.p, e.end));

            // The constant names must be treated by the the <code>encodingNames</code> helper.
            for (String constName : EncodingUtils.encodingNames(e.bytes, e.p, e.end)) {
                Layouts.MODULE.getFields(encodingClass).setConstant(context, null, constName, rubyEncoding);
            }
        }
    }

    public void initializeDefaultEncodings(TruffleNFIPlatform nfi, NativeConfiguration nativeConfiguration) {
        initializeLocaleEncoding(nfi, nativeConfiguration);

        // External should always have a value, but Encoding.external_encoding{,=} will lazily setup
        final String externalEncodingName = context.getOptions().EXTERNAL_ENCODING;
        if (!externalEncodingName.isEmpty()) {
            final DynamicObject loadedEncoding = getRubyEncoding(externalEncodingName);
            if (loadedEncoding == null) {
                // TODO (nirvdrum 28-Oct-16): This should just print a nice error message and exit
                // with a status code of 1 -- it's essentially an input validation error -- no need
                // to show the user a full trace.
                throw new RuntimeException("unknown encoding name - " + externalEncodingName);
            } else {
                setDefaultExternalEncoding(EncodingOperations.getEncoding(loadedEncoding));
            }
        } else {
            setDefaultExternalEncoding(getLocaleEncoding());
        }

        // The internal encoding is nil by default
        final String internalEncodingName = context.getOptions().INTERNAL_ENCODING;
        if (!internalEncodingName.isEmpty()) {
            final DynamicObject rubyEncoding = getRubyEncoding(internalEncodingName);
            if (rubyEncoding == null) {
                // TODO (nirvdrum 28-Oct-16): This should just print a nice error message and exit
                // with a status code of 1 -- it's essentially an input validation error -- no need
                // to show the user a full trace.
                throw new RuntimeException("unknown encoding name - " + internalEncodingName);
            } else {
                setDefaultInternalEncoding(EncodingOperations.getEncoding(rubyEncoding));
            }
        }
    }

    private void initializeLocaleEncoding(TruffleNFIPlatform nfi, NativeConfiguration nativeConfiguration) {
        if (TruffleOptions.AOT) {
            // Call setlocale(LC_ALL, "") to ensure the locale is set to the environment's locale
            // rather than the default "C" locale.
            Compiler.command(new Object[]{ "com.oracle.svm.core.posix.PosixUtils.setLocale(String, String)String", "LC_ALL", "" });
        }

        final String localeEncodingName;
        if (nfi != null) {
            final int codeset = (int) nativeConfiguration.get("platform.langinfo.CODESET");

            // char *nl_langinfo(nl_item item);
            // nl_item is int on at least Linux, macOS & Solaris
            final NativeFunction nl_langinfo = nfi.getFunction("nl_langinfo", 1, "(sint32):string");

            final long address = nfi.asPointer((TruffleObject) nl_langinfo.call(codeset));
            final byte[] bytes = new Pointer(address).readZeroTerminatedByteArray(context, 0);
            localeEncodingName = new String(bytes, StandardCharsets.ISO_8859_1);
        } else {
            localeEncodingName = Charset.defaultCharset().name();
        }

        DynamicObject rubyEncoding = getRubyEncoding(localeEncodingName);
        if (rubyEncoding == null) {
            rubyEncoding = getRubyEncoding("US-ASCII");
        }

        localeEncoding = EncodingOperations.getEncoding(rubyEncoding);
    }

    @TruffleBoundary
    private static DynamicObject newRubyEncoding(RubyContext context, Encoding encoding, byte[] name, int p, int end, boolean dummy) {
        assert p == 0 : "Ropes can't be created with non-zero offset: " + p;
        assert end == name.length : "Ropes must have the same exact length as the name array (len = " + end + "; name.length = " + name.length + ")";

        final Rope rope = RopeOperations.create(name, USASCIIEncoding.INSTANCE, CodeRange.CR_7BIT);
        final Rope cachedRope = context.getRopeCache().getRope(rope.getBytes(), rope.getEncoding(), rope.getCodeRange());
        final DynamicObject string = context.getFrozenStrings().getFrozenString(cachedRope);

        return Layouts.ENCODING.createEncoding(context.getCoreLibrary().getEncodingFactory(), encoding, string, dummy);
    }

    public static Encoding getEncoding(String name) {
        return getEncoding(RopeOperations.encodeAscii(name, USASCIIEncoding.INSTANCE));
    }

    @TruffleBoundary
    public static Encoding getEncoding(Rope name) {
        EncodingDB.Entry entry = EncodingDB.getEncodings().get(name.getBytes());

        if (entry == null) {
            entry = EncodingDB.getAliases().get(name.getBytes());
        }

        if (entry != null) {
            return entry.getEncoding();
        }

        return null;
    }

    @TruffleBoundary
    public Object[] getEncodingList() {
        return new ArrayList<>(ENCODING_LIST_BY_ENCODING_LIST_INDEX).toArray();
    }

    @TruffleBoundary
    public DynamicObject getRubyEncoding(String name) {
        return LOOKUP.get(name.toLowerCase(Locale.ENGLISH));
    }

    @TruffleBoundary
    public DynamicObject getRubyEncoding(int encodingListIndex) {
        return ENCODING_LIST_BY_ENCODING_LIST_INDEX.get(encodingListIndex);
    }

    @TruffleBoundary
    public DynamicObject getRubyEncoding(Encoding encoding) {
        return ENCODING_LIST_BY_ENCODING_INDEX.get(encoding.getIndex());
    }

    @TruffleBoundary
    public int getEncodingListIndex(DynamicObject rubyEncoding) {
        final int index = ENCODING_LIST_BY_ENCODING_LIST_INDEX.indexOf(rubyEncoding);
        if (index < 0) {
            final String encodingName = StringOperations.getString(Layouts.ENCODING.getName(rubyEncoding));
            throw new UnsupportedOperationException("Encoding not found: " + encodingName);
        }
        return index;
    }

    @TruffleBoundary
    public synchronized DynamicObject defineEncoding(EncodingDB.Entry encodingEntry, byte[] name, int p, int end) {
        final Encoding encoding = encodingEntry.getEncoding();
        final DynamicObject rubyEncoding = newRubyEncoding(context, encoding, name, p, end, encodingEntry.isDummy());

        assert ENCODING_LIST_BY_ENCODING_LIST_INDEX.size() == encodingEntry.getIndex();
        ENCODING_LIST_BY_ENCODING_LIST_INDEX.add(rubyEncoding);
        while (encoding.getIndex() >= ENCODING_LIST_BY_ENCODING_INDEX.size()) {
            ENCODING_LIST_BY_ENCODING_INDEX.add(null);
        }
        ENCODING_LIST_BY_ENCODING_INDEX.set(encoding.getIndex(), rubyEncoding);
        LOOKUP.put(Layouts.ENCODING.getName(rubyEncoding).toString().toLowerCase(Locale.ENGLISH), rubyEncoding);
        return rubyEncoding;
    }

    @TruffleBoundary
    public DynamicObject defineAlias(int encodingListIndex, String name) {
        final DynamicObject rubyEncoding = getRubyEncoding(encodingListIndex);
        LOOKUP.put(name.toLowerCase(Locale.ENGLISH), rubyEncoding);
        return rubyEncoding;
    }

    @TruffleBoundary
    public synchronized DynamicObject replicateEncoding(Encoding encoding, String name) {
        if (getRubyEncoding(name) != null) {
            return null;
        }

        EncodingDB.replicate(name, new String(encoding.getName()));
        byte[] nameBytes = name.getBytes();
        final Entry entry = EncodingDB.getEncodings().get(nameBytes);
        return defineEncoding(entry, nameBytes, 0, nameBytes.length);
    }

    @TruffleBoundary
    public static Charset charsetForEncoding(Encoding encoding) {
        return encoding.getCharset();
    }

    public Encoding getLocaleEncoding() {
        return localeEncoding;
    }

    public void setDefaultExternalEncoding(Encoding defaultExternalEncoding) {
        this.defaultExternalEncoding = defaultExternalEncoding;
    }

    public Encoding getDefaultExternalEncoding() {
        return defaultExternalEncoding;
    }

    public void setDefaultInternalEncoding(Encoding defaultInternalEncoding) {
        this.defaultInternalEncoding = defaultInternalEncoding;
    }

    public Encoding getDefaultInternalEncoding() {
        return defaultInternalEncoding;
    }
}
