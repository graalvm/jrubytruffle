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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;

import org.truffleruby.core.CoreLibrary;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;

/**
 * Casts a value into an int.
 */
@ImportStatic(CoreLibrary.class)
@NodeChild(value = "value", type = RubyNode.class)
public abstract class IntegerCastNode extends RubyNode {

    public static IntegerCastNode create() {
        return IntegerCastNodeGen.create(null);
    }

    public abstract int executeCastInt(Object value);

    @Specialization
    public int doInt(int value) {
        return value;
    }

    @Specialization(guards = "fitsIntoInteger(value)")
    public int doLong(long value) {
        return (int) value;
    }

    @Fallback
    public int doBasicObject(Object object) {
        throw new RaiseException(notAFixnum(object));
    }

    @TruffleBoundary
    private DynamicObject notAFixnum(Object object) {
        return coreExceptions().typeErrorIsNotA(object.toString(), "Fixnum (fitting in int)", this);
    }

}
