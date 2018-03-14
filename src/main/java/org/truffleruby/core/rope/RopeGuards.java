/*
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */


package org.truffleruby.core.rope;

import org.jcodings.Encoding;
import org.jcodings.specific.ASCIIEncoding;

public class RopeGuards {

    public static boolean isSingleByteString(Rope rope) {
        return rope.byteLength() == 1;
    }

    public static boolean isLeafRope(Rope rope) {
        return rope instanceof LeafRope;
    }

    public static boolean isEmpty(byte[] bytes) {
        return bytes.length == 0;
    }

    public static boolean isBinaryString(Encoding encoding) {
        return encoding == ASCIIEncoding.INSTANCE;
    }

    public static boolean isAsciiCompatible(Encoding encoding) {
        return encoding.isAsciiCompatible();
    }

    public static boolean isFixedWidthEncoding(Rope rope) {
        return rope.getEncoding().isFixedWidth();
    }

}
