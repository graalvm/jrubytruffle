/*
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.language.globals;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.RubyNode;

public class UpdateVerbosityNode extends RubyNode {

    @Child private RubyNode child;

    public UpdateVerbosityNode(RubyNode child) {
        this.child = child;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object value = child.execute(frame);
        validateVerbose(value);
        return value;
    }

    @TruffleBoundary
    private void validateVerbose(Object value) {
        if (value instanceof Boolean || value == nil()) {
            // OK
        } else {
            throw new UnsupportedOperationException();
        }
    }

}
