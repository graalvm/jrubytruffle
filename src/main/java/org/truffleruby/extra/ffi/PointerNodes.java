/*
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.extra.ffi;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import org.jcodings.specific.ASCIIEncoding;
import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.builtins.CoreClass;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.builtins.UnaryCoreMethodNode;
import org.truffleruby.core.encoding.EncodingManager;
import org.truffleruby.core.numeric.BignumOperations;
import org.truffleruby.core.rope.CodeRange;
import org.truffleruby.core.rope.Rope;
import org.truffleruby.core.rope.RopeConstants;
import org.truffleruby.core.string.StringNodes;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.language.Visibility;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.objects.AllocateObjectNode;
import org.truffleruby.platform.NativeTypes;

@CoreClass("Truffle::FFI::Pointer")
public abstract class PointerNodes {

    public static final BigInteger TWO_POW_64 = BigInteger.valueOf(1).shiftLeft(64);

    private static abstract class PointerPrimitiveArrayArgumentsNode extends PrimitiveArrayArgumentsNode {

        private final BranchProfile nullPointerProfile = BranchProfile.create();

        protected void checkNull(Pointer ptr) {
            if (ptr.isNull()) {
                nullPointerProfile.enter();
                throw new RaiseException(coreExceptions().ffiNullPointerError("invalid memory access at address=0x0", this));
            }
        }

    }

    @CoreMethod(names = "__allocate__", constructor = true, visibility = Visibility.PRIVATE)
    public static abstract class AllocateNode extends UnaryCoreMethodNode {

        @Child private AllocateObjectNode allocateObjectNode = AllocateObjectNode.create();

        @Specialization
        public DynamicObject allocate(DynamicObject pointerClass) {
            return allocateObjectNode.allocate(pointerClass, Pointer.NULL);
        }

    }

    @Primitive(name = "nativefunction_type_size", needsSelf = false, lowerFixnum = 1)
    public static abstract class NativeFunctionTypeSizePrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        public long typeSize(int type) {
            switch (type) {
                case NativeTypes.TYPE_CHAR:
                case NativeTypes.TYPE_UCHAR:
                    return 1;

                case NativeTypes.TYPE_SHORT:
                case NativeTypes.TYPE_USHORT:
                    return 2;

                case NativeTypes.TYPE_INT:
                case NativeTypes.TYPE_UINT:
                    return 4;

                case NativeTypes.TYPE_LONG:
                case NativeTypes.TYPE_ULONG:
                case NativeTypes.TYPE_LL:
                case NativeTypes.TYPE_ULL:
                    return 8;

                case NativeTypes.TYPE_FLOAT:
                    return 4;

                case NativeTypes.TYPE_DOUBLE:
                    return 8;

                case NativeTypes.TYPE_PTR:
                case NativeTypes.TYPE_STRPTR:
                case NativeTypes.TYPE_STRING:
                case NativeTypes.TYPE_CHARARR:
                    return 8;

                case NativeTypes.TYPE_BOOL:
                case NativeTypes.TYPE_VOID:
                case NativeTypes.TYPE_ENUM:
                case NativeTypes.TYPE_VARARGS:
                default:
                    throw new UnsupportedOperationException("no type size for: " + type);
            }
        }

        @Specialization(guards = "!isInteger(type)")
        public Object fallback(Object type) {
            return FAILURE;
        }

    }

    @Primitive(name = "pointer_set_address")
    public static abstract class PointerSetAddressPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        public long setAddress(DynamicObject pointer, long address) {
            Layouts.POINTER.setPointer(pointer, new Pointer(address));
            return address;
        }

    }

    @Primitive(name = "pointer_address")
    public static abstract class PointerAddressPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        public long address(DynamicObject pointer) {
            return Layouts.POINTER.getPointer(pointer).getAddress();
        }

    }

    @Primitive(name = "pointer_set_autorelease")
    public static abstract class PointerSetAutoreleasePrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "autorelease")
        public boolean enableAutorelease(DynamicObject pointer, boolean autorelease) {
            Layouts.POINTER.getPointer(pointer).enableAutorelease(getContext().getFinalizationService());
            return autorelease;
        }

        @Specialization(guards = "!autorelease")
        public boolean disableAutorelease(DynamicObject pointer, boolean autorelease) {
            Layouts.POINTER.getPointer(pointer).disableAutorelease(getContext().getFinalizationService());
            return autorelease;
        }

    }

    @Primitive(name = "pointer_malloc")
    public static abstract class PointerMallocPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Child private AllocateObjectNode allocateObjectNode = AllocateObjectNode.create();

        @Specialization
        public DynamicObject malloc(DynamicObject pointerClass, long size) {
            // TODO CS 15-Nov-17 I think this should possibly also set @total to size
            return allocateObjectNode.allocate(pointerClass, Pointer.malloc(size));
        }

    }

    @Primitive(name = "pointer_free")
    public static abstract class PointerFreePrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        public DynamicObject free(DynamicObject pointer) {
            Layouts.POINTER.getPointer(pointer).free();
            return pointer;
        }

    }

    @Primitive(name = "pointer_clear", lowerFixnum = 1)
    public abstract static class PointerClearNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "isRubyPointer(pointer)")
        public DynamicObject clear(DynamicObject pointer, long length) {
            Layouts.POINTER.getPointer(pointer).writeBytes(0, length, (byte) 0);
            return pointer;
        }

    }

    @Primitive(name = "pointer_add")
    public static abstract class PointerAddPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Child private AllocateObjectNode allocateObjectNode = AllocateObjectNode.create();

        @Specialization
        public DynamicObject add(DynamicObject a, long b) {
            return allocateObjectNode.allocate(Layouts.BASIC_OBJECT.getLogicalClass(a),
                    new Pointer(Layouts.POINTER.getPointer(a).getAddress() + b));
        }

    }

    // Special reads and writes

    @Primitive(name = "pointer_read_string", lowerFixnum = 1)
    public static abstract class PointerReadStringPrimitiveNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization
        public DynamicObject readString(DynamicObject pointer, int length,
                @Cached("createBinaryProfile()") ConditionProfile zeroProfile,
                @Cached("create()") StringNodes.MakeStringNode makeStringNode) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            if (zeroProfile.profile(length == 0)) {
                // No need to check the pointer address if we read nothing
                return makeStringNode.fromRope(RopeConstants.EMPTY_ASCII_8BIT_ROPE);
            } else {
                checkNull(ptr);
                final byte[] bytes = new byte[length];
                ptr.readBytes(0, bytes, 0, length);
                return makeStringNode.executeMake(bytes, ASCIIEncoding.INSTANCE, CodeRange.CR_UNKNOWN);
            }
        }

    }

    @Primitive(name = "pointer_read_string_to_null")
    public static abstract class PointerReadStringToNullPrimitiveNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization(guards = "isNullPointer(pointer)")
        public DynamicObject readNullPointer(DynamicObject pointer,
                                             @Cached("create()") AllocateObjectNode allocateObjectNode) {
            return allocateObjectNode.allocate(coreLibrary().getStringClass(), Layouts.STRING.build(false, false, RopeConstants.EMPTY_ASCII_8BIT_ROPE, null));
        }

        @Specialization(guards = "!isNullPointer(pointer)")
        public DynamicObject readStringToNull(DynamicObject pointer,
                                              @Cached("create()") StringNodes.MakeStringNode makeStringNode) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            final byte[] bytes = ptr.readZeroTerminatedByteArray(getContext(), 0);
            return makeStringNode.executeMake(bytes, ASCIIEncoding.INSTANCE, CodeRange.CR_UNKNOWN);
        }

    }

    @Primitive(name = "pointer_read_pointer")
    public static abstract class PointerReadPointerPrimitiveNode extends PointerPrimitiveArrayArgumentsNode {

        @Child private AllocateObjectNode allocateObjectNode = AllocateObjectNode.create();

        @Specialization
        public DynamicObject readPointer(DynamicObject pointer) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            final Pointer readPointer = ptr.readPointer(0);
            return allocateObjectNode.allocate(Layouts.BASIC_OBJECT.getLogicalClass(pointer), readPointer);
        }

    }

    @Primitive(name = "pointer_write_string", lowerFixnum = 2)
    public static abstract class PointerWriteStringPrimitiveNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization(guards = "isRubyString(string)")
        public DynamicObject address(DynamicObject pointer, DynamicObject string, int maxLength) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            final Rope rope = StringOperations.rope(string);
            final int length = Math.min(rope.byteLength(), maxLength);
            if (length != 0) {
                // No need to check the pointer address if we write nothing
                checkNull(ptr);
            }
            // Does NOT write an extra final NULL byte, like upstream FFI.
            ptr.writeBytes(0, rope.getBytes(), 0, length);
            return pointer;
        }

    }

    @Primitive(name = "pointer_write_pointer", lowerFixnum = 2)
    public static abstract class PointerWritePointerPrimitiveNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization(guards = "isRubyPointer(value)")
        public DynamicObject address(DynamicObject pointer, DynamicObject value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            ptr.writePointer(0, Layouts.POINTER.getPointer(value));
            return value;
        }

    }

    // Reads and writes of number types

    @Primitive(name = "pointer_read_char")
    public static abstract class PointerReadCharPrimitiveNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization(guards = "signed")
        public int readCharSigned(DynamicObject pointer, boolean signed) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return ptr.readByte(0);
        }

        @Specialization(guards = "!signed")
        public int readCharUnsigned(DynamicObject pointer, boolean signed) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return Byte.toUnsignedInt(ptr.readByte(0));
        }

    }

    @Primitive(name = "pointer_read_short")
    public static abstract class PointerReadShortPrimitiveNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization(guards = "signed")
        public int readShortSigned(DynamicObject pointer, boolean signed) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return ptr.readShort(0);
        }

        @Specialization(guards = "!signed")
        public int readShortUnsigned(DynamicObject pointer, boolean signed) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return Short.toUnsignedInt(ptr.readShort(0));
        }

    }

    @Primitive(name = "pointer_read_int")
    public static abstract class PointerReadIntPrimitiveNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization(guards = "signed")
        public int readIntSigned(DynamicObject pointer, boolean signed) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return ptr.readInt(0);
        }

        @Specialization(guards = "!signed")
        public long readIntUnsigned(DynamicObject pointer, boolean signed) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return Integer.toUnsignedLong(ptr.readInt(0));
        }

    }

    @Primitive(name = "pointer_read_long")
    public static abstract class PointerReadLongPrimitiveNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization(guards = "signed")
        public long readLongSigned(DynamicObject pointer, boolean signed) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return ptr.readLong(0);
        }

        @Specialization(guards = "!signed")
        public Object readLongUnsigned(DynamicObject pointer, boolean signed) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return readUnsignedLong(getContext(), ptr, 0);
        }

        @TruffleBoundary
        private static Object readUnsignedLong(RubyContext context, Pointer ptr, int offset) {
            long signedValue = ptr.readLong(offset);
            if (signedValue >= 0) {
                return signedValue;
            } else {
                return BignumOperations.createBignum(context, BigInteger.valueOf(signedValue).add(TWO_POW_64));
            }
        }

    }

    @Primitive(name = "pointer_read_double")
    public static abstract class PointerReadDoublePrimitiveNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization
        public double readDouble(DynamicObject pointer) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return ptr.readDouble(0);
        }

    }

    @Primitive(name = "pointer_get_at_offset", lowerFixnum = { 1, 2 })
    @ImportStatic(NativeTypes.class)
    public static abstract class PointerGetAtOffsetPrimitiveNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization(guards = "type == TYPE_CHAR")
        public int getAtOffsetChar(DynamicObject pointer, int offset, int type) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return ptr.readByte(offset);
        }

        @Specialization(guards = "type == TYPE_UCHAR")
        public int getAtOffsetUChar(DynamicObject pointer, int offset, int type) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return Byte.toUnsignedInt(ptr.readByte(offset));
        }

        @Specialization(guards = "type == TYPE_SHORT")
        public int getAtOffsetShort(DynamicObject pointer, int offset, int type) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return ptr.readShort(offset);
        }

        @Specialization(guards = "type == TYPE_USHORT")
        public int getAtOffsetUShort(DynamicObject pointer, int offset, int type) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return Short.toUnsignedInt(ptr.readShort(offset));
        }

        @Specialization(guards = "type == TYPE_INT")
        public int getAtOffsetInt(DynamicObject pointer, int offset, int type) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return ptr.readInt(offset);
        }

        @Specialization(guards = "type == TYPE_UINT")
        public long getAtOffsetUInt(DynamicObject pointer, int offset, int type) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return Integer.toUnsignedLong(ptr.readInt(offset));
        }

        @Specialization(guards = "type == TYPE_LONG")
        public long getAtOffsetLong(DynamicObject pointer, int offset, int type) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return ptr.readLong(offset);
        }

        @Specialization(guards = "type == TYPE_ULONG")
        public Object getAtOffsetULong(DynamicObject pointer, int offset, int type) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return PointerReadLongPrimitiveNode.readUnsignedLong(getContext(), ptr, offset);
        }

        @Specialization(guards = "type == TYPE_LL")
        public long getAtOffsetLL(DynamicObject pointer, int offset, int type) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return ptr.readLong(offset);
        }

        @Specialization(guards = "type == TYPE_ULL")
        public Object getAtOffsetULL(DynamicObject pointer, int offset, int type) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return PointerReadLongPrimitiveNode.readUnsignedLong(getContext(), ptr, offset);
        }

        @TruffleBoundary
        @Specialization(guards = "type == TYPE_STRING")
        public DynamicObject getAtOffsetString(DynamicObject pointer, int offset, int type,
                                               @Cached("create()") StringNodes.MakeStringNode makeStringNode) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            final byte[] bytes = ptr.readZeroTerminatedByteArray(getContext(), offset);
            return makeStringNode.executeMake(bytes, ASCIIEncoding.INSTANCE, CodeRange.CR_UNKNOWN);
        }

        @Specialization(guards = "type == TYPE_PTR")
        public DynamicObject getAtOffsetPointer(DynamicObject pointer, int offset, int type,
                                                @Cached("create()") AllocateObjectNode allocateObjectNode) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            final Pointer readPointer = ptr.readPointer(offset);
            return allocateObjectNode.allocate(Layouts.BASIC_OBJECT.getLogicalClass(pointer), readPointer);
        }

    }

    @Primitive(name = "pointer_set_at_offset", lowerFixnum = { 1, 2, 3 })
    @ImportStatic(NativeTypes.class)
    public static abstract class PointerSetAtOffsetPrimitiveNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization(guards = "type == TYPE_CHAR")
        public int setAtOffsetChar(DynamicObject pointer, int offset, int type, int value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            assert ((byte) value) == value;
            ptr.writeByte(offset, (byte) value);
            return value;
        }

        @Specialization(guards = "type == TYPE_UCHAR")
        public int setAtOffsetUChar(DynamicObject pointer, int offset, int type, int value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            assert value >= 0 && value < (1 << Byte.SIZE);
            ptr.writeByte(offset, (byte) value);
            return value;
        }

        @Specialization(guards = "type == TYPE_SHORT")
        public int setAtOffsetShort(DynamicObject pointer, int offset, int type, int value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            assert ((short) value) == value;
            ptr.writeShort(offset, (short) value);
            return value;
        }

        @Specialization(guards = "type == TYPE_USHORT")
        public int setAtOffsetUShort(DynamicObject pointer, int offset, int type, int value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            assert value >= 0 && value < (1 << Short.SIZE);
            ptr.writeShort(offset, (short) value);
            return value;
        }

        @Specialization(guards = "type == TYPE_INT")
        public int setAtOffsetInt(DynamicObject pointer, int offset, int type, int value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            ptr.writeInt(offset, value);
            return value;
        }

        @Specialization(guards = "type == TYPE_UINT")
        public long setAtOffsetUInt(DynamicObject pointer, int offset, int type, long value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            assert value >= 0 && value < (1L << Integer.SIZE);
            ptr.writeInt(offset, (int) value);
            return value;
        }

        @Specialization(guards = "type == TYPE_LONG")
        public long setAtOffsetLong(DynamicObject pointer, int offset, int type, long value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            ptr.writeLong(offset, value);
            return value;
        }

        @Specialization(guards = "type == TYPE_ULONG")
        public long setAtOffsetULong(DynamicObject pointer, int offset, int type, long value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            assert value >= 0L;
            ptr.writeLong(offset, value);
            return value;
        }

        @Specialization(guards = { "type == TYPE_ULONG", "isRubyBignum(value)" })
        public DynamicObject setAtOffsetULong(DynamicObject pointer, int offset, int type, DynamicObject value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            PointerWriteLongPrimitiveNode.writeUnsignedLong(ptr, offset, value);
            return value;
        }

        @Specialization(guards = "type == TYPE_LL")
        public long setAtOffsetLL(DynamicObject pointer, int offset, int type, long value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            ptr.writeLong(offset, value);
            return value;
        }

        @Specialization(guards = "type == TYPE_ULL")
        public long setAtOffsetULL(DynamicObject pointer, int offset, int type, long value) {
            assert value >= 0L;
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            ptr.writeLong(offset, value);
            return value;
        }

        @Specialization(guards = { "type == TYPE_ULL", "isRubyBignum(value)" })
        public DynamicObject setAtOffsetULL(DynamicObject pointer, int offset, int type, DynamicObject value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            PointerWriteLongPrimitiveNode.writeUnsignedLong(ptr, offset, value);
            return value;
        }

        @Specialization(guards = { "type == TYPE_PTR", "isRubyPointer(value)" })
        public DynamicObject setAtOffsetPtr(DynamicObject pointer, int offset, int type, DynamicObject value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            ptr.writePointer(offset, Layouts.POINTER.getPointer(value));
            return value;
        }

        @TruffleBoundary
        @Specialization(guards = {"type == TYPE_CHARARR", "isRubyString(string)"})
        public DynamicObject setAtOffsetCharArr(DynamicObject pointer, int offset, int type, DynamicObject string) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            final String str = StringOperations.getString(string);
            ByteBuffer buf = EncodingManager.charsetForEncoding(StringOperations.encoding(string)).encode(str);
            int len = Math.min(str.length(), buf.remaining());
            ptr.writeZeroTerminatedBytes(offset, buf.array(), buf.arrayOffset() + buf.position(), len);
            return string;
        }

    }

    /* Rubinius use these write primitives for both signed and unsigned numbers.
     * Values from 0 to MAX_SIGNED are encoded the same for both signed and unsigned.
     * Larger values need to be converted to the corresponding negative signed number
     * as Java only has signed numbers. */

    @Primitive(name = "pointer_write_char", lowerFixnum = 1)
    public static abstract class PointerWriteCharPrimitiveNode extends PointerPrimitiveArrayArgumentsNode {

        static final int MIN_CHAR = Byte.MIN_VALUE;
        static final int MAX_CHAR = Byte.MAX_VALUE;

        @Specialization(guards = { "MIN_CHAR <= value", "value <= MAX_CHAR" })
        public DynamicObject writeChar(DynamicObject pointer, int value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            byte byteValue = (byte) value;
            ptr.writeByte(0, byteValue);
            return pointer;
        }

        @Specialization(guards = "value > MAX_CHAR")
        public DynamicObject writeUnsignedChar(DynamicObject pointer, int value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            assert value < (1 << Byte.SIZE);
            byte signed = (byte) value; // Same as value - 2^8
            ptr.writeByte(0, signed);
            return pointer;
        }

    }

    @Primitive(name = "pointer_write_short", lowerFixnum = 1)
    public static abstract class PointerWriteShortPrimitiveNode extends PointerPrimitiveArrayArgumentsNode {

        static final int MIN_SHORT = Short.MIN_VALUE;
        static final int MAX_SHORT = Short.MAX_VALUE;

        @Specialization(guards = { "MIN_SHORT <= value", "value <= MAX_SHORT" })
        public DynamicObject writeShort(DynamicObject pointer, int value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            short shortValue = (short) value;
            ptr.writeShort(0, shortValue);
            return pointer;
        }

        @Specialization(guards = "value > MAX_SHORT")
        public DynamicObject writeUnsignedSort(DynamicObject pointer, int value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            assert value < (1 << Short.SIZE);
            short signed = (short) value; // Same as value - 2^16
            ptr.writeShort(0, signed);
            return pointer;
        }

    }

    @Primitive(name = "pointer_write_int", lowerFixnum = 1)
    public static abstract class PointerWriteIntPrimitiveNode extends PointerPrimitiveArrayArgumentsNode {

        static final long MAX_INT = Integer.MAX_VALUE;

        @Specialization
        public DynamicObject writeInt(DynamicObject pointer, int value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            ptr.writeInt(0, value);
            return pointer;
        }

        @Specialization(guards = "value > MAX_INT")
        public DynamicObject writeUnsignedInt(DynamicObject pointer, long value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            assert value < (1L << Integer.SIZE);
            int signed = (int) value; // Same as value - 2^32
            ptr.writeInt(0, signed);
            return pointer;
        }

    }

    @Primitive(name = "pointer_write_long", lowerFixnum = 1)
    public static abstract class PointerWriteLongPrimitiveNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization
        public DynamicObject writeLong(DynamicObject pointer, long value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            ptr.writeLong(0, value);
            return pointer;
        }

        @Specialization(guards = "isRubyBignum(value)")
        public DynamicObject writeUnsignedLong(DynamicObject pointer, DynamicObject value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            writeUnsignedLong(ptr, 0, value);
            return pointer;
        }

        @TruffleBoundary
        private static void writeUnsignedLong(Pointer ptr, int offset, DynamicObject value) {
            BigInteger v = Layouts.BIGNUM.getValue(value);
            assert v.signum() >= 0;
            assert v.compareTo(TWO_POW_64) < 0;
            BigInteger signed = v.subtract(TWO_POW_64);
            ptr.writeLong(offset, signed.longValueExact());
        }

    }

}
