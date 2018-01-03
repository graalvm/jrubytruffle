/*
 * Copyright (c) 2013, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.gem.bcrypt;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import org.jcodings.specific.USASCIIEncoding;
import org.truffleruby.builtins.CoreClass;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.core.rope.CodeRange;
import org.truffleruby.core.string.StringNodes;
import org.truffleruby.core.string.StringOperations;

@CoreClass("Truffle::Gem::BCrypt")
public abstract class BCryptNodes {

    @CoreMethod(names = "hashpw", required = 2, onSingleton = true)
    public abstract static class HashPassword extends CoreMethodArrayArgumentsNode {

        @Child private StringNodes.MakeStringNode makeStringNode = StringNodes.MakeStringNode.create();

        @TruffleBoundary
        @Specialization(guards = { "isRubyString(secret)", "isRubyString(salt)" })
        public Object hashpw(DynamicObject secret, DynamicObject salt) {
            final String result = BCrypt.hashpw(
                    StringOperations.getString(secret),
                    StringOperations.getString(salt));

            return makeStringNode.executeMake(result, USASCIIEncoding.INSTANCE, CodeRange.CR_7BIT);
        }
    }

    @CoreMethod(names = "gensalt", required = 1, onSingleton = true, lowerFixnum = 1)
    public abstract static class GenerateSalt extends CoreMethodArrayArgumentsNode {

        @Child private StringNodes.MakeStringNode makeStringNode = StringNodes.MakeStringNode.create();

        @TruffleBoundary
        @Specialization
        public Object gensalt(int cost) {
            return makeStringNode.executeMake(BCrypt.gensalt(cost), USASCIIEncoding.INSTANCE, CodeRange.CR_7BIT);
        }
    }

}
