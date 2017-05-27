/*
 * Copyright (c) 2013, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.language.objects;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;

public class ReadClassVariableNode extends RubyNode {

    private final String name;
    private final BranchProfile missingProfile = BranchProfile.create();

    @Child private RubyNode lexicalScopeNode;

    public ReadClassVariableNode(RubyNode lexicalScopeNode, String name) {
        this.lexicalScopeNode = lexicalScopeNode;
        this.name = name;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final LexicalScope lexicalScope = (LexicalScope) lexicalScopeNode.execute(frame);
        // TODO CS 21-Feb-16 these two operations are uncached and use loops - same for isDefined below
        final DynamicObject module = LexicalScope.resolveTargetModuleForClassVariables(lexicalScope);

        final Object value = ModuleOperations.lookupClassVariable(module, name);

        if (value == null) {
            missingProfile.enter();
            throw new RaiseException(coreExceptions().nameErrorUninitializedClassVariable(module, name, this));
        }

        return value;
    }

    @Override
    public Object isDefined(VirtualFrame frame) {
        final LexicalScope lexicalScope = (LexicalScope) lexicalScopeNode.execute(frame);
        final DynamicObject module = LexicalScope.resolveTargetModuleForClassVariables(lexicalScope);

        final Object value = ModuleOperations.lookupClassVariable(module, name);

        if (value == null) {
            return nil();
        } else {
            return coreStrings().CLASS_VARIABLE.createInstance();
        }
    }

}
