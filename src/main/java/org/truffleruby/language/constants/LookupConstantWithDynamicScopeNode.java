/*
 * Copyright (c) 2015, 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.language.constants;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.core.module.ConstantLookupResult;
import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyConstant;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.WarnNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;

public abstract class LookupConstantWithDynamicScopeNode extends RubyNode implements LookupConstantInterface {

    private final String name;
    @Child private WarnNode warnNode;

    public LookupConstantWithDynamicScopeNode(String name) {
        this.name = name;
    }

    public abstract RubyConstant executeLookupConstant(VirtualFrame frame);

    public RubyConstant lookupConstant(VirtualFrame frame, Object module, String name) {
        assert name == this.name;
        return executeLookupConstant(frame);
    }

    public LexicalScope getLexicalScope(VirtualFrame frame) {
        return RubyArguments.getMethod(frame).getLexicalScope();
    }

    @Specialization(guards = "getLexicalScope(frame) == lexicalScope",
                    assumptions = "constant.getAssumptions()",
                    limit = "getCacheLimit()")
    protected RubyConstant lookupConstant(VirtualFrame frame,
                    @Cached("getLexicalScope(frame)") LexicalScope lexicalScope,
                    @Cached("doLookup(lexicalScope)") ConstantLookupResult constant,
                    @Cached("isVisible(lexicalScope, constant)") boolean isVisible) {
        if (!isVisible) {
            throw new RaiseException(coreExceptions().nameErrorPrivateConstant(constant.getConstant().getDeclaringModule(), name, this));
        }
        if (constant.isDeprecated()) {
            warnDeprecatedConstant(frame, name);
        }
        return constant.getConstant();
    }

    @Specialization
    protected RubyConstant lookupConstantUncached(VirtualFrame frame,
            @Cached("createBinaryProfile()") ConditionProfile isVisibleProfile,
            @Cached("createBinaryProfile()") ConditionProfile isDeprecatedProfile) {
        final LexicalScope lexicalScope = getLexicalScope(frame);
        final ConstantLookupResult constant = doLookup(lexicalScope);
        if (isVisibleProfile.profile(!isVisible(lexicalScope, constant))) {
            throw new RaiseException(coreExceptions().nameErrorPrivateConstant(constant.getConstant().getDeclaringModule(), name, this));
        }
        if (isDeprecatedProfile.profile(constant.isDeprecated())) {
            warnDeprecatedConstant(frame, name);
        }
        return constant.getConstant();
    }

    @TruffleBoundary
    protected ConstantLookupResult doLookup(LexicalScope lexicalScope) {
        return ModuleOperations.lookupConstantWithLexicalScope(getContext(), lexicalScope, name);
    }

    @TruffleBoundary
    protected boolean isVisible(LexicalScope lexicalScope, ConstantLookupResult constant) {
        return constant.isVisibleTo(getContext(), lexicalScope, lexicalScope.getLiveModule());
    }

    private void warnDeprecatedConstant(VirtualFrame frame, String name) {
        if (warnNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            warnNode = insert(new WarnNode());
        }
        warnNode.execute(frame, "constant ", name, " is deprecated");
    }

    protected int getCacheLimit() {
        return getContext().getOptions().CONSTANT_CACHE;
    }
}
