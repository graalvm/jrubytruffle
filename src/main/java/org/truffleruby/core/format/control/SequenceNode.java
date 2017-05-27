/*
 * Copyright (c) 2015, 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.format.control;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import org.truffleruby.core.format.FormatNode;

public class SequenceNode extends FormatNode {

    @Children private final FormatNode[] children;

    public SequenceNode(FormatNode[] children) {
        this.children = children;
    }

    @ExplodeLoop
    @Override
    public Object execute(VirtualFrame frame) {
        for (FormatNode child : children) {
            child.execute(frame);
        }

        if (CompilerDirectives.inInterpreter()) {
            LoopNode.reportLoopCount(this, children.length);
        }

        return null;
    }

}
