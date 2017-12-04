/*
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.interop;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyNode;

/**
 * Convert a Ruby String or Symbol to a Java string, or return a default string if a value was not provided.
 */
@NodeChild(value = "value", type = RubyNode.class)
public abstract class ToJavaStringWithDefaultNode extends RubyNode {

    private final String defaultValue;
    @Child private ToJavaStringNode toJavaStringNode = ToJavaStringNodeGen.create(null);

    public ToJavaStringWithDefaultNode(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public abstract String executeString(Object value);

    @Specialization
    public String doDefault(NotProvided value) {
        return toJavaStringNode.executeToJavaString(defaultValue);
    }

    @Specialization(guards = "wasProvided(value)")
    public String doProvided(Object value) {
        return toJavaStringNode.executeToJavaString(value);
    }

}
