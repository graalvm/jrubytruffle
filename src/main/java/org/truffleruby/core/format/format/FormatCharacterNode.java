/*
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.format.format;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.LiteralFormatNode;
import org.truffleruby.core.format.convert.ToIntegerNode;
import org.truffleruby.core.format.convert.ToIntegerNodeGen;
import org.truffleruby.core.format.convert.ToStringNode;
import org.truffleruby.core.format.convert.ToStringNodeGen;
import org.truffleruby.core.format.exceptions.NoImplicitConversionException;
import org.truffleruby.core.format.write.bytes.WriteByteNodeGen;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.language.control.RaiseException;

import java.nio.charset.StandardCharsets;

@NodeChildren({
    @NodeChild(value = "width", type = FormatNode.class),
    @NodeChild(value = "value", type = FormatNode.class),
})
public abstract class FormatCharacterNode extends FormatNode {

    private final boolean hasMinusFlag;

    @Child private ToIntegerNode toIntegerNode;
    @Child private ToStringNode toStringNode;

    public FormatCharacterNode(boolean hasMinusFlag) {
        this.hasMinusFlag = hasMinusFlag;
    }

    @Specialization(
        guards = {
            "width == cachedWidth"
        },
        limit = "getLimit()"
    )
    byte[] formatCached(VirtualFrame frame, int width, Object value,
                        @Cached("width") int cachedWidth,
                        @Cached("makeFormatString(width)") String cachedFormatString) {
        final String charString = getCharString(frame, value);
        return doFormat(charString, cachedFormatString);
    }

    @Specialization(replaces = "formatCached")
    protected byte[] format(VirtualFrame frame, int width, Object value) {
        final String charString = getCharString(frame, value);
        return doFormat(charString, makeFormatString(width));
    }

    protected String getCharString(VirtualFrame frame, Object value) {
        if (toStringNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toStringNode = insert(ToStringNodeGen.create(
                false,
                "to_str",
                false,
                null,
                WriteByteNodeGen.create(new LiteralFormatNode(value))));
        }
        Object toStrResult;
        try {
            toStrResult = toStringNode.executeToString(frame, value);
        } catch (NoImplicitConversionException e) {
            toStrResult = null;
        }

        final String charString;
        if (toStrResult == null || isNil(toStrResult)) {
            if (toIntegerNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toIntegerNode = insert(ToIntegerNodeGen.create(null));
            }
            final int charValue = (int) toIntegerNode.executeToInteger(frame, value);
            // TODO BJF check char length is > 0
            charString = Character.toString((char) charValue);
        } else {
            final String resultString = StringUtils.create((byte[]) toStrResult);
            final int size = resultString.length();
            if (size > 1) {
                throw new RaiseException(getContext().getCoreExceptions().argumentErrorCharacterRequired(this));
            }
            charString = resultString;
        }
        return charString;
    }

    @TruffleBoundary
    protected String makeFormatString(int width) {
        final boolean leftJustified = hasMinusFlag || width < 0;
        if (width < 0) {
            width = -width;
        }
        return "%" + (leftJustified ? "-" : "") + width + "." + width + "s";
    }

    @TruffleBoundary
    private byte[] doFormat(String charString, String formatString) {
        return StringUtils.format(formatString, charString).getBytes(StandardCharsets.US_ASCII);
    }

    protected int getLimit() {
        return getContext().getOptions().PACK_CACHE;
    }

}
