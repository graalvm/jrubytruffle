/*
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved. This
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
import org.truffleruby.core.basicobject.BasicObjectNodes.ReferenceEqualNode;
import org.truffleruby.core.binding.BindingNodes;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.objects.shared.WriteBarrierNode;
import org.truffleruby.language.yield.YieldNode;

@NodeChild(value = "value")
public abstract class WriteGlobalVariableNode extends RubyNode {

    private final String name;

    @Child protected ReferenceEqualNode referenceEqualNode = ReferenceEqualNode.create();
    @Child protected WriteBarrierNode writeBarrierNode = WriteBarrierNode.create();

    public WriteGlobalVariableNode(String name) {
        this.name = name;
    }

    @Specialization(guards = { "storage.isSimple()", "referenceEqualNode.executeReferenceEqual(value, previousValue)" },
                    assumptions = "storage.getUnchangedAssumption()")
    public Object writeTryToKeepConstant(Object value,
                    @Cached("getStorage()") GlobalVariableStorage storage,
                    @Cached("storage.getValue()") Object previousValue) {
        // NOTE: we still do the volatile write to get the proper memory barrier,
        // as the global variable could be used as a publication mechanism.
        storage.setValueInternal(value);
        return previousValue;
    }

    @Specialization(guards = { "storage.isSimple()", "storage.isAssumeConstant()" },
                    assumptions = "storage.getUnchangedAssumption()")
    public Object writeAssumeConstant(Object value,
                    @Cached("getStorage()") GlobalVariableStorage storage) {
        if (getContext().getSharedObjects().isSharing()) {
            writeBarrierNode.executeWriteBarrier(value);
        }
        storage.setValueInternal(value);
        storage.updateAssumeConstant(getContext());
        return value;
    }

    @Specialization(guards = { "storage.isSimple()", "!storage.isAssumeConstant()" }, replaces = "writeAssumeConstant")
    public Object write(Object value,
                    @Cached("getStorage()") GlobalVariableStorage storage) {
        if (getContext().getSharedObjects().isSharing()) {
            writeBarrierNode.executeWriteBarrier(value);
        }
        storage.setValueInternal(value);
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

    protected GlobalVariableStorage getStorage() {
        return getContext().getCoreLibrary().getGlobalVariables().getStorage(name);
    }

    @Override
    public Object isDefined(VirtualFrame frame) {
        return coreStrings().ASSIGNMENT.createInstance();
    }

}
