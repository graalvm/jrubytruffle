/*
 * Copyright (c) 2013, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.extra;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.object.DynamicObject;
import org.truffleruby.Layouts;
import org.truffleruby.builtins.CoreClass;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.core.proc.ProcType;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.literal.ObjectLiteralNode;
import org.truffleruby.language.locals.ReadDeclarationVariableNode;
import org.truffleruby.language.locals.WriteDeclarationVariableNode;
import org.truffleruby.language.methods.InternalMethod;

@CoreClass("Truffle::Graal")
public abstract class TruffleGraalNodes {

    @CoreMethod(names = "assert_constant", onSingleton = true, required = 1)
    public abstract static class AssertConstantNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        public DynamicObject assertConstant(Object value) {
            throw new RaiseException(coreExceptions().runtimeErrorNotConstant(this));
        }

    }

    @CoreMethod(names = "assert_not_compiled", onSingleton = true)
    public abstract static class AssertNotCompiledNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        public DynamicObject assertNotCompiled() {
            throw new RaiseException(coreExceptions().runtimeErrorCompiled(this));
        }

    }

    @CoreMethod(names = "always_split", onSingleton = true, required = 1)
    public abstract static class AlwaysSplitNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isRubyMethod(rubyMethod)")
        public DynamicObject splitMethod(DynamicObject rubyMethod) {
            InternalMethod internalMethod = Layouts.METHOD.getMethod(rubyMethod);
            internalMethod.getSharedMethodInfo().setAlwaysClone(true);
            return rubyMethod;
        }

        @Specialization(guards = "isRubyUnboundMethod(rubyMethod)")
        public DynamicObject splitUnboundMethod(DynamicObject rubyMethod) {
            InternalMethod internalMethod = Layouts.UNBOUND_METHOD.getMethod(rubyMethod);
            internalMethod.getSharedMethodInfo().setAlwaysClone(true);
            return rubyMethod;
        }

        @Specialization(guards = "isRubyProc(rubyProc)")
        public DynamicObject splitProc(DynamicObject rubyProc) {
            Layouts.PROC.getSharedMethodInfo(rubyProc).setAlwaysClone(true);
            return rubyProc;
        }
    }

    // Like Smalltalk's fixTemps but not mutating the Proc
    @CoreMethod(names = "copy_captured_locals", onSingleton = true, required = 1)
    public abstract static class CopyCapturedLocalsNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(guards = "isRubyProc(proc)")
        public DynamicObject copyCapturedLocals(DynamicObject proc) {
            final MaterializedFrame declarationFrame = Layouts.PROC.getDeclarationFrame(proc);

            final RootCallTarget callTarget = (RootCallTarget) Layouts.PROC.getCallTargetForType(proc);
            final RubyRootNode rootNode = (RubyRootNode) callTarget.getRootNode();

            final RubyNode newBody = NodeUtil.cloneNode(rootNode.getBody());

            assert NodeUtil.findAllNodeInstances(newBody, WriteDeclarationVariableNode.class).isEmpty();

            for (ReadDeclarationVariableNode readNode : NodeUtil.findAllNodeInstances(newBody, ReadDeclarationVariableNode.class)) {
                MaterializedFrame frame = RubyArguments.getDeclarationFrame(declarationFrame, readNode.getFrameDepth() - 1);
                Object value = frame.getValue(readNode.getFrameSlot());
                readNode.replace(new ObjectLiteralNode(value));
            }
            final RubyRootNode newRootNode = new RubyRootNode(getContext(), rootNode.getSourceSection(), rootNode.getFrameDescriptor(), rootNode.getSharedMethodInfo(), newBody);
            final CallTarget newCallTarget = Truffle.getRuntime().createCallTarget(newRootNode);

            final CallTarget callTargetForLambdas;
            if (Layouts.PROC.getType(proc) == ProcType.LAMBDA) {
                callTargetForLambdas = newCallTarget;
            } else {
                callTargetForLambdas = Layouts.PROC.getCallTargetForLambdas(proc);
            }

            return Layouts.PROC.createProc(coreLibrary().getProcFactory(),
                    Layouts.PROC.getType(proc),
                    Layouts.PROC.getSharedMethodInfo(proc),
                    newCallTarget,
                    callTargetForLambdas,
                    null, // The Proc no longer needs a declaration frame
                    Layouts.PROC.getMethod(proc),
                    Layouts.PROC.getSelf(proc),
                    Layouts.PROC.getBlock(proc),
                    Layouts.PROC.getFrameOnStackMarker(proc));
        }

    }

}
