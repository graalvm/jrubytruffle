/*
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.inlined;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import org.truffleruby.RubyContext;
import org.truffleruby.core.string.StringNodes;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;
import org.truffleruby.language.methods.LookupMethodNode;

public abstract class InlinedByteSizeNode extends UnaryInlinedOperationNode {

    protected static final String METHOD = "bytesize";

    public InlinedByteSizeNode(RubyContext context, RubyCallNodeParameters callNodeParameters) {
        super(callNodeParameters);
    }

    @Specialization(guards = {
            "lookupNode.lookup(frame, self, METHOD) == coreMethods().STRING_BYTESIZE",
    }, assumptions = "assumptions", limit = "1")
    int byteSize(VirtualFrame frame, DynamicObject self,
            @Cached("create()") LookupMethodNode lookupNode,
            @Cached("create()") StringNodes.ByteSizeNode byteSizeNode) {
        return byteSizeNode.executeByteSize(self);
    }

    @Specialization
    Object fallback(VirtualFrame frame, Object self) {
        return rewriteAndCall(frame, self);
    }

}
