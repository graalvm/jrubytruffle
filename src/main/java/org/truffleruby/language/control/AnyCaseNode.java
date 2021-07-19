/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.control;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import java.util.Arrays;

public class AnyCaseNode extends RubyContextSourceNode {

    @Children private BooleanCastNode[] conditionNodes;
    @Children private RubyNode[] bodyNodes;
    @Child private RubyNode elseNode;

    private static final int[] EMPTY_ENABLED = new int[]{};
    @CompilationFinal(dimensions = 1) private int[] enabled = EMPTY_ENABLED;
    @CompilationFinal boolean elseEnabled = false;

    public AnyCaseNode(BooleanCastNode[] conditionNodes, RubyNode[] bodyNodes, RubyNode elseNode) {
        this.conditionNodes = conditionNodes;
        this.bodyNodes = bodyNodes;
        this.elseNode = elseNode;
    }

    @ExplodeLoop
    @Override
    public Object execute(VirtualFrame frame) {
        for (int n = 0; n < enabled.length; n++) {
            final int i = enabled[n];
            if (conditionNodes[i].executeBoolean(frame)) {
                return bodyNodes[i].execute(frame);
            }
        }

        if (elseEnabled) {
            return elseNode.execute(frame);
        }

        CompilerDirectives.transferToInterpreterAndInvalidate();
        return respecialize(frame);
    }

    private Object respecialize(VirtualFrame frame) {
        CompilerAsserts.neverPartOfCompilation();
        assert enabled.length < conditionNodes.length;

        for (int n = 0; n < conditionNodes.length; n++) {
            if (conditionNodes[n].executeBoolean(frame)) {
                enabled = Arrays.copyOf(enabled, enabled.length + 1);
                enabled[enabled.length - 1] = n;
                return bodyNodes[n].execute(frame);
            }
        }

        final int[] allEnabled = new int[conditionNodes.length];
        for (int n = 0; n < allEnabled.length; n++) {
            allEnabled[n] = n;
        }
        enabled = allEnabled;
        elseEnabled = true;
        return elseNode.execute(frame);
    }

}
