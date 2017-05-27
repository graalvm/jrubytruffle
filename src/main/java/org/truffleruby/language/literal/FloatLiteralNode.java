/*
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.language.literal;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.truffleruby.language.RubyNode;

@NodeInfo(cost = NodeCost.NONE)
public class FloatLiteralNode extends RubyNode {

    private final double value;

    public FloatLiteralNode(double value) {
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return value;
    }

}
