/*
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.language.objects;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import org.truffleruby.core.symbol.SymbolTable;
import org.truffleruby.language.RubyNode;

@NodeChildren({ @NodeChild("object"), @NodeChild("name") })
public abstract class ObjectIVarGetNode extends RubyNode {

    private final boolean checkName;

    public ObjectIVarGetNode(boolean checkName) {
        this.checkName = checkName;
    }

    public static ObjectIVarGetNode create() {
        return ObjectIVarGetNodeGen.create(false, null, null);
    }

    public abstract Object executeIVarGet(DynamicObject object, Object name);

    @Specialization(guards = "name == cachedName", limit = "getCacheLimit()")
    public Object ivarGetCached(DynamicObject object, Object name,
            @Cached("name") Object cachedName,
            @Cached("createReadFieldNode(checkName(cachedName, object))") ReadObjectFieldNode readObjectFieldNode) {
        return readObjectFieldNode.execute(object);
    }

    @TruffleBoundary
    @Specialization(replaces = "ivarGetCached")
    public Object ivarGetUncached(DynamicObject object, Object name) {
        return ReadObjectFieldNode.read(object, checkName(name, object), nil());
    }

    protected Object checkName(Object name, DynamicObject object) {
        return checkName ? SymbolTable.checkInstanceVariableName(getContext(), (String) name, object, this) : name;
    }

    protected ReadObjectFieldNode createReadFieldNode(Object name) {
        return ReadObjectFieldNodeGen.create(name, nil());
    }

    protected int getCacheLimit() {
        return getContext().getOptions().INSTANCE_VARIABLE_CACHE;
    }

}
