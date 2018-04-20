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
import org.truffleruby.core.numeric.IntegerNodes.LeftShiftNode;
import org.truffleruby.core.numeric.IntegerNodesFactory.LeftShiftNodeFactory;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class InlinedLeftShiftNode extends BinaryInlinedOperationNode {

    @Child LeftShiftNode fixnumLeftShift;

    public InlinedLeftShiftNode(RubyContext context, RubyCallNodeParameters callNodeParameters) {
        super(callNodeParameters,
                context.getCoreMethods().fixnumLeftShiftAssumption);
    }

    @Specialization(assumptions = "assumptions")
    Object intLeftShift(int a, int b) {
        return getLeftShiftNode().executeLeftShift(a, b);
    }

    @Specialization(assumptions = "assumptions")
    Object longLeftShift(long a, int b) {
        return getLeftShiftNode().executeLeftShift(a, b);
    }

    @Specialization
    Object fallback(VirtualFrame frame, Object a, Object b) {
        return rewriteAndCall(frame, a, b);
    }

    private LeftShiftNode getLeftShiftNode() {
        if (fixnumLeftShift == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            fixnumLeftShift = insert(LeftShiftNodeFactory.create(null));
        }
        return fixnumLeftShift;
    }

}
