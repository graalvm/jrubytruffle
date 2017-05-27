/*
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.format.read.array;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.read.SourceNode;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.CallDispatchHeadNode;
import org.truffleruby.language.dispatch.DispatchHeadNodeFactory;

@NodeChildren({
        @NodeChild(value = "source", type = SourceNode.class),
})
public abstract class ReadHashValueNode extends FormatNode {

    private final Object key;

    @Child private CallDispatchHeadNode fetchNode;

    private final ConditionProfile oneHashProfile = ConditionProfile.createBinaryProfile();

    public ReadHashValueNode(Object key) {
        this.key = key;
    }

    @Specialization
    public Object read(VirtualFrame frame, Object[] source) {
        if (oneHashProfile.profile(source.length != 1 || !RubyGuards.isRubyHash(source[0]))) {
            throw new RaiseException(getContext().getCoreExceptions().argumentErrorOneHashRequired(this));
        }

        final DynamicObject hash = (DynamicObject) source[0];

        if (fetchNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            fetchNode = insert(DispatchHeadNodeFactory.createMethodCall(true));
        }

        return fetchNode.call(frame, hash, "fetch", key);
    }

}
