/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.language.globals;

import org.truffleruby.Layouts;
import org.truffleruby.core.binding.BindingNodes;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.yield.YieldNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class IsDefinedGlobalVariableNode extends RubyNode {

    public static IsDefinedGlobalVariableNode create(String name) {
        return IsDefinedGlobalVariableNodeGen.create(name);
    }

    private final String name;

    public IsDefinedGlobalVariableNode(String name) {
        this.name = name;
    }

    @Specialization(guards = "storage.isSimple()")
    public Object executeDefined(
            @Cached("getStorage()") GlobalVariableStorage storage) {
        if (storage.isDefined()) {
            return coreStrings().GLOBAL_VARIABLE.createInstance();
        } else {
            return nil();
        }
    }

    @Specialization(guards = { "storage.hasHooks()", "arity == 0" })
    public Object executeDefinedHooks(VirtualFrame frame,
            @Cached("getStorage()") GlobalVariableStorage storage,
            @Cached("isDefinedArity(storage)") int arity,
            @Cached("new()") YieldNode yieldNode) {
        return yieldNode.dispatch(storage.getIsDefined());
    }

    @Specialization(guards = { "storage.hasHooks()", "arity == 1" })
    public Object executeDefinedHooksWithBinding(VirtualFrame frame,
            @Cached("getStorage()") GlobalVariableStorage storage,
            @Cached("isDefinedArity(storage)") int arity,
            @Cached("new()") YieldNode yieldNode) {
        return yieldNode.dispatch(storage.getIsDefined(), BindingNodes.createBinding(getContext(), frame.materialize()));
    }

    protected int isDefinedArity(GlobalVariableStorage storage) {
        return Layouts.PROC.getSharedMethodInfo(storage.getIsDefined()).getArity().getArityNumber();
    }

    protected GlobalVariableStorage getStorage() {
        return getContext().getCoreLibrary().getGlobalVariables().getStorage(name);
    }

}
