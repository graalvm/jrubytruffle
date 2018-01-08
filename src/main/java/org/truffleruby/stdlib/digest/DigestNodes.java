/*
 * Copyright (c) 2015, 2018 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.stdlib.digest;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import org.jcodings.specific.ASCIIEncoding;
import org.jcodings.specific.USASCIIEncoding;
import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.builtins.CoreClass;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.collections.ByteArrayBuilder;
import org.truffleruby.core.rope.CodeRange;
import org.truffleruby.core.rope.Rope;
import org.truffleruby.core.rope.RopeOperations;
import org.truffleruby.core.string.StringNodes;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.language.control.JavaException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@CoreClass("Truffle::Digest")
public abstract class DigestNodes {

    @TruffleBoundary
    private static MessageDigest getMessageDigestInstance(String name) {
        try {
            return MessageDigest.getInstance(name);
        } catch (NoSuchAlgorithmException e) {
            throw new JavaException(e);
        }
    }

    private static DynamicObject createDigest(RubyContext context, DigestAlgorithm algorithm) {
        return Layouts.DIGEST.createDigest(
                context.getCoreLibrary().getDigestFactory(),
                algorithm,
                getMessageDigestInstance(algorithm.getName()));
    }

    @CoreMethod(names = "md5", onSingleton = true)
    public abstract static class MD5Node extends CoreMethodArrayArgumentsNode {

        @Specialization
        public DynamicObject md5() {
            return createDigest(getContext(), DigestAlgorithm.MD5);
        }

    }

    @CoreMethod(names = "sha1", onSingleton = true)
    public abstract static class SHA1Node extends CoreMethodArrayArgumentsNode {

        @Specialization
        public DynamicObject sha1() {
            return createDigest(getContext(), DigestAlgorithm.SHA1);
        }

    }

    @CoreMethod(names = "sha256", onSingleton = true)
    public abstract static class SHA256Node extends CoreMethodArrayArgumentsNode {

        @Specialization
        public DynamicObject sha256() {
            return createDigest(getContext(), DigestAlgorithm.SHA256);
        }

    }

    @CoreMethod(names = "sha384", onSingleton = true)
    public abstract static class SHA384Node extends CoreMethodArrayArgumentsNode {

        @Specialization
        public DynamicObject sha384() {
            return createDigest(getContext(), DigestAlgorithm.SHA384);
        }

    }

    @CoreMethod(names = "sha512", onSingleton = true)
    public abstract static class SHA512Node extends CoreMethodArrayArgumentsNode {

        @Specialization
        public DynamicObject sha512() {
            return createDigest(getContext(), DigestAlgorithm.SHA512);
        }

    }

    @CoreMethod(names = "update", onSingleton = true, required = 2)
    public abstract static class UpdateNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isRubyString(message)")
        public DynamicObject update(DynamicObject digestObject, DynamicObject message) {
            final MessageDigest digest = Layouts.DIGEST.getDigest(digestObject);

            RopeOperations.visitBytes(StringOperations.rope(message), digest::update);

            return digestObject;
        }

    }

    @CoreMethod(names = "reset", onSingleton = true, required = 1)
    public abstract static class ResetNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        public DynamicObject reset(DynamicObject digestObject) {
            Layouts.DIGEST.getDigest(digestObject).reset();
            return digestObject;
        }

    }

    @CoreMethod(names = "digest", onSingleton = true, required = 1)
    public abstract static class DigestNode extends CoreMethodArrayArgumentsNode {

        @Child private StringNodes.MakeStringNode makeStringNode = StringNodes.MakeStringNode.create();

        @Specialization
        public DynamicObject digest(DynamicObject digestObject) {
            final MessageDigest digest = Layouts.DIGEST.getDigest(digestObject);

            return makeStringNode.executeMake(cloneAndDigest(digest), ASCIIEncoding.INSTANCE, CodeRange.CR_VALID);
        }

        // TODO CS 10-Apr-17 the Ruby code for digest also clones in some cases! Are we cloning redundantly?

        @TruffleBoundary
        private static byte[] cloneAndDigest(MessageDigest digest) {
            final MessageDigest clonedDigest;

            try {
                clonedDigest = (MessageDigest) digest.clone();
            } catch (CloneNotSupportedException e) {
                throw new JavaException(e);
            }

            return clonedDigest.digest();
        }

    }

    @CoreMethod(names = "digest_length", onSingleton = true, required = 1)
    public abstract static class DigestLengthNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        public int digestLength(DynamicObject digestObject) {
            return Layouts.DIGEST.getAlgorithm(digestObject).getLength();
        }

    }

    @CoreMethod(names = "bubblebabble", onSingleton = true, required = 1)
    public abstract static class BubbleBabbleNode extends CoreMethodArrayArgumentsNode {

        @Child private StringNodes.MakeStringNode makeStringNode = StringNodes.MakeStringNode.create();

        @TruffleBoundary
        @Specialization(guards = "isRubyString(message)")
        public DynamicObject bubblebabble(DynamicObject message) {
            final Rope rope = StringOperations.rope(message);
            final byte[] bubblebabbleBytes = bubblebabble(rope.getBytes(), 0, rope.byteLength()).getBytes();

            return makeStringNode.executeMake(bubblebabbleBytes, USASCIIEncoding.INSTANCE, CodeRange.CR_7BIT);
        }

        /**
         * Ported from OpenSSH (https://github.com/openssh/openssh-portable/blob/957fbceb0f3166e41b76fdb54075ab3b9cc84cba/sshkey.c#L942-L987)
         *
         * OpenSSH License Notice
         *
         * Copyright (c) 2000, 2001 Markus Friedl.  All rights reserved.
         * Copyright (c) 2008 Alexander von Gernler.  All rights reserved.
         * Copyright (c) 2010,2011 Damien Miller.  All rights reserved.
         *
         * Redistribution and use in source and binary forms, with or without
         * modification, are permitted provided that the following conditions
         * are met:
         * 1. Redistributions of source code must retain the above copyright
         *    notice, this list of conditions and the following disclaimer.
         * 2. Redistributions in binary form must reproduce the above copyright
         *    notice, this list of conditions and the following disclaimer in the
         *    documentation and/or other materials provided with the distribution.
         *
         * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
         * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
         * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
         * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
         * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
         * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
         * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
         * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
         * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
         * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
         */
        public static ByteArrayBuilder bubblebabble(byte[] message, int begin, int length) {
            char[] vowels = new char[]{'a', 'e', 'i', 'o', 'u', 'y'};
            char[] consonants = new char[]{'b', 'c', 'd', 'f', 'g', 'h', 'k', 'l', 'm',
                    'n', 'p', 'r', 's', 't', 'v', 'z', 'x'};

            long seed = 1;

            ByteArrayBuilder retval = new ByteArrayBuilder();

            int rounds = (length / 2) + 1;
            retval.append('x');
            for (int i = 0; i < rounds; i++) {
                int idx0, idx1, idx2, idx3, idx4;

                if ((i + 1 < rounds) || (length % 2 != 0)) {
                    long b = message[begin + 2 * i] & 0xFF;
                    idx0 = (int) ((((b >> 6) & 3) + seed) % 6) & 0xFFFFFFFF;
                    idx1 = (int) (((b) >> 2) & 15) & 0xFFFFFFFF;
                    idx2 = (int) (((b & 3) + (seed / 6)) % 6) & 0xFFFFFFFF;
                    retval.append(vowels[idx0]);
                    retval.append(consonants[idx1]);
                    retval.append(vowels[idx2]);
                    if ((i + 1) < rounds) {
                        long b2 = message[begin + (2 * i) + 1] & 0xFF;
                        idx3 = (int) ((b2 >> 4) & 15) & 0xFFFFFFFF;
                        idx4 = (int) ((b2) & 15) & 0xFFFFFFFF;
                        retval.append(consonants[idx3]);
                        retval.append('-');
                        retval.append(consonants[idx4]);
                        seed = ((seed * 5) +
                                ((b * 7) +
                                        b2)) % 36;
                    }
                } else {
                    idx0 = (int) (seed % 6) & 0xFFFFFFFF;
                    idx1 = 16;
                    idx2 = (int) (seed / 6) & 0xFFFFFFFF;
                    retval.append(vowels[idx0]);
                    retval.append(consonants[idx1]);
                    retval.append(vowels[idx2]);
                }
            }
            retval.append('x');

            return retval;
        }

    }

}
