/*
 * Copyright (c) 2013, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.language.yield;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import org.truffleruby.Layouts;
import org.truffleruby.language.methods.DeclarationContext;

public class YieldNode extends Node {

    private final DeclarationContext declarationContext;

    @Child private CallBlockNode callBlockNode;

    public YieldNode() {
        this(DeclarationContext.BLOCK);
    }

    public YieldNode(DeclarationContext declarationContext) {
        this.declarationContext = declarationContext;
    }

    public Object dispatch(DynamicObject block, Object... argumentsObjects) {
        return getCallBlockNode().executeCallBlock(
                declarationContext,
                block,
                Layouts.PROC.getSelf(block),
                Layouts.PROC.getBlock(block),
                argumentsObjects);
    }

    public Object dispatchWithBlock(DynamicObject block, DynamicObject blockArgument, Object... argumentsObjects) {
        return getCallBlockNode().executeCallBlock(
                declarationContext,
                block,
                Layouts.PROC.getSelf(block),
                blockArgument,
                argumentsObjects);
    }

    public Object dispatchWithModifiedSelf(DynamicObject block, Object self, Object... argumentsObjects) {
        return getCallBlockNode().executeCallBlock(
                declarationContext,
                block,
                self,
                Layouts.PROC.getBlock(block),
                argumentsObjects);
    }

    private CallBlockNode getCallBlockNode() {
        if (callBlockNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callBlockNode = insert(CallBlockNodeGen.create(null, null, null, null));
        }

        return callBlockNode;
    }

}
