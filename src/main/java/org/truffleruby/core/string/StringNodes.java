
/*
 * Copyright (c) 2013, 2018 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 *
 * Some of the code in this class is transposed from org.jruby.RubyString
 * and String Support and licensed under the same EPL1.0/GPL 2.0/LGPL 2.1
 * used throughout.
 *
 * Copyright (C) 2001 Alan Moore <alan_moore@gmx.net>
 * Copyright (C) 2001-2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2001-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2002-2006 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2004 David Corbin <dcorbin@users.sourceforge.net>
 * Copyright (C) 2005 Tim Azzopardi <tim@tigerfive.com>
 * Copyright (C) 2006 Miguel Covarrubias <mlcovarrubias@gmail.com>
 * Copyright (C) 2006 Ola Bini <ola@ologix.com>
 * Copyright (C) 2007 Nick Sieger <nicksieger@gmail.com>
 *
 * Some of the code in this class is transposed from org.jruby.util.ByteList,
 * licensed under the same EPL1.0/GPL 2.0/LGPL 2.1 used throughout.
 *
 * Copyright (C) 2007-2010 JRuby Community
 * Copyright (C) 2007 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2007 Nick Sieger <nicksieger@gmail.com>
 * Copyright (C) 2007 Ola Bini <ola@ologix.com>
 * Copyright (C) 2007 William N Dortch <bill.dortch@gmail.com>
 *
 * Some of the code in this class is transliterated from C++ code in Rubinius.
 *
 * Copyright (c) 2007-2014, Evan Phoenix and contributors
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * * Neither the name of Rubinius nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.truffleruby.core.string;

import static org.truffleruby.core.rope.CodeRange.CR_7BIT;
import static org.truffleruby.core.rope.CodeRange.CR_UNKNOWN;
import static org.truffleruby.core.rope.CodeRange.CR_VALID;
import static org.truffleruby.core.rope.RopeConstants.EMPTY_ASCII_8BIT_ROPE;
import static org.truffleruby.core.string.StringOperations.encoding;
import static org.truffleruby.core.string.StringOperations.rope;
import static org.truffleruby.core.string.StringSupport.MBCLEN_CHARFOUND_LEN;
import static org.truffleruby.core.string.StringSupport.MBCLEN_CHARFOUND_P;
import static org.truffleruby.core.string.StringSupport.MBCLEN_INVALID_P;
import static org.truffleruby.core.string.StringSupport.MBCLEN_NEEDMORE_P;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.jcodings.Encoding;
import org.jcodings.exception.EncodingException;
import org.jcodings.specific.ASCIIEncoding;
import org.jcodings.specific.USASCIIEncoding;
import org.jcodings.specific.UTF8Encoding;
import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.builtins.CoreClass;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.CoreMethodNode;
import org.truffleruby.builtins.NonStandard;
import org.truffleruby.builtins.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.builtins.PrimitiveNode;
import org.truffleruby.builtins.YieldingCoreMethodNode;
import org.truffleruby.collections.ByteArrayBuilder;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.binding.BindingNodes;
import org.truffleruby.core.cast.TaintResultNode;
import org.truffleruby.core.cast.ToIntNode;
import org.truffleruby.core.cast.ToIntNodeGen;
import org.truffleruby.core.cast.ToStrNode;
import org.truffleruby.core.cast.ToStrNodeGen;
import org.truffleruby.core.encoding.EncodingNodes;
import org.truffleruby.core.encoding.EncodingNodes.CheckRopeEncodingNode;
import org.truffleruby.core.encoding.EncodingNodesFactory;
import org.truffleruby.core.encoding.EncodingOperations;
import org.truffleruby.core.format.FormatExceptionTranslator;
import org.truffleruby.core.format.exceptions.FormatException;
import org.truffleruby.core.format.unpack.ArrayResult;
import org.truffleruby.core.format.unpack.UnpackCompiler;
import org.truffleruby.core.kernel.KernelNodes;
import org.truffleruby.core.kernel.KernelNodesFactory;
import org.truffleruby.core.numeric.FixnumLowerNodeGen;
import org.truffleruby.core.numeric.FixnumOrBignumNode;
import org.truffleruby.core.rope.CodeRange;
import org.truffleruby.core.rope.ConcatRope;
import org.truffleruby.core.rope.LeafRope;
import org.truffleruby.core.rope.NativeRope;
import org.truffleruby.core.rope.RepeatingRope;
import org.truffleruby.core.rope.Rope;
import org.truffleruby.core.rope.RopeBuilder;
import org.truffleruby.core.rope.RopeConstants;
import org.truffleruby.core.rope.RopeNodes;
import org.truffleruby.core.rope.RopeNodes.RepeatNode;
import org.truffleruby.core.rope.RopeNodesFactory;
import org.truffleruby.core.rope.RopeOperations;
import org.truffleruby.core.rope.SubstringRope;
import org.truffleruby.core.string.StringNodesFactory.CountRopesNodeFactory;
import org.truffleruby.core.string.StringNodesFactory.DeleteBangNodeFactory;
import org.truffleruby.core.string.StringNodesFactory.DeleteBangRopesNodeFactory;
import org.truffleruby.core.string.StringNodesFactory.StringAreComparableNodeGen;
import org.truffleruby.core.string.StringNodesFactory.StringEqualNodeGen;
import org.truffleruby.core.string.StringNodesFactory.SumNodeFactory;
import org.truffleruby.core.string.StringSupport.TrTables;
import org.truffleruby.language.CheckLayoutNode;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.Visibility;
import org.truffleruby.language.arguments.ReadCallerFrameNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.CallDispatchHeadNode;
import org.truffleruby.language.objects.AllocateObjectNode;
import org.truffleruby.language.objects.IsTaintedNode;
import org.truffleruby.language.objects.TaintNode;
import org.truffleruby.language.yield.YieldNode;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CreateCast;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreClass("String")
public abstract class StringNodes {

    @NodeChildren({ @NodeChild("bytes"), @NodeChild("encoding"), @NodeChild("codeRange") })
    public abstract static class MakeStringNode extends RubyNode {

        @Child private AllocateObjectNode allocateObjectNode;
        @Child private RopeNodes.MakeLeafRopeNode makeLeafRopeNode = RopeNodes.MakeLeafRopeNode.create();

        public abstract DynamicObject executeMake(Object payload, Object encoding, Object codeRange);

        public DynamicObject fromRope(Rope rope) {
            return executeMake(rope, NotProvided.INSTANCE, NotProvided.INSTANCE);
        }

        public DynamicObject fromBuilder(RopeBuilder builder, CodeRange codeRange) {
            return executeMake(builder.getBytes(), builder.getEncoding(), codeRange);
        }

        /**
         * All callers of this factory method must guarantee that the builder's byte array cannot change after this
         * call, otherwise the rope built from the builder will end up in an inconsistent state.
         */
        public DynamicObject fromBuilderUnsafe(RopeBuilder builder, CodeRange codeRange) {
            final byte[] unsafeBytes = builder.getUnsafeBytes();
            final byte[] ropeBytes;

            // While the caller must guarantee the builder's byte[] cannot change after this call, it's possible
            // the builder has allocated more space than it needs. Ropes require that the backing byte array
            // is the exact length required. If the builder doesn't satisfy this constraint, we must make a copy.
            // Alternatively, we could make a leaf rope and then take a substring of it, but that would complicate
            // the specializations here.
            if (unsafeBytes.length == builder.getLength()) {
                ropeBytes = unsafeBytes;
            } else {
                ropeBytes = builder.getBytes();
            }

            return executeMake(ropeBytes, builder.getEncoding(), codeRange);
        }

        public static MakeStringNode create() {
            return StringNodesFactory.MakeStringNodeGen.create(null, null, null);
        }

        @Specialization
        protected DynamicObject makeStringFromRope(Rope rope, NotProvided encoding, NotProvided codeRange) {
            return allocate(coreLibrary().getStringClass(), Layouts.STRING.build(false, false, rope, null));
        }

        @Specialization
        protected DynamicObject makeStringFromBytes(byte[] bytes, Encoding encoding, CodeRange codeRange) {
            final LeafRope rope = makeLeafRopeNode.executeMake(bytes, encoding, codeRange, NotProvided.INSTANCE);

            return allocate(coreLibrary().getStringClass(), Layouts.STRING.build(false, false, rope, null));
        }

        private DynamicObject allocate(DynamicObject object, Object[] values) {
            if (allocateObjectNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                allocateObjectNode = insert(AllocateObjectNode.create());
            }
            return allocateObjectNode.allocate(object, values);
        }

        @Specialization(guards = "is7Bit(codeRange)")
        protected DynamicObject makeAsciiStringFromString(String string, Encoding encoding, CodeRange codeRange) {
            final byte[] bytes = RopeOperations.encodeAsciiBytes(string);

            return makeStringFromBytes(bytes, encoding, codeRange);
        }

        @Specialization(guards = "!is7Bit(codeRange)")
        protected DynamicObject makeStringFromString(String string, Encoding encoding, CodeRange codeRange) {
            final byte[] bytes = StringOperations.encodeBytes(string, encoding);

            return makeStringFromBytes(bytes, encoding, codeRange);
        }

        protected static boolean is7Bit(CodeRange codeRange) {
            return codeRange == CR_7BIT;
        }

    }

    @CoreMethod(names = "__allocate__", constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Child private AllocateObjectNode allocateObjectNode = AllocateObjectNode.create();

        @Specialization
        public DynamicObject allocate(DynamicObject rubyClass) {
            return allocateObjectNode.allocate(rubyClass, Layouts.STRING.build(false, false, EMPTY_ASCII_8BIT_ROPE, null));
        }

    }

    @CoreMethod(names = "+", required = 1)
    @NodeChildren({
        @NodeChild(type = RubyNode.class, value = "string"),
        @NodeChild(type = RubyNode.class, value = "other")
    })
    @ImportStatic(StringGuards.class)
    public abstract static class AddNode extends CoreMethodNode {

        @Child private AllocateObjectNode allocateObjectNode = AllocateObjectNode.create();
        @Child private TaintResultNode taintResultNode = new TaintResultNode();

        @CreateCast("other") public RubyNode coerceOtherToString(RubyNode other) {
            return ToStrNodeGen.create(other);
        }

        @Specialization(guards = "isRubyString(other)")
        public DynamicObject add(DynamicObject string, DynamicObject other,
                                 @Cached("create()") StringAppendNode stringAppendNode) {
            final Rope concatRope = stringAppendNode.executeStringAppend(string, other);

            final DynamicObject ret = allocateObjectNode.allocate(coreLibrary().getStringClass(), Layouts.STRING.build(false, false, concatRope, null));

            taintResultNode.maybeTaint(string, ret);
            taintResultNode.maybeTaint(other, ret);

            return ret;
        }

    }

    @CoreMethod(names = "*", required = 1, lowerFixnum = 1, taintFrom = 0)
    @ImportStatic(StringGuards.class)
    public abstract static class MulNode extends CoreMethodArrayArgumentsNode {

        @Child private AllocateObjectNode allocateObjectNode = AllocateObjectNode.create();
        @Child private ToIntNode toIntNode;

        public abstract DynamicObject executeInt(VirtualFrame frame, DynamicObject string, int times);

        @Specialization(guards = "times < 0")
        public DynamicObject multiplyTimesNegative(DynamicObject string, long times) {
            throw new RaiseException(coreExceptions().argumentError("negative argument", this));
        }

        @Specialization(guards = { "times >= 0", "!isEmpty(string)" })
        public DynamicObject multiply(DynamicObject string, int times,
                                      @Cached("create()") RepeatNode repeatNode) {
            final Rope repeated = repeatNode.executeRepeat(rope(string), times);

            return allocateObjectNode.allocate(Layouts.BASIC_OBJECT.getLogicalClass(string), Layouts.STRING.build(false, false, repeated, null));
        }

        @Specialization(guards = { "times >= 0", "!isEmpty(string)", "!fitsInInteger(times)" })
        public DynamicObject multiply(DynamicObject string, long times) {
            throw new RaiseException(coreExceptions().argumentError("'long' is too big to convert into 'int'", this));
        }

        @Specialization(guards = { "times >= 0", "isEmpty(string)" })
        public DynamicObject multiplyEmpty(DynamicObject string, long times,
                @Cached("create()") RopeNodes.RepeatNode repeatNode) {
            final Rope repeated = repeatNode.executeRepeat(rope(string), 0);

            return allocateObjectNode.allocate(Layouts.BASIC_OBJECT.getLogicalClass(string), Layouts.STRING.build(false, false, repeated, null));
        }

        @Specialization(guards = "isRubyBignum(times)")
        public DynamicObject multiply(DynamicObject string, DynamicObject times) {
            throw new RaiseException(coreExceptions().rangeError("bignum too big to convert into `int'", this));
        }

        @Specialization(guards = { "!isRubyBignum(times)", "!isInteger(times)", "!isLong(times)" })
        public DynamicObject multiply(VirtualFrame frame, DynamicObject string, Object times) {
            if (toIntNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toIntNode = insert(ToIntNode.create());
            }

            return executeInt(frame, string, toIntNode.doInt(frame, times));
        }
    }

    @CoreMethod(names = {"==", "===", "eql?"}, required = 1)
    public abstract static class EqualNode extends CoreMethodArrayArgumentsNode {

        @Child private StringEqualNode stringEqualNode = StringEqualNodeGen.create(null, null);
        @Child private KernelNodes.RespondToNode respondToNode;
        @Child private CallDispatchHeadNode objectEqualNode;
        @Child private CheckLayoutNode checkLayoutNode;

        @Specialization(guards = "isRubyString(b)")
        public boolean equal(DynamicObject a, DynamicObject b) {
            return stringEqualNode.executeStringEqual(a, b);
        }

        @Specialization(guards = "!isRubyString(b)")
        public boolean equal(VirtualFrame frame, DynamicObject a, Object b) {
            if (respondToNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                respondToNode = insert(KernelNodesFactory.RespondToNodeFactory.create(null, null, null));
            }

            if (respondToNode.doesRespondToString(frame, b, coreStrings().TO_STR.createInstance(), false)) {
                if (objectEqualNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    objectEqualNode = insert(CallDispatchHeadNode.create());
                }

                return objectEqualNode.callBoolean(frame, b, "==", a);
            }

            return false;
        }

        protected boolean isRubyString(DynamicObject object) {
            if (checkLayoutNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                checkLayoutNode = insert(new CheckLayoutNode());
            }

            return checkLayoutNode.isString(object);
        }
    }

    @Primitive(name = "string_cmp")
    public abstract static class CompareNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isRubyString(b)")
        public int compare(DynamicObject a, DynamicObject b,
                           @Cached("createBinaryProfile()") ConditionProfile sameRopeProfile,
                           @Cached("createBinaryProfile()") ConditionProfile equalSubsequenceProfile,
                           @Cached("createBinaryProfile()") ConditionProfile equalLengthProfile,
                           @Cached("createBinaryProfile()") ConditionProfile firstStringShorterProfile,
                           @Cached("createBinaryProfile()") ConditionProfile greaterThanProfile,
                           @Cached("createBinaryProfile()") ConditionProfile equalProfile,
                           @Cached("createBinaryProfile()") ConditionProfile notComparableProfile,
                           @Cached("createBinaryProfile()") ConditionProfile encodingIndexGreaterThanProfile,
                           @Cached("create()") RopeNodes.BytesNode firstBytesNode,
                           @Cached("create()") RopeNodes.BytesNode secondBytesNode) {
            // Taken from org.jruby.RubyString#op_cmp

            final Rope firstRope = rope(a);
            final Rope secondRope = rope(b);

            if (sameRopeProfile.profile(firstRope == secondRope)) {
                return 0;
            }

            final boolean firstRopeShorter = firstStringShorterProfile.profile(firstRope.byteLength() < secondRope.byteLength());
            final int memcmpLength;
            if (firstRopeShorter) {
                memcmpLength = firstRope.byteLength();
            } else {
                memcmpLength = secondRope.byteLength();
            }

            final byte[] bytes = firstBytesNode.execute(firstRope);
            final byte[] otherBytes = secondBytesNode.execute(secondRope);

            final int ret;
            final int cmp = ArrayUtils.memcmp(bytes, 0, otherBytes, 0, memcmpLength);
            if (equalSubsequenceProfile.profile(cmp == 0)) {
                if (equalLengthProfile.profile(firstRope.byteLength() == secondRope.byteLength())) {
                    ret = 0;
                } else {
                    if (firstRopeShorter) {
                        ret = -1;
                    } else {
                        ret = 1;
                    }
                }
            } else {
                ret = greaterThanProfile.profile(cmp > 0) ? 1 : -1;
            }

            if (equalProfile.profile(ret == 0)) {
                if (notComparableProfile.profile(!RopeOperations.areComparable(firstRope, secondRope))) {
                    if (encodingIndexGreaterThanProfile.profile(firstRope.getEncoding().getIndex() > secondRope.getEncoding().getIndex())) {
                        return 1;
                    } else {
                        return -1;
                    }
                }
            }

            return ret;
        }

        @Specialization(guards = "!isRubyString(b)")
        public Object compare(VirtualFrame frame, DynamicObject a, Object b) {
            return null;
        }
    }

    @CoreMethod(names = { "<<", "concat" }, required = 1, taintFrom = 1, raiseIfFrozenSelf = true)
    @NodeChildren({
            @NodeChild(type = RubyNode.class, value = "string"),
            @NodeChild(type = RubyNode.class, value = "other")
    })
    @ImportStatic(StringGuards.class)
    public abstract static class ConcatNode extends CoreMethodNode {

        @Specialization(guards = "isRubyString(other)")
        public DynamicObject concat(DynamicObject string, DynamicObject other,
                                    @Cached("create()") StringAppendPrimitiveNode stringAppendNode) {
            return stringAppendNode.executeStringAppend(string, other);
        }

        @Specialization(guards = "!isRubyString(other)")
        public Object concatGeneric(
                VirtualFrame frame,
                DynamicObject string,
                Object other,
                @Cached("create()") CallDispatchHeadNode callNode) {
            return callNode.call(frame, string, "concat_internal", other);
        }

    }

    @CoreMethod(names = { "[]", "slice" }, required = 1, optional = 1, lowerFixnum = { 1, 2 }, taintFrom = 0)
    public abstract static class GetIndexNode extends CoreMethodArrayArgumentsNode {

        @Child private AllocateObjectNode allocateObjectNode = AllocateObjectNode.create();
        @Child private CallDispatchHeadNode includeNode;
        @Child private CallDispatchHeadNode dupNode;
        @Child private NormalizeIndexNode normalizeIndexNode;
        @Child private StringSubstringPrimitiveNode substringNode;
        @Child private CallDispatchHeadNode toIntNode;

        private final BranchProfile outOfBounds = BranchProfile.create();

        @Specialization
        public Object getIndex(VirtualFrame frame, DynamicObject string, int index, NotProvided length) {
            // Check for the only difference from str[index, 1]
            if (index == rope(string).characterLength()) {
                outOfBounds.enter();
                return nil();
            }
            return getSubstringNode().execute(frame, string, index, 1);
        }

        @Specialization(guards = { "!isRubyRange(index)", "!isRubyRegexp(index)", "!isRubyString(index)" })
        public Object getIndex(VirtualFrame frame, DynamicObject string, Object index, NotProvided length) {
            return getIndex(frame, string, toInt(frame, index), length);
        }

        @Specialization(guards = "isIntRange(range)")
        public Object sliceIntegerRange(VirtualFrame frame, DynamicObject string, DynamicObject range, NotProvided length) {
            return sliceRange(frame, string, Layouts.INT_RANGE.getBegin(range), Layouts.INT_RANGE.getEnd(range), Layouts.INT_RANGE.getExcludedEnd(range));
        }

        @Specialization(guards = "isLongRange(range)")
        public Object sliceLongRange(VirtualFrame frame, DynamicObject string, DynamicObject range, NotProvided length) {
            // TODO (nirvdrum 31-Mar-15) The begin and end values should be properly lowered, only if possible.
            return sliceRange(frame, string, (int) Layouts.LONG_RANGE.getBegin(range), (int) Layouts.LONG_RANGE.getEnd(range), Layouts.LONG_RANGE.getExcludedEnd(range));
        }

        @Specialization(guards = "isObjectRange(range)")
        public Object sliceObjectRange(VirtualFrame frame, DynamicObject string, DynamicObject range, NotProvided length) {
            // TODO (nirvdrum 31-Mar-15) The begin and end values may return Fixnums beyond int boundaries and we should handle that -- Bignums are always errors.
            final int coercedBegin = toInt(frame, Layouts.OBJECT_RANGE.getBegin(range));
            final int coercedEnd = toInt(frame, Layouts.OBJECT_RANGE.getEnd(range));

            return sliceRange(frame, string, coercedBegin, coercedEnd, Layouts.OBJECT_RANGE.getExcludedEnd(range));
        }

        private Object sliceRange(VirtualFrame frame, DynamicObject string, int begin, int end, boolean doesExcludeEnd) {
            assert RubyGuards.isRubyString(string);

            if (normalizeIndexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                normalizeIndexNode = insert(StringNodesFactory.NormalizeIndexNodeGen.create(null, null));
            }

            final int stringLength = rope(string).characterLength();
            begin = normalizeIndexNode.executeNormalize(begin, stringLength);

            if (begin < 0 || begin > stringLength) {
                outOfBounds.enter();
                return nil();
            } else {

                if (begin == stringLength) {
                    final RopeBuilder builder = new RopeBuilder();
                    builder.setEncoding(encoding(string));
                    return allocateObjectNode.allocate(Layouts.BASIC_OBJECT.getLogicalClass(string),
                            Layouts.STRING.build(false, false, RopeOperations.withEncodingVerySlow(RopeConstants.EMPTY_ASCII_8BIT_ROPE, encoding(string)), null));
                }

                end = normalizeIndexNode.executeNormalize(end, stringLength);
                int length = StringOperations.clampExclusiveIndex(string, doesExcludeEnd ? end : end + 1);

                if (length > stringLength) {
                    length = stringLength;
                }

                length -= begin;

                if (length < 0) {
                    length = 0;
                }

                return getSubstringNode().execute(frame, string, begin, length);
            }
        }

        @Specialization
        public Object slice(VirtualFrame frame, DynamicObject string, int start, int length) {
            return getSubstringNode().execute(frame, string, start, length);
        }

        @Specialization(guards = "wasProvided(length)")
        public Object slice(VirtualFrame frame, DynamicObject string, int start, Object length) {
            return slice(frame, string, start, toInt(frame, length));
        }

        @Specialization(guards = { "!isRubyRange(start)", "!isRubyRegexp(start)", "!isRubyString(start)", "wasProvided(length)" })
        public Object slice(VirtualFrame frame, DynamicObject string, Object start, Object length) {
            return slice(frame, string, toInt(frame, start), toInt(frame, length));
        }

        @Specialization(guards = "isRubyRegexp(regexp)")
        public Object slice1(
                VirtualFrame frame,
                DynamicObject string,
                DynamicObject regexp,
                NotProvided capture,
                @Cached("createOnSelf()") CallDispatchHeadNode callNode,
                @Cached("createOnSelf()") CallDispatchHeadNode setLastMatchNode,
                @Cached("create()") ReadCallerFrameNode readCallerNode) {
            return sliceCapture(frame, string, regexp, 0, callNode, setLastMatchNode, readCallerNode);
        }

        @Specialization(guards = {"isRubyRegexp(regexp)", "wasProvided(capture)"})
        public Object sliceCapture(
                VirtualFrame frame,
                DynamicObject string,
                DynamicObject regexp,
                Object capture,
                @Cached("createOnSelf()") CallDispatchHeadNode callNode,
                @Cached("createOnSelf()") CallDispatchHeadNode setLastMatchNode,
                @Cached("create()") ReadCallerFrameNode readCallerNode) {
            final Object matchStrPair = callNode.call(frame, string, "subpattern", regexp, capture);

            DynamicObject binding = BindingNodes.createBinding(getContext(), readCallerNode.execute(frame).materialize());
            if (matchStrPair == nil()) {
                setLastMatchNode.call(frame, coreLibrary().getTruffleRegexpOperationsModule(), "set_last_match", nil(), binding);
                return nil();
            }

            final Object[] array = (Object[]) Layouts.ARRAY.getStore((DynamicObject) matchStrPair);

            setLastMatchNode.call(frame, coreLibrary().getTruffleRegexpOperationsModule(), "set_last_match", array[0], binding);

            return array[1];
        }

        @Specialization(guards = "isRubyString(matchStr)")
        public Object slice2(VirtualFrame frame, DynamicObject string, DynamicObject matchStr, NotProvided length) {
            if (includeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                includeNode = insert(CallDispatchHeadNode.create());
            }

            boolean result = includeNode.callBoolean(frame, string, "include?", matchStr);

            if (result) {
                if (dupNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    dupNode = insert(CallDispatchHeadNode.create());
                }

                throw new TaintResultNode.DoNotTaint(dupNode.call(frame, matchStr, "dup"));
            }

            return nil();
        }

        private StringSubstringPrimitiveNode getSubstringNode() {
            if (substringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                substringNode = insert(StringNodesFactory.StringSubstringPrimitiveNodeFactory.create(null));
            }

            return substringNode;
        }

        private int toInt(VirtualFrame frame, Object value) {
            if (toIntNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toIntNode = insert(CallDispatchHeadNode.createOnSelf());
            }

            return (int) toIntNode.call(frame, coreLibrary().getTruffleTypeModule(), "rb_num2int", value);
        }
    }

    @CoreMethod(names = "ascii_only?")
    @ImportStatic(StringGuards.class)
    public abstract static class ASCIIOnlyNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = { "is7Bit(string)" })
        public boolean asciiOnlyAsciiCompatible7BitCR(DynamicObject string) {
            return true;
        }

        @Specialization(guards = { "!is7Bit(string)" })
        public boolean asciiOnlyAsciiCompatible(DynamicObject string) {
            return false;
        }

    }

    @CoreMethod(names = "b", taintFrom = 0)
    public abstract static class BNode extends CoreMethodArrayArgumentsNode {

        @Child private RopeNodes.WithEncodingNode withEncodingNode = RopeNodesFactory.WithEncodingNodeGen.create(null, null, null);

        @Specialization
        public DynamicObject b(DynamicObject string,
                               @Cached("create()") StringNodes.MakeStringNode makeStringNode,
                               @Cached("createBinaryProfile()") ConditionProfile is7BitProfile,
                               @Cached("createBinaryProfile()") ConditionProfile isAsciiCompatibleProfile) {
            final Rope rope = rope(string);
            final CodeRange newCodeRange;

            if (is7BitProfile.profile(rope.getCodeRange() == CodeRange.CR_7BIT)) {
                // If the rope is already known to be 7-bit, it'll continue to be 7-bit in ASCII 8-bit.
                newCodeRange = CodeRange.CR_7BIT;
            } else {
                if (isAsciiCompatibleProfile.profile(rope.getEncoding().isAsciiCompatible())) {
                    // If the rope is not 7-bit, but has an ASCII-compatible encoding, then whatever byte sequence it has
                    // (broken or valid) can only be valid in ASCII 8-bit as it's impossible to have a broken binary string.
                    newCodeRange = CodeRange.CR_VALID;
                } else {
                    // If the rope doesn't have an ASCII-compatible encoding, we can't make any guarantees about the code
                    // range in ASCII 8-bit. The byte sequence for this rope may end up being either 7-bit or valid. We
                    // must perform a byte scan to figure it out.
                    newCodeRange = CodeRange.CR_UNKNOWN;
                }
            }

            final Rope newRope = withEncodingNode.executeWithEncoding(rope, ASCIIEncoding.INSTANCE, newCodeRange);

            return makeStringNode.fromRope(newRope);
        }

    }

    @CoreMethod(names = "bytes", needsBlock = true)
    public abstract static class BytesNode extends YieldingCoreMethodNode {

        @Child private RopeNodes.BytesNode bytesNode = RopeNodes.BytesNode.create();

        @Specialization
        public DynamicObject bytes(VirtualFrame frame, DynamicObject string, NotProvided block) {
            final Rope rope = rope(string);
            final byte[] bytes = bytesNode.execute(rope);

            final int[] store = new int[bytes.length];

            for (int n = 0; n < store.length; n++) {
                store[n] = bytes[n] & 0xFF;
            }

            return createArray(store, store.length);
        }

        @Specialization
        public DynamicObject bytes(DynamicObject string, DynamicObject block) {
            Rope rope = rope(string);
            byte[] bytes = bytesNode.execute(rope);

            for (int i = 0; i < bytes.length; i++) {
                yield(block, bytes[i] & 0xff);
            }

            return string;
        }

    }

    @CoreMethod(names = "bytesize")
    public abstract static class ByteSizeNode extends CoreMethodArrayArgumentsNode {

        public static ByteSizeNode create() {
            return StringNodesFactory.ByteSizeNodeFactory.create(null);
        }

        public abstract int executeByteSize(DynamicObject string);

        @Specialization
        protected int byteSize(DynamicObject string) {
            return rope(string).byteLength();
        }

    }

    @CoreMethod(names = "casecmp", required = 1)
    @NodeChildren({
        @NodeChild(type = RubyNode.class, value = "string"),
        @NodeChild(type = RubyNode.class, value = "other")
    })
    public abstract static class CaseCmpNode extends CoreMethodNode {

        @Child private EncodingNodes.NegotiateCompatibleEncodingNode negotiateCompatibleEncodingNode = EncodingNodesFactory.NegotiateCompatibleEncodingNodeGen.create(null, null);
        private final ConditionProfile incompatibleEncodingProfile = ConditionProfile.createBinaryProfile();

        @CreateCast("other") public RubyNode coerceOtherToString(RubyNode other) {
            return ToStrNodeGen.create(other);
        }

        @Specialization(guards = {"isRubyString(other)", "bothSingleByteOptimizable(string, other)"})
        public Object caseCmpSingleByte(DynamicObject string, DynamicObject other) {
            // Taken from org.jruby.RubyString#casecmp19.

            final Encoding encoding = negotiateCompatibleEncodingNode.executeNegotiate(string, other);
            if (incompatibleEncodingProfile.profile(encoding == null)) {
                return nil();
            }

            return RopeOperations.caseInsensitiveCmp(rope(string), rope(other));
        }

        @Specialization(guards = {"isRubyString(other)", "!bothSingleByteOptimizable(string, other)"})
        public Object caseCmp(DynamicObject string, DynamicObject other) {
            // Taken from org.jruby.RubyString#casecmp19 and

            final Encoding encoding = negotiateCompatibleEncodingNode.executeNegotiate(string, other);

            if (incompatibleEncodingProfile.profile(encoding == null)) {
                return nil();
            }

            return StringSupport.multiByteCasecmp(encoding, rope(string), rope(other));
        }

        public static boolean bothSingleByteOptimizable(DynamicObject string, DynamicObject other) {
            assert RubyGuards.isRubyString(string);
            assert RubyGuards.isRubyString(other);

            return rope(string).isSingleByteOptimizable() && rope(other).isSingleByteOptimizable();
        }
    }

    @CoreMethod(names = "count", rest = true)
    public abstract static class CountNode extends CoreMethodArrayArgumentsNode {

        @Child private ToStrNode toStr = ToStrNode.create();
        @Child private CountRopesNode countRopesNode = CountRopesNode.create();

        @Specialization(guards = "args.length == size", limit = "getCacheLimit()")
        public int count(VirtualFrame frame, DynamicObject string, Object[] args,
                @Cached("args.length") int size) {
            final Rope[] ropes = argRopes(frame, args);
            return countRopesNode.executeCount(string, ropes);
        }

        @Specialization(replaces = "count")
        public int countSlow(VirtualFrame frame, DynamicObject string, Object[] args) {
            final Rope[] ropes = argRopesSlow(frame, args);
            return countRopesNode.executeCount(string, ropes);
        }

        @ExplodeLoop
        protected Rope[] argRopes(VirtualFrame frame, Object[] args) {
            final Rope[] strs = new Rope[args.length];
            for (int i = 0; i < args.length; i++) {
                strs[i] = rope(toStr.executeToStr(frame, args[i]));
            }
            return strs;
        }

        protected Rope[] argRopesSlow(VirtualFrame frame, Object[] args) {
            final Rope[] strs = new Rope[args.length];
            for (int i = 0; i < args.length; i++) {
                strs[i] = rope(toStr.executeToStr(frame, args[i]));
            }
            return strs;
        }

        protected int getCacheLimit() {
            return getContext().getOptions().DEFAULT_CACHE;
        }
    }

    @ImportStatic({ StringGuards.class, StringOperations.class })
    public abstract static class CountRopesNode extends TrTableNode {

        public static CountRopesNode create() {
            return CountRopesNodeFactory.create(null);
        }

        public abstract int executeCount(DynamicObject string, Rope[] ropes);

        @Specialization(guards = "isEmpty(string)")
        public int count(DynamicObject string, Object[] args) {
            return 0;
        }

        @Specialization(guards = { "cachedArgs.length > 0",
                "!isEmpty(string)", "cachedArgs.length == args.length",
                "argsMatch(cachedArgs, args)", "encodingsMatch(string, cachedEncoding)"
        })
        public int countFast(DynamicObject string, Rope[] args,
                @Cached(value = "args", dimensions = 1) Rope[] cachedArgs,
                @Cached("encoding(string)") Encoding cachedEncoding,
                @Cached(value = "squeeze()", dimensions = 1) boolean[] squeeze,
                @Cached("findEncoding(string, cachedArgs)") Encoding compatEncoding,
                @Cached("makeTables(string, cachedArgs, squeeze, compatEncoding)") TrTables tables) {
            return processStr(string, squeeze, compatEncoding, tables);
        }

        @TruffleBoundary
        private int processStr(DynamicObject string, boolean[] squeeze, Encoding compatEncoding, TrTables tables) {
            return StringSupport.strCount(rope(string), squeeze, tables, compatEncoding);
        }

        @Specialization(guards = "!isEmpty(string)")
        public int count(DynamicObject string, Rope[] ropes,
                @Cached("create()") BranchProfile errorProfile) {
            if (ropes.length == 0) {
                errorProfile.enter();
                throw new RaiseException(coreExceptions().argumentErrorEmptyVarargs(this));
            }

            Encoding enc = findEncoding(string, ropes);
            return countSlow(string, ropes, enc);
        }

        @TruffleBoundary
        private int countSlow(DynamicObject string, Rope[] ropes, Encoding enc) {
            assert RubyGuards.isRubyString(string);

            final boolean[] table = squeeze();
            final StringSupport.TrTables tables = makeTables(string, ropes, table, enc);
            return processStr(string, table, enc, tables);
        }
    }

    public abstract static class TrTableNode extends CoreMethodArrayArgumentsNode {
        @Child protected CheckRopeEncodingNode checkEncodingNode = CheckRopeEncodingNode.create();
        @Child protected RopeNodes.EqualNode ropeEqualNode = RopeNodes.EqualNode.create();

        protected boolean[] squeeze() {
            return new boolean[StringSupport.TRANS_SIZE + 1];
        }

        protected Encoding findEncoding(DynamicObject string, Rope[] ropes) {
            final Rope rope = StringOperations.rope(string);
            Encoding enc = checkEncodingNode.executeCheckEncoding(rope, ropes[0]);
            for (int i = 1; i < ropes.length; i++) {
                enc = checkEncodingNode.executeCheckEncoding(rope, ropes[i]);
            }
            return enc;
        }

        protected TrTables makeTables(DynamicObject string, Rope[] ropes, boolean[] squeeze, Encoding enc) {
            // The trSetupTable method will consume the bytes from the rope one encoded character at a time and
            // build a TrTable from this. Previously we started with the encoding of rope zero, and at each
            // stage found a compatible encoding to build that TrTable with. Although we now calculate a single
            // encoding with which to build the tables it must be compatible with all ropes, so will not
            // affect the consumption of characters from those ropes.
            StringSupport.TrTables tables = StringSupport.trSetupTable(ropes[0], squeeze, null, true, enc);

            for (int i = 1; i < ropes.length; i++) {
                tables = StringSupport.trSetupTable(ropes[i], squeeze, tables, false, enc);
            }
            return tables;
        }

        protected boolean encodingsMatch(DynamicObject string, Encoding encoding) {
            return encoding == StringOperations.encoding(string);
        }

        @ExplodeLoop
        protected boolean argsMatch(Rope[] cachedRopes, Rope[] ropes) {
            for (int i = 0; i < cachedRopes.length; i++) {
                if (!ropeEqualNode.execute(cachedRopes[i], ropes[i])) {
                    return false;
                }
            }
            return true;
        }
    }

    @CoreMethod(names = "delete!", rest = true, raiseIfFrozenSelf = true)
    @ImportStatic(StringGuards.class)
    public abstract static class DeleteBangNode extends CoreMethodArrayArgumentsNode {

        @Child private ToStrNode toStr = ToStrNode.create();
        @Child private DeleteBangRopesNode deleteBangRopesNode = DeleteBangRopesNode.create();

        public static DeleteBangNode create() {
            return DeleteBangNodeFactory.create(null);
        }

        public abstract DynamicObject executeDeleteBang(VirtualFrame frame, DynamicObject string, Object[] args);

        @Specialization(guards = "args.length == size", limit = "getCacheLimit()")
        public DynamicObject deleteBang(VirtualFrame frame, DynamicObject string, Object[] args,
                @Cached("args.length") int size) {
            final Rope[] ropes = argRopes(frame, args);
            return deleteBangRopesNode.executeDeleteBang(string, ropes);
        }

        @Specialization(replaces = "deleteBang")
        public DynamicObject deleteBangSlow(VirtualFrame frame, DynamicObject string, Object[] args) {
            final Rope[] ropes = argRopesSlow(frame, args);
            return deleteBangRopesNode.executeDeleteBang(string, ropes);
        }

        @ExplodeLoop
        protected Rope[] argRopes(VirtualFrame frame, Object[] args) {
            final Rope[] strs = new Rope[args.length];
            for (int i = 0; i < args.length; i++) {
                strs[i] = rope(toStr.executeToStr(frame, args[i]));
            }
            return strs;
        }

        protected Rope[] argRopesSlow(VirtualFrame frame, Object[] args) {
            final Rope[] strs = new Rope[args.length];
            for (int i = 0; i < args.length; i++) {
                strs[i] = rope(toStr.executeToStr(frame, args[i]));
            }
            return strs;
        }

        protected int getCacheLimit() {
            return getContext().getOptions().DEFAULT_CACHE;
        }
    }

    @ImportStatic({ StringGuards.class, StringOperations.class })
    public abstract static class DeleteBangRopesNode extends TrTableNode {

        public static DeleteBangRopesNode create() {
            return DeleteBangRopesNodeFactory.create(null);
        }

        public abstract DynamicObject executeDeleteBang(DynamicObject string, Rope[] ropes);

        @Specialization(guards = "isEmpty(string)")
        public DynamicObject deleteBangEmpty(DynamicObject string, Object[] args) {
            return nil();
        }

        @Specialization(guards = { "!isEmpty(string)", "cachedArgs.length == args.length",
                "argsMatch(cachedArgs, args)", "encodingsMatch(string, cachedEncoding)"
        })
        public DynamicObject deleteBangFast(DynamicObject string, Rope[] args,
                @Cached(value = "args", dimensions = 1) Rope[] cachedArgs,
                @Cached("encoding(string)") Encoding cachedEncoding,
                @Cached(value = "squeeze()", dimensions = 1) boolean[] squeeze,
                @Cached("findEncoding(string, cachedArgs)") Encoding compatEncoding,
                @Cached("makeTables(string, cachedArgs, squeeze, compatEncoding)") TrTables tables,
                @Cached("create()") BranchProfile nullProfile) {
            final Rope processedRope = processStr(string, squeeze, compatEncoding, tables);
            if (processedRope == null) {
                nullProfile.enter();
                return nil();
            }

            StringOperations.setRope(string, processedRope);

            return string;
        }

        @Specialization(guards = "!isEmpty(string)")
        public DynamicObject deleteBang(DynamicObject string, Rope[] args,
                @Cached("create()") BranchProfile errorProfile) {
            if (args.length == 0) {
                errorProfile.enter();
                throw new RaiseException(coreExceptions().argumentErrorEmptyVarargs(this));
            }

            Encoding enc = findEncoding(string, args);

            return deleteBangSlow(string, args, enc);
        }

        @TruffleBoundary
        private DynamicObject deleteBangSlow(DynamicObject string, Rope[] ropes, Encoding enc) {
            assert RubyGuards.isRubyString(string);

            final boolean[] squeeze = new boolean[StringSupport.TRANS_SIZE + 1];

            final StringSupport.TrTables tables = makeTables(string, ropes, squeeze, enc);

            final Rope processedRope = processStr(string, squeeze, enc, tables);
            if (processedRope == null) {
                return nil();
            }

            StringOperations.setRope(string, processedRope);

            return string;
        }

        @TruffleBoundary
        private Rope processStr(DynamicObject string, boolean[] squeeze, Encoding enc, StringSupport.TrTables tables) {
            return StringSupport.delete_bangCommon19(rope(string), squeeze, tables, enc);
        }
    }

    @CoreMethod(names = "downcase!", raiseIfFrozenSelf = true)
    @ImportStatic(StringGuards.class)
    public abstract static class DowncaseBangNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isSingleByteOptimizable(string)")
        public DynamicObject downcaseSingleByte(DynamicObject string,
                                              @Cached("createUpperToLower()") InvertAsciiCaseNode invertAsciiCaseNode) {
            return invertAsciiCaseNode.executeInvert(string);
        }

        @Specialization(guards = "!isSingleByteOptimizable(string)")
        public DynamicObject downcase(DynamicObject string,
                                      @Cached("create()") RopeNodes.MakeLeafRopeNode makeLeafRopeNode,
                                      @Cached("createBinaryProfile()") ConditionProfile dummyEncodingProfile,
                                      @Cached("createBinaryProfile()") ConditionProfile modifiedProfile) {
            final Rope rope = rope(string);
            final Encoding encoding = rope.getEncoding();

            if (dummyEncodingProfile.profile(encoding.isDummy())) {
                throw new RaiseException(coreExceptions().encodingCompatibilityErrorIncompatibleWithOperation(encoding, this));
            }

            final byte[] outputBytes = rope.getBytesCopy();
            final boolean modified = StringSupport.multiByteDowncase(encoding, outputBytes, 0, outputBytes.length);

            if (modifiedProfile.profile(modified)) {
                StringOperations.setRope(string, makeLeafRopeNode.executeMake(outputBytes, rope.getEncoding(), rope.getCodeRange(), rope.characterLength()));

                return string;
            } else {
                return nil();
            }
        }

    }

    @CoreMethod(names = "each_byte", needsBlock = true, enumeratorSize = "bytesize")
    public abstract static class EachByteNode extends YieldingCoreMethodNode {

        @Specialization
        public DynamicObject eachByte(DynamicObject string, DynamicObject block,
                @Cached("create()") RopeNodes.BytesNode bytesNode,
                @Cached("create()") RopeNodes.BytesNode updatedBytesNode,
                @Cached("createBinaryProfile()") ConditionProfile ropeChangedProfile) {
            Rope rope = rope(string);
            byte[] bytes = bytesNode.execute(rope);

            for (int i = 0; i < bytes.length; i++) {
                yield(block, bytes[i] & 0xff);

                Rope updatedRope = rope(string);
                if (ropeChangedProfile.profile(rope != updatedRope)) {
                    rope = updatedRope;
                    bytes = updatedBytesNode.execute(updatedRope);
                }
            }

            return string;
        }

    }

    @CoreMethod(names = "each_char", needsBlock = true, enumeratorSize = "size")
    @ImportStatic(StringGuards.class)
    public abstract static class EachCharNode extends YieldingCoreMethodNode {

        @Child private AllocateObjectNode allocateObjectNode = AllocateObjectNode.create();
        @Child private RopeNodes.SubstringNode substringNode = RopeNodes.SubstringNode.create();
        @Child private TaintResultNode taintResultNode;
        @Child private RopeNodes.BytesNode bytesNode = RopeNodes.BytesNode.create();

        @Specialization(guards = "!isBrokenCodeRange(string)")
        public DynamicObject eachChar(DynamicObject string, DynamicObject block,
                @Cached("create()") RopeNodes.EncodingLengthNode encodingLengthNode) {
            final Rope rope = rope(string);
            final byte[] ptrBytes = bytesNode.execute(rope);
            final int len = ptrBytes.length;
            final Encoding enc = rope.getEncoding();

            int n;

            for (int i = 0; i < len; i += n) {
                n = encodingLengthNode.executeLength(enc, ptrBytes, i, len);

                yield(block, substr(rope, string, i, n));
            }

            return string;
        }

        @Specialization(guards = "isBrokenCodeRange(string)")
        public DynamicObject eachCharMultiByteEncoding(DynamicObject string, DynamicObject block) {
            final Rope rope = rope(string);
            final byte[] ptrBytes = bytesNode.execute(rope);
            final int len = ptrBytes.length;
            final Encoding enc = rope.getEncoding();

            int n;

            for (int i = 0; i < len; i += n) {
                n = StringSupport.length(enc, ptrBytes, i, len);

                yield(block, substr(rope, string, i, n));
            }

            return string;
        }

        // TODO (nirvdrum 10-Mar-15): This was extracted from JRuby, but likely will need to become a primitive.
        // Don't be tempted to extract the rope from the passed string. If the block being yielded to modifies the
        // source string, you'll get a different rope. Unlike String#each_byte, String#each_char does not make
        // modifications to the string visible to the rest of the iteration.
        private Object substr(Rope rope, DynamicObject string, int beg, int len) {
            int length = rope.byteLength();
            if (len < 0 || beg > length) {
                return nil();
            }

            if (beg < 0) {
                beg += length;
                if (beg < 0) {
                    return nil();
                }
            }

            int end = Math.min(length, beg + len);

            final Rope substringRope = substringNode.executeSubstring(rope, beg, end - beg);

            if (taintResultNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                taintResultNode = insert(new TaintResultNode());
            }

            final DynamicObject ret = allocateObjectNode.allocate(Layouts.BASIC_OBJECT.getLogicalClass(string), Layouts.STRING.build(false, false, substringRope, null));

            return taintResultNode.maybeTaint(string, ret);
        }
    }

    @CoreMethod(names = "force_encoding", required = 1, raiseIfFrozenSelf = true)
    public abstract static class ForceEncodingNode extends CoreMethodArrayArgumentsNode {

        @Child private RopeNodes.WithEncodingNode withEncodingNode = RopeNodesFactory.WithEncodingNodeGen.create(null, null, null);
        private final ConditionProfile differentEncodingProfile = ConditionProfile.createBinaryProfile();

        @Specialization(guards = "isRubyString(encodingName)")
        public DynamicObject forceEncodingString(DynamicObject string, DynamicObject encodingName) {
            final DynamicObject encoding = getContext().getEncodingManager().getRubyEncoding(StringOperations.getString(encodingName));
            return forceEncodingEncoding(string, encoding);
        }

        @Specialization(guards = "isRubyEncoding(rubyEncoding)")
        public DynamicObject forceEncodingEncoding(DynamicObject string, DynamicObject rubyEncoding) {
            final Encoding encoding = EncodingOperations.getEncoding(rubyEncoding);
            final Rope rope = rope(string);

            if (differentEncodingProfile.profile(rope.getEncoding() != encoding)) {
                final Rope newRope = withEncodingNode.executeWithEncoding(rope, encoding, CodeRange.CR_UNKNOWN);
                StringOperations.setRope(string, newRope);
            }

            return string;
        }

        @Specialization(guards = { "!isRubyString(encoding)", "!isRubyEncoding(encoding)" })
        public DynamicObject forceEncoding(VirtualFrame frame, DynamicObject string, Object encoding,
                @Cached("create()") ToStrNode toStrNode) {
            return forceEncodingString(string, toStrNode.executeToStr(frame, encoding));
        }

    }

    @CoreMethod(names = "getbyte", required = 1, lowerFixnum = 1)
    public abstract static class GetByteNode extends CoreMethodArrayArgumentsNode {

        @Child private NormalizeIndexNode normalizeIndexNode = StringNodesFactory.NormalizeIndexNodeGen.create(null, null);
        @Child private RopeNodes.GetByteNode ropeGetByteNode = RopeNodesFactory.GetByteNodeGen.create(null, null);

        @Specialization
        public Object getByte(DynamicObject string, int index,
                              @Cached("createBinaryProfile()") ConditionProfile negativeIndexProfile,
                              @Cached("createBinaryProfile()") ConditionProfile indexOutOfBoundsProfile) {
            final Rope rope = rope(string);
            final int normalizedIndex = normalizeIndexNode.executeNormalize(index, rope.byteLength());

            if (indexOutOfBoundsProfile.profile((normalizedIndex < 0) || (normalizedIndex >= rope.byteLength()))) {
                return nil();
            }

            return ropeGetByteNode.executeGetByte(rope, normalizedIndex);
        }

    }

    @CoreMethod(names = "hash")
    public abstract static class HashNode extends CoreMethodArrayArgumentsNode {

        protected static final int CLASS_SALT = 54008340; // random number, stops hashes for similar values but different classes being the same, static because we want deterministic hashes

        @Specialization
        public long hash(DynamicObject string,
                @Cached("create()") RopeNodes.HashNode hashNode) {
            return getContext().getHashing().hash(CLASS_SALT, hashNode.execute(rope(string)));
        }

    }

    @Primitive(name = "string_initialize")
    public abstract static class InitializeNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isRubyEncoding(encoding)")
        public DynamicObject initializeJavaString(DynamicObject self, String from, DynamicObject encoding) {
            StringOperations.setRope(self, StringOperations.encodeRope(from, EncodingOperations.getEncoding(encoding)));
            return self;
        }

        @Specialization(guards = "isNil(encoding)")
        public DynamicObject initializeJavaStringNoEncoding(DynamicObject self, String from, DynamicObject encoding) {
            throw new RaiseException(coreExceptions().argumentError("String.new(javaString) needs to be called with an Encoding like String.new(javaString, encoding: someEncoding)", this));
        }

        @Specialization(guards = "isRubyString(from)")
        public DynamicObject initialize(DynamicObject self, DynamicObject from, DynamicObject encoding) {
            StringOperations.setRope(self, rope(from));
            return self;
        }

        @Specialization(guards = {"!isRubyString(from)", "!isString(from)"})
        public DynamicObject initialize(VirtualFrame frame, DynamicObject self, Object from, DynamicObject encoding,
                @Cached("create()") ToStrNode toStrNode) {
            StringOperations.setRope(self, rope(toStrNode.executeToStr(frame, from)));
            return self;
        }

    }

    @Primitive(name = "string_get_coderange", needsSelf = false)
    public abstract static class GetCodeRangeNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isRubyString(str)")
        public int getCodeRange(DynamicObject str) {
            return Layouts.STRING.getRope(str).getCodeRange().toInt();
        }

    }

    @Primitive(name = "string_get_rstring_ptr", needsSelf = false)
    public abstract static class GetRstringPtrNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "isRubyString(string)")
        public DynamicObject getRstringPtr(DynamicObject string) {
            final DynamicObject existingPtr = Layouts.STRING.getRstringPtr(string);
            if (existingPtr != null) {
                return existingPtr;
            } else {
                return nil();
            }
        }

    }

    @Primitive(name = "string_set_rstring_ptr", needsSelf = false)
    public abstract static class SetRstringPtrNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "isRubyString(string)")
        public DynamicObject setRstringPtr(DynamicObject string, DynamicObject rstringPtr) {
            Layouts.STRING.setRstringPtr(string, rstringPtr);
            return rstringPtr;
        }

    }

    @Primitive(name = "string_get_rope", needsSelf = false)
    public abstract static class GetRopeNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isRubyString(str)")
        public Rope getRope(DynamicObject str) {
            return Layouts.STRING.getRope(str);
        }
    }

    @CoreMethod(names = "initialize_copy", required = 1)
    public abstract static class InitializeCopyNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "self == from")
        public Object initializeCopySelfIsSameAsFrom(DynamicObject self, DynamicObject from) {
            return self;
        }


        @Specialization(guards = { "self != from", "isRubyString(from)" })
        public Object initializeCopy(DynamicObject self, DynamicObject from) {
            StringOperations.setRope(self, rope(from));

            return self;
        }

    }

    @CoreMethod(names = "lstrip!", raiseIfFrozenSelf = true)
    @ImportStatic(StringGuards.class)
    public abstract static class LstripBangNode extends CoreMethodArrayArgumentsNode {

        @Child private RopeNodes.GetCodePointNode getCodePointNode = RopeNodes.GetCodePointNode.create();
        @Child private RopeNodes.SubstringNode substringNode = RopeNodes.SubstringNode.create();

        @Specialization(guards = "isEmpty(string)")
        public DynamicObject lstripBangEmptyString(DynamicObject string) {
            return nil();
        }

        @Specialization(guards = { "!isEmpty(string)", "isSingleByteOptimizable(string)" })
        public Object lstripBangSingleByte(DynamicObject string,
                                           @Cached("create()") RopeNodes.BytesNode bytesNode,
                                           @Cached("createBinaryProfile()") ConditionProfile noopProfile) {
            // Taken from org.jruby.RubyString#lstrip_bang19 and org.jruby.RubyString#singleByteLStrip.

            final Rope rope = rope(string);
            final int firstCodePoint = getCodePointNode.executeGetCodePoint(rope, 0);

            // Check the first code point to see if it's a space. In the case of strings without leading spaces,
            // this check can avoid having to materialize the entire byte[] (a potentially expensive operation
            // for ropes) and can avoid having to compile the while loop.
            if (noopProfile.profile(!StringSupport.isAsciiSpace((byte) firstCodePoint))) {
                return nil();
            }

            final int end = rope.byteLength();
            final byte[] bytes = bytesNode.execute(rope);

            int p = 0;
            while (p < end && StringSupport.isAsciiSpace(bytes[p])) {
                p++;
            }

            StringOperations.setRope(string, substringNode.executeSubstring(rope, p, end - p));

            return string;
        }

        @TruffleBoundary
        @Specialization(guards = { "!isEmpty(string)", "!isSingleByteOptimizable(string)" })
        public Object lstripBang(DynamicObject string) {
            // Taken from org.jruby.RubyString#lstrip_bang19 and org.jruby.RubyString#multiByteLStrip.

            final Rope rope = rope(string);
            final Encoding enc = RopeOperations.STR_ENC_GET(rope);
            final int s = 0;
            final int end = s + rope.byteLength();

            int p = s;
            while (p < end) {
                int c = getCodePointNode.executeGetCodePoint(rope, p);
                if (!ASCIIEncoding.INSTANCE.isSpace(c)) {
                    break;
                }
                p += StringSupport.codeLength(enc, c);
            }

            if (p > s) {
                StringOperations.setRope(string, substringNode.executeSubstring(rope, p - s, end - p));

                return string;
            }

            return nil();
        }
    }

    @CoreMethod(names = "ord")
    @ImportStatic(StringGuards.class)
    public abstract static class OrdNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isEmpty(string)")
        public int ordEmpty(DynamicObject string) {
            throw new RaiseException(coreExceptions().argumentError("empty string", this));
        }

        @Specialization(guards = "!isEmpty(string)")
        public int ord(DynamicObject string,
                       @Cached("create()") RopeNodes.GetCodePointNode getCodePointNode) {
            return getCodePointNode.executeGetCodePoint(rope(string), 0);
        }

    }

    @CoreMethod(names = "replace", required = 1, raiseIfFrozenSelf = true, taintFrom = 1)
    @NodeChildren({
        @NodeChild(type = RubyNode.class, value = "string"),
        @NodeChild(type = RubyNode.class, value = "other")
    })
    public abstract static class ReplaceNode extends CoreMethodNode {

        @CreateCast("other") public RubyNode coerceOtherToString(RubyNode other) {
            return ToStrNodeGen.create(other);
        }

        @Specialization(guards = "string == other")
        public DynamicObject replaceStringIsSameAsOther(DynamicObject string, DynamicObject other) {
            return string;
        }


        @Specialization(guards = { "string != other", "isRubyString(other)" })
        public DynamicObject replace(DynamicObject string, DynamicObject other) {
            StringOperations.setRope(string, rope(other));

            return string;
        }

    }

    @CoreMethod(names = "rstrip!", raiseIfFrozenSelf = true)
    @ImportStatic(StringGuards.class)
    public abstract static class RstripBangNode extends CoreMethodArrayArgumentsNode {

        @Child private RopeNodes.GetCodePointNode getCodePointNode = RopeNodes.GetCodePointNode.create();
        @Child private RopeNodes.SubstringNode substringNode = RopeNodes.SubstringNode.create();

        @Specialization(guards = "isEmpty(string)")
        public DynamicObject rstripBangEmptyString(DynamicObject string) {
            return nil();
        }

        @Specialization(guards = { "!isEmpty(string)", "isSingleByteOptimizable(string)" })
        public Object rstripBangSingleByte(DynamicObject string,
                                           @Cached("create()") RopeNodes.BytesNode bytesNode,
                                           @Cached("createBinaryProfile()") ConditionProfile noopProfile) {
            // Taken from org.jruby.RubyString#rstrip_bang19 and org.jruby.RubyString#singleByteRStrip19.

            final Rope rope = rope(string);
            final int lastCodePoint = getCodePointNode.executeGetCodePoint(rope, rope.byteLength() - 1);

            // Check the last code point to see if it's a space or NULL. In the case of strings without leading spaces,
            // this check can avoid having to materialize the entire byte[] (a potentially expensive operation
            // for ropes) and can avoid having to compile the while loop.
            final boolean willStrip = lastCodePoint == 0x00 || StringSupport.isAsciiSpace((byte) lastCodePoint);
            if (noopProfile.profile(!willStrip)) {
                return nil();
            }

            final int end = rope.byteLength();
            final byte[] bytes = bytesNode.execute(rope);

            int endp = end - 1;
            while (endp >= 0 && (bytes[endp] == 0 || StringSupport.isAsciiSpace(bytes[endp]))) {
                endp--;
            }

            StringOperations.setRope(string, substringNode.executeSubstring(rope, 0, endp + 1));

            return string;
        }

        @TruffleBoundary
        @Specialization(guards = { "!isEmpty(string)", "!isSingleByteOptimizable(string)" })
        public Object rstripBang(DynamicObject string,
                                 @Cached("createBinaryProfile()") ConditionProfile dummyEncodingProfile) {
            // Taken from org.jruby.RubyString#rstrip_bang19 and org.jruby.RubyString#multiByteRStrip19.

            final Rope rope = rope(string);
            final Encoding enc = RopeOperations.STR_ENC_GET(rope);

            if (dummyEncodingProfile.profile(enc.isDummy())) {
                throw new RaiseException(coreExceptions().encodingCompatibilityErrorIncompatibleWithOperation(enc, this));
            }

            final byte[] bytes = rope.getBytes();
            final int start = 0;
            final int end = rope.byteLength();

            int endp = end;
            int prev;
            while ((prev = prevCharHead(enc, bytes, start, endp, end)) != -1) {
                int point = getCodePointNode.executeGetCodePoint(rope, prev);
                if (point != 0 && !ASCIIEncoding.INSTANCE.isSpace(point)) {
                    break;
                }
                endp = prev;
            }

            if (endp < end) {
                StringOperations.setRope(string, substringNode.executeSubstring(rope, 0, endp - start));

                return string;
            }
            return nil();
        }

        @TruffleBoundary
        private int prevCharHead(Encoding enc, byte[]bytes, int p, int s, int end) {
            return enc.prevCharHead(bytes, p, s, end);
        }
    }

    @Primitive(name = "string_scrub")
    @ImportStatic(StringGuards.class)
    public abstract static class ScrubNode extends PrimitiveArrayArgumentsNode {

        @Child private YieldNode yieldNode = new YieldNode();
        @Child private RopeNodes.ConcatNode concatNode = RopeNodes.ConcatNode.create();
        @Child private RopeNodes.SubstringNode substringNode = RopeNodes.SubstringNode.create();
        @Child private MakeStringNode makeStringNode = StringNodes.MakeStringNode.create();
        @Child private RopeNodes.EncodingLengthNode encodingLengthNode = RopeNodes.EncodingLengthNode.create();
        @Child private RopeNodes.BytesNode bytesNode = RopeNodes.BytesNode.create();

        @Specialization(guards = { "isBrokenCodeRange(string)", "isAsciiCompatible(string)" })
        public DynamicObject scrubAsciiCompat(DynamicObject string, DynamicObject block) {
            final Rope rope = rope(string);
            final Encoding enc = rope.getEncoding();
            Rope buf = RopeConstants.EMPTY_ASCII_8BIT_ROPE;

            final byte[] pBytes = bytesNode.execute(rope);
            final int e = pBytes.length;

            int p = 0;
            int p1 = 0;

            p = StringSupport.searchNonAscii(pBytes, p, e);
            if (p == -1) {
                p = e;
            }
            while (p < e) {
                int ret = encodingLengthNode.executeLength(enc, pBytes, p, e);
                if (MBCLEN_NEEDMORE_P(ret)) {
                    break;
                } else if (MBCLEN_CHARFOUND_P(ret)) {
                    p += MBCLEN_CHARFOUND_LEN(ret);
                } else if (MBCLEN_INVALID_P(ret)) {
                    // p1~p: valid ascii/multibyte chars
                    // p ~e: invalid bytes + unknown bytes
                    int clen = enc.maxLength();
                    if (p1 < p) {
                        buf = concatNode.executeConcat(buf, substringNode.executeSubstring(rope, p1, p - p1), enc);
                    }

                    if (e - p < clen) {
                        clen = e - p;
                    }
                    if (clen <= 2) {
                        clen = 1;
                    } else {
                        final int q = p;
                        clen--;
                        for (; clen > 1; clen--) {
                            ret = StringSupport.encLength(enc, pBytes, q, q + clen);
                            if (MBCLEN_NEEDMORE_P(ret)) {
                                break;
                            } else if (MBCLEN_INVALID_P(ret)) {
                                continue;
                            }
                        }
                    }
                    DynamicObject repl = (DynamicObject) yield(block, makeStringNode.fromRope(substringNode.executeSubstring(rope, p, clen)));
                    buf = concatNode.executeConcat(buf, rope(repl), enc);
                    p += clen;
                    p1 = p;
                    p = StringSupport.searchNonAscii(pBytes, p, e);
                    if (p == -1) {
                        p = e;
                        break;
                    }
                }
            }
            if (p1 < p) {
                buf = concatNode.executeConcat(buf, substringNode.executeSubstring(rope, p1, p - p1), enc);
            }
            if (p < e) {
                DynamicObject repl = (DynamicObject) yield(block, makeStringNode.fromRope(substringNode.executeSubstring(rope, p, e - p)));
                buf = concatNode.executeConcat(buf, rope(repl), enc);
            }

            return makeStringNode.fromRope(buf);
        }

        @Specialization(guards = { "isBrokenCodeRange(string)", "!isAsciiCompatible(string)" })
        public DynamicObject scrubAsciiIncompatible(DynamicObject string, DynamicObject block,
                @Cached("create()") RopeNodes.PreciseLengthNode preciseLengthNode) {
            final Rope rope = rope(string);
            final Encoding enc = rope.getEncoding();
            Rope buf = RopeConstants.EMPTY_ASCII_8BIT_ROPE;

            final byte[] pBytes = bytesNode.execute(rope);
            final int e = pBytes.length;

            int p = 0;
            int p1 = 0;
            final int mbminlen = enc.minLength();

            while (p < e) {
                int ret = preciseLengthNode.executeLength(enc, pBytes, p, e);
                if (MBCLEN_NEEDMORE_P(ret)) {
                    break;
                } else if (MBCLEN_CHARFOUND_P(ret)) {
                    p += MBCLEN_CHARFOUND_LEN(ret);
                } else if (MBCLEN_INVALID_P(ret)) {
                    final int q = p;
                    int clen = enc.maxLength();

                    if (p1 < p) {
                        buf = concatNode.executeConcat(buf, substringNode.executeSubstring(rope, p1, p - p1), enc);
                    }

                    if (e - p < clen) {
                        clen = e - p;
                    }
                    if (clen <= mbminlen * 2) {
                        clen = mbminlen;
                    } else {
                        clen -= mbminlen;
                        for (; clen > mbminlen; clen -= mbminlen) {
                            ret = encodingLengthNode.executeLength(enc, pBytes, q, q + clen);
                            if (MBCLEN_NEEDMORE_P(ret)) {
                                break;
                            }
                        }
                    }

                    DynamicObject repl = (DynamicObject) yield(block, makeStringNode.fromRope(substringNode.executeSubstring(rope, p, clen)));
                    buf = concatNode.executeConcat(buf, rope(repl), enc);
                    p += clen;
                    p1 = p;
                }
            }
            if (p1 < p) {
                buf = concatNode.executeConcat(buf, substringNode.executeSubstring(rope, p1, p - p1), enc);
            }
            if (p < e) {
                DynamicObject repl = (DynamicObject) yield(block, makeStringNode.fromRope(substringNode.executeSubstring(rope, p, e - p)));
                buf = concatNode.executeConcat(buf, rope(repl), enc);
            }

            return makeStringNode.fromRope(buf);
        }

        public Object yield(DynamicObject block, Object... arguments) {
            return yieldNode.dispatch(block, arguments);
        }

    }

    @CoreMethod(names = "swapcase!", raiseIfFrozenSelf = true)
    @ImportStatic(StringGuards.class)
    public abstract static class SwapcaseBangNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isSingleByteOptimizable(string)")
        public DynamicObject swapcaseSingleByte(DynamicObject string,
                                                @Cached("createSwapCase()") InvertAsciiCaseNode invertAsciiCaseNode) {
            return invertAsciiCaseNode.executeInvert(string);
        }

        @Specialization(guards = "!isSingleByteOptimizable(string)")
        public DynamicObject swapcase(DynamicObject string,
                                      @Cached("create()") RopeNodes.MakeLeafRopeNode makeLeafRopeNode,
                                      @Cached("createBinaryProfile()") ConditionProfile dummyEncodingProfile,
                                      @Cached("createBinaryProfile()") ConditionProfile modifiedProfile) {
            // Taken from org.jruby.RubyString#swapcase_bang19.

            final Rope rope = rope(string);
            final Encoding enc = rope.getEncoding();

            if (dummyEncodingProfile.profile(enc.isDummy())) {
                throw new RaiseException(coreExceptions().encodingCompatibilityErrorIncompatibleWithOperation(enc, this));
            }

            final int s = 0;
            final int end = s + rope.byteLength();
            final byte[] bytes = rope.getBytesCopy();
            final boolean modified = StringSupport.multiByteSwapcase(enc, bytes, s, end);

            if (modifiedProfile.profile(modified)) {
                StringOperations.setRope(string, makeLeafRopeNode.executeMake(bytes, rope.getEncoding(), rope.getCodeRange(), rope.characterLength()));

                return string;
            } else {
                return nil();
            }
        }
    }

    @CoreMethod(names = "dump", taintFrom = 0)
    @ImportStatic(StringGuards.class)
    public abstract static class DumpNode extends CoreMethodArrayArgumentsNode {

        @Child private AllocateObjectNode allocateObjectNode = AllocateObjectNode.create();
        @Child private RopeNodes.MakeLeafRopeNode makeLeafRopeNode = RopeNodes.MakeLeafRopeNode.create();

        @Specialization(guards = "isAsciiCompatible(string)")
        public DynamicObject dumpAsciiCompatible(DynamicObject string) {
            // Taken from org.jruby.RubyString#dump

            RopeBuilder outputBytes = dumpCommon(string);
            outputBytes.setEncoding(encoding(string));

            final Rope rope = makeLeafRopeNode.executeMake(outputBytes.getBytes(), outputBytes.getEncoding(), CR_7BIT, outputBytes.getLength());
            final DynamicObject result = allocateObjectNode.allocate(Layouts.BASIC_OBJECT.getLogicalClass(string),
                    Layouts.STRING.build(false, false, rope, null));

            return result;
        }

        @TruffleBoundary
        @Specialization(guards = "!isAsciiCompatible(string)")
        public DynamicObject dump(DynamicObject string) {
            // Taken from org.jruby.RubyString#dump

            RopeBuilder outputBytes = dumpCommon(string);

            try {
                outputBytes.append(".force_encoding(\"".getBytes("UTF-8"));
            } catch (UnsupportedEncodingException e) {
                throw new UnsupportedOperationException(e);
            }

            outputBytes.append(encoding(string).getName());
            outputBytes.append((byte) '"');
            outputBytes.append((byte) ')');

            outputBytes.setEncoding(ASCIIEncoding.INSTANCE);

            final Rope rope = makeLeafRopeNode.executeMake(outputBytes.getBytes(), outputBytes.getEncoding(), CR_7BIT, outputBytes.getLength());
            final DynamicObject result = allocateObjectNode.allocate(Layouts.BASIC_OBJECT.getLogicalClass(string),
                    Layouts.STRING.build(false, false, rope, null));

            return result;
        }

        @TruffleBoundary
        private RopeBuilder dumpCommon(DynamicObject string) {
            assert RubyGuards.isRubyString(string);
            return dumpCommon(rope(string));
        }

        private RopeBuilder dumpCommon(Rope rope) {
            RopeBuilder buf = null;
            Encoding enc = rope.getEncoding();

            int p = 0;
            int end = rope.byteLength();
            byte[]bytes = rope.getBytes();

            int len = 2;
            while (p < end) {
                int c = bytes[p++] & 0xff;

                switch (c) {
                    case '"':case '\\':case '\n':case '\r':case '\t':case '\f':
                    case '\013': case '\010': case '\007': case '\033':
                        len += 2;
                        break;
                    case '#':
                        len += isEVStr(bytes, p, end) ? 2 : 1;
                        break;
                    default:
                        if (ASCIIEncoding.INSTANCE.isPrint(c)) {
                            len++;
                        } else {
                            if (enc.isUTF8()) {
                                int n = StringSupport.preciseLength(enc, bytes, p - 1, end) - 1;
                                if (n > 0) {
                                    if (buf == null) {
                                        buf = new RopeBuilder();
                                    }
                                    int cc = codePointX(enc, bytes, p - 1, end);
                                    buf.append(String.format("%x", cc).getBytes(StandardCharsets.US_ASCII));
                                    len += buf.getLength() + 4;
                                    buf.setLength(0);
                                    p += n;
                                    break;
                                }
                            }
                            len += 4;
                        }
                        break;
                }
            }

            if (!enc.isAsciiCompatible()) {
                len += ".force_encoding(\"".length() + enc.getName().length + "\")".length();
            }

            RopeBuilder outBytes = new RopeBuilder();
            outBytes.unsafeEnsureSpace(len);
            byte out[] = outBytes.getUnsafeBytes();
            int q = 0;
            p = 0;
            end = rope.byteLength();

            out[q++] = '"';
            while (p < end) {
                int c = bytes[p++] & 0xff;
                if (c == '"' || c == '\\') {
                    out[q++] = '\\';
                    out[q++] = (byte) c;
                } else if (c == '#') {
                    if (isEVStr(bytes, p, end)) {
                        out[q++] = '\\';
                    }
                    out[q++] = '#';
                } else if (c == '\n') {
                    out[q++] = '\\';
                    out[q++] = 'n';
                } else if (c == '\r') {
                    out[q++] = '\\';
                    out[q++] = 'r';
                } else if (c == '\t') {
                    out[q++] = '\\';
                    out[q++] = 't';
                } else if (c == '\f') {
                    out[q++] = '\\';
                    out[q++] = 'f';
                } else if (c == '\013') {
                    out[q++] = '\\';
                    out[q++] = 'v';
                } else if (c == '\010') {
                    out[q++] = '\\';
                    out[q++] = 'b';
                } else if (c == '\007') {
                    out[q++] = '\\';
                    out[q++] = 'a';
                } else if (c == '\033') {
                    out[q++] = '\\';
                    out[q++] = 'e';
                } else if (ASCIIEncoding.INSTANCE.isPrint(c)) {
                    out[q++] = (byte) c;
                } else {
                    out[q++] = '\\';
                    if (enc.isUTF8()) {
                        int n = StringSupport.preciseLength(enc, bytes, p - 1, end) - 1;
                        if (n > 0) {
                            int cc = codePointX(enc, bytes, p - 1, end);
                            p += n;
                            outBytes.setLength(q);
                            outBytes.append(String.format("u{%x}", cc).getBytes(StandardCharsets.US_ASCII));
                            q = outBytes.getLength();
                            continue;
                        }
                    }
                    outBytes.setLength(q);
                    outBytes.append(String.format("x%02X", c).getBytes(StandardCharsets.US_ASCII));
                    q = outBytes.getLength();
                }
            }
            out[q++] = '"';
            outBytes.setLength(q);
            assert out == outBytes.getUnsafeBytes(); // must not reallocate

            return outBytes;
        }

        private static boolean isEVStr(byte[] bytes, int p, int end) {
            return p < end ? isEVStr(bytes[p] & 0xff) : false;
        }

        private static boolean isEVStr(int c) {
            return c == '$' || c == '@' || c == '{';
        }

        private int codePointX(Encoding enc, byte[] bytes, int p, int end) {
            try {
                return StringSupport.codePoint(enc, bytes, p, end);
            } catch (IllegalArgumentException e) {
                throw new RaiseException(getContext().getCoreExceptions().argumentError(e.getMessage(), this));
            }
        }
    }

    @CoreMethod(names = "setbyte", required = 2, raiseIfFrozenSelf = true, lowerFixnum = { 1, 2 })
    @NodeChildren({
        @NodeChild(type = RubyNode.class, value = "string"),
        @NodeChild(type = RubyNode.class, value = "index"),
        @NodeChild(type = RubyNode.class, value = "value")
    })
    @ImportStatic(StringGuards.class)
    public abstract static class SetByteNode extends CoreMethodNode {

        @Child private CheckIndexNode checkIndexNode = StringNodesFactory.CheckIndexNodeGen.create(null, null);
        @Child private RopeNodes.ConcatNode composedConcatNode = RopeNodes.ConcatNode.create();
        @Child private RopeNodes.ConcatNode middleConcatNode = RopeNodes.ConcatNode.create();
        @Child private RopeNodes.MakeLeafRopeNode makeLeafRopeNode = RopeNodes.MakeLeafRopeNode.create();
        @Child private RopeNodes.SubstringNode leftSubstringNode = RopeNodes.SubstringNode.create();
        @Child private RopeNodes.SubstringNode rightSubstringNode = RopeNodes.SubstringNode.create();

        @CreateCast("index") public RubyNode coerceIndexToInt(RubyNode index) {
            return FixnumLowerNodeGen.create(ToIntNodeGen.create(index));
        }

        @CreateCast("value") public RubyNode coerceValueToInt(RubyNode value) {
            return FixnumLowerNodeGen.create(ToIntNodeGen.create(value));
        }

        public abstract int executeSetByte(DynamicObject string, int index, Object value);

        @Specialization
        public int setByte(DynamicObject string, int index, int value) {
            final Rope rope = rope(string);
            final int normalizedIndex = checkIndexNode.executeCheck(index, rope.byteLength());

            final Rope left = leftSubstringNode.executeSubstring(rope, 0, normalizedIndex);
            final Rope right = rightSubstringNode.executeSubstring(rope, normalizedIndex + 1, rope.byteLength() - normalizedIndex - 1);
            final Rope middle = makeLeafRopeNode.executeMake(new byte[] { (byte) value }, rope.getEncoding(), CodeRange.CR_UNKNOWN, NotProvided.INSTANCE);
            final Rope composed = composedConcatNode.executeConcat(middleConcatNode.executeConcat(left, middle, rope.getEncoding()), right, rope.getEncoding());

            StringOperations.setRope(string, composed);

            return value;
        }

    }

    @NodeChildren({ @NodeChild("index"), @NodeChild("length") })
    public static abstract class CheckIndexNode extends RubyNode {

        public abstract int executeCheck(int index, int length);

        @Specialization
        protected int checkIndex(int index, int length,
                                 @Cached("createBinaryProfile()") ConditionProfile negativeIndexProfile,
                                 @Cached("create()") BranchProfile errorProfile) {
            if (index >= length) {
                errorProfile.enter();
                throw new RaiseException(getContext().getCoreExceptions().indexErrorOutOfString(index, this));
            }

            if (negativeIndexProfile.profile(index < 0)) {
                if (-index > length) {
                    errorProfile.enter();
                    throw new RaiseException(getContext().getCoreExceptions().indexErrorOutOfString(index, this));
                }

                index += length;
            }

            return index;
        }

    }

    @NodeChildren({ @NodeChild("index"), @NodeChild("length") })
    public static abstract class NormalizeIndexNode extends RubyNode {

        public abstract int executeNormalize(int index, int length);

        public static NormalizeIndexNode create() {
            return StringNodesFactory.NormalizeIndexNodeGen.create(null, null);
        }

        @Specialization
        protected int normalizeIndex(int index, int length,
                                     @Cached("createBinaryProfile()") ConditionProfile negativeIndexProfile) {
            if (negativeIndexProfile.profile(index < 0)) {
                return index + length;
            }

            return index;
        }

    }

    @CoreMethod(names = {"size", "length"})
    @ImportStatic(StringGuards.class)
    public abstract static class SizeNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        public int size(DynamicObject string) {
            final Rope rope = rope(string);

            return rope.characterLength();
        }

    }

    @CoreMethod(names = "squeeze!", rest = true, raiseIfFrozenSelf = true)
    @ImportStatic(StringGuards.class)
    public abstract static class SqueezeBangNode extends CoreMethodArrayArgumentsNode {

        @Child private EncodingNodes.CheckEncodingNode checkEncodingNode;
        private final ConditionProfile singleByteOptimizableProfile = ConditionProfile.createBinaryProfile();

        @Specialization(guards = "isEmpty(string)")
        public DynamicObject squeezeBangEmptyString(DynamicObject string, Object[] args) {
            return nil();
        }

        @Specialization(guards = { "!isEmpty(string)", "noArguments(args)" })
        @TruffleBoundary
        public Object squeezeBangZeroArgs(DynamicObject string, Object[] args) {
            // Taken from org.jruby.RubyString#squeeze_bang19.

            final Rope rope = rope(string);
            final RopeBuilder buffer = RopeOperations.toRopeBuilderCopy(rope);

            final boolean squeeze[] = new boolean[StringSupport.TRANS_SIZE];
            for (int i = 0; i < StringSupport.TRANS_SIZE; i++) {
                squeeze[i] = true;
            }

            if (singleByteOptimizableProfile.profile(rope.isSingleByteOptimizable())) {
                if (!StringSupport.singleByteSqueeze(buffer, squeeze)) {
                    return nil();
                } else {
                    StringOperations.setRope(string, RopeOperations.ropeFromRopeBuilder(buffer));
                }
            } else {
                if (!StringSupport.multiByteSqueeze(buffer, squeeze, null, encoding(string), false)) {
                    return nil();
                } else {
                    StringOperations.setRope(string, RopeOperations.ropeFromRopeBuilder(buffer));
                }
            }

            return string;
        }

        @Specialization(guards = { "!isEmpty(string)", "!noArguments(args)" })
        public Object squeezeBang(VirtualFrame frame, DynamicObject string, Object[] args,
                @Cached("create()") ToStrNode toStrNode) {
            // Taken from org.jruby.RubyString#squeeze_bang19.

            final DynamicObject[] otherStrings = new DynamicObject[args.length];

            for (int i = 0; i < args.length; i++) {
                otherStrings[i] = toStrNode.executeToStr(frame, args[i]);
            }

            return performSqueezeBang(string, otherStrings);
        }

        @TruffleBoundary
        private Object performSqueezeBang(DynamicObject string, DynamicObject[] otherStrings) {

            if (checkEncodingNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                checkEncodingNode = insert(EncodingNodesFactory.CheckEncodingNodeGen.create(null, null));
            }

            final Rope rope = rope(string);
            final RopeBuilder buffer = RopeOperations.toRopeBuilderCopy(rope);

            DynamicObject otherStr = otherStrings[0];
            Rope otherRope = rope(otherStr);
            Encoding enc = checkEncodingNode.executeCheckEncoding(string, otherStr);
            final boolean squeeze[] = new boolean[StringSupport.TRANS_SIZE + 1];
            StringSupport.TrTables tables = StringSupport.trSetupTable(otherRope, squeeze, null, true, enc);

            boolean singlebyte = rope.isSingleByteOptimizable() && otherRope.isSingleByteOptimizable();

            for (int i = 1; i < otherStrings.length; i++) {
                otherStr = otherStrings[i];
                otherRope = rope(otherStr);
                enc = checkEncodingNode.executeCheckEncoding(string, otherStr);
                singlebyte = singlebyte && otherRope.isSingleByteOptimizable();
                tables = StringSupport.trSetupTable(otherRope, squeeze, tables, false, enc);
            }

            if (singleByteOptimizableProfile.profile(singlebyte)) {
                if (!StringSupport.singleByteSqueeze(buffer, squeeze)) {
                    return nil();
                } else {
                    StringOperations.setRope(string, RopeOperations.ropeFromRopeBuilder(buffer));
                }
            } else {
                if (!StringSupport.multiByteSqueeze(buffer, squeeze, tables, enc, true)) {
                    return nil();
                } else {
                    StringOperations.setRope(string, RopeOperations.ropeFromRopeBuilder(buffer));
                }
            }

            return string;
        }

    }

    @CoreMethod(names = "succ!", raiseIfFrozenSelf = true)
    public abstract static class SuccBangNode extends CoreMethodArrayArgumentsNode {

        @Child private RopeNodes.MakeLeafRopeNode makeLeafRopeNode = RopeNodes.MakeLeafRopeNode.create();

        @Specialization
        public DynamicObject succBang(DynamicObject string) {
            final Rope rope = rope(string);

            if (!rope.isEmpty()) {
                final RopeBuilder succBuilder = StringSupport.succCommon(rope);

                final Rope newRope = makeLeafRopeNode.executeMake(succBuilder.getBytes(), rope.getEncoding(), CodeRange.CR_UNKNOWN, NotProvided.INSTANCE);
                StringOperations.setRope(string, newRope);
            }

            return string;
        }
    }

    // String#sum is in Java because without OSR we can't warm up the Rubinius implementation

    @CoreMethod(names = "sum", optional = 1)
    public abstract static class SumNode extends CoreMethodArrayArgumentsNode {

        public static SumNode create() {
            return SumNodeFactory.create(null);
        }

        public abstract Object executeSum(VirtualFrame frame, DynamicObject string, Object bits);

        @Child private CallDispatchHeadNode addNode = CallDispatchHeadNode.create();
        @Child private CallDispatchHeadNode subNode = CallDispatchHeadNode.create();
        @Child private CallDispatchHeadNode shiftNode = CallDispatchHeadNode.create();
        @Child private CallDispatchHeadNode andNode = CallDispatchHeadNode.create();
        private final RopeNodes.BytesNode bytesNode = RopeNodes.BytesNode.create();

        @Specialization
        public Object sum(VirtualFrame frame, DynamicObject string, int bits) {
            return sum(frame, string, (long) bits);
        }

        @Specialization
        public Object sum(VirtualFrame frame, DynamicObject string, long bits) {
            // Copied from JRuby

            final Rope rope = rope(string);
            final byte[] bytes = bytesNode.execute(rope);
            int p = 0;
            final int len = rope.byteLength();
            final int end = p + len;

            if (bits >= 8 * 8) { // long size * bits in byte
                Object sum = 0;
                while (p < end) {
                    sum = addNode.call(frame, sum, "+", bytes[p++] & 0xff);
                }
                if (bits != 0) {
                    final Object mod = shiftNode.call(frame, 1, "<<", bits);
                    sum = andNode.call(frame, sum, "&", subNode.call(frame, mod, "-", 1));
                }
                return sum;
            } else {
                long sum = 0;
                while (p < end) {
                    sum += bytes[p++] & 0xff;
                }
                return bits == 0 ? sum : sum & (1L << bits) - 1L;
            }
        }

        @Specialization
        public Object sum(VirtualFrame frame, DynamicObject string, NotProvided bits) {
            return sum(frame, string, 16);
        }

        @Specialization(guards = { "!isInteger(bits)", "!isLong(bits)", "wasProvided(bits)" })
        public Object sum(VirtualFrame frame, DynamicObject string, Object bits,
                @Cached("create()") ToIntNode toIntNode,
                @Cached("create()") SumNode sumNode) {
            return sumNode.executeSum(frame, string, toIntNode.executeIntOrLong(frame, bits));
        }

    }

    @CoreMethod(names = "to_f")
    public abstract static class ToFNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        @TruffleBoundary
        public double toF(DynamicObject string) {
            try {
                return convertToDouble(string);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        @TruffleBoundary
        private double convertToDouble(DynamicObject string) {
            return new DoubleConverter().parse(rope(string), false, true);
        }
    }

    @CoreMethod(names = { "to_s", "to_str" })
    public abstract static class ToSNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "!isStringSubclass(string)")
        public DynamicObject toS(DynamicObject string) {
            return string;
        }

        @Specialization(guards = "isStringSubclass(string)")
        public Object toSOnSubclass(DynamicObject string,
                @Cached("create()") IsTaintedNode isTaintedNode) {
            return coreLibrary().getStringFactory().newInstance(Layouts.STRING.build(
                    false, isTaintedNode.executeIsTainted(string), rope(string), null));
        }

        public boolean isStringSubclass(DynamicObject string) {
            return Layouts.BASIC_OBJECT.getLogicalClass(string) != coreLibrary().getStringClass();
        }

    }

    @CoreMethod(names = {"to_sym", "intern"})
    @ImportStatic({ StringCachingGuards.class, StringGuards.class, StringOperations.class })
    public abstract static class ToSymNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = {
                "!isBrokenCodeRange(string)",
                "equalNode.execute(rope(string),cachedRope)"
        }, limit = "getLimit()")
        public DynamicObject toSymCached(DynamicObject string,
                @Cached("privatizeRope(string)") Rope cachedRope,
                @Cached("getSymbol(cachedRope)") DynamicObject cachedSymbol,
                @Cached("create()") RopeNodes.EqualNode equalNode) {
            return cachedSymbol;
        }

        @Specialization(guards = "!isBrokenCodeRange(string)", replaces = "toSymCached")
        public DynamicObject toSym(DynamicObject string) {
            return getSymbol(rope(string));
        }

        @Specialization(guards = "isBrokenCodeRange(string)")
        public DynamicObject toSymBroken(DynamicObject string) {
            throw new RaiseException(coreExceptions().encodingError("invalid encoding symbol", this));
        }

        protected int getLimit() {
            return getContext().getOptions().DEFAULT_CACHE;
        }
    }

    @CoreMethod(names = "reverse!", raiseIfFrozenSelf = true)
    @ImportStatic(StringGuards.class)
    public abstract static class ReverseBangNode extends CoreMethodArrayArgumentsNode {

        @Child private RopeNodes.MakeLeafRopeNode makeLeafRopeNode = RopeNodes.MakeLeafRopeNode.create();

        @Specialization(guards = "reverseIsEqualToSelf(string)")
        public DynamicObject reverseNoOp(DynamicObject string) {
            return string;
        }

        @Specialization(guards = { "!reverseIsEqualToSelf(string)", "isSingleByteOptimizable(string)" })
        public DynamicObject reverseSingleByteOptimizable(DynamicObject string,
                @Cached("create()") RopeNodes.BytesNode bytesNode) {
            final Rope rope = rope(string);
            final byte[] originalBytes = bytesNode.execute(rope);
            final int len = originalBytes.length;
            final byte[] reversedBytes = new byte[len];

            for (int i = 0; i < len; i++) {
                reversedBytes[len - i - 1] = originalBytes[i];
            }

            StringOperations.setRope(string, makeLeafRopeNode.executeMake(reversedBytes, rope.getEncoding(), rope.getCodeRange(), rope.characterLength()));

            return string;
        }

        @Specialization(guards = { "!reverseIsEqualToSelf(string)", "!isSingleByteOptimizable(string)" })
        public DynamicObject reverse(DynamicObject string,
                @Cached("create()") RopeNodes.BytesNode bytesNode) {
            // Taken from org.jruby.RubyString#reverse!

            final Rope rope = rope(string);
            final byte[] originalBytes = bytesNode.execute(rope);
            int p = 0;
            final int len = originalBytes.length;

            final Encoding enc = rope.getEncoding();
            final int end = p + len;
            int op = len;
            final byte[] reversedBytes = new byte[len];

            while (p < end) {
                int cl = StringSupport.length(enc, originalBytes, p, end);
                if (cl > 1 || (originalBytes[p] & 0x80) != 0) {
                    op -= cl;
                    System.arraycopy(originalBytes, p, reversedBytes, op, cl);
                    p += cl;
                } else {
                    reversedBytes[--op] = originalBytes[p++];
                }
            }

            StringOperations.setRope(string, makeLeafRopeNode.executeMake(reversedBytes, rope.getEncoding(), rope.getCodeRange(), rope.characterLength()));

            return string;
        }

        public static boolean reverseIsEqualToSelf(DynamicObject string) {
            assert RubyGuards.isRubyString(string);

            return rope(string).characterLength() <= 1;
        }
    }

    @CoreMethod(names = "tr!", required = 2, raiseIfFrozenSelf = true)
    @NodeChildren({
        @NodeChild(type = RubyNode.class, value = "self"),
        @NodeChild(type = RubyNode.class, value = "fromStr"),
        @NodeChild(type = RubyNode.class, value = "toStrNode")
    })
    @ImportStatic(StringGuards.class)
    public abstract static class TrBangNode extends CoreMethodNode {

        @Child private EncodingNodes.CheckEncodingNode checkEncodingNode;
        @Child private DeleteBangNode deleteBangNode;

        @CreateCast("fromStr") public RubyNode coerceFromStrToString(RubyNode fromStr) {
            return ToStrNodeGen.create(fromStr);
        }

        @CreateCast("toStrNode") public RubyNode coerceToStrToString(RubyNode toStr) {
            return ToStrNodeGen.create(toStr);
        }

        @Specialization(guards = "isEmpty(self)")
        public Object trBangEmpty(DynamicObject self, DynamicObject fromStr, DynamicObject toStr) {
            return nil();
        }

        @Specialization(guards = { "!isEmpty(self)", "isRubyString(fromStr)", "isRubyString(toStr)", "isEmpty(toStr)" })
        public Object trBangEmpty(VirtualFrame frame, DynamicObject self, DynamicObject fromStr, DynamicObject toStr) {
            if (deleteBangNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                deleteBangNode = insert(DeleteBangNode.create());
            }

            return deleteBangNode.executeDeleteBang(frame, self, new DynamicObject[]{ fromStr });
        }

        @Specialization(guards = { "!isEmpty(self)", "isRubyString(fromStr)", "isRubyString(toStr)", "!isEmpty(toStr)" })
        public Object trBang(VirtualFrame frame, DynamicObject self, DynamicObject fromStr, DynamicObject toStr) {
            if (checkEncodingNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                checkEncodingNode = insert(EncodingNodesFactory.CheckEncodingNodeGen.create(null, null));
            }

            return StringNodesHelper.trTransHelper(getContext(), checkEncodingNode, self, fromStr, toStr, false);
        }
    }

    @CoreMethod(names = "tr_s!", required = 2, raiseIfFrozenSelf = true)
    @NodeChildren({
            @NodeChild(type = RubyNode.class, value = "self"),
            @NodeChild(type = RubyNode.class, value = "fromStr"),
            @NodeChild(type = RubyNode.class, value = "toStrNode")
    })
    @ImportStatic(StringGuards.class)
    public abstract static class TrSBangNode extends CoreMethodNode {

        @Child private EncodingNodes.CheckEncodingNode checkEncodingNode;
        @Child private DeleteBangNode deleteBangNode;

        @CreateCast("fromStr") public RubyNode coerceFromStrToString(RubyNode fromStr) {
            return ToStrNodeGen.create(fromStr);
        }

        @CreateCast("toStrNode") public RubyNode coerceToStrToString(RubyNode toStr) {
            return ToStrNodeGen.create(toStr);
        }

        @Specialization(guards = "isEmpty(self)")
        public DynamicObject trSBangEmpty(DynamicObject self, DynamicObject fromStr, DynamicObject toStr) {
            return nil();
        }

        @Specialization(guards = { "!isEmpty(self)", "isRubyString(fromStr)", "isRubyString(toStr)" })
        public Object trSBang(VirtualFrame frame, DynamicObject self, DynamicObject fromStr, DynamicObject toStr) {
            if (rope(toStr).isEmpty()) {
                if (deleteBangNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    deleteBangNode = insert(DeleteBangNode.create());
                }

                return deleteBangNode.executeDeleteBang(frame, self, new DynamicObject[] { fromStr });
            }

            if (checkEncodingNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                checkEncodingNode = insert(EncodingNodesFactory.CheckEncodingNodeGen.create(null, null));
            }

            return StringNodesHelper.trTransHelper(getContext(), checkEncodingNode, self, fromStr, toStr, true);
        }
    }

    @NodeChild(value = "string", type = RubyNode.class)
    @NodeChild(value = "format", type = RubyNode.class)
    @CoreMethod(names = "unpack", required = 1, taintFrom = 1)
    @ImportStatic({ StringCachingGuards.class, StringOperations.class })
    public abstract static class UnpackNode extends CoreMethodNode {

        @CreateCast("format")
        public RubyNode coerceFormat(RubyNode format) {
            return ToStrNodeGen.create(format);
        }

        @Child private TaintNode taintNode;

        private final BranchProfile exceptionProfile = BranchProfile.create();

        @Specialization(
                guards = "equalNode.execute(rope(format), cachedFormat)",
                limit = "getCacheLimit()")
        public DynamicObject unpackCached(
                DynamicObject string,
                DynamicObject format,
                @Cached("privatizeRope(format)") Rope cachedFormat,
                @Cached("create(compileFormat(format))") DirectCallNode callUnpackNode,
                @Cached("create()") RopeNodes.BytesNode bytesNode,
                @Cached("create()") RopeNodes.EqualNode equalNode) {
            final Rope rope = rope(string);

            final ArrayResult result;

            try {
                result = (ArrayResult) callUnpackNode.call(
                        new Object[]{ bytesNode.execute(rope), rope.byteLength() });
            } catch (FormatException e) {
                exceptionProfile.enter();
                throw FormatExceptionTranslator.translate(this, e);
            }

            return finishUnpack(result);
        }

        @Specialization(replaces = "unpackCached")
        public DynamicObject unpackUncached(
                DynamicObject string,
                DynamicObject format,
                @Cached("create()") IndirectCallNode callUnpackNode,
                @Cached("create()") RopeNodes.BytesNode bytesNode) {
            final Rope rope = rope(string);

            final ArrayResult result;

            try {
                result = (ArrayResult) callUnpackNode.call(compileFormat(format),
                        new Object[]{ bytesNode.execute(rope), rope.byteLength() });
            } catch (FormatException e) {
                exceptionProfile.enter();
                throw FormatExceptionTranslator.translate(this, e);
            }

            return finishUnpack(result);
        }

        private DynamicObject finishUnpack(ArrayResult result) {
            final DynamicObject array = createArray(result.getOutput(), result.getOutputLength());

            if (result.isTainted()) {
                if (taintNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    taintNode = insert(TaintNode.create());
                }

                taintNode.executeTaint(array);
            }

            return array;
        }

        @TruffleBoundary
        protected CallTarget compileFormat(DynamicObject format) {
            return new UnpackCompiler(getContext(), this).compile(format.toString());
        }

        protected int getCacheLimit() {
            return getContext().getOptions().UNPACK_CACHE;
        }

    }

    @NodeChildren({
            @NodeChild("bytes"),
            @NodeChild("start")
    })
    public abstract static class InvertAsciiCaseBytesNode extends RubyNode {

        private final boolean lowerToUpper;
        private final boolean upperToLower;

        public static InvertAsciiCaseBytesNode createLowerToUpper() {
            return StringNodesFactory.InvertAsciiCaseBytesNodeGen.create(true, false, null, null);
        }

        public static InvertAsciiCaseBytesNode createUpperToLower() {
            return StringNodesFactory.InvertAsciiCaseBytesNodeGen.create(false, true, null, null);
        }

        public static InvertAsciiCaseBytesNode createSwapCase() {
            return StringNodesFactory.InvertAsciiCaseBytesNodeGen.create(true, true, null, null);
        }

        protected InvertAsciiCaseBytesNode(boolean lowerToUpper, boolean upperToLower) {
            this.lowerToUpper = lowerToUpper;
            this.upperToLower = upperToLower;
        }

        public abstract byte[] executeInvert(byte[] bytes, int start);

        @Specialization
        protected byte[] invert(byte[] bytes, int start,
                                       @Cached("create()") BranchProfile foundLowerCaseCharProfile,
                                       @Cached("create()") BranchProfile foundUpperCaseCharProfile) {
            byte[] modified = null;

            for (int i = start; i < bytes.length; i++) {
                final byte b = bytes[i];

                if (lowerToUpper && StringSupport.isAsciiLowercase(b)) {
                    foundLowerCaseCharProfile.enter();

                    if (modified == null) {
                        modified = bytes.clone();
                    }

                    // Convert lower-case ASCII char to upper-case.
                    modified[i] ^= 0x20;
                }

                if (upperToLower && StringSupport.isAsciiUppercase(b)) {
                    foundUpperCaseCharProfile.enter();

                    if (modified == null) {
                        modified = bytes.clone();
                    }

                    // Convert upper-case ASCII char to lower-case.
                    modified[i] ^= 0x20;
                }
            }

            return modified;
        }

    }

    @NodeChild("bytes")
    public abstract static class InvertAsciiCaseNode extends RubyNode {

        @Child private InvertAsciiCaseBytesNode invertNode;
        @Child private RopeNodes.MakeLeafRopeNode makeLeafRopeNode = RopeNodes.MakeLeafRopeNode.create();

        public static InvertAsciiCaseNode createLowerToUpper() {
            final InvertAsciiCaseNode ret = StringNodesFactory.InvertAsciiCaseNodeGen.create(null);
            ret.invertNode = InvertAsciiCaseBytesNode.createLowerToUpper();

            return ret;
        }

        public static InvertAsciiCaseNode createUpperToLower() {
            final InvertAsciiCaseNode ret = StringNodesFactory.InvertAsciiCaseNodeGen.create(null);
            ret.invertNode = InvertAsciiCaseBytesNode.createUpperToLower();

            return ret;
        }

        public static InvertAsciiCaseNode createSwapCase() {
            final InvertAsciiCaseNode ret = StringNodesFactory.InvertAsciiCaseNodeGen.create(null);
            ret.invertNode = InvertAsciiCaseBytesNode.createSwapCase();

            return ret;
        }

        public abstract DynamicObject executeInvert(DynamicObject string);

        @Specialization
        protected DynamicObject invert(DynamicObject string,
                                    @Cached("createBinaryProfile()") ConditionProfile noopProfile,
                                    @Cached("create()") RopeNodes.BytesNode bytesNode) {
            final Rope rope = rope(string);

            final byte[] bytes = bytesNode.execute(rope);
            byte[] modified = invertNode.executeInvert(bytes, 0);

            if (noopProfile.profile(modified == null)) {
                return nil();
            } else {
                final Rope newRope = makeLeafRopeNode.executeMake(modified, rope.getEncoding(), rope.getCodeRange(), rope.characterLength());
                StringOperations.setRope(string, newRope);

                return string;
            }
        }

    }

    @CoreMethod(names = "upcase!", raiseIfFrozenSelf = true)
    @ImportStatic(StringGuards.class)
    public abstract static class UpcaseBangNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isSingleByteOptimizable(string)")
        public DynamicObject upcaseSingleByte(DynamicObject string,
                                              @Cached("createLowerToUpper()") InvertAsciiCaseNode invertAsciiCaseNode) {
            return invertAsciiCaseNode.executeInvert(string);
        }

        @Specialization(guards = "!isSingleByteOptimizable(string)")
        public DynamicObject upcase(DynamicObject string,
                                    @Cached("create()") RopeNodes.BytesNode bytesNode,
                                    @Cached("create()") RopeNodes.MakeLeafRopeNode makeLeafRopeNode,
                                    @Cached("createBinaryProfile()") ConditionProfile dummyEncodingProfile,
                                    @Cached("createBinaryProfile()") ConditionProfile modifiedProfile) {
            final Rope rope = rope(string);
            final Encoding encoding = rope.getEncoding();

            if (dummyEncodingProfile.profile(encoding.isDummy())) {
                throw new RaiseException(coreExceptions().encodingCompatibilityErrorIncompatibleWithOperation(encoding, this));
            }

            final RopeBuilder bytes = RopeBuilder.createRopeBuilder(bytesNode.execute(rope), rope.getEncoding());
            final boolean modified = StringSupport.multiByteUpcase(encoding, bytes.getUnsafeBytes(), 0, bytes.getLength());
            if (modifiedProfile.profile(modified)) {
                StringOperations.setRope(string, makeLeafRopeNode.executeMake(bytes.getBytes(), rope.getEncoding(), rope.getCodeRange(), rope.characterLength()));

                return string;
            } else {
                return nil();
            }
        }

    }

    @CoreMethod(names = "valid_encoding?")
    @ImportStatic(StringGuards.class)
    public abstract static class ValidEncodingQueryNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isBrokenCodeRange(string)")
        public boolean validEncodingQueryBroken(DynamicObject string) {
            return false;
        }

        @Specialization(guards = "!isBrokenCodeRange(string)")
        public boolean validEncodingQuery(DynamicObject string) {
            return true;
        }

    }

    @CoreMethod(names = "capitalize!", raiseIfFrozenSelf = true)
    @ImportStatic(StringGuards.class)
    public abstract static class CapitalizeBangNode extends CoreMethodArrayArgumentsNode {

        @Child private RopeNodes.GetCodePointNode getCodePointNode = RopeNodes.GetCodePointNode.create();
        @Child private RopeNodes.MakeLeafRopeNode makeLeafRopeNode = RopeNodes.MakeLeafRopeNode.create();

        @Specialization(guards = "isSingleByteOptimizable(string)")
        public DynamicObject capitalizeSingleByte(DynamicObject string,
                                                  @Cached("createUpperToLower()") InvertAsciiCaseBytesNode invertAsciiCaseNode,
                                                  @Cached("create()") RopeNodes.BytesNode bytesNode,
                                                  @Cached("createBinaryProfile()") ConditionProfile emptyStringProfile,
                                                  @Cached("createBinaryProfile()") ConditionProfile firstCharIsLowerProfile,
                                                  @Cached("createBinaryProfile()") ConditionProfile otherCharsAlreadyLowerProfile,
                                                  @Cached("createBinaryProfile()") ConditionProfile mustCapitalizeFirstCharProfile) {
            final Rope rope = rope(string);

            if (emptyStringProfile.profile(rope.isEmpty())) {
                return nil();
            }

            final byte[] sourceBytes = bytesNode.execute(rope);
            final byte[] finalBytes;

            final byte[] processedBytes = invertAsciiCaseNode.executeInvert(sourceBytes, 1);

            if (otherCharsAlreadyLowerProfile.profile(processedBytes == null)) {
                // Bytes 1..N are either not letters or already lowercased. Time to check the first byte.

                if (firstCharIsLowerProfile.profile(StringSupport.isAsciiLowercase(sourceBytes[0]))) {
                    // The first char requires capitalization, but the remaining bytes in the original string are
                    // already properly cased.
                    finalBytes = sourceBytes.clone();
                } else {
                    // The string is already capitalized.
                    return nil();
                }
            } else {
                // At least one char was lowercased when looking at bytes 1..N. We still must check the first byte.
                finalBytes = processedBytes;
            }

            if (mustCapitalizeFirstCharProfile.profile(StringSupport.isAsciiLowercase(sourceBytes[0]))) {
                finalBytes[0] ^= 0x20;
            }

            StringOperations.setRope(string, makeLeafRopeNode.executeMake(finalBytes, rope.getEncoding(), rope.getCodeRange(), rope.characterLength()));

            return string;
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        @Specialization(guards = "!isSingleByteOptimizable(string)")
        public DynamicObject capitalizeBang(DynamicObject string) {
            // Taken from org.jruby.RubyString#capitalize_bang19.

            final Rope rope = rope(string);
            final Encoding enc = rope.getEncoding();

            if (enc.isDummy()) {
                throw new RaiseException(coreExceptions().encodingCompatibilityErrorIncompatibleWithOperation(enc, this));
            }

            if (rope.isEmpty()) {
                return nil();
            }

            int s = 0;
            int end = s + rope.byteLength();
            byte[] bytes = rope.getBytesCopy();
            boolean modify = false;

            int c = getCodePointNode.executeGetCodePoint(rope, s);
            if (enc.isLower(c)) {
                enc.codeToMbc(StringSupport.toUpper(enc, c), bytes, s);
                modify = true;
            }

            s += StringSupport.codeLength(enc, c);
            while (s < end) {
                c = getCodePointNode.executeGetCodePoint(rope, s);
                if (enc.isUpper(c)) {
                    enc.codeToMbc(StringSupport.toLower(enc, c), bytes, s);
                    modify = true;
                }
                s += StringSupport.codeLength(enc, c);
            }

            if (modify) {
                StringOperations.setRope(string, makeLeafRopeNode.executeMake(bytes, rope.getEncoding(), rope.getCodeRange(), rope.characterLength()));
                return string;
            }

            return nil();
        }
    }

    @CoreMethod(names = "clear", raiseIfFrozenSelf = true)
    public abstract static class ClearNode extends CoreMethodArrayArgumentsNode {

        @Child private RopeNodes.SubstringNode substringNode = RopeNodes.SubstringNode.create();

        @Specialization
        public DynamicObject clear(DynamicObject string) {
            StringOperations.setRope(string, substringNode.executeSubstring(rope(string), 0, 0));

            return string;
        }
    }

    public static class StringNodesHelper {

        @TruffleBoundary
        private static Object trTransHelper(RubyContext context, EncodingNodes.CheckEncodingNode checkEncodingNode,
                                            DynamicObject self, DynamicObject fromStr,
                                            DynamicObject toStr, boolean sFlag) {
            assert RubyGuards.isRubyString(self);
            assert RubyGuards.isRubyString(fromStr);
            assert RubyGuards.isRubyString(toStr);

            final Encoding e1 = checkEncodingNode.executeCheckEncoding(self, fromStr);
            final Encoding e2 = checkEncodingNode.executeCheckEncoding(self, toStr);
            final Encoding enc = e1 == e2 ? e1 : checkEncodingNode.executeCheckEncoding(fromStr, toStr);

            final Rope ret = StringSupport.trTransHelper(rope(self), rope(fromStr), rope(toStr), e1, enc, sFlag);

            if (ret == null) {
                return context.getCoreLibrary().getNil();
            }

            StringOperations.setRope(self, ret);

            return self;
        }
    }

    @Primitive(name = "character_printable_p")
    public static abstract class CharacterPrintablePrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        public boolean isCharacterPrintable(DynamicObject character,
                                            @Cached("createBinaryProfile()") ConditionProfile is7BitProfile,
                                            @Cached("create()") RopeNodes.GetCodePointNode getCodePointNode) {
            final Rope rope = rope(character);
            final int codePoint = getCodePointNode.executeGetCodePoint(rope, 0);

            if (is7BitProfile.profile(rope.isAsciiOnly())) {
                return StringSupport.isAsciiPrintable(codePoint);
            } else {
                return isMBCPrintable(rope.getEncoding(), codePoint);
            }
        }

        @TruffleBoundary
        protected boolean isMBCPrintable(Encoding encoding, int codePoint) {
            return encoding.isPrint(codePoint);
        }

    }

    @NonStandard
    @CoreMethod(names = "append", required = 1)
    public static abstract class StringAppendPrimitiveNode extends CoreMethodArrayArgumentsNode {

        @Child private StringAppendNode stringAppendNode = StringNodesFactory.StringAppendNodeGen.create(null, null);

        public static StringAppendPrimitiveNode create() {
            return StringNodesFactory.StringAppendPrimitiveNodeFactory.create(null);
        }

        public abstract DynamicObject executeStringAppend(DynamicObject string, DynamicObject other);

        @Specialization(guards = "isRubyString(other)")
        public DynamicObject stringAppend(DynamicObject string, DynamicObject other) {
            StringOperations.setRope(string, stringAppendNode.executeStringAppend(string, other));

            return string;
        }

    }

    @Primitive(name = "string_awk_split", lowerFixnum = 1)
    public static abstract class StringAwkSplitPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Child private RopeNodes.GetCodePointNode getCodePointNode = RopeNodes.GetCodePointNode.create();
        @Child private RopeNodes.SubstringNode substringNode = RopeNodes.SubstringNode.create();
        @Child private TaintResultNode taintResultNode = new TaintResultNode();

        @TruffleBoundary
        @Specialization
        public DynamicObject stringAwkSplit(DynamicObject string, int lim) {
            final List<DynamicObject> ret = new ArrayList<>();
            final Rope rope = rope(string);
            final boolean limit = lim > 0;
            int i = lim > 0 ? 1 : 0;

            byte[]bytes = rope.getBytes();
            int p = 0;
            int ptr = p;
            int len = rope.byteLength();
            int end = p + len;
            Encoding enc = rope.getEncoding();
            boolean skip = true;

            int e = 0, b = 0;
            final boolean singlebyte = rope.isSingleByteOptimizable();
            while (p < end) {
                final int c;
                if (singlebyte) {
                    c = bytes[p++] & 0xff;
                } else {
                    c = getCodePointNode.executeGetCodePoint(rope, p);
                    p += StringSupport.length(enc, bytes, p, end);
                }

                if (skip) {
                    if (enc.isSpace(c)) {
                        b = p - ptr;
                    } else {
                        e = p - ptr;
                        skip = false;
                        if (limit && lim <= i) {
                            break;
                        }
                    }
                } else {
                    if (enc.isSpace(c)) {
                        ret.add(makeString(string, b, e - b));
                        skip = true;
                        b = p - ptr;
                        if (limit) {
                            i++;
                        }
                    } else {
                        e = p - ptr;
                    }
                }
            }

            if (len > 0 && (limit || len > b || lim < 0)) {
                ret.add(makeString(string, b, len - b));
            }

            Object[] objects = ret.toArray();
            return createArray(objects, objects.length);
        }

        // because the factory is not constant
        @TruffleBoundary
        private DynamicObject makeString(DynamicObject source, int index, int length) {
            assert RubyGuards.isRubyString(source);

            final Rope rope = substringNode.executeSubstring(rope(source), index, length);

            final DynamicObject ret = Layouts.CLASS.getInstanceFactory(Layouts.BASIC_OBJECT.getLogicalClass(source)).newInstance(Layouts.STRING.build(false, false, rope, null));
            taintResultNode.maybeTaint(source, ret);

            return ret;
        }
    }

    @Primitive(name = "string_byte_substring", lowerFixnum = { 1, 2 })
    @NodeChildren({
            @NodeChild(type = RubyNode.class, value = "string"),
            @NodeChild(type = RubyNode.class, value = "index"),
            @NodeChild(type = RubyNode.class, value = "length")
    })
    public static abstract class StringByteSubstringPrimitiveNode extends PrimitiveNode {

        @Child private AllocateObjectNode allocateObjectNode = AllocateObjectNode.create();
        @Child private NormalizeIndexNode normalizeIndexNode = StringNodesFactory.NormalizeIndexNodeGen.create(null, null);
        @Child private RopeNodes.SubstringNode substringNode = RopeNodes.SubstringNode.create();
        @Child private TaintResultNode taintResultNode = new TaintResultNode();

        public static StringByteSubstringPrimitiveNode create() {
            return StringNodesFactory.StringByteSubstringPrimitiveNodeFactory.create(null, null, null);
        }

        public Object executeStringByteSubstring(DynamicObject string, Object index, Object length) {
            return nil();
        }

        @Specialization
        public Object stringByteSubstring(DynamicObject string, int index, NotProvided length,
                                          @Cached("createBinaryProfile()") ConditionProfile negativeLengthProfile,
                                          @Cached("createBinaryProfile()") ConditionProfile indexOutOfBoundsProfile,
                                          @Cached("createBinaryProfile()") ConditionProfile lengthTooLongProfile,
                                          @Cached("createBinaryProfile()") ConditionProfile nilSubstringProfile,
                                          @Cached("createBinaryProfile()") ConditionProfile emptySubstringProfile) {
            final DynamicObject subString = (DynamicObject) stringByteSubstring(string, index, 1, negativeLengthProfile, indexOutOfBoundsProfile, lengthTooLongProfile);

            if (nilSubstringProfile.profile(subString == nil())) {
                return subString;
            }

            if (emptySubstringProfile.profile(rope(subString).isEmpty())) {
                return nil();
            }

            return subString;
        }

        @Specialization
        public Object stringByteSubstring(DynamicObject string, int index, int length,
                                          @Cached("createBinaryProfile()") ConditionProfile negativeLengthProfile,
                                          @Cached("createBinaryProfile()") ConditionProfile indexOutOfBoundsProfile,
                                          @Cached("createBinaryProfile()") ConditionProfile lengthTooLongProfile) {
            if (negativeLengthProfile.profile(length < 0)) {
                return nil();
            }

            final Rope rope = rope(string);
            final int stringByteLength = rope.byteLength();
            final int normalizedIndex = normalizeIndexNode.executeNormalize(index, stringByteLength);

            if (indexOutOfBoundsProfile.profile(normalizedIndex < 0 || normalizedIndex > stringByteLength)) {
                return nil();
            }

            if (lengthTooLongProfile.profile(normalizedIndex + length > stringByteLength)) {
                length = rope.byteLength() - normalizedIndex;
            }

            final Rope substringRope = substringNode.executeSubstring(rope, normalizedIndex, length);
            final DynamicObject result = allocateObjectNode.allocate(Layouts.BASIC_OBJECT.getLogicalClass(string), Layouts.STRING.build(false, false, substringRope, null));

            return taintResultNode.maybeTaint(string, result);
        }

        @Fallback
        public Object stringByteSubstring(Object string, Object range, Object length) {
            return FAILURE;
        }

    }

    @NonStandard
    @CoreMethod(names = "chr_at", required = 1, lowerFixnum = 1)
    @ImportStatic(StringGuards.class)
    public static abstract class StringChrAtPrimitiveNode extends CoreMethodArrayArgumentsNode {

        @Child private StringByteSubstringPrimitiveNode stringByteSubstringNode = StringByteSubstringPrimitiveNode.create();

        @Specialization(guards = "indexOutOfBounds(string, byteIndex)")
        public Object stringChrAtOutOfBounds(DynamicObject string, int byteIndex) {
            return nil();
        }

        @Specialization(guards = { "!indexOutOfBounds(string, byteIndex)", "isSingleByteOptimizable(string)" })
        public Object stringChrAtSingleByte(DynamicObject string, int byteIndex) {
            return stringByteSubstringNode.executeStringByteSubstring(string, byteIndex, 1);
        }

        @Specialization(guards = { "!indexOutOfBounds(string, byteIndex)", "!isSingleByteOptimizable(string)" })
        public Object stringChrAt(DynamicObject string, int byteIndex,
                @Cached("create()") RopeNodes.BytesNode bytesNode,
                @Cached("create()") RopeNodes.PreciseLengthNode preciseLengthNode) {
            // Taken from Rubinius's Character::create_from.

            final Rope rope = rope(string);
            final int end = rope.byteLength();
            final int c = preciseLengthNode.executeLength(rope.getEncoding(), bytesNode.execute(rope), byteIndex, end);

            if (!StringSupport.MBCLEN_CHARFOUND_P(c)) {
                return nil();
            }

            final int n = StringSupport.MBCLEN_CHARFOUND_LEN(c);
            if (n + byteIndex > end) {
                return nil();
            }

            return stringByteSubstringNode.executeStringByteSubstring(string, byteIndex, n);
        }

        protected static boolean indexOutOfBounds(DynamicObject string, int byteIndex) {
            return ((byteIndex < 0) || (byteIndex >= rope(string).byteLength()));
        }

    }

    @ImportStatic(StringGuards.class)
    @NodeChildren({ @NodeChild("first"), @NodeChild("second") })
    public static abstract class StringAreComparableNode extends RubyNode {

        public abstract boolean executeAreComparable(DynamicObject first, DynamicObject second);

        @Specialization(guards = "getEncoding(a) == getEncoding(b)")
        protected boolean sameEncoding(DynamicObject a, DynamicObject b) {
            return true;
        }

        @Specialization(guards = "isEmpty(a)")
        protected boolean firstEmpty(DynamicObject a, DynamicObject b) {
            return true;
        }

        @Specialization(guards = "isEmpty(b)")
        protected boolean secondEmpty(DynamicObject a, DynamicObject b) {
            return true;
        }

        @Specialization(guards = { "is7Bit(a)", "is7Bit(b)" })
        protected boolean bothCR7bit(DynamicObject a, DynamicObject b) {
            return true;
        }

        @Specialization(guards = { "is7Bit(a)", "isAsciiCompatible(b)" })
        protected boolean CR7bitASCII(DynamicObject a, DynamicObject b) {
            return true;
        }

        @Specialization(guards = { "isAsciiCompatible(a)", "is7Bit(b)" })
        protected boolean ASCIICR7bit(DynamicObject a, DynamicObject b) {
            return true;
        }

        @Fallback
        protected boolean notCompatible(Object a, Object b) {
            return false;
        }

        protected static Encoding getEncoding(DynamicObject string) {
            return rope(string).getEncoding();
        }

    }

    @ImportStatic({ StringGuards.class, StringOperations.class })
    @NodeChildren({ @NodeChild("first"), @NodeChild("second") })
    public static abstract class StringEqualNode extends RubyNode {

        @Child private StringAreComparableNode areComparableNode;

        public abstract boolean executeStringEqual(DynamicObject string, DynamicObject other);

        // Same Rope implies same Encoding and therefore comparable
        @Specialization(guards = "rope(string) == rope(other)")
        public boolean sameRope(DynamicObject string, DynamicObject other) {
            return true;
        }

        @Specialization(guards = "!areComparable(string, other)")
        public boolean notComparable(DynamicObject string, DynamicObject other) {
            return false;
        }

        @Specialization(guards = "areComparable(string, other)")
        public boolean stringEquals(DynamicObject string, DynamicObject other,
                @Cached("create()") RopeNodes.BytesEqualNode bytesEqualNode) {
            return bytesEqualNode.execute(rope(string), rope(other));
        }

        protected boolean areComparable(DynamicObject first, DynamicObject second) {
            if (areComparableNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                areComparableNode = insert(StringAreComparableNodeGen.create(null, null));
            }
            return areComparableNode.executeAreComparable(first, second);
        }

    }

    @Primitive(name = "string_escape", needsSelf = false)
    public abstract static class StringEscapePrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Child private IsTaintedNode isTaintedNode = IsTaintedNode.create();
        @Child private TaintNode taintNode = TaintNode.create();
        private final ConditionProfile taintedProfile = ConditionProfile.createBinaryProfile();

        @Specialization
        public DynamicObject string_escape(DynamicObject string,
                                           @Cached("create()") StringNodes.MakeStringNode makeStringNode) {
            final DynamicObject result = makeStringNode.fromRope(rbStrEscape(rope(string)));

            if (taintedProfile.profile(isTaintedNode.isTainted(string))) {
                taintNode.executeTaint(result);
            }

            return result;
        }

        // MRI: rb_str_escape
        @TruffleBoundary
        private static Rope rbStrEscape(Rope str) {
            Encoding enc = str.getEncoding();
            byte[] pBytes = str.getBytes();
            int p = 0;
            int pend = str.byteLength();
            int prev = p;
            RopeBuilder result = new RopeBuilder();
            boolean unicode_p = enc.isUnicode();
            boolean asciicompat = enc.isAsciiCompatible();

            while (p < pend) {
                int c, cc;
                int n = StringSupport.encLength(enc, pBytes, p, pend);
                if (!MBCLEN_CHARFOUND_P(n)) {
                    if (p > prev) {
                        result.append(pBytes, prev, p - prev);
                    }
                    n = enc.minLength();
                    if (pend < p + n) {
                        n = (pend - p);
                    }
                    while ((n--) > 0) {
                        result.append(String.format("\\x%02X", (long) (pBytes[p] & 0377)).getBytes(StandardCharsets.US_ASCII));
                        prev = ++p;
                    }
                    continue;
                }
                n = MBCLEN_CHARFOUND_LEN(n);
                c = enc.mbcToCode(pBytes, p, pend);
                p += n;
                switch (c) {
                    case '\n': cc = 'n'; break;
                    case '\r': cc = 'r'; break;
                    case '\t': cc = 't'; break;
                    case '\f': cc = 'f'; break;
                    case '\013': cc = 'v'; break;
                    case '\010': cc = 'b'; break;
                    case '\007': cc = 'a'; break;
                    case 033: cc = 'e'; break;
                    default: cc = 0; break;
                }
                if (cc != 0) {
                    if (p - n > prev) {
                        result.append(pBytes, prev, p - n - prev);
                    }
                    result.append('\\');
                    result.append((byte) cc);
                    prev = p;
                } else if (asciicompat && Encoding.isAscii(c) && (c < 0x7F && c > 31 /* ISPRINT(c) */)) {
                } else {
                    if (p - n > prev) {
                        result.append(pBytes, prev, p - n - prev);
                    }

                    if (unicode_p && (c & 0xFFFFFFFFL) < 0x7F && Encoding.isAscii(c) && ASCIIEncoding.INSTANCE.isPrint(c)) {
                        result.append(String.format("%c", (char) (c & 0xFFFFFFFFL)).getBytes(StandardCharsets.US_ASCII));
                    } else {
                        result.append(String.format(escapedCharFormat(c, unicode_p), c & 0xFFFFFFFFL).getBytes(StandardCharsets.US_ASCII));
                    }

                    prev = p;
                }
            }
            if (p > prev) {
                result.append(pBytes, prev, p - prev);
            }

            result.setEncoding(USASCIIEncoding.INSTANCE);
            return result.toRope(CodeRange.CR_7BIT);
        }

        private static int MBCLEN_CHARFOUND_LEN(int r) {
            return r;
        }

        // MBCLEN_CHARFOUND_P, ONIGENC_MBCLEN_CHARFOUND_P
        private static boolean MBCLEN_CHARFOUND_P(int r) {
            return 0 < r;
        }

        private static String escapedCharFormat(int c, boolean isUnicode) {
            String format;
            // c comparisons must be unsigned 32-bit
            if (isUnicode) {

                if ((c & 0xFFFFFFFFL) < 0x7F && Encoding.isAscii(c) && ASCIIEncoding.INSTANCE.isPrint(c)) {
                    throw new UnsupportedOperationException();
                } else if (c < 0x10000) {
                    format = "\\u%04X";
                } else {
                    format = "\\u{%X}";
                }
            } else {
                if ((c & 0xFFFFFFFFL) < 0x100) {
                    format = "\\x%02X";
                } else {
                    format = "\\x{%X}";
                }
            }
            return format;
        }

    }

    @NonStandard
    @CoreMethod(names = "find_character", required = 1, lowerFixnum = 1)
    @ImportStatic(StringGuards.class)
    public static abstract class StringFindCharacterNode extends CoreMethodArrayArgumentsNode {

        @Child private AllocateObjectNode allocateObjectNode = AllocateObjectNode.create();
        @Child private RopeNodes.SubstringNode substringNode = RopeNodes.SubstringNode.create();
        @Child private TaintResultNode taintResultNode;

        @Specialization(guards = "offset < 0")
        public Object stringFindCharacterNegativeOffset(DynamicObject string, int offset) {
            return nil();
        }

        @Specialization(guards = "offsetTooLarge(string, offset)")
        public Object stringFindCharacterOffsetTooLarge(DynamicObject string, int offset) {
            return nil();
        }

        @Specialization(guards = { "offset >= 0", "!offsetTooLarge(string, offset)", "isSingleByteOptimizable(string)" })
        public Object stringFindCharacterSingleByte(DynamicObject string, int offset) {
            // Taken from Rubinius's String::find_character.

            final Rope rope = rope(string);

            final DynamicObject ret = allocateObjectNode.allocate(Layouts.BASIC_OBJECT.getLogicalClass(string), Layouts.STRING.build(false, false, substringNode.executeSubstring(rope, offset, 1), null));

            return propagate(string, ret);
        }

        @Specialization(guards = { "offset >= 0", "!offsetTooLarge(string, offset)", "!isSingleByteOptimizable(string)" })
        public Object stringFindCharacter(DynamicObject string, int offset,
                @Cached("create()") RopeNodes.PreciseLengthNode preciseLengthNode) {
            // Taken from Rubinius's String::find_character.

            final Rope rope = rope(string);

            final Encoding enc = rope.getEncoding();
            final int clen = preciseLengthNode.executeLength(enc, rope.getBytes(), offset, offset + enc.maxLength());

            final DynamicObject ret = allocateObjectNode.allocate(Layouts.BASIC_OBJECT.getLogicalClass(string),
                    Layouts.STRING.build(false, false, substringNode.executeSubstring(rope, offset, clen), null));

            return propagate(string, ret);
        }

        private Object propagate(DynamicObject string, DynamicObject ret) {
            return maybeTaint(string, ret);
        }

        private Object maybeTaint(DynamicObject source, DynamicObject value) {
            if (taintResultNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                taintResultNode = insert(new TaintResultNode());
            }

            return taintResultNode.maybeTaint(source, value);
        }

        protected static boolean offsetTooLarge(DynamicObject string, int offset) {
            assert RubyGuards.isRubyString(string);

            return offset >= rope(string).byteLength();
        }

    }

    @NonStandard
    @CoreMethod(names = "from_codepoint", onSingleton = true, required = 2, lowerFixnum = 1)
    public static abstract class StringFromCodepointPrimitiveNode extends CoreMethodArrayArgumentsNode {

        @Child private StringNodes.MakeStringNode makeStringNode = StringNodes.MakeStringNode.create();

        @Specialization(guards = { "isRubyEncoding(rubyEncoding)", "isSimple(longCode, rubyEncoding)", "isCodepoint(longCode)" })
        public DynamicObject stringFromCodepointSimple(long longCode, DynamicObject rubyEncoding,
                                                       @Cached("createBinaryProfile()") ConditionProfile isUTF8Profile,
                                                       @Cached("createBinaryProfile()") ConditionProfile isUSAsciiProfile,
                                                       @Cached("createBinaryProfile()") ConditionProfile isAscii8BitProfile) {
            final int code = (int) longCode; // isSimple() guarantees this is OK
            final Encoding encoding = EncodingOperations.getEncoding(rubyEncoding);
            final Rope rope;

            if (isUTF8Profile.profile(encoding == UTF8Encoding.INSTANCE)) {
                rope = RopeConstants.UTF8_SINGLE_BYTE_ROPES[code];
            } else if (isUSAsciiProfile.profile(encoding == USASCIIEncoding.INSTANCE)) {
                rope = RopeConstants.US_ASCII_SINGLE_BYTE_ROPES[code];
            } else if (isAscii8BitProfile.profile(encoding == ASCIIEncoding.INSTANCE)) {
                rope = RopeConstants.ASCII_8BIT_SINGLE_BYTE_ROPES[code];
            } else {
                rope = RopeOperations.create(new byte[] { (byte) code }, encoding, CodeRange.CR_UNKNOWN);
            }

            return makeStringNode.fromRope(rope);
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        @Specialization(guards = { "isRubyEncoding(rubyEncoding)", "!isSimple(code, rubyEncoding)", "isCodepoint(code)" })
        public DynamicObject stringFromCodepoint(long code, DynamicObject rubyEncoding,
                @Cached("create()") RopeNodes.PreciseLengthNode preciseLengthNode) {
            final Encoding encoding = EncodingOperations.getEncoding(rubyEncoding);
            final int length;

            try {
                length = encoding.codeToMbcLength((int) code);
            } catch (EncodingException e) {
                throw new RaiseException(coreExceptions().rangeError(code, rubyEncoding, this));
            }

            if (length <= 0) {
                throw new RaiseException(coreExceptions().rangeError(code, rubyEncoding, this));
            }

            final byte[] bytes = new byte[length];

            final int codeToMbc = encoding.codeToMbc((int) code, bytes, 0);
            if (codeToMbc < 0) {
                throw new RaiseException(coreExceptions().rangeError(code, rubyEncoding, this));
            }

            if (preciseLengthNode.executeLength(encoding, bytes, 0, length) != length) {
                throw new RaiseException(coreExceptions().rangeError(code, rubyEncoding, this));
            }

            return makeStringNode.executeMake(bytes, encoding, CodeRange.CR_VALID);
        }

        protected boolean isCodepoint(long code) {
            // Fits in an unsigned int
            return code >= 0 && code < (1L << 32);
        }

        protected boolean isSimple(long code, DynamicObject encoding) {
            final Encoding enc = EncodingOperations.getEncoding(encoding);

            return (enc.isAsciiCompatible() && code >= 0x00 && code < 0x80) || (enc == ASCIIEncoding.INSTANCE && code >= 0x00 && code <= 0xFF);
        }

    }

    @Primitive(name = "string_to_f", needsSelf = false)
    public static abstract class StringToFPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary(transferToInterpreterOnException = false)
        @Specialization
        public Object stringToF(DynamicObject string, boolean strict,
                                @Cached("create(getSourceIndexLength())") FixnumOrBignumNode fixnumOrBignumNode) {
            final Rope rope = rope(string);
            if (rope.isEmpty()) {
                throw new RaiseException(coreExceptions().argumentError(coreStrings().INVALID_VALUE_FOR_FLOAT.getRope(), this));
            }
            if (string.toString().startsWith("0x")) {
                try {
                    return Double.parseDouble(string.toString());
                } catch (NumberFormatException e) {
                    // Try falling back to this implementation if the first fails, neither 100% complete
                    final Object result = ConvertBytes.byteListToInum19(getContext(), this, fixnumOrBignumNode, string, 16, true);
                    if (result instanceof Integer) {
                        return ((Integer) result).doubleValue();
                    } else if (result instanceof Long) {
                        return ((Long) result).doubleValue();
                    } else if (result instanceof Double) {
                        return result;
                    } else {
                        return nil();
                    }
                }
            }
            try {
                return new DoubleConverter().parse(rope, strict, true);
            } catch (NumberFormatException e) {
                if (strict) {
                    throw new RaiseException(coreExceptions().argumentError(coreStrings().INVALID_VALUE_FOR_FLOAT.getRope(), this));
                }
                return 0.0;
            }
        }
    }

    @NonStandard
    @CoreMethod(names = "find_string", required = 2, lowerFixnum = 2)
    @ImportStatic(StringGuards.class)
    public static abstract class StringIndexPrimitiveNode extends CoreMethodArrayArgumentsNode {

        @Child private EncodingNodes.CheckEncodingNode checkEncodingNode;

        @Specialization(guards = "isEmpty(pattern)")
        public Object stringIndexEmptyPattern(DynamicObject string, DynamicObject pattern, int byteOffset) {
            assert byteOffset >= 0;

            return byteOffset;
        }

        @Specialization(guards = {
                "isSingleByteString(pattern)",
                "!isBrokenCodeRange(pattern)",
                "canMemcmp(string, pattern)"
        })
        public Object stringIndexSingleBytePattern(DynamicObject string, DynamicObject pattern, int byteOffset,
                @Cached("create()") RopeNodes.BytesNode bytesNode,
                @Cached("create()") BranchProfile matchFoundProfile,
                @Cached("create()") BranchProfile noMatchProfile) {
            assert byteOffset >= 0;

            checkEncoding(string, pattern);

            final Rope sourceRope = rope(string);
            final int end = sourceRope.byteLength();
            final byte[] sourceBytes = bytesNode.execute(sourceRope);
            final byte searchByte = bytesNode.execute(rope(pattern))[0];

            for (int i = byteOffset; i < end; i++) {
                if (sourceBytes[i] == searchByte) {
                    matchFoundProfile.enter();
                    return i;
                }
            }

            noMatchProfile.enter();
            return nil();
        }

        @Specialization(guards = {
                "!isEmpty(pattern)",
                "!isSingleByteString(pattern)",
                "!isBrokenCodeRange(pattern)",
                "canMemcmp(string, pattern)"
        })
        public Object stringIndexMultiBytePattern(DynamicObject string, DynamicObject pattern, int byteOffset,
                @Cached("create()") RopeNodes.BytesNode bytesNode,
                @Cached("create()") BranchProfile matchFoundProfile,
                @Cached("create()") BranchProfile noMatchProfile) {
            assert byteOffset >= 0;

            checkEncoding(string, pattern);

            final Rope sourceRope = rope(string);
            final byte[] sourceBytes = bytesNode.execute(sourceRope);
            final Rope searchRope = rope(pattern);
            final byte[] searchBytes = bytesNode.execute(searchRope);

            int end = sourceRope.byteLength() - searchRope.byteLength();

            for (int i = byteOffset; i <= end; i++) {
                if (sourceBytes[i] == searchBytes[0]) {
                    if (ArrayUtils.memcmp(sourceBytes, i, searchBytes, 0, searchRope.byteLength()) == 0) {
                        matchFoundProfile.enter();
                        return i;
                    }
                }
            }

            noMatchProfile.enter();
            return nil();
        }

        @Specialization(guards = "isBrokenCodeRange(pattern)")
        public Object stringIndexBrokenPattern(DynamicObject string, DynamicObject pattern, int byteOffset) {
            assert byteOffset >= 0;

            return nil();
        }

        @Specialization(guards = { "!isBrokenCodeRange(pattern)", "!canMemcmp(string, pattern)" })
        public Object stringIndexGeneric(DynamicObject string, DynamicObject pattern, int byteOffset,
                @Cached("create()") StringByteCharacterIndexNode byteIndexToCharIndexNode,
                @Cached("create()") NormalizeIndexNode normalizeIndexNode,
                @Cached("createBinaryProfile()") ConditionProfile badIndexProfile) {
            assert byteOffset >= 0;

            checkEncoding(string, pattern);

            // Rubinius will pass in a byte index for the `start` value, but StringSupport.index requires a character index.
            final int charIndex = byteIndexToCharIndexNode.executeStringByteCharacterIndex(string, byteOffset);

            final int index = index(rope(string), rope(pattern), charIndex, encoding(string), normalizeIndexNode);

            if (badIndexProfile.profile(index == -1)) {
                return nil();
            }

            return index;
        }

        @TruffleBoundary
        private int index(Rope source, Rope other, int byteOffset, Encoding enc, NormalizeIndexNode normalizeIndexNode) {
            // Taken from org.jruby.util.StringSupport.index.
            assert byteOffset >= 0;

            int sourceLen = source.characterLength();
            int otherLen = other.characterLength();

            byteOffset = normalizeIndexNode.executeNormalize(byteOffset, sourceLen);

            if (sourceLen - byteOffset < otherLen) {
                return -1;
            }
            byte[]bytes = source.getBytes();
            int p = 0;
            int end = p + source.byteLength();
            if (byteOffset != 0) {
                byteOffset = source.isSingleByteOptimizable() ? byteOffset : StringSupport.offset(enc, bytes, p, end, byteOffset);
                p += byteOffset;
            }
            if (otherLen == 0) {
                return byteOffset;
            }

            while (true) {
                int pos = indexOf(source, other, p);
                if (pos < 0) {
                    return pos;
                }
                pos -= p;
                int t = enc.rightAdjustCharHead(bytes, p, p + pos, end);
                if (t == p + pos) {
                    return pos + byteOffset;
                }
                if ((sourceLen -= t - p) <= 0) {
                    return -1;
                }
                byteOffset += t - p;
                p = t;
            }
        }

        @TruffleBoundary
        private int indexOf(Rope sourceRope, Rope otherRope, int fromIndex) {
            // Taken from org.jruby.util.ByteList.indexOf.

            final byte[] source = sourceRope.getBytes();
            final int sourceOffset = 0;
            final int sourceCount = sourceRope.byteLength();
            final byte[] target = otherRope.getBytes();
            final int targetOffset = 0;
            final int targetCount = otherRope.byteLength();

            if (fromIndex >= sourceCount) {
                return (targetCount == 0 ? sourceCount : -1);
            }
            if (fromIndex < 0) {
                fromIndex = 0;
            }
            if (targetCount == 0) {
                return fromIndex;
            }

            byte first  = target[targetOffset];
            int max = sourceOffset + (sourceCount - targetCount);

            for (int i = sourceOffset + fromIndex; i <= max; i++) {
                if (source[i] != first) {
                    while (++i <= max && source[i] != first) {
                    }
                }

                if (i <= max) {
                    int j = i + 1;
                    int end = j + targetCount - 1;
                    for (int k = targetOffset + 1; j < end && source[j] == target[k]; j++, k++) {
                    }

                    if (j == end) {
                        return i - sourceOffset;
                    }
                }
            }
            return -1;
        }

        private void checkEncoding(DynamicObject string, DynamicObject pattern) {
            if (checkEncodingNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                checkEncodingNode = insert(EncodingNodes.CheckEncodingNode.create());
            }

            checkEncodingNode.executeCheckEncoding(string, pattern);
        }

    }

    @Primitive(name = "string_character_byte_index", needsSelf = false, lowerFixnum = 2)
    @ImportStatic({ StringGuards.class, StringOperations.class })
    public static abstract class CharacterByteIndexNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "isSingleByteOptimizable(string)")
        int singleByteOptimizable(DynamicObject string, int charIndex) {
            return charIndex;
        }

        @Specialization(guards = { "!isSingleByteOptimizable(string)", "isFixedWidthEncoding(string)" })
        int fixedWidthEncoding(DynamicObject string, int charIndex) {
            final Encoding encoding = encoding(string);
            return charIndex * encoding.minLength();
        }

        @Specialization(guards = { "!isSingleByteOptimizable(string)", "!isFixedWidthEncoding(string)",
                "charIndex == 0" })
        int multiByteZeroIndex(DynamicObject string, int charIndex) {
            return 0;
        }

        @Specialization(guards = { "!isSingleByteOptimizable(string)", "!isFixedWidthEncoding(string)",
                "charIndex == rope(string).characterLength()" })
        int multiByteEndIndex(DynamicObject string, int charIndex) {
            return rope(string).byteLength();
        }

        @Specialization(guards = {
                "!isSingleByteOptimizable(string)", "!isFixedWidthEncoding(string)",
                "charIndex != 0", "charIndex != rope(string).characterLength()" })
        int multiByteEncoding(DynamicObject string, int charIndex) {
            final Rope rope = rope(string);
            return StringSupport.nth(rope.getEncoding(), rope.getBytes(), 0, rope.byteLength(), charIndex);
        }
    }

    @Primitive(name = "string_byte_character_index", needsSelf = false, lowerFixnum = 2)
    @ImportStatic(StringGuards.class)
    public static abstract class StringByteCharacterIndexNode extends PrimitiveArrayArgumentsNode {

        public abstract int executeStringByteCharacterIndex(DynamicObject string, int byteIndex);

        public static StringByteCharacterIndexNode create() {
            return StringNodesFactory.StringByteCharacterIndexNodeFactory.create(null);
        }

        @Specialization(guards = "isSingleByteOptimizable(string)")
        public int singleByte(DynamicObject string, int byteIndex) {
            return byteIndex;
        }

        @Specialization(guards = { "!isSingleByteOptimizable(string)", "isFixedWidthEncoding(string)" })
        public int fixedWidth(DynamicObject string, int byteIndex) {
            return byteIndex / encoding(string).minLength();
        }

        @Specialization(guards = { "!isSingleByteOptimizable(string)", "!isFixedWidthEncoding(string)", "isValidUtf8(string)" })
        public int validUtf8(DynamicObject string, int byteIndex) {
            // Taken from Rubinius's String::find_byte_character_index.
            // TODO (nirvdrum 02-Apr-15) There's a way to optimize this for UTF-8, but porting all that code isn't necessary at the moment.
            return notValidUtf8(string, byteIndex);
        }

        @TruffleBoundary
        @Specialization(guards = { "!isSingleByteOptimizable(string)", "!isFixedWidthEncoding(string)", "!isValidUtf8(string)" })
        public int notValidUtf8(DynamicObject string, int byteIndex) {
            // Taken from Rubinius's String::find_byte_character_index and Encoding::find_byte_character_index.

            final Rope rope = rope(string);
            final byte[] bytes = rope.getBytes();
            final Encoding encoding = rope.getEncoding();
            int p = 0;
            final int end = bytes.length;
            int charIndex = 0;

            while (p < end && byteIndex > 0) {
                final int charLen = StringSupport.length(encoding, bytes, p, end);
                p += charLen;
                byteIndex -= charLen;
                charIndex++;
            }

            return charIndex;
        }
    }

    @Primitive(name = "string_character_index", needsSelf = false, lowerFixnum = 3)
    public static abstract class StringCharacterIndexPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(guards = "isRubyString(pattern)")
        public Object stringCharacterIndex(DynamicObject string, DynamicObject pattern, int offset,
                @Cached("create()") RopeNodes.PreciseLengthNode preciseLengthNode) {
            if (offset < 0) {
                return nil();
            }

            final Rope stringRope = rope(string);
            final Rope patternRope = rope(pattern);

            final int total = stringRope.byteLength();
            int p = 0;
            final int e = p + total;
            final int pe = patternRope.byteLength();
            final int l = e - pe + 1;

            final byte[] stringBytes = stringRope.getBytes();
            final byte[] patternBytes = patternRope.getBytes();

            if (stringRope.isSingleByteOptimizable()) {
                for (p += offset; p < l; p++) {
                    if (ArrayUtils.memcmp(stringBytes, p, patternBytes, 0, pe) == 0) {
                        return p;
                    }
                }

                return nil();
            }

            final Encoding enc = stringRope.getEncoding();
            int index = 0;
            int c = 0;

            while (p < e && index < offset) {
                c = preciseLengthNode.executeLength(enc, stringBytes, p, e);

                if (StringSupport.MBCLEN_CHARFOUND_P(c)) {
                    p += c;
                    index++;
                } else {
                    return nil();
                }
            }

            for (; p < l; p += c, ++index) {
                c = preciseLengthNode.executeLength(enc, stringBytes, p, e);
                if (!StringSupport.MBCLEN_CHARFOUND_P(c)) {
                    return nil();
                }
                if (ArrayUtils.memcmp(stringBytes, p, patternBytes, 0, pe) == 0) {
                    return index;
                }
            }

            return nil();
        }
    }

    @Primitive(name = "string_byte_index_from_char_index", needsSelf = false, lowerFixnum = 2)
    @ImportStatic(StringGuards.class)
    public static abstract class StringByteIndexFromCharIndexNode extends PrimitiveArrayArgumentsNode {

        public abstract Object executeFindByteIndex(DynamicObject string, int characterIndex);

        @Specialization(guards = "characterIndex < 0")
        protected Object byteIndexNegativeIndex(DynamicObject string, int characterIndex) {
            throw new RaiseException(
                    getContext().getCoreExceptions().argumentError(
                            coreStrings().CHARACTER_INDEX_NEGATIVE.getRope(), this));
        }

        @Specialization(guards = "characterIndexTooLarge(string, characterIndex)")
        protected Object byteIndexTooLarge(DynamicObject string, int characterIndex) {
            return nil();
        }

        @Specialization(guards = { "characterIndexInBounds(string, characterIndex)", "isSingleByteOptimizable(string)" })
        protected Object singleByte(DynamicObject string, int characterIndex) {
            return characterIndex;
        }

        @Specialization(guards = { "characterIndexInBounds(string, characterIndex)", "!isSingleByteOptimizable(string)" })
        protected Object multiBytes(DynamicObject string, int characterIndex,
                @Cached("createBinaryProfile()") ConditionProfile indexTooLargeProfile,
                @Cached("createBinaryProfile()") ConditionProfile invalidByteProfile,
                @Cached("create()") RopeNodes.BytesNode bytesNode,
                @Cached("create()") RopeNodes.PreciseLengthNode preciseLengthNode) {
            // Taken from Rubinius's String::byte_index.

            final Rope rope = rope(string);
            final Encoding enc = rope.getEncoding();
            int p = 0;
            final int e = p + rope.byteLength();

            int i, k = characterIndex;

            for (i = 0; i < k && p < e; i++) {
                final int c = preciseLengthNode.executeLength(enc, bytesNode.execute(rope), p, e);

                // TODO (nirvdrum 22-Dec-16): Consider having a specialized version for CR_BROKEN strings to avoid these checks.
                // If it's an invalid byte, just treat it as a single byte
                if (invalidByteProfile.profile(!StringSupport.MBCLEN_CHARFOUND_P(c))) {
                    ++p;
                } else {
                    p += StringSupport.MBCLEN_CHARFOUND_LEN(c);
                }
            }

            // TODO (nirvdrum 22-Dec-16): Since we specialize elsewhere on index being too large, do we need this? Can character boundary search in a CR_BROKEN string cause us to encounter this case?
            if (indexTooLargeProfile.profile(i < k)) {
                return nil();
            } else {
                return p;
            }
        }

        protected boolean characterIndexTooLarge(DynamicObject string, int characterIndex) {
            return characterIndex > rope(string).characterLength();
        }

        protected boolean characterIndexInBounds(DynamicObject string, int characterIndex) {
            return characterIndex >= 0 && !characterIndexTooLarge(string, characterIndex);
        }

    }

    // Port of Rubinius's String::previous_byte_index.
    //
    // This method takes a byte index, finds the corresponding character the byte index belongs to, and then returns
    // the byte index marking the start of the previous character in the string.
    @Primitive(name = "string_previous_byte_index", lowerFixnum = 1)
    @ImportStatic(StringGuards.class)
    public static abstract class StringPreviousByteIndexNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "index < 0")
        public Object negativeIndex(DynamicObject string, int index) {
            throw new RaiseException(coreExceptions().argumentError("negative index given", this));
        }

        @Specialization(guards = "index == 0")
        public Object zeroIndex(DynamicObject string, int index) {
            return nil();
        }

        @Specialization(guards = { "index > 0", "isSingleByteOptimizable(string)" })
        public int singleByteOptimizable(DynamicObject string, int index) {
            return index - 1;
        }

        @Specialization(guards = { "index > 0", "!isSingleByteOptimizable(string)", "isFixedWidthEncoding(string)" })
        public int fixedWidthEncoding(DynamicObject string, int index,
                                      @Cached("createBinaryProfile()") ConditionProfile firstCharacterProfile) {
            final Encoding encoding = encoding(string);

            // TODO (nirvdrum 11-Apr-16) Determine whether we need to be bug-for-bug compatible with Rubinius.
            // Implement a bug in Rubinius. We already special-case the index == 0 by returning nil. For all indices
            // corresponding to a given character, we treat them uniformly. However, for the first character, we only
            // return nil if the index is 0. If any other index into the first character is encountered, we return 0.
            // It seems unlikely this will ever be encountered in practice, but it's here for completeness.
            if (firstCharacterProfile.profile(index < encoding.maxLength())) {
                return 0;
            }

            return (index / encoding.maxLength() - 1) * encoding.maxLength();
        }

        @Specialization(guards = { "index > 0", "!isSingleByteOptimizable(string)", "!isFixedWidthEncoding(string)" })
        @TruffleBoundary
        public Object other(DynamicObject string, int index) {
            final Rope rope = rope(string);
            final int p = 0;
            final int end = p + rope.byteLength();

            final int b = rope.getEncoding().prevCharHead(rope.getBytes(), p, p + index, end);

            if (b == -1) {
                return nil();
            }

            return b - p;
        }

    }

    @NonStandard
    @CoreMethod(names = "find_string_reverse", required = 2, lowerFixnum = 2)
    @ImportStatic(StringGuards.class)
    public static abstract class StringRindexPrimitiveNode extends CoreMethodArrayArgumentsNode {

        @Child private EncodingNodes.CheckEncodingNode checkEncodingNode;

        @Specialization(guards = "isEmpty(pattern)")
        public Object stringRindexEmptyPattern(DynamicObject string, DynamicObject pattern, int byteOffset) {
            assert byteOffset >= 0;

            return byteOffset;
        }

        @Specialization(guards = {
                "isSingleByteString(pattern)",
                "!isBrokenCodeRange(pattern)",
                "canMemcmp(string, pattern)"
        })
        public Object stringRindexSingleBytePattern(DynamicObject string, DynamicObject pattern, int byteOffset,
                @Cached("create()") RopeNodes.BytesNode bytesNode,
                @Cached("create()") BranchProfile startTooLargeProfile,
                @Cached("create()") BranchProfile matchFoundProfile,
                @Cached("create()") BranchProfile noMatchProfile) {
            assert byteOffset >= 0;

            checkEncoding(string, pattern);

            final Rope sourceRope = rope(string);
            final int end = sourceRope.byteLength();
            final byte[] sourceBytes = bytesNode.execute(sourceRope);
            final byte searchByte = bytesNode.execute(rope(pattern))[0];
            int normalizedStart = byteOffset;

            if (normalizedStart >= end) {
                startTooLargeProfile.enter();
                normalizedStart = end - 1;
            }

            for (int i = normalizedStart; i >= 0; i--) {
                if (sourceBytes[i] == searchByte) {
                    matchFoundProfile.enter();
                    return i;
                }
            }

            noMatchProfile.enter();
            return nil();
        }

        @Specialization(guards = {
                "!isEmpty(pattern)",
                "!isSingleByteString(pattern)",
                "!isBrokenCodeRange(pattern)",
                "canMemcmp(string, pattern)"
        })
        public Object stringRindexMultiBytePattern(DynamicObject string, DynamicObject pattern, int byteOffset,
                @Cached("create()") RopeNodes.BytesNode bytesNode,
                @Cached("create()") BranchProfile startOutOfBoundsProfile,
                @Cached("create()") BranchProfile startTooCloseToEndProfile,
                @Cached("create()") BranchProfile matchFoundProfile,
                @Cached("create()") BranchProfile noMatchProfile) {
            assert byteOffset >= 0;

            checkEncoding(string, pattern);

            final Rope sourceRope = rope(string);
            final int end = sourceRope.byteLength();
            final byte[] sourceBytes = bytesNode.execute(sourceRope);
            final Rope searchRope = rope(pattern);
            final int matchSize = searchRope.byteLength();
            final byte[] searchBytes = bytesNode.execute(searchRope);
            int normalizedStart = byteOffset;

            if (normalizedStart >= end) {
                startOutOfBoundsProfile.enter();
                normalizedStart = end - 1;
            }

            if (end - normalizedStart < matchSize) {
                startTooCloseToEndProfile.enter();
                normalizedStart = end - matchSize;
            }

            for (int i = normalizedStart; i >= 0; i--) {
                if (sourceBytes[i] == searchBytes[0]) {
                    if (ArrayUtils.memcmp(sourceBytes, i, searchBytes, 0, matchSize) == 0) {
                        matchFoundProfile.enter();
                        return i;
                    }
                }
            }

            noMatchProfile.enter();
            return nil();
        }

        @Specialization(guards = "isBrokenCodeRange(pattern)")
        public Object stringRindexBrokenPattern(DynamicObject string, DynamicObject pattern, int byteOffset) {
            assert byteOffset >= 0;

            return nil();
        }

        @Specialization(guards = { "!isBrokenCodeRange(pattern)", "!canMemcmp(string, pattern)" })
        public Object stringRindex(DynamicObject string, DynamicObject pattern, int byteOffset,
                @Cached("create()") RopeNodes.BytesNode stringBytes,
                @Cached("create()") RopeNodes.BytesNode patternBytes,
                @Cached("create()") RopeNodes.GetByteNode patternGetByteNode,
                @Cached("create()") RopeNodes.GetByteNode stringGetByteNode) {
            // Taken from Rubinius's String::rindex.
            assert byteOffset >= 0;

            int pos = byteOffset;

            final Rope stringRope = rope(string);
            final Rope patternRope = rope(pattern);
            final int total = stringRope.byteLength();
            final int matchSize = patternRope.byteLength();

            if (pos >= total) {
                pos = total - 1;
            }

            switch(matchSize) {
                case 0: {
                    return byteOffset;
                }

                case 1: {
                    final int matcher = patternGetByteNode.executeGetByte(patternRope, 0);

                    while (pos >= 0) {
                        if (stringGetByteNode.executeGetByte(stringRope, pos) == matcher) {
                            return pos;
                        }

                        pos--;
                    }

                    return nil();
                }

                default: {
                    if (total - pos < matchSize) {
                        pos = total - matchSize;
                    }

                    int cur = pos;

                    while (cur >= 0) {
                        // TODO (nirvdrum 21-Jan-16): Investigate a more rope efficient memcmp.
                        if (ArrayUtils.memcmp(stringBytes.execute(stringRope), cur, patternBytes.execute(patternRope), 0, matchSize) == 0) {
                            return cur;
                        }

                        cur--;
                    }
                }
            }

            return nil();
        }

        private void checkEncoding(DynamicObject string, DynamicObject pattern) {
            if (checkEncodingNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                checkEncodingNode = insert(EncodingNodes.CheckEncodingNode.create());
            }

            checkEncodingNode.executeCheckEncoding(string, pattern);
        }

    }

    @NonStandard
    @CoreMethod(names = "pattern", constructor = true, required = 2, lowerFixnum = { 1, 2 })
    public static abstract class StringPatternPrimitiveNode extends CoreMethodArrayArgumentsNode {

        @Child private AllocateObjectNode allocateObjectNode = AllocateObjectNode.create();
        @Child private RopeNodes.MakeLeafRopeNode makeLeafRopeNode = RopeNodes.MakeLeafRopeNode.create();
        @Child private RopeNodes.RepeatNode repeatNode = RopeNodes.RepeatNode.create();

        @Specialization(guards = "value >= 0")
        public DynamicObject stringPatternZero(DynamicObject stringClass, int size, int value) {
            final Rope repeatingRope = repeatNode.executeRepeat(RopeConstants.ASCII_8BIT_SINGLE_BYTE_ROPES[value], size);

            return allocateObjectNode.allocate(stringClass, Layouts.STRING.build(false, false, repeatingRope, null));
        }

        @Specialization(guards = { "isRubyString(string)", "patternFitsEvenly(string, size)" })
        public DynamicObject stringPatternFitsEvenly(DynamicObject stringClass, int size, DynamicObject string) {
            final Rope rope = rope(string);
            final Rope repeatingRope = repeatNode.executeRepeat(rope, size / rope.byteLength());

            return allocateObjectNode.allocate(stringClass, Layouts.STRING.build(false, false, repeatingRope, null));
        }

        @Specialization(guards = { "isRubyString(string)", "!patternFitsEvenly(string, size)" })
        @TruffleBoundary
        public DynamicObject stringPattern(DynamicObject stringClass, int size, DynamicObject string) {
            final Rope rope = rope(string);
            final byte[] bytes = new byte[size];

            // TODO (nirvdrum 21-Jan-16): Investigate whether using a ConcatRope (potentially combined with a RepeatingRope) would be better here.
            if (!rope.isEmpty()) {
                for (int n = 0; n < size; n += rope.byteLength()) {
                    System.arraycopy(rope.getBytes(), 0, bytes, n, Math.min(rope.byteLength(), size - n));
                }
            }

            // If we reach this specialization, the `size` attribute will cause a truncated `string` to appear at the
            // end of the resulting string in order to pad the value out. A truncated CR_7BIT string is always CR_7BIT.
            // A truncated CR_VALID string could be any of the code range values.
            final CodeRange codeRange = rope.getCodeRange() == CodeRange.CR_7BIT ? CodeRange.CR_7BIT : CodeRange.CR_UNKNOWN;
            final Object characterLength = codeRange == CodeRange.CR_7BIT ? size : NotProvided.INSTANCE;

            return allocateObjectNode.allocate(stringClass, Layouts.STRING.build(false, false, makeLeafRopeNode.executeMake(bytes, encoding(string), codeRange, characterLength), null));
        }

        protected boolean patternFitsEvenly(DynamicObject string, int size) {
            assert RubyGuards.isRubyString(string);

            final int byteLength = rope(string).byteLength();

            return byteLength > 0 && (size % byteLength) == 0;
        }

    }

    @Primitive(name = "string_splice", needsSelf = false, lowerFixnum = { 3, 4 })
    @ImportStatic(StringGuards.class)
    public static abstract class StringSplicePrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = { "indexAtStartBound(spliceByteIndex)", "isRubyString(other)", "isRubyEncoding(rubyEncoding)" })
        public Object splicePrepend(DynamicObject string, DynamicObject other, int spliceByteIndex, int byteCountToReplace, DynamicObject rubyEncoding,
                @Cached("create()") RopeNodes.SubstringNode prependSubstringNode,
                @Cached("create()") RopeNodes.ConcatNode prependConcatNode) {

            final Encoding encoding = EncodingOperations.getEncoding(rubyEncoding);
            final Rope original = rope(string);
            final Rope left = rope(other);
            final Rope right = prependSubstringNode.executeSubstring(original, byteCountToReplace, original.byteLength() - byteCountToReplace);

            StringOperations.setRope(string, prependConcatNode.executeConcat(left, right, encoding));

            return string;
        }

        @Specialization(guards = { "indexAtEndBound(string, spliceByteIndex)", "isRubyString(other)", "isRubyEncoding(rubyEncoding)" })
        public Object spliceAppend(DynamicObject string, DynamicObject other, int spliceByteIndex, int byteCountToReplace, DynamicObject rubyEncoding,
                @Cached("create()") RopeNodes.ConcatNode appendConcatNode) {
            final Encoding encoding = EncodingOperations.getEncoding(rubyEncoding);
            final Rope left = rope(string);
            final Rope right = rope(other);

            StringOperations.setRope(string, appendConcatNode.executeConcat(left, right, encoding));

            return string;
        }

        @Specialization(guards = { "!indexAtEitherBounds(string, spliceByteIndex)", "isRubyString(other)", "isRubyEncoding(rubyEncoding)" })
        public DynamicObject splice(DynamicObject string, DynamicObject other, int spliceByteIndex, int byteCountToReplace, DynamicObject rubyEncoding,
                @Cached("createBinaryProfile()") ConditionProfile insertStringIsEmptyProfile,
                @Cached("createBinaryProfile()") ConditionProfile splitRightIsEmptyProfile,
                @Cached("create()") RopeNodes.SubstringNode leftSubstringNode,
                @Cached("create()") RopeNodes.SubstringNode rightSubstringNode,
                @Cached("create()") RopeNodes.ConcatNode leftConcatNode,
                @Cached("create()") RopeNodes.ConcatNode rightConcatNode) {

            final Encoding encoding = EncodingOperations.getEncoding(rubyEncoding);
            final Rope source = rope(string);
            final Rope insert = rope(other);
            final int rightSideStartingIndex = spliceByteIndex + byteCountToReplace;

            final Rope splitLeft = leftSubstringNode.executeSubstring(source, 0, spliceByteIndex);
            final Rope splitRight = rightSubstringNode.executeSubstring(source, rightSideStartingIndex, source.byteLength() - rightSideStartingIndex);

            final Rope joinedLeft;
            if (insertStringIsEmptyProfile.profile(insert.isEmpty())) {
                joinedLeft = splitLeft;
            } else {
                joinedLeft = leftConcatNode.executeConcat(splitLeft, insert, encoding);
            }

            final Rope joinedRight;
            if (splitRightIsEmptyProfile.profile(splitRight.isEmpty())) {
                joinedRight = joinedLeft;
            } else {
                joinedRight = rightConcatNode.executeConcat(joinedLeft, splitRight, encoding);
            }

            StringOperations.setRope(string, joinedRight);

            return string;
        }

        protected boolean indexAtStartBound(int index) {
            return index == 0;
        }

        protected boolean indexAtEndBound(DynamicObject string, int index) {
            assert RubyGuards.isRubyString(string);

            return index == rope(string).byteLength();
        }

        protected boolean indexAtEitherBounds(DynamicObject string, int index) {
            assert RubyGuards.isRubyString(string);

            return indexAtStartBound(index) || indexAtEndBound(string, index);
        }

    }

    @Primitive(name = "string_to_inum", lowerFixnum = 1)
    public static abstract class StringToInumPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        public Object stringToInum(DynamicObject string, int fixBase, boolean strict,
                @Cached("create(getSourceIndexLength())") FixnumOrBignumNode fixnumOrBignumNode,
                @Cached("create()") BranchProfile exceptionProfile) {
            try {
                return ConvertBytes.byteListToInum19(getContext(),
                        this,
                        fixnumOrBignumNode,
                        string,
                        fixBase,
                        strict);
            } catch (RaiseException e) {
                exceptionProfile.enter();
                throw e;
            }
        }

    }

    @NonStandard
    @CoreMethod(names = "byte_append", required = 1)
    public static abstract class StringByteAppendPrimitiveNode extends CoreMethodArrayArgumentsNode {

        @Child private RopeNodes.ConcatNode concatNode = RopeNodes.ConcatNode.create();

        @Specialization(guards = "isRubyString(other)")
        public DynamicObject stringByteAppend(DynamicObject string, DynamicObject other) {
            final Rope left = rope(string);
            final Rope right = rope(other);

            // The semantics of this primitive are such that the original string's byte[] should be extended without
            // negotiating the encoding.

            StringOperations.setRope(string, concatNode.executeConcat(left, right, left.getEncoding()));

            return string;
        }

    }

    @NonStandard
    @CoreMethod(names = "substring", lowerFixnum = { 1, 2 }, required = 2)
    @ImportStatic(StringGuards.class)
    public static abstract class StringSubstringPrimitiveNode extends CoreMethodArrayArgumentsNode {

        @Child private AllocateObjectNode allocateNode;
        @Child private NormalizeIndexNode normalizeIndexNode = StringNodesFactory.NormalizeIndexNodeGen.create(null, null);
        @Child private RopeNodes.SubstringNode substringNode;
        @Child private TaintResultNode taintResultNode;

        public abstract Object execute(VirtualFrame frame, DynamicObject string, int index, int characterLength);

        @Specialization(guards = { "!indexTriviallyOutOfBounds(string, beg, characterLen)", "noCharacterSearch(string)" })
        public Object stringSubstringSingleByte(DynamicObject string, int beg, int characterLen,
                @Cached("createBinaryProfile()") ConditionProfile negativeIndexProfile,
                @Cached("createBinaryProfile()") ConditionProfile tooLargeTotalProfile) {
            final Rope rope = rope(string);
            final int index = normalizeIndexNode.executeNormalize(beg, rope.characterLength());
            int characterLength = characterLen;

            if (negativeIndexProfile.profile(index < 0)) {
                return nil();
            }

            if (tooLargeTotalProfile.profile(index + characterLength > rope.characterLength())) {
                characterLength = rope.characterLength() - index;
            }

            return makeRope(string, rope, index, characterLength);
        }

        @Specialization(guards = { "!indexTriviallyOutOfBounds(string, beg, characterLen)", "!noCharacterSearch(string)" })
        public Object stringSubstringGeneric(DynamicObject string, int beg, int characterLen,
                @Cached("createBinaryProfile()") ConditionProfile negativeIndexProfile,
                @Cached("createBinaryProfile()") ConditionProfile tooLargeTotalProfile,
                @Cached("createBinaryProfile()") ConditionProfile foundSingleByteOptimizableDescendentProfile,
                @Cached("create()") BranchProfile singleByteOptimizableBaseProfile,
                @Cached("create()") BranchProfile leafBaseProfile,
                @Cached("create()") BranchProfile slowSearchProfile,
                @Cached("create()") BranchProfile utf8Profile,
                @Cached("create()") BranchProfile fixedWidthProfile,
                @Cached("create()") BranchProfile fallbackProfile,
                @Cached("create()") RopeNodes.BytesNode bytesNode) {
            final Rope rope = rope(string);
            final int index = normalizeIndexNode.executeNormalize(beg, rope.characterLength());
            int characterLength = characterLen;

            if (negativeIndexProfile.profile(index < 0)) {
                return nil();
            }

            if (tooLargeTotalProfile.profile(index + characterLength > rope.characterLength())) {
                characterLength = rope.characterLength() - index;
            }

            final SearchResult searchResult = searchForSingleByteOptimizableDescendant(rope, index, characterLength,
                    singleByteOptimizableBaseProfile , leafBaseProfile, slowSearchProfile);

            if (foundSingleByteOptimizableDescendentProfile.profile(searchResult.rope.isSingleByteOptimizable())) {
                return makeRope(string, searchResult.rope, searchResult.index, characterLength);
            }

            return stringSubstringMultiByte(string, index, characterLength, bytesNode, utf8Profile, fixedWidthProfile, fallbackProfile);
        }

        @Specialization(guards = "indexTriviallyOutOfBounds(string, beg, len)")
        public Object stringSubstringNegativeLength(DynamicObject string, int beg, int len) {
            return nil();
        }

        private SearchResult searchForSingleByteOptimizableDescendant(Rope base, int index, int characterLength,
                BranchProfile singleByteOptimizableBaseProfile,
                BranchProfile leafBaseProfile,
                BranchProfile slowSearchProfile) {

            if (base.isSingleByteOptimizable()) {
                singleByteOptimizableBaseProfile.enter();
                return new SearchResult(index, base);
            }

            if (base instanceof LeafRope) {
                leafBaseProfile.enter();
                return new SearchResult(index, base);
            }

            slowSearchProfile.enter();
            return searchForSingleByteOptimizableDescendantSlow(base, index, characterLength);
        }

        @TruffleBoundary
        private SearchResult searchForSingleByteOptimizableDescendantSlow(Rope base, int index, int characterLength) {
            // If we've found something that's single-byte optimizable, we can halt the search. Taking a substring of
            // a single byte optimizable rope is a fast operation.
            if (base.isSingleByteOptimizable()) {
                return new SearchResult(index, base);
            }

            if (base instanceof LeafRope) {
                return new SearchResult(index, base);
            } else if (base instanceof SubstringRope) {
                final SubstringRope substringRope = (SubstringRope) base;
                if (substringRope.isSingleByteOptimizable()) {
                    // the substring byte offset is also a character offset
                    return searchForSingleByteOptimizableDescendantSlow(substringRope.getChild(), index + substringRope.getByteOffset(), characterLength);
                } else {
                    return new SearchResult(index, substringRope);
                }
            } else if (base instanceof ConcatRope) {
                final ConcatRope concatRope = (ConcatRope) base;
                final Rope left = concatRope.getLeft();
                final Rope right = concatRope.getRight();

                if (index + characterLength <= left.characterLength()) {
                    return searchForSingleByteOptimizableDescendantSlow(left, index, characterLength);
                } else if (index >= left.characterLength()) {
                    return searchForSingleByteOptimizableDescendantSlow(right, index - left.characterLength(), characterLength);
                } else {
                    return new SearchResult(index, concatRope);
                }
            } else if (base instanceof RepeatingRope) {
                final RepeatingRope repeatingRope = (RepeatingRope) base;

                if (index + characterLength <= repeatingRope.getChild().characterLength()) {
                    return searchForSingleByteOptimizableDescendantSlow(repeatingRope.getChild(), index, characterLength);
                } else {
                    return new SearchResult(index, repeatingRope);
                }
            } else if (base instanceof NativeRope) {
                final NativeRope nativeRope = (NativeRope) base;
                return new SearchResult(index, nativeRope.toLeafRope());
            } else {
                throw new UnsupportedOperationException("Don't know how to traverse rope type: " + base.getClass().getName());
            }
        }

        private Object stringSubstringMultiByte(DynamicObject string, int beg, int characterLen,
                RopeNodes.BytesNode bytesNode,
                BranchProfile utf8Profile,
                BranchProfile fixedWidthProfile,
                BranchProfile fallbackProfile) {
            // Taken from org.jruby.RubyString#substr19 & org.jruby.RubyString#multibyteSubstr19.

            final Rope rope = rope(string);
            final int length = rope.byteLength();

            final Encoding enc = rope.getEncoding();
            int p;
            int s = 0;
            int end = s + length;
            byte[] bytes = bytesNode.execute(rope);
            int substringByteLength = characterLen;

            if (rope.getCodeRange() == CR_VALID && enc.isUTF8()) {
                utf8Profile.enter();

                p = StringSupport.utf8Nth(bytes, s, end, beg);
                substringByteLength = StringSupport.utf8Offset(bytes, p, end, characterLen);
            } else if (enc.isFixedWidth()) {
                fixedWidthProfile.enter();

                int w = enc.maxLength();
                p = s + beg * w;
                if (p > end) {
                    p = end;
                    substringByteLength = 0;
                } else if (characterLen * w > end - p) {
                    substringByteLength = end - p;
                } else {
                    substringByteLength *= w;
                }
            } else {
                fallbackProfile.enter();

                if ((p = StringSupport.nth(enc, bytes, s, end, beg)) == end) {
                    substringByteLength = 0;
                } else {
                    substringByteLength = StringSupport.offset(enc, bytes, p, end, characterLen);
                }
            }

            return makeRope(string, rope, p - s, substringByteLength);
        }

        private DynamicObject makeRope(DynamicObject string, Rope rope, int beg, int byteLength) {
            assert RubyGuards.isRubyString(string);

            if (allocateNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                allocateNode = insert(AllocateObjectNode.create());
            }

            if (substringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                substringNode = insert(RopeNodes.SubstringNode.create());
            }

            if (taintResultNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                taintResultNode = insert(new TaintResultNode());
            }

            final DynamicObject ret = allocateNode.allocate(
                    Layouts.BASIC_OBJECT.getLogicalClass(string),
                    Layouts.STRING.build(false, false, substringNode.executeSubstring(rope, beg, byteLength), null));

            taintResultNode.maybeTaint(string, ret);

            return ret;
        }

        protected static boolean indexTriviallyOutOfBounds(DynamicObject string, int index, int length) {
            assert RubyGuards.isRubyString(string);

            return (length < 0) || (index > rope(string).characterLength());
        }

        protected static boolean noCharacterSearch(DynamicObject string) {
            final Rope rope = rope(string);
            return rope.isEmpty() || rope.isSingleByteOptimizable();
        }

        private static final class SearchResult {
            public final int index;
            public final Rope rope;

            public SearchResult(final int index, final Rope rope) {
                this.index = index;
                this.rope = rope;
            }
        }

    }

    @NonStandard
    @CoreMethod(names = "from_bytearray", onSingleton = true, required = 4, lowerFixnum = { 2, 3 })
    public static abstract class StringFromByteArrayPrimitiveNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = { "isByteArray(bytes)", "isRubyEncoding(rubyEncoding)" })
        public DynamicObject stringFromByteArray(DynamicObject bytes, int start, int count, DynamicObject rubyEncoding,
                                                 @Cached("create()") StringNodes.MakeStringNode makeStringNode) {
            final ByteArrayBuilder builder = Layouts.BYTE_ARRAY.getBytes(bytes);
            final byte[] array = ArrayUtils.extractRange(builder.getUnsafeBytes(), start, start + count);
            final Encoding encoding = EncodingOperations.getEncoding(rubyEncoding);

            return makeStringNode.executeMake(array, encoding, CR_UNKNOWN);
        }

    }

    @NodeChildren({ @NodeChild("string"), @NodeChild("other") })
    public static abstract class StringAppendNode extends RubyNode {

        @Child private EncodingNodes.CheckEncodingNode checkEncodingNode;
        @Child private RopeNodes.ConcatNode concatNode;

        public static StringAppendNode create() {
            return StringNodesFactory.StringAppendNodeGen.create(null, null);
        }

        public abstract Rope executeStringAppend(DynamicObject string, DynamicObject other);

        @Specialization
        public Rope stringAppend(DynamicObject string, DynamicObject other) {
            assert RubyGuards.isRubyString(string);
            assert RubyGuards.isRubyString(other);

            final Rope left = rope(string);
            final Rope right = rope(other);

            final Encoding compatibleEncoding = executeCheckEncoding(string, other);

            return executeConcat(left, right, compatibleEncoding);
        }

        private Rope executeConcat(Rope left, Rope right, Encoding compatibleEncoding) {
            if (concatNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                concatNode = insert(RopeNodes.ConcatNode.create());
            }
            return concatNode.executeConcat(left, right, compatibleEncoding);
        }

        private Encoding executeCheckEncoding(DynamicObject string, DynamicObject other) {
            if (checkEncodingNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                checkEncodingNode = insert(EncodingNodesFactory.CheckEncodingNodeGen.create(null, null));
            }
            return checkEncodingNode.executeCheckEncoding(string, other);
        }

    }

    @Primitive(name = "string_to_null_terminated_byte_array", needsSelf = false)
    public static abstract class StringToNullTerminatedByteArrayNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "isRubyString(string)")
        protected TruffleObject stringToNullTerminatedByteArray(DynamicObject string,
                @Cached("create()") RopeNodes.BytesNode bytesNode) {
            // NOTE: we always need one copy here, as native code could modify the passed byte[]
            final byte[] bytes = bytesNode.execute(rope(string));
            final byte[] bytesWithNull = new byte[bytes.length + 1];
            System.arraycopy(bytes, 0, bytesWithNull, 0, bytes.length);

            return JavaInterop.asTruffleObject(bytesWithNull);
        }

        @Specialization(guards = "isNil(string)")
        protected TruffleObject emptyString(DynamicObject string) {
            return JavaInterop.asTruffleObject(null);
        }

    }

}
