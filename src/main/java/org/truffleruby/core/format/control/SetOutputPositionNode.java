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

import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.core.format.FormatNode;

public class SetOutputPositionNode extends FormatNode {

    private final int position;

    public SetOutputPositionNode(int position) {
        this.position = position;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        setOutputPosition(frame, position);
        return null;
    }

}
