/*
 * Copyright (c) 2014, 2018 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.interop;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.source.Source;
import org.truffleruby.builtins.CoreClass;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.core.rope.Rope;
import org.truffleruby.core.rope.RopeNodes;
import org.truffleruby.core.string.StringCachingGuards;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.JavaException;

import java.io.File;
import java.io.IOException;

@CoreClass("Polyglot")
public abstract class PolyglotNodes {

    @CoreMethod(names = "eval", isModuleFunction = true, required = 2)
    @ImportStatic({ StringCachingGuards.class, StringOperations.class })
    public abstract static class EvalNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = {
                "isRubyString(id)",
                "isRubyString(source)",
                "idEqualNode.execute(rope(id), cachedMimeType)",
                "sourceEqualNode.execute(rope(source), cachedSource)"
        }, limit = "getCacheLimit()")
        public Object evalCached(
                DynamicObject id,
                DynamicObject source,
                @Cached("privatizeRope(id)") Rope cachedMimeType,
                @Cached("privatizeRope(source)") Rope cachedSource,
                @Cached("create(parse(id, source))") DirectCallNode callNode,
                @Cached("create()") RopeNodes.EqualNode idEqualNode,
                @Cached("create()") RopeNodes.EqualNode sourceEqualNode
        ) {
            return callNode.call(RubyNode.EMPTY_ARGUMENTS);
        }

        @Specialization(guards = {"isRubyString(id)", "isRubyString(source)"}, replaces = "evalCached")
        public Object evalUncached(DynamicObject id, DynamicObject source,
                @Cached("create()") IndirectCallNode callNode) {
            return callNode.call(parse(id, source), RubyNode.EMPTY_ARGUMENTS);
        }

        @TruffleBoundary
        protected CallTarget parse(DynamicObject id, DynamicObject source) {
            final String idString = id.toString();
            final Source sourceObject = Source.newBuilder(source.toString())
                    .name("(eval)")
                    .language(idString)
                    .build();
            return getContext().getEnv().parse(sourceObject);
        }

        protected int getCacheLimit() {
            return getContext().getOptions().EVAL_CACHE;
        }

    }

    @CoreMethod(names = "eval_file", isModuleFunction = true, required = 1, optional = 1)
    public abstract static class EvalFileNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(guards = "isRubyString(fileName)")
        public Object evalFile(DynamicObject fileName, NotProvided id) {
            try {
                final Source sourceObject = Source.newBuilder(new File(fileName.toString().intern()))
                        .build();
                getContext().getEnv().parse(sourceObject).call();
            } catch (IOException e) {
                throw new JavaException(e);
            }

            return nil();
        }

        @TruffleBoundary
        @Specialization(guards = {"isRubyString(id)", "isRubyString(fileName)"})
        public Object evalFile(DynamicObject id, DynamicObject fileName) {
            final String idString = id.toString();
            try {
                final Source sourceObject = Source.newBuilder(new File(fileName.toString().intern()))
                        .language(idString)
                        .build();
                getContext().getEnv().parse(sourceObject).call();
            } catch (IOException e) {
                throw new JavaException(e);
            }

            return nil();
        }

    }

}
