/*
 * Copyright (c) 2015, 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.format.convert;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import org.truffleruby.core.format.FormatNode;

@NodeChildren({
        @NodeChild(value = "value", type = FormatNode.class),
})
public abstract class ReinterpretByteAsIntegerNode extends FormatNode {

    public boolean signed;

    public ReinterpretByteAsIntegerNode(boolean signed) {
        this.signed = signed;
    }

    @Specialization(guards = "isNil(nil)")
    public DynamicObject decode(DynamicObject nil) {
        return nil;
    }

    @Specialization
    public int decode(byte value) {
        if (signed) {
            return value;
        } else {
            return value & 0xff;
        }
    }

}
