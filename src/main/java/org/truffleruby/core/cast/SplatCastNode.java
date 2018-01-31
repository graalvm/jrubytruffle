/*
 * Copyright (c) 2013, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.cast;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import org.truffleruby.Layouts;
import org.truffleruby.core.array.ArrayDupNode;
import org.truffleruby.core.array.ArrayDupNodeGen;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.CallDispatchHeadNode;

/**
 * Splat as used to cast a value to an array if it isn't already, as in {@code *value}.
 */
@NodeChild("child")
public abstract class SplatCastNode extends RubyNode {

    public enum NilBehavior {
        EMPTY_ARRAY,
        ARRAY_WITH_NIL,
        NIL,
        CONVERT
    }

    private final NilBehavior nilBehavior;
    private final DynamicObject conversionMethod;
    @CompilationFinal private boolean copy = true;

    @Child private ArrayDupNode dup;
    @Child private CallDispatchHeadNode toA;

    public SplatCastNode(NilBehavior nilBehavior, boolean useToAry) {
        this.nilBehavior = nilBehavior;
        // Calling private #to_a is allowed for the *splat operator.
        String name = useToAry ? "to_ary" : "to_a";
        conversionMethod = getContext().getSymbolTable().getSymbol(name);
    }

    public void doNotCopy() {
        copy = false;
    }

    public abstract RubyNode getChild();

    @Specialization(guards = "isNil(nil)")
    public Object splatNil(VirtualFrame frame, Object nil) {
        switch (nilBehavior) {
            case EMPTY_ARRAY:
                return createArray(null, 0);

            case ARRAY_WITH_NIL:
                return createArray(new Object[] { nil() }, 1);

            case CONVERT:
                return callToA(frame, nil);

            case NIL:
                return nil;

            default: {
                throw new UnsupportedOperationException();
            }
        }
    }

    @Specialization(guards = "isRubyArray(array)")
    public DynamicObject splat(VirtualFrame frame, DynamicObject array) {
        // TODO(cs): is it necessary to dup here in all cases?
        // It is needed at least for [*ary] (parsed as just a SplatParseNode) and b = *ary.
        if (copy) {
            return executeDup(frame, array);
        } else {
            return array;
        }
    }

    @Specialization(guards = {"!isNil(object)", "!isRubyArray(object)"})
    public DynamicObject splat(VirtualFrame frame, Object object,
            @Cached("create()") BranchProfile errorProfile,
            @Cached("createOnSelf()") CallDispatchHeadNode toArrayNode) {
        final Object array = toArrayNode.call(null, coreLibrary().getTruffleTypeModule(), "rb_check_convert_type", object, coreLibrary().getArrayClass(), conversionMethod);
        if (RubyGuards.isRubyArray(array)) {
            return (DynamicObject) array;
        } else if (array == nil()) {
            return createArray(new Object[]{object}, 1);
        } else {
            errorProfile.enter();
            throw new RaiseException(coreExceptions().typeErrorCantConvertTo(object, "Array",
                Layouts.SYMBOL.getString(conversionMethod), array, this));
        }
    }

    private Object callToA(VirtualFrame frame, Object nil) {
        if (toA == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toA = insert(CallDispatchHeadNode.createReturnMissing());
        }
        return toA.call(frame, nil, "to_a");
    }

    private DynamicObject executeDup(VirtualFrame frame, DynamicObject array) {
        if (dup == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            dup = insert(ArrayDupNodeGen.create(null));
        }
        return dup.executeDup(frame, array);
    }

}
