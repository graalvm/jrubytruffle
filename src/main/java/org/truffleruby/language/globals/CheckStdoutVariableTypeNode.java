/*
 * Copyright (c) 2014, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.language.globals;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DoesRespondDispatchHeadNode;

public class CheckStdoutVariableTypeNode extends RubyNode {

    @Child private RubyNode child;

    @Child private DoesRespondDispatchHeadNode respondToWriteNode;

    private final BranchProfile unsuitableTypeProfile = BranchProfile.create();

    public CheckStdoutVariableTypeNode(RubyNode child) {
        this.child = child;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object childValue = child.execute(frame);

        if ((childValue == nil() || !getRespondToWriteNode().doesRespondTo(frame, "write", childValue))) {
            unsuitableTypeProfile.enter();
            throw new RaiseException(coreExceptions().typeErrorMustHaveWriteMethod(childValue, this));
        }

        return childValue;
    }

    private DoesRespondDispatchHeadNode getRespondToWriteNode() {
        if (respondToWriteNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            respondToWriteNode = insert(new DoesRespondDispatchHeadNode(false));
        }

        return respondToWriteNode;
    }

}
