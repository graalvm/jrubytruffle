/*
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.bool;

import org.truffleruby.builtins.CoreClass;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodNode;
import org.truffleruby.core.inlined.InlinedIsNilNode;

import com.oracle.truffle.api.dsl.Specialization;

@CoreClass("NilClass")
public abstract class NilClassNodes {

    /** Needs to be in Java for {@link InlinedIsNilNode} */
    @CoreMethod(names = "nil?", needsSelf = false)
    public abstract static class AndNode extends CoreMethodNode {

        @Specialization
        public boolean isNil() {
            return true;
        }
    }

}
