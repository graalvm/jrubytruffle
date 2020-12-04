/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.symbol;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.Hashing;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.rope.LeafRope;
import org.truffleruby.language.ImmutableRubyObject;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import org.truffleruby.language.methods.DeclarationContext;

@ExportLibrary(InteropLibrary.class)
public final class RubySymbol extends ImmutableRubyObject implements TruffleObject {

    public static final int UNASSIGNED_ID = -1;

    private static final int CLASS_SALT = 92021474; // random number, stops hashes for similar values but different classes being the same, static because we want deterministic hashes

    private final String string;
    private final LeafRope rope;
    private final int javaStringHashCode;
    private final long id;

    private volatile RootCallTarget callTargetNoRefinements = null;

    public RubySymbol(String string, LeafRope rope, long id) {
        this.string = string;
        this.rope = rope;
        this.javaStringHashCode = string.hashCode();
        this.id = id;
    }

    public RubySymbol(String string, LeafRope rope) {
        this(string, rope, UNASSIGNED_ID);
    }

    public long getId() {
        return id;
    }

    public String getString() {
        return string;
    }

    public LeafRope getRope() {
        return rope;
    }

    @TruffleBoundary
    public RootCallTarget getCallTargetNoRefinements(RubyLanguage language) {
        if (callTargetNoRefinements == null) {
            synchronized (this) {
                if (callTargetNoRefinements == null) {
                    callTargetNoRefinements = SymbolNodes.ToProcNode
                            .createCallTarget(language, this, DeclarationContext.NO_REFINEMENTS);
                }
            }
        }

        return callTargetNoRefinements;
    }

    public long computeHashCode(Hashing hashing) {
        return hashing.hash(CLASS_SALT, javaStringHashCode);
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return ":" + string;
    }

    // region InteropLibrary messages
    @Override
    @ExportMessage
    public String toDisplayString(boolean allowSideEffects) {
        return toString();
    }

    @ExportMessage
    public boolean hasMetaObject() {
        return true;
    }

    @ExportMessage
    public RubyClass getMetaObject(
            @CachedContext(RubyLanguage.class) RubyContext context) {
        return context.getCoreLibrary().symbolClass;
    }

    // region String messages
    @ExportMessage
    public boolean isString() {
        return true;
    }

    @ExportMessage
    public static class AsString {

        @Specialization(guards = "symbol == cachedSymbol", limit = "getLimit()")
        protected static String asString(RubySymbol symbol,
                @Cached("symbol") RubySymbol cachedSymbol,
                @Cached("cachedSymbol.getString()") String cachedString) {
            return cachedString;
        }

        @Specialization(replaces = "asString")
        protected static String uncachedAsString(RubySymbol symbol) {
            return symbol.getString();
        }

        protected static int getLimit() {
            return RubyLanguage.getCurrentContext().getOptions().INTEROP_CONVERT_CACHE;
        }
    }
    // endregion
    // endregion

}
