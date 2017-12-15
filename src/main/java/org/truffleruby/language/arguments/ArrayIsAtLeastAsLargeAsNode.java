/*
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.language.arguments;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import org.truffleruby.Layouts;
import org.truffleruby.language.RubyNode;

public class ArrayIsAtLeastAsLargeAsNode extends RubyNode {

    private final int requiredSize;

    @Child private RubyNode child;

    public ArrayIsAtLeastAsLargeAsNode(int requiredSize, RubyNode child) {
        this.requiredSize = requiredSize;
        this.child = child;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final int actualSize = Layouts.ARRAY.getSize((DynamicObject) child.execute(frame));
        return actualSize >= requiredSize;
    }

}
