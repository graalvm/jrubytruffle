/*
 * Copyright (c) 2013, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.cast;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.object.DynamicObject;
import org.truffleruby.language.RubyNode;

/**
 * Casts a value into a boolean.
 */
@NodeChild(value = "value", type = RubyNode.class)
public abstract class BooleanCastNode extends RubyNode {

    public static BooleanCastNode create() {
        return BooleanCastNodeGen.create(null);
    }

    /** Execute with child node */
    public abstract boolean executeBoolean(VirtualFrame frame);

    /** Execute with given value */
    public abstract boolean executeToBoolean(Object value);

    @Specialization(guards = "isNil(nil)")
    public boolean doNil(Object nil) {
        return false;
    }

    @Specialization
    public boolean doBoolean(boolean value) {
        return value;
    }

    @Specialization
    public boolean doInt(int value) {
        return true;
    }

    @Specialization
    public boolean doLong(long value) {
        return true;
    }

    @Specialization
    public boolean doFloat(double value) {
        return true;
    }

    @Specialization(guards = "!isNil(object)")
    public boolean doBasicObject(DynamicObject object) {
        return true;
    }

    @Specialization(guards = "isForeignObject(object)")
    public boolean doForeignObject(TruffleObject object) {
        return true;
    }

    @Specialization(guards = {"!isTruffleObject(object)", "!isBoxedPrimitive(object)"})
    public boolean doOther(Object object) {
        return true;
    }
}
