/*
 * Copyright (c) 2014, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.cast;

import com.oracle.truffle.api.library.CachedLibrary;
import org.truffleruby.core.kernel.KernelNodes;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.DispatchNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.library.RubyStringLibrary;

@NodeChild(value = "value", type = RubyNode.class)
public abstract class ToSNode extends RubyContextSourceNode {

    @Child private KernelNodes.ToSNode kernelToSNode;

    @Specialization
    protected RubyString toS(RubyString string) {
        return string;
    }

    @Specialization
    protected ImmutableRubyString toS(ImmutableRubyString string) {
        return string;
    }

    @Specialization(guards = "isNotRubyString(object)")
    protected Object toSFallback(VirtualFrame frame, Object object,
            @Cached DispatchNode callToSNode,
            @CachedLibrary(limit = "2") RubyStringLibrary libString) {
        final Object value = callToSNode.dispatch(frame, object, "to_s", null, EMPTY_ARGUMENTS);

        if (libString.isRubyString(value)) {
            return value;
        } else {
            return kernelToS(object);
        }
    }

    protected RubyString kernelToS(Object object) {
        if (kernelToSNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            kernelToSNode = insert(KernelNodes.ToSNode.create());
        }
        return kernelToSNode.executeToS(object);
    }

}
