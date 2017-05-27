/*
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.jcodings.specific.UTF8Encoding;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.dispatch.CallDispatchHeadNode;
import org.truffleruby.language.dispatch.DispatchHeadNodeFactory;

/** Load libraries required from the command line (-r LIBRARY) */
public class LoadRequiredLibrariesNode extends RubyNode {

    @Child private CallDispatchHeadNode requireNode = DispatchHeadNodeFactory.createMethodCallOnSelf();

    @Override
    public Object execute(VirtualFrame frame) {
        Object self = RubyArguments.getSelf(frame);

        for (String requiredLibrary : getContext().getOptions().REQUIRED_LIBRARIES) {
            requireNode.call(frame, self, "require", createString(StringOperations.encodeRope(requiredLibrary, UTF8Encoding.INSTANCE)));
        }

        return nil();
    }

}
