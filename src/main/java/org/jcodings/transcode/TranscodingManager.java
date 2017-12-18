/*
 * Copyright (c) 2014, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 *
 * Contains code modified from JRuby's RubyConverter.java and licensed under the same EPL1.0/GPL 2.0/LGPL 2.1
 * used throughout.
 *
 * Contains code modified jcodings's TranscoderDB.java and EConv.java, which is licensed under the MIT license:
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.jcodings.transcode;

import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.jcodings.Encoding;
import org.jcodings.Ptr;
import org.jcodings.specific.UTF32BEEncoding;
import org.jcodings.util.CaseInsensitiveBytesHash;
import org.jcodings.util.Hash;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.jcodings.transcode.EConv.NULL_STRING;
import static org.jcodings.transcode.EConvFlags.ERROR_HANDLER_MASK;
import static org.jcodings.transcode.EConvFlags.MAX_ECFLAGS_DECORATORS;
import static org.jcodings.util.CaseInsensitiveBytesHash.caseInsensitiveEquals;

public class TranscodingManager {

    public static final class TranscoderReference {

        private final Transcoder transcoder;
        private final TranscoderDB.Entry entry;

        public TranscoderReference(Transcoder transcoder, TranscoderDB.Entry entry) {
            this.transcoder = transcoder;
            this.entry = entry;
        }

        public Transcoder getTranscoder() {
            if (TruffleOptions.AOT) {
                return transcoder;
            } else {
                return entry.getTranscoder();
            }
        }

    }

    public static final Map<String, Map<String, TranscoderReference>> allTranscoders;
    private static final MethodHandle convertInternalMethodHandle;

    static {
        allTranscoders = new HashMap<>();

        for (CaseInsensitiveBytesHash<TranscoderDB.Entry> sourceEntry : TranscoderDB.transcoders) {
            for (Hash.HashEntry<TranscoderDB.Entry> destinationEntry : sourceEntry.entryIterator()) {
                final TranscoderDB.Entry e = destinationEntry.value;

                final String sourceName = new String(e.getSource()).toUpperCase();
                final String destinationName = new String(e.getDestination()).toUpperCase();

                final TranscoderReference transcoder;
                if (TruffleOptions.AOT) {
                    // Load the classes eagerly
                    transcoder = new TranscoderReference(e.getTranscoder(), null);
                } else {
                    transcoder = new TranscoderReference(null, e);
                }

                allTranscoders.putIfAbsent(sourceName, new HashMap<>());
                final Map<String, TranscoderReference> fromSource = allTranscoders.get(sourceName);
                fromSource.put(destinationName, transcoder);
            }
        }

        try {
            final Method m = EConv.class.getDeclaredMethod("convertInternal", byte[].class, Ptr.class, int.class, byte[].class, Ptr.class, int.class, int.class);
            m.setAccessible(true);
            convertInternalMethodHandle = MethodHandles.lookup().unreflect(m);
            m.setAccessible(false);
        } catch (Throwable t) {
            throw new UnsupportedOperationException(t);
        }
    }

    public static EConv create(Encoding sourceEncoding, Encoding destinationEncoding, int options) {
        final EConv ec = open(sourceEncoding.getName(), destinationEncoding.getName(), rubiniusToJRubyFlags(options));

        if (ec == null) {
            return null;
        }

        ec.sourceEncoding = sourceEncoding;
        ec.destinationEncoding = destinationEncoding;

        return ec;
    }

    private static EConv create(byte[] sourceEncodingName, byte[] destinationEncodingName, int options) {
        final EConv ec = open(sourceEncodingName, destinationEncodingName, rubiniusToJRubyFlags(options));

        if (ec == null) {
            return null;
        }

        return ec;
    }

    /**
     * Rubinius and JRuby process Encoding::Converter options flags differently.  Rubinius splits the processing
     * between initial setup and the replacement value setup, whereas JRuby handles them all during initial setup.
     * We figure out what flags JRuby additionally expects to be set and set them to satisfy EConv.
     */
    private static int rubiniusToJRubyFlags(int flags) {
        if ((flags & EConvFlags.XML_TEXT_DECORATOR) != 0) {
            flags |= EConvFlags.UNDEF_HEX_CHARREF;
        }

        if ((flags & EConvFlags.XML_ATTR_CONTENT_DECORATOR) != 0) {
            flags |= EConvFlags.UNDEF_HEX_CHARREF;
        }

        return flags;
    }

    @TruffleBoundary
    private static EConv open(byte[] sourceEncodingName, byte[] destinationEncodingName, int ecflags) {
        // Taken from org.jcodings.transcode.TranscoderDB.open.

        byte[][] decorators = new byte[MAX_ECFLAGS_DECORATORS][];

        int numDecorators = TranscoderDB.decoratorNames(ecflags, decorators);
        if (numDecorators == -1) {
            return null;
        }

        EConv ec = open0(sourceEncodingName, destinationEncodingName, ecflags & ERROR_HANDLER_MASK);
        if (ec == null) {
            return null;
        }

        for (int i = 0; i < numDecorators; i++) {
            if (!decorateAtLast(ec, decorators[i])) {
                ec.close();
                return null;
            }
        }

        ec.flags |= ecflags & ~ERROR_HANDLER_MASK;
        return ec;
    }

    @TruffleBoundary
    private static EConv open0(byte[] sourceEncodingName, byte[] destinationEncodingName, int ecflags) {
        // Taken from org.jcodings.transcode.TranscoderDB.open0.

        final List<Transcoder> transcoders = searchPath(sourceEncodingName, destinationEncodingName);

        if (transcoders.isEmpty()) {
            return null;
        }

        final EConv ec = TranscoderDB.alloc(transcoders.size());
        for (Transcoder transcoder : transcoders) {
            ec.addTranscoderAt(transcoder, ec.numTranscoders);
        }

        ec.flags = ecflags;
        ec.source = sourceEncodingName;
        ec.destination = destinationEncodingName;

        return ec;
    }

    @TruffleBoundary
    private static List<Transcoder> searchPath(byte[] source, byte[] destination) {
        final List<Transcoder> ret = new ArrayList<>();
        final String sourceEncodingName = new String(source).toUpperCase();
        final String destinationEncodingName = new String(destination).toUpperCase();

        if (!allTranscoders.containsKey(sourceEncodingName)) {
            return Collections.emptyList();
        }

        final TranscoderReference directMapping = allTranscoders.get(sourceEncodingName).get(destinationEncodingName);

        if (directMapping != null) {
            return Collections.singletonList(directMapping.getTranscoder());
        } else {
            final List<String> path = bfs(sourceEncodingName, destinationEncodingName);

            if (! path.isEmpty()) {
                String sourceName = path.remove(0);

                for (String destinationName : path) {
                    ret.add(allTranscoders.get(sourceName).get(destinationName).getTranscoder());
                    sourceName = destinationName;
                }
            }
        }

        return ret;
    }

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    public static List<String> bfs(String sourceEncodingName, String destinationEncodingName) {
        final Deque<String> queue = new ArrayDeque<>();
        final HashMap<String, String> invertedList = new HashMap<>();

        invertedList.put(sourceEncodingName, null);

        queue.add(sourceEncodingName);
        while (!queue.isEmpty()) {
            String current = queue.pop();

            for (String child : allTranscoders.get(current).keySet()) {
                if (invertedList.containsKey(child)) {
                    // We've already visited this path or are scheduled to.
                    continue;
                }

                if (child.equals(destinationEncodingName)) {
                    // Search finished.
                    final LinkedList<String> ret = new LinkedList<>();
                    ret.add(child);

                    String next = current;
                    while (next != null) {
                        ret.addFirst(next);
                        next = invertedList.get(next);
                    }

                    return ret;
                }

                invertedList.put(child, current);
                queue.add(child);
            }
        }

        return Collections.emptyList();
    }

    private static boolean decorateAtLast(EConv ec, byte[] decorator) {
        // Taken from org.jcodings.transcode.EConv#decorateAtLast.

        if (ec.numTranscoders == 0) {
            return decorateAt(ec, decorator, 0);
        }

        final Transcoder transcoder = ec.elements[ec.numTranscoders - 1].transcoding.transcoder;

        if (!EConv.decorator(transcoder.source, transcoder.destination) && transcoder.compatibility.isEncoder()) {
            return decorateAt(ec, decorator, ec.numTranscoders - 1);
        }

        return decorateAt(ec, decorator, ec.numTranscoders);
    }

    @TruffleBoundary
    private static boolean decorateAt(EConv ec, byte[] decorator, int n) {
        // Taken from org.jcodings.transcode.EConv#decorateAt.

        if (ec.started) {
            return false;
        }

        if (! allTranscoders.containsKey("")) {
            return false;
        }

        final TranscoderReference transcoder = allTranscoders.get("").get(new String(decorator).toUpperCase());
        if (transcoder == null) {
            return false;
        }

        ec.addTranscoderAt(transcoder.getTranscoder(), n);
        return true;
    }

    @TruffleBoundary
    public static EConvResult convert(EConv ec, byte[] in, Ptr inPtr, int inStop, byte[] out, Ptr outPtr, int outStop, int flags) {
        // Taken from org.jcodings.transcode.EConv#convert.

        ec.started = true;

        if (in == null || inPtr == null) {
            in = NULL_STRING;
            inPtr = Ptr.NULL;
            inStop = 0;
        }

        if (out == null || outPtr == null) {
            out = NULL_STRING;
            outPtr = Ptr.NULL;
            outStop = 0;
        }

        resume: while (true) {
            EConvResult ret = null;
            try {
                ret = (EConvResult) convertInternalMethodHandle.invoke(ec, in, inPtr, inStop, out, outPtr, outStop, flags);
            } catch (Throwable t) {
                throw new UnsupportedOperationException(t);
            }
            if (ret.isInvalidByteSequence() || ret.isIncompleteInput()) {
                switch (ec.flags & EConvFlags.INVALID_MASK) {
                    case EConvFlags.INVALID_REPLACE:
                        if (outputReplacementCharacter(ec) == 0) {
                            continue resume;
                        }
                }
            }

            if (ret.isUndefinedConversion()) {
                switch (ec.flags & EConvFlags.UNDEF_MASK) {
                    case EConvFlags.UNDEF_REPLACE:
                        if (outputReplacementCharacter(ec) == 0) {
                            continue resume;
                        }
                        break;
                    case EConvFlags.UNDEF_HEX_CHARREF:
                        if (outputHexCharref(ec) == 0) {
                            continue resume;
                        }
                        break;
                }
            }
            return ret;
        }
    }

    /* output_replacement_character */
    @TruffleBoundary
    private static int outputReplacementCharacter(EConv ec) {
        // Taken from org.jcodings.transcode.EConv#outputReplacementCharacter.

        if (ec.makeReplacement() == -1) {
            return -1;
        }
        if (insertOutput(ec, ec.replacementString, 0, ec.replacementLength, ec.replacementEncoding) == -1) {
            return -1;
        }
        return 0;
    }

    /* output_hex_charref */
    @TruffleBoundary
    private static int outputHexCharref(EConv ec) {
        // Taken from org.jcodings.transcode.EConv#outputHexCharref.

        final byte[] utfBytes;
        final int utfP;
        int utfLen;

        if (caseInsensitiveEquals(ec.lastError.source, "UTF-32BE".getBytes())) {
            utfBytes = ec.lastError.errorBytes;
            utfP = ec.lastError.errorBytesP;
            utfLen = ec.lastError.errorBytesLength;
        } else {
            Ptr utfLenA = new Ptr();

            // TODO: better calculation?
            byte[] utfBuf = new byte[ec.lastError.errorBytesLength * UTF32BEEncoding.INSTANCE.maxLength()];
            utfBytes = allocateConvertedString(ec.lastError.source, "UTF-32BE".getBytes(), ec.lastError.errorBytes, ec.lastError.errorBytesP, ec.lastError.errorBytesLength, utfBuf, utfLenA);

            if (utfBytes == null) {
                return -1;
            }
            utfP = 0;
            utfLen = utfLenA.p;
        }

        if (utfLen % 4 != 0) {
            return -1;
        }

        int p = utfP;
        while (4 <= utfLen) {
            int u = 0; // long ??
            u += (utfBytes[p] & 0xff) << 24;
            u += (utfBytes[p + 1] & 0xff) << 16;
            u += (utfBytes[p + 2] & 0xff) << 8;
            u += (utfBytes[p + 3] & 0xff);
            byte[] charrefbuf = String.format("&#x%X;", u).getBytes(); // FIXME: use faster sprintf ??

            if (insertOutput(ec, charrefbuf, 0, charrefbuf.length, "US-ASCII".getBytes()) == -1) {
                return -1;
            }

            p += 4;
            utfLen -= 4;
        }

        return 0;
    }

    /* allocate_converted_string */
    @TruffleBoundary
    private static byte[] allocateConvertedString(byte[] source, byte[] destination, byte[] str, int strP, int strLen, byte[] callerDstBuf, Ptr dstLenPtr) {
        // Taken from org.jcodings.transcode.EConv.allocateConvertedString.

        int dstBufSize;

        if (callerDstBuf != null) {
            dstBufSize = callerDstBuf.length;
        } else if (strLen == 0) {
            dstBufSize = 1; // ??
        } else {
            dstBufSize = strLen;
        }

        EConv ec = create(source, destination, 0);
        if (ec == null) {
            return null;
        }

        byte[] dstStr;
        if (callerDstBuf != null) {
            dstStr = callerDstBuf;
        } else {
            dstStr = new byte[dstBufSize];
        }

        int dstLen = 0;
        Ptr sp = new Ptr(strP);
        Ptr dp = new Ptr(dstLen);
        EConvResult res = convert(ec, str, sp, strP + strLen, dstStr, dp, dstBufSize, 0);
        dstLen = dp.p;

        while (res.isDestinationBufferFull()) {
            dstBufSize *= 2;
            byte[] tmp = new byte[dstBufSize];
            System.arraycopy(dstStr, 0, tmp, 0, dstBufSize / 2);
            dstStr = tmp;

            dp.p = dstLen; // ??
            res = convert(ec, str, sp, strP + strLen, dstStr, dp, dstBufSize, 0);
            dstLen = dp.p;
        }

        if (!res.isFinished()) {
            return null;
        }

        ec.close();
        dstLenPtr.p = dstLen;

        return dstStr;
    }

    /* rb_econv_insert_output */
    @TruffleBoundary
    private static int insertOutput(EConv ec, byte[] str, int strP, int strLen, byte[] strEncoding) {
        // Taken from org.jcodings.transcode.EConv#insertOutput.

        byte[] insertEncoding = ec.encodingToInsertOutput();
        byte[] insertBuf = null;

        ec.started = true;

        if (strLen == 0) {
            return 0;
        }

        final byte[] insertStr;
        final int insertP;
        final int insertLen;
        if (caseInsensitiveEquals(insertEncoding, strEncoding)) {
            insertStr = str;
            insertP = 0;
            insertLen = strLen;
        } else {
            Ptr insertLenP = new Ptr();
            insertBuf = new byte[4096]; // FIXME: wasteful
            insertStr = allocateConvertedString(strEncoding, insertEncoding, str, strP, strLen, insertBuf, insertLenP);
            insertLen = insertLenP.p;
            insertP = insertStr == str ? strP : 0;
            if (insertStr == null) {
                return -1;
            }
        }

        int need = insertLen;

        final int lastTranscodingIndex = ec.numTranscoders - 1;
        final Transcoding transcoding;

        Buffer buf;

        if (ec.numTranscoders == 0) {
            transcoding = null;
            buf = ec.inBuf;
        } else if (ec.elements[lastTranscodingIndex].transcoding.transcoder.compatibility.isEncoder()) {
            transcoding = ec.elements[lastTranscodingIndex].transcoding;
            need += transcoding.readAgainLength;
            if (need < insertLen) {
                return -1;
            }

            if (lastTranscodingIndex == 0) {
                buf = ec.inBuf;
            } else {
                buf = ec.elements[lastTranscodingIndex - 1];
            }
        } else {
            transcoding = ec.elements[lastTranscodingIndex].transcoding;
            buf = ec.elements[lastTranscodingIndex];
        }

        if (buf == null) {
            buf = new Buffer();
            buf.allocate(need);
        } else if (buf.bytes == null) {
            buf.allocate(need);
        } else if ((buf.bufEnd - buf.dataEnd) < need) {
            // try to compact buffer by moving data portion back to bufStart
            System.arraycopy(buf.bytes, buf.dataStart, buf.bytes, buf.bufStart, buf.dataEnd - buf.dataStart);
            buf.dataEnd = buf.bufStart + (buf.dataEnd - buf.dataStart);
            buf.dataStart = buf.bufStart;

            if ((buf.bufEnd - buf.dataEnd) < need) {
                // still not enough room; use a separate buffer
                int s = (buf.dataEnd - buf.bufStart) + need;
                if (s < need) {
                    return -1;
                }
                Buffer buf2 = buf = new Buffer();
                buf2.allocate(s);
                System.arraycopy(buf.bytes, buf.bufStart, buf2.bytes, 0, s); // ??
                buf2.dataStart = 0;
                buf2.dataEnd = buf.dataEnd - buf.bufStart;
            }
        }

        System.arraycopy(insertStr, insertP, buf.bytes, buf.dataEnd, insertLen);
        buf.dataEnd += insertLen;
        if (transcoding != null && transcoding.transcoder.compatibility.isEncoder()) {
            System.arraycopy(transcoding.readBuf, transcoding.recognizedLength, buf.bytes, buf.dataEnd, transcoding.readAgainLength);
            buf.dataEnd += transcoding.readAgainLength;
            transcoding.readAgainLength = 0;
        }

        return 0;
    }

    /* rb_econv_set_replacement */
    @TruffleBoundary
    public static int setReplacement(EConv ec, byte[] str, int p, int len, byte[] encname) {
        // Taken from org.jcodings.transcode.EConv#setReplacement.

        byte[] encname2 = encodingToInsertOutput(ec);

        final byte[] str2;
        final int len2;

        if (encname2.length == 0 || caseInsensitiveEquals(encname, encname2)) {
            str2 = new byte[len];
            System.arraycopy(str, p, str2, 0, len); // ??
            len2 = len;
            encname2 = encname;
        } else {
            Ptr len2p = new Ptr();
            str2 = allocateConvertedString(encname, encname2, str, p, len, null, len2p);
            if (str2 == null) {
                return -1;
            }
            len2 = len2p.p;
        }

        ec.replacementString = str2;
        ec.replacementLength = len2;
        ec.replacementEncoding = encname2;
        return 0;
    }

    /* rb_econv_encoding_to_insert_output */
    @TruffleBoundary
    public static byte[] encodingToInsertOutput(EConv ec) {
        // Taken from org.jcodings.transcode.EConv#encodingToInsertOutput.

        Transcoding transcoding = ec.lastTranscoding;
        if (transcoding == null) {
            return NULL_STRING;
        }
        Transcoder transcoder = transcoding.transcoder;
        return transcoder.compatibility.isEncoder() ? transcoder.source : transcoder.destination;
    }
}
