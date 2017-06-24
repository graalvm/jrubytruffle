/*
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.inlined;

import org.truffleruby.RubyContext;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class InlinedGreaterThanNode extends BinaryInlinedOperationNode {

    public InlinedGreaterThanNode(RubyContext context, RubyCallNodeParameters callNodeParameters) {
        super(callNodeParameters,
                context.getCoreMethods().fixnumGreaterThanAssumption);
    }

    @Specialization(assumptions = "assumptions")
    boolean doInt(int a, int b) {
        return a > b;
    }

    @Specialization(assumptions = "assumptions")
    boolean doLong(long a, long b) {
        return a > b;
    }

    @Specialization
    Object fallback(VirtualFrame frame, Object a, Object b) {
        return rewriteAndCall(frame, a, b);
    }

}
