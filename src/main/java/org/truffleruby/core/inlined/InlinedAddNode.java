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
import org.truffleruby.core.numeric.FixnumNodes.AddNode;
import org.truffleruby.core.numeric.FixnumNodesFactory.AddNodeFactory;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class InlinedAddNode extends BinaryInlinedOperationNode {

    @Child AddNode fixnumAdd;

    public InlinedAddNode(RubyContext context, RubyCallNodeParameters callNodeParameters) {
        super(callNodeParameters,
                context.getCoreMethods().fixnumAddAssumption,
                context.getCoreMethods().floatAddAssumption);
    }

    @Specialization(assumptions = "assumptions")
    Object intAdd(int a, int b) {
        return getAddNode().executeAdd(a, b);
    }

    @Specialization(assumptions = "assumptions")
    Object longAdd(long a, long b) {
        return getAddNode().executeAdd(a, b);
    }

    @Specialization(assumptions = "assumptions")
    double floatAdd(double a, double b) {
        return a + b;
    }

    @Specialization
    Object fallback(VirtualFrame frame, Object a, Object b) {
        return rewriteAndCall(frame, a, b);
    }

    private AddNode getAddNode() {
        if (fixnumAdd == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            fixnumAdd = insert(AddNodeFactory.create(null));
        }
        return fixnumAdd;
    }

}
