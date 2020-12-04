/*
 * Copyright (c) 2013, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.symbol;

import org.graalvm.collections.Pair;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.CoreModule;
import org.truffleruby.builtins.UnaryCoreMethodNode;
import org.truffleruby.collections.ConcurrentOperations;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.proc.ProcOperations;
import org.truffleruby.core.proc.ProcType;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.StringNodes;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.Visibility;
import org.truffleruby.language.arguments.ReadCallerFrameNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.methods.Arity;
import org.truffleruby.language.methods.DeclarationContext;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.language.methods.Split;
import org.truffleruby.language.methods.SymbolProcNode;
import org.truffleruby.language.threadlocal.SpecialVariableStorage;
import org.truffleruby.parser.ArgumentDescriptor;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import java.util.Map;

@CoreModule(value = "Symbol", isClass = true)
public abstract class SymbolNodes {

    @CoreMethod(names = "all_symbols", onSingleton = true)
    public abstract static class AllSymbolsNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected RubyArray allSymbols() {
            return createArray(getLanguage().symbolTable.allSymbols());
        }

    }

    @CoreMethod(names = { "==", "eql?" }, required = 1)
    public abstract static class EqualNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected boolean equal(RubySymbol a, Object b) {
            return a == b;
        }

    }

    @CoreMethod(names = "hash")
    public abstract static class HashNode extends CoreMethodArrayArgumentsNode {

        public static HashNode create() {
            return SymbolNodesFactory.HashNodeFactory.create(null);
        }

        public abstract long execute(RubySymbol rubySymbol);

        // Cannot cache a Symbol's hash while pre-initializing, as it will change in SymbolTable#rehash()
        @Specialization(guards = { "symbol == cachedSymbol", "!preInitializing" }, limit = "getIdentityCacheLimit()")
        protected long hashCached(RubySymbol symbol,
                @Cached("isPreInitializing()") boolean preInitializing,
                @Cached("symbol") RubySymbol cachedSymbol,
                @Cached("hash(cachedSymbol)") long cachedHash) {
            return cachedHash;
        }

        @Specialization
        protected long hash(RubySymbol symbol) {
            return symbol.computeHashCode(getContext().getHashing());
        }

        protected boolean isPreInitializing() {
            return getContext().isPreInitializing();
        }

    }

    @CoreMethod(names = "to_proc")
    public abstract static class ToProcNode extends CoreMethodArrayArgumentsNode {

        public static final Arity ARITY = new Arity(0, 0, true);

        public static ToProcNode create() {
            return SymbolNodesFactory.ToProcNodeFactory.create(null);
        }

        public abstract RubyProc execute(VirtualFrame frame, RubySymbol symbol);

        @Child private ReadCallerFrameNode readCallerFrame = ReadCallerFrameNode.create();

        @Specialization(
                guards = { "symbol == cachedSymbol", "getRefinements(frame) == cachedRefinements" },
                limit = "getIdentityCacheLimit()")
        protected RubyProc toProcCached(VirtualFrame frame, RubySymbol symbol,
                @Cached("symbol") RubySymbol cachedSymbol,
                @Cached("getRefinements(frame)") Map<RubyModule, RubyModule[]> cachedRefinements,
                @Cached("getOrCreateCallTarget(getContext(), getLanguage(), cachedSymbol, cachedRefinements)") RootCallTarget callTarget,
                @Cached("createProc(getContext(), getLanguage(), cachedRefinements, callTarget)") RubyProc cachedProc) {
            return cachedProc;
        }

        @Specialization(replaces = "toProcCached")
        protected RubyProc toProcUncached(VirtualFrame frame, RubySymbol symbol) {
            final Map<RubyModule, RubyModule[]> refinements = getRefinements(frame);
            final RootCallTarget callTarget = getOrCreateCallTarget(getContext(), getLanguage(), symbol, refinements);
            return createProc(getContext(), getLanguage(), refinements, callTarget);
        }

        @TruffleBoundary
        public static RootCallTarget getOrCreateCallTarget(RubyContext context, RubyLanguage language,
                RubySymbol symbol, Map<RubyModule, RubyModule[]> refinements) {
            if (refinements == DeclarationContext.NO_REFINEMENTS) {
                return symbol.getCallTargetNoRefinements(language);
            } else {
                // TODO (eregon, 23 Sep 2020): this should ideally cache on the refinements by comparing classes, and not by identity.
                return ConcurrentOperations.getOrCompute(
                        context.cachedSymbolToProcTargetsWithRefinements,
                        Pair.create(symbol, refinements),
                        key -> createCallTarget(language, symbol, refinements));
            }
        }

        public static RubyProc createProc(RubyContext context, RubyLanguage language,
                Map<RubyModule, RubyModule[]> refinements, RootCallTarget callTarget) {
            final InternalMethod method = context.getCoreMethods().SYMBOL_TO_PROC;
            final DeclarationContext declarationContext = refinements == DeclarationContext.NO_REFINEMENTS
                    ? DeclarationContext.NONE
                    : new DeclarationContext(Visibility.PUBLIC, null, refinements);

            final Object[] args = RubyArguments
                    .pack(null, null, method, declarationContext, null, nil, null, EMPTY_ARGUMENTS);
            // MRI raises an error on Proc#binding if you attempt to access the binding of a Proc generated
            // by Symbol#to_proc. We generate a declaration frame here so that all procedures will have a
            // binding as this simplifies the logic elsewhere in the runtime.
            final MaterializedFrame declarationFrame = Truffle
                    .getRuntime()
                    .createVirtualFrame(args, context.getCoreLibrary().emptyDeclarationDescriptor)
                    .materialize();
            SpecialVariableStorage variables = new SpecialVariableStorage();
            declarationFrame.setObject(context.getCoreLibrary().emptyDeclarationSpecialVariableSlot, variables);

            return ProcOperations.createRubyProc(
                    context.getCoreLibrary().procClass,
                    language.procShape,
                    ProcType.PROC,
                    ((RubyRootNode) callTarget.getRootNode()).getSharedMethodInfo(),
                    callTarget,
                    callTarget,
                    declarationFrame,
                    variables,
                    method,
                    null,
                    null,
                    declarationContext);
        }

        public static RootCallTarget createCallTarget(RubyLanguage language, RubySymbol symbol,
                // unused but the CallTarget will capture the refinements in the DispatchNode on first call
                Map<RubyModule, RubyModule[]> refinements) {
            final SourceSection sourceSection = CoreLibrary.UNAVAILABLE_SOURCE_SECTION;

            final SharedMethodInfo sharedMethodInfo = new SharedMethodInfo(
                    sourceSection,
                    LexicalScope.IGNORE,
                    ARITY,
                    null,
                    symbol.getString(),
                    0,
                    "proc",
                    ArgumentDescriptor.ANON_REST);

            final RubyRootNode rootNode = new RubyRootNode(
                    language,
                    sourceSection,
                    new FrameDescriptor(nil),
                    sharedMethodInfo,
                    new SymbolProcNode(symbol.getString()),
                    Split.HEURISTIC);

            return Truffle.getRuntime().createCallTarget(rootNode);
        }

        protected InternalMethod getMethod(VirtualFrame frame) {
            return RubyArguments.getMethod(frame);
        }

        protected Map<RubyModule, RubyModule[]> getRefinements(VirtualFrame frame) {
            final MaterializedFrame callerFrame = readCallerFrame.execute(frame);
            final DeclarationContext declarationContext = RubyArguments.tryGetDeclarationContext(callerFrame);
            return declarationContext != null
                    ? declarationContext.getRefinements()
                    : DeclarationContext.NONE.getRefinements();
        }

        protected int getCacheLimit() {
            return getContext().getOptions().SYMBOL_TO_PROC_CACHE;
        }

    }

    @CoreMethod(names = "to_s")
    public abstract static class ToSNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected RubyString toS(RubySymbol symbol,
                @Cached StringNodes.MakeStringNode makeStringNode) {
            return makeStringNode.fromRope(symbol.getRope());
        }

    }

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends UnaryCoreMethodNode {

        @Specialization
        protected Object allocate(RubyClass rubyClass) {
            throw new RaiseException(getContext(), coreExceptions().typeErrorAllocatorUndefinedFor(rubyClass, this));
        }

    }

}
