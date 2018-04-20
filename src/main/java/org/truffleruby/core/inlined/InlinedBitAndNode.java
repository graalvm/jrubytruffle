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
import org.truffleruby.core.numeric.IntegerNodes.BitAndNode;
import org.truffleruby.core.numeric.IntegerNodesFactory.BitAndNodeFactory;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class InlinedBitAndNode extends BinaryInlinedOperationNode {

    @Child BitAndNode fixnumBitAnd;

    public InlinedBitAndNode(RubyContext context, RubyCallNodeParameters callNodeParameters) {
        super(callNodeParameters,
                context.getCoreMethods().fixnumBitAndAssumption);
    }

    @Specialization(assumptions = "assumptions")
    Object intBitAnd(int a, int b) {
        return getBitAndNode().executeBitAnd(a, b);
    }

    @Specialization(assumptions = "assumptions")
    Object intLongBitAnd(int a, long b) {
        return getBitAndNode().executeBitAnd(a, b);
    }

    @Specialization(assumptions = "assumptions")
    Object longIntBitAnd(long a, int b) {
        return getBitAndNode().executeBitAnd(a, b);
    }

    @Specialization(assumptions = "assumptions")
    Object longBitAnd(long a, long b) {
        return getBitAndNode().executeBitAnd(a, b);
    }

    @Specialization
    Object fallback(VirtualFrame frame, Object a, Object b) {
        return rewriteAndCall(frame, a, b);
    }

    private BitAndNode getBitAndNode() {
        if (fixnumBitAnd == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            fixnumBitAnd = insert(BitAndNodeFactory.create(null));
        }
        return fixnumBitAnd;
    }

}
