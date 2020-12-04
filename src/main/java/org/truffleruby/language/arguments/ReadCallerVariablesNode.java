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

import org.truffleruby.language.threadlocal.SpecialVariableStorage;
import org.truffleruby.core.kernel.TruffleKernelNodes;
import org.truffleruby.language.FrameAndVariablesSendingNode;

import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;

public class ReadCallerVariablesNode extends ReadCallerDataNode {

    public static ReadCallerVariablesNode create() {
        return new ReadCallerVariablesNode();
    }

    @Override
    public SpecialVariableStorage execute(VirtualFrame frame) {
        return (SpecialVariableStorage) super.execute(frame);
    }

    @Override
    protected SpecialVariableStorage getData(VirtualFrame frame) {
        return RubyArguments.getCallerStorage(frame);
    }

    @Override
    protected void startSending(FrameAndVariablesSendingNode node) {
        node.startSendingOwnVariables();
    }

    @Override
    protected Object getDataFromFrame(MaterializedFrame frame) {
        return TruffleKernelNodes.GetSpecialVariableStorage.getSlow(frame);
    }
}
