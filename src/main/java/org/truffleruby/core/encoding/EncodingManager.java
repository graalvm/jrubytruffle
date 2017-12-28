/*
 * Copyright (c) 2014, 2017 Oracle and/or its affiliates. All rights reserved. This
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
import org.jcodings.specific.ISO8859_16Encoding;
import org.jcodings.specific.USASCIIEncoding;
import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.core.rope.CodeRange;
import org.truffleruby.core.rope.Rope;
import org.truffleruby.core.rope.RopeOperations;
import org.truffleruby.core.string.ISO_8859_16;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.platform.NativeConfiguration;
import org.truffleruby.platform.TruffleNFIPlatform;
import org.truffleruby.platform.TruffleNFIPlatform.NativeFunction;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
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

        if (TruffleOptions.AOT) {
            // Call setlocale(LC_ALL, "") to ensure the locale is set to the environment's locale
            // rather than the default "C" locale.
            Compiler.command(new Object[]{ "com.oracle.svm.core.posix.PosixUtils.setLocale(String, String)String", "LC_ALL", "" });
        }
    }

    // This must be run after the locale is set for native, see the setLocale() call above
    public void initializeLocaleEncoding(TruffleNFIPlatform nfi, NativeConfiguration nativeConfiguration) {
        final String localeEncodingName;
        if (context.getOptions().NATIVE_PLATFORM) {
            final int codeset = (int) nativeConfiguration.get("platform.langinfo.CODESET");

            // char *nl_langinfo(nl_item item);
            // nl_item is int on at least Linux, macOS & Solaris
            final NativeFunction nl_langinfo = nfi.getFunction("nl_langinfo", 1, "(sint32):string");

            final long address = nfi.asPointer((TruffleObject) nl_langinfo.call(codeset));
            final byte[] bytes = new Pointer(address).readZeroTerminatedByteArray(0);
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
        final Rope cachedRope = context.getRopeTable().getRope(rope.getBytes(), rope.getEncoding(), rope.getCodeRange());
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
    public void defineAlias(int encodingListIndex, String name) {
        final DynamicObject rubyEncoding = getRubyEncoding(encodingListIndex);

        LOOKUP.put(name.toLowerCase(Locale.ENGLISH), rubyEncoding);
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
        final String encodingName = encoding.toString();

        if (encodingName.equals("ASCII-8BIT")) {
            return StandardCharsets.ISO_8859_1;
        }

        if (encoding == ISO8859_16Encoding.INSTANCE) {
            return ISO_8859_16.INSTANCE;
        }

        try {
            return Charset.forName(encodingName);
        } catch (UnsupportedCharsetException uce) {
            throw new UnsupportedOperationException("no java.nio.charset.Charset found for encoding `" + encoding.toString() + "'", uce);
        }
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
