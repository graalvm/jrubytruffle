/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.string;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.truffleruby.RubyContext;
import org.truffleruby.collections.ConcurrentOperations;
import org.truffleruby.core.rope.Rope;

import com.oracle.truffle.api.object.DynamicObject;

public class FrozenStringLiterals {

    private final RubyContext context;
    private final Map<Rope, DynamicObject> strings = new ConcurrentHashMap<>();

    public FrozenStringLiterals(RubyContext context) {
        this.context = context;
    }

    public DynamicObject getFrozenStringLiteral(Rope rope) {
        return ConcurrentOperations.getOrCompute(strings, rope, r -> StringOperations.createFrozenString(context, rope));
    }

}
