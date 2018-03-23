/*
 * Copyright (c) 2013, 2018 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;

import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.CallDispatchHeadNode;
import org.truffleruby.language.dispatch.DoesRespondDispatchHeadNode;
import org.truffleruby.language.objects.ObjectIVarGetNode;

@NodeChildren({
        @NodeChild("receiver"),
        @NodeChild("name"),
        @NodeChild("stringName"),
        @NodeChild("isIVar")
})
abstract class ForeignReadStringCachedHelperNode extends RubyNode {

    @Child private DoesRespondDispatchHeadNode definedNode;
    @Child private DoesRespondDispatchHeadNode indexDefinedNode;
    @Child private CallDispatchHeadNode callNode;

    protected final static String INDEX_METHOD_NAME = "[]";
    protected final static String METHOD_NAME = "method";

    public abstract Object executeStringCachedHelper(VirtualFrame frame, DynamicObject receiver, Object name, Object stringName, boolean isIVar);

    @Specialization(guards = "isRubyArray(receiver) || isRubyHash(receiver)")
    public Object readArrayHash(
            VirtualFrame frame,
            DynamicObject receiver,
            Object name,
            Object stringName,
            boolean isIVar,
            @Cached("create()") ForeignToRubyNode nameToRubyNode) {
        return getCallNode().call(frame, receiver, INDEX_METHOD_NAME, nameToRubyNode.executeConvert(name));
    }

    @Specialization(guards = {"!isRubyArray(receiver)", "!isRubyHash(receiver)", "isIVar"})
    public Object readInstanceVariable(
            DynamicObject receiver,
            Object name,
            Object stringName,
            boolean isIVar,
            @Cached("create()") ObjectIVarGetNode readObjectFieldNode) {
        return readObjectFieldNode.executeIVarGet(receiver, stringName);
    }

    @Specialization(guards = {
            "!isRubyArray(receiver)", "!isRubyHash(receiver)", "!isIVar",
            "methodDefined(frame, receiver, stringName, getDefinedNode())",
            "!methodDefined(frame, receiver, INDEX_METHOD_NAME, getIndexDefinedNode())"
    })
    public Object callMethod(
            VirtualFrame frame,
            DynamicObject receiver,
            Object name,
            Object stringName,
            boolean isIVar,
            @Cached("create()") ForeignToRubyNode nameToRubyNode) {
        return getCallNode().call(frame, receiver, METHOD_NAME, nameToRubyNode.executeConvert(name));
    }

    @Specialization(guards = {
            "!isRubyArray(receiver)", "!isRubyHash(receiver)", "!isIVar",
            "methodDefined(frame, receiver, stringName, getDefinedNode())",
            "methodDefined(frame, receiver, INDEX_METHOD_NAME, getIndexDefinedNode())"
    })
    public Object callMethodPriorityOverIndex(
            VirtualFrame frame,
            DynamicObject receiver,
            Object name,
            Object stringName,
            boolean isIVar,
            @Cached("create()") ForeignToRubyNode nameToRubyNode) {
        return getCallNode().call(frame, receiver, INDEX_METHOD_NAME, nameToRubyNode.executeConvert(name));
    }

    @Specialization(guards = {
            "!isRubyArray(receiver)", "!isRubyHash(receiver)", "!isIVar",
            "!methodDefined(frame, receiver, stringName, getDefinedNode())",
            "methodDefined(frame, receiver, INDEX_METHOD_NAME, getIndexDefinedNode())"
    })
    public Object index(
            VirtualFrame frame,
            DynamicObject receiver,
            Object name,
            Object stringName,
            boolean isIVar,
            @Cached("create()") ForeignToRubyNode nameToRubyNode,
            @Cached("createBinaryProfile()") ConditionProfile arrayProfile,
            @Cached("createBinaryProfile()") ConditionProfile validArrayIndexProfile) {
        return getCallNode().call(frame, receiver, INDEX_METHOD_NAME, nameToRubyNode.executeConvert(name));
    }

    @Specialization(guards = {
            "!isRubyArray(receiver)", "!isRubyHash(receiver)", "!isIVar",
            "!methodDefined(frame, receiver, stringName, getDefinedNode())",
            "!methodDefined(frame, receiver, INDEX_METHOD_NAME, getIndexDefinedNode())"
    })
    public Object unknownIdentifier(
            VirtualFrame frame,
            DynamicObject receiver,
            Object name,
            Object stringName,
            boolean isIVar) {
        throw UnknownIdentifierException.raise(toString(name));
    }

    @TruffleBoundary
    private String toString(Object name) {
        return name.toString();
    }

    protected DoesRespondDispatchHeadNode getDefinedNode() {
        if (definedNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            definedNode = insert(DoesRespondDispatchHeadNode.create());
        }

        return definedNode;
    }

    protected DoesRespondDispatchHeadNode getIndexDefinedNode() {
        if (indexDefinedNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            indexDefinedNode = insert(DoesRespondDispatchHeadNode.create());
        }

        return indexDefinedNode;
    }

    protected boolean methodDefined(VirtualFrame frame, DynamicObject receiver, Object stringName,
                                    DoesRespondDispatchHeadNode definedNode) {
        if (stringName == null) {
            return false;
        } else {
            return definedNode.doesRespondTo(frame, stringName, receiver);
        }
    }

    protected CallDispatchHeadNode getCallNode() {
        if (callNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callNode = insert(CallDispatchHeadNode.createOnSelf());
        }

        return callNode;
    }

}
