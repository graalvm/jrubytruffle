/*
 * Copyright (c) 2015, 2018 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.language.globals;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import org.truffleruby.Layouts;
import org.truffleruby.core.binding.BindingNodes;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.yield.YieldNode;

@NodeChild(value = "value")
public abstract class WriteGlobalVariableNode extends RubyNode {

    private final String name;

    public WriteGlobalVariableNode(String name) {
        this.name = name;
    }

    @Specialization(guards = "storage.isSimple()")
    public Object write(VirtualFrame frame, Object value,
            @Cached("getStorage()") GlobalVariableStorage storage,
            @Cached("createSimpleNode()") WriteSimpleGlobalVariableNode simpleNode) {
        simpleNode.execute(value);
        return value;
    }

    @Specialization(guards = { "storage.hasHooks()", "arity != 2" })
    public Object writeHooks(VirtualFrame frame, Object value,
                             @Cached("getStorage()") GlobalVariableStorage storage,
                             @Cached("setterArity(storage)") int arity,
                             @Cached("new()") YieldNode yieldNode) {
        yieldNode.dispatch(storage.getSetter(), value);
        return value;
    }

    @Specialization(guards = { "storage.hasHooks()", "arity == 2" })
    public Object writeHooksWithBinding(VirtualFrame frame, Object value,
            @Cached("getStorage()") GlobalVariableStorage storage,
            @Cached("setterArity(storage)") int arity,
            @Cached("new()") YieldNode yieldNode) {
        yieldNode.dispatch(storage.getSetter(), value, BindingNodes.createBinding(getContext(), frame.materialize()));
        return value;
    }

    protected int setterArity(GlobalVariableStorage storage) {
        return Layouts.PROC.getSharedMethodInfo(storage.getSetter()).getArity().getArityNumber();
    }

    protected WriteSimpleGlobalVariableNode createSimpleNode() {
        return WriteSimpleGlobalVariableNodeGen.create(getStorage(), null);
    }

    protected GlobalVariableStorage getStorage() {
        return getContext().getCoreLibrary().getGlobalVariables().getStorage(name);
    }

    @Override
    public Object isDefined(VirtualFrame frame) {
        return coreStrings().ASSIGNMENT.createInstance();
    }

}
