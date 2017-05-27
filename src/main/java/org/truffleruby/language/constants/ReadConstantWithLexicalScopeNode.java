/*
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.language.constants;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import org.jcodings.specific.UTF8Encoding;
import org.truffleruby.Layouts;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyConstant;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;

/** Read a constant using the current lexical scope: CONST */
public class ReadConstantWithLexicalScopeNode extends RubyNode {

    private final LexicalScope lexicalScope;
    private final String name;

    @Child private LookupConstantWithLexicalScopeNode lookupConstantNode;
    @Child private GetConstantNode getConstantNode = GetConstantNode.create();

    public ReadConstantWithLexicalScopeNode(LexicalScope lexicalScope, String name) {
        this.lexicalScope = lexicalScope;
        this.name = name;
        this.lookupConstantNode = LookupConstantWithLexicalScopeNodeGen.create(lexicalScope, name);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final RubyConstant constant = lookupConstantNode.executeLookupConstant(frame);
        final DynamicObject module = lexicalScope.getLiveModule();

        return getConstantNode.executeGetConstant(frame, module, name, constant, lookupConstantNode);
    }

    public Object readConstant(VirtualFrame frame, Object module, String name) {
        return execute(frame);
    }

    @Override
    public Object isDefined(VirtualFrame frame) {
        final RubyConstant constant;
        try {
            constant = lookupConstantNode.executeLookupConstant(frame);
        } catch (RaiseException e) {
            if (Layouts.BASIC_OBJECT.getLogicalClass(e.getException()) == coreLibrary().getNameErrorClass()) {
                // private constant
                return nil();
            }
            throw e;
        }

        if (constant == null) {
            return nil();
        } else {
            return create7BitString("constant", UTF8Encoding.INSTANCE);
        }
    }

}
