/*
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.language.arguments;

import org.truffleruby.Log;
import org.truffleruby.core.binding.BindingNodes;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.CallDispatchHeadNode;
import org.truffleruby.language.locals.ReadFrameSlotNode;
import org.truffleruby.language.locals.ReadFrameSlotNodeGen;
import org.truffleruby.language.locals.WriteFrameSlotNode;
import org.truffleruby.language.locals.WriteFrameSlotNodeGen;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;

public class RunBlockKWArgsHelperNode extends RubyNode {

    @Child private ReadFrameSlotNode readArrayNode;
    @Child private WriteFrameSlotNode writeArrayNode;
    @Child private CallDispatchHeadNode callHelperNode;

    private final Object kwrestName;

    public RunBlockKWArgsHelperNode(FrameSlot arrayFrameSlot, Object kwrestName) {
        readArrayNode = ReadFrameSlotNodeGen.create(arrayFrameSlot);
        writeArrayNode = WriteFrameSlotNodeGen.create(arrayFrameSlot);
        callHelperNode = CallDispatchHeadNode.createOnSelf();
        this.kwrestName = kwrestName;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Log.notOptimizedOnce(Log.KWARGS_NOT_OPTIMIZED_YET);

        final Object array = readArrayNode.executeRead(frame);

        final DynamicObject binding = BindingNodes.createBinding(getContext(), frame.materialize());
        final Object remainingArray = callHelperNode.call(frame, coreLibrary().getTruffleModule(), "load_arguments_from_array_kw_helper", array, kwrestName, binding);

        writeArrayNode.executeWrite(frame, remainingArray);

        return nil();
    }

}
