/*
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.inlined;

import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.RubyCallNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeUtil;

public abstract class InlinedOperationNode extends RubyNode {

    private final RubyCallNodeParameters callNodeParameters;

    protected final Assumption[] assumptions;

    private RubyCallNode replacedBy = null;

    public InlinedOperationNode(RubyCallNodeParameters callNodeParameters, Assumption... assumptions) {
        this.callNodeParameters = callNodeParameters;

        this.assumptions = new Assumption[1 + assumptions.length];
        this.assumptions[0] = getContext().getTraceManager().getUnusedAssumption();
        ArrayUtils.arraycopy(assumptions, 0, this.assumptions, 1, assumptions.length);
    }

    protected abstract RubyNode getReceiverNode();

    protected abstract RubyNode[] getArgumentNodes();

    private RubyCallNode rewriteToCallNode() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        return atomic(() -> {
            // Check if we are still in the AST
            boolean found = !NodeUtil.forEachChild(getParent(), node -> node != this);

            if (found) {
                // We need to pass the updated children of this node to the call node
                RubyCallNode callNode = new RubyCallNode(callNodeParameters.withReceiverAndArguments(
                        getReceiverNode(), getArgumentNodes(), callNodeParameters.getBlock()));
                callNode.unsafeSetSourceSection(getSourceIndexLength());
                replacedBy = callNode;
                return replace(callNode, this + " could not be executed inline");
            } else {
                return replacedBy;
            }
        });
    }

    protected Object rewriteAndCall(VirtualFrame frame, Object receiver, Object... arguments) {
        return rewriteToCallNode().executeWithArgumentsEvaluated(frame, receiver, arguments);
    }

    protected CoreMethods coreMethods() {
        return getContext().getCoreMethods();
    }

    @Override
    public Object isDefined(VirtualFrame frame) {
        return rewriteToCallNode().isDefined(frame);
    }

}
