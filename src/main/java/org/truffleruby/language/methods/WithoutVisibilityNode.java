/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.language.methods;

import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class WithoutVisibilityNode extends RubyBaseNode {

    public abstract DeclarationContext executeWithoutVisibility(DeclarationContext declarationContext);

    @Specialization(guards = "declarationContext == cachedContext")
    protected DeclarationContext cached(DeclarationContext declarationContext,
            @Cached("declarationContext") DeclarationContext cachedContext,
            @Cached("uncached(cachedContext)") DeclarationContext without) {
        return without;
    }

    @Specialization(replaces = "cached")
    protected DeclarationContext uncached(DeclarationContext declarationContext) {
        return declarationContext.withVisibility(null);
    }

}
