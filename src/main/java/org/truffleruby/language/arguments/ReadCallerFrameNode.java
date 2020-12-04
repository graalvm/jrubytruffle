/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.arguments;

import org.truffleruby.language.FrameAndVariablesSendingNode;

import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;

public class ReadCallerFrameNode extends ReadCallerDataNode {

    public static ReadCallerFrameNode create() {
        return new ReadCallerFrameNode();
    }

    @Override
    public MaterializedFrame execute(VirtualFrame frame) {
        return (MaterializedFrame) super.execute(frame);
    }

    @Override
    protected MaterializedFrame getData(VirtualFrame frame) {
        return RubyArguments.getCallerFrame(frame);
    }

    @Override
    protected void startSending(FrameAndVariablesSendingNode node) {
        node.startSendingOwnFrame();
    }

    @Override
    protected Object getDataFromFrame(MaterializedFrame frame) {
        return frame;
    }
}
