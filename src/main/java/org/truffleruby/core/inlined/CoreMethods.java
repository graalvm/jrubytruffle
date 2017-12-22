/*
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.inlined;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.object.DynamicObject;

import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.core.module.ModuleFields;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.RubyCallNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;
import org.truffleruby.language.methods.InternalMethod;

/**
 * We inline basic operations as it makes little sense to compile them in isolation without the
 * surrounding method and it delays more interesting compilations by filling the compilation queue.
 * The performance in interpreter is typically also improved, making inlined basic operations an
 * optimization useful mostly for warmup. The choice of inlining a basic operation is based on
 * running benchmarks and observing which basic operations methods are compiled early.
 * <p>
 * Each inlined basic operation has its own Node to conveniently express guards and assumptions.
 * Inlined basic operations execute in the caller frame, which may be useful for some operations
 * accessing the caller frame like Kernel#block_given?. Therefore, the guards must ensure no
 * exception (e.g.: division by 0) can happen during the inlined operation, as that would make the
 * inlined operation NOT appear in the backtrace.
 * <p>
 * Two strategies are used to check method re-definition.
 * <li>If the class is a leaf class (there cannot be instances of a subclass of that class), or we
 * know the operation will likely be called on instances of a given class, then we only need to
 * check the receiver is an instance of that class and register an Assumption for the given method
 * name (see {@link ModuleFields#registerAssumption(String)}). In such cases the method must be
 * public as we do not check visibility.</li>
 * <li>Otherwise, we need to do a method lookup and verify the method that would be called is the
 * standard definition we expect.</li>
 */
public class CoreMethods {

    private final RubyContext context;

    final Assumption fixnumAddAssumption, floatAddAssumption;
    final Assumption fixnumSubAssumption, floatSubAssumption;
    final Assumption fixnumMulAssumption, floatMulAssumption;
    final Assumption fixnumDivAssumption, floatDivAssumption;
    final Assumption fixnumModAssumption, floatModAssumption;
    public final Assumption fixnumCmpAssumption, floatCmpAssumption;

    final Assumption fixnumLeftShiftAssumption;
    final Assumption fixnumRightShiftAssumption;
    final Assumption fixnumBitOrAssumption;
    final Assumption fixnumBitAndAssumption;

    final Assumption fixnumEqualAssumption;
    final Assumption fixnumLessThanAssumption, fixnumLessOrEqualAssumption;
    final Assumption fixnumGreaterThanAssumption, fixnumGreaterOrEqualAssumption;

    final Assumption nilClassIsNilAssumption;

    public final InternalMethod BLOCK_GIVEN;
    public final InternalMethod NOT;
    public final InternalMethod KERNEL_IS_NIL;
    public final InternalMethod STRING_BYTESIZE;

    public CoreMethods(RubyContext context) {
        this.context = context;
        final DynamicObject basicObjectClass = context.getCoreLibrary().getBasicObjectClass();
        final DynamicObject kernelModule = context.getCoreLibrary().getKernelModule();
        final DynamicObject fixnumClass = context.getCoreLibrary().getFixnumClass();
        final DynamicObject floatClass = context.getCoreLibrary().getFloatClass();
        final DynamicObject nilClass = context.getCoreLibrary().getNilClass();
        final DynamicObject stringClass = context.getCoreLibrary().getStringClass();

        fixnumAddAssumption = registerAssumption(fixnumClass, "+");
        floatAddAssumption = registerAssumption(floatClass, "+");

        fixnumSubAssumption = registerAssumption(fixnumClass, "-");
        floatSubAssumption = registerAssumption(floatClass, "-");

        fixnumMulAssumption = registerAssumption(fixnumClass, "*");
        floatMulAssumption = registerAssumption(floatClass, "*");

        fixnumDivAssumption = registerAssumption(fixnumClass, "/");
        floatDivAssumption = registerAssumption(floatClass, "/");

        fixnumModAssumption = registerAssumption(fixnumClass, "%");
        floatModAssumption = registerAssumption(floatClass, "%");

        fixnumCmpAssumption = registerAssumption(fixnumClass, "<=>");
        floatCmpAssumption = registerAssumption(floatClass, "<=>");

        fixnumLeftShiftAssumption = registerAssumption(fixnumClass, "<<");
        fixnumRightShiftAssumption = registerAssumption(fixnumClass, ">>");
        fixnumBitOrAssumption = registerAssumption(fixnumClass, "|");
        fixnumBitAndAssumption = registerAssumption(fixnumClass, "&");

        fixnumEqualAssumption = registerAssumption(fixnumClass, "==");
        fixnumLessThanAssumption = registerAssumption(fixnumClass, "<");
        fixnumLessOrEqualAssumption = registerAssumption(fixnumClass, "<=");
        fixnumGreaterThanAssumption = registerAssumption(fixnumClass, ">");
        fixnumGreaterOrEqualAssumption = registerAssumption(fixnumClass, ">=");

        nilClassIsNilAssumption = registerAssumption(nilClass, "nil?");

        BLOCK_GIVEN = getMethod(kernelModule, "block_given?");
        NOT = getMethod(basicObjectClass, "!");
        KERNEL_IS_NIL = getMethod(kernelModule, "nil?");
        STRING_BYTESIZE = getMethod(stringClass, "bytesize");
    }

    private Assumption registerAssumption(DynamicObject klass, String methodName) {
        return Layouts.MODULE.getFields(klass).registerAssumption(methodName);
    }

    private InternalMethod getMethod(DynamicObject module, String name) {
        final InternalMethod method = Layouts.MODULE.getFields(module).getMethod(name);
        if (method == null || method.isUndefined()) {
            throw new AssertionError();
        }
        return method;
    }

    public RubyNode createCallNode(RubyCallNodeParameters callParameters) {
        if (!context.getOptions().BASICOPS_INLINE ||
                callParameters.getBlock() != null || callParameters.isSplatted() || callParameters.isSafeNavigation()) {
            return new RubyCallNode(callParameters);
        }

        final RubyNode self = callParameters.getReceiver();
        final RubyNode[] args = callParameters.getArguments();
        int n = 1 /* self */ + args.length;

        if (n == 1) {
            switch (callParameters.getMethodName()) {
                case "!":
                    return InlinedNotNodeGen.create(context, callParameters, self);
                case "block_given?":
                    if (callParameters.isIgnoreVisibility()) {
                        return InlinedBlockGivenNodeGen.create(context, callParameters, self);
                    }
                    break;
                case "nil?":
                    return InlinedIsNilNodeGen.create(context, callParameters, self);
                case "bytesize":
                    return InlinedByteSizeNodeGen.create(context, callParameters, self);
                default:
            }
        } else if (n == 2) {
            switch (callParameters.getMethodName()) {
                case "+":
                    return InlinedAddNodeGen.create(context, callParameters, self, args[0]);
                case "-":
                    return InlinedSubNodeGen.create(context, callParameters, self, args[0]);
                case "*":
                    return InlinedMulNodeGen.create(context, callParameters, self, args[0]);
                case "/":
                    return InlinedDivNodeGen.create(context, callParameters, self, args[0]);
                case "%":
                    return InlinedModNodeGen.create(context, callParameters, self, args[0]);
                case "<<":
                    return InlinedLeftShiftNodeGen.create(context, callParameters, self, args[0]);
                case ">>":
                    return InlinedRightShiftNodeGen.create(context, callParameters, self, args[0]);
                case "&":
                    return InlinedBitAndNodeGen.create(context, callParameters, self, args[0]);
                case "|":
                    return InlinedBitOrNodeGen.create(context, callParameters, self, args[0]);
                case "==":
                    return InlinedEqualNodeGen.create(context, callParameters, self, args[0]);
                case "<":
                    return InlinedLessThanNodeGen.create(context, callParameters, self, args[0]);
                case "<=":
                    return InlinedLessOrEqualNodeGen.create(context, callParameters, self, args[0]);
                case ">":
                    return InlinedGreaterThanNodeGen.create(context, callParameters, self, args[0]);
                case ">=":
                    return InlinedGreaterOrEqualNodeGen.create(context, callParameters, self, args[0]);
                default:
            }
        }

        return new RubyCallNode(callParameters);
    }


}
