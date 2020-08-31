/*
 * Copyright (c) 2016, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.inlined;

import org.truffleruby.RubyContext;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.module.ModuleFields;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.RubyCallNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;
import org.truffleruby.language.methods.BlockDefinitionNode;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.parser.TranslatorEnvironment;

import com.oracle.truffle.api.Assumption;

/** We inline basic operations directly in the AST (instead of a method call) as it makes little sense to compile them
 * in isolation without the surrounding method and it delays more interesting compilations by filling the compilation
 * queue. The performance in interpreter is typically also improved, making inlined basic operations an optimization
 * useful mostly for warmup. The choice of inlining a basic operation is based on running benchmarks and observing which
 * basic operations methods are compiled early. AST inlining is also useful to guarantee splitting for small operations
 * which make sense to always split.
 * <p>
 * Each inlined basic operation has its own Node to conveniently express guards and assumptions. Inlined basic
 * operations execute in the caller frame, which may be useful for some operations accessing the caller frame like
 * Kernel#block_given?.
 * <p>
 * IMPORTANT: The specialization guards must ensure no exception (e.g.: division by 0) can happen during the inlined
 * operation (and therefore no nested Ruby call as that could raise an exception), as that would make the inlined
 * operation NOT appear in the backtrace which would be incorrect and confusing. Inlined nodes should use as few nodes
 * as possible to save on footprint. In trivial cases it is better to inline the logic directly (e.g. for Integer#==
 * with two int's).
 * <p>
 * Two strategies are used to check method re-definition.
 * <li>If the class is a leaf class (there cannot be instances of a subclass of that class), then we only need to check
 * the receiver is an instance of that class and register an Assumption for the given method name (see
 * {@link ModuleFields#registerAssumption(String)}). In such cases the method must be public as we do not check
 * visibility.</li>
 * <li>Otherwise, we need to do a method lookup and verify the method that would be called is the standard definition we
 * expect.</li>
 * <p>
 * Every specialization should use {@code assumptions = "assumptions",} to check at least the tracing Assumption. When
 * adding a new node, it is a good idea to add a debug print in the non-inlined method and try calling it with and
 * without a {@code set_trace_func proc{};} before to see if the inlined version is used correctly. */
public class CoreMethods {

    private final RubyContext context;

    final Assumption integerNegAssumption, floatNegAssumption;
    final Assumption integerAddAssumption, floatAddAssumption;
    final Assumption integerSubAssumption, floatSubAssumption;
    final Assumption integerMulAssumption, floatMulAssumption;
    final Assumption integerDivAssumption, floatDivAssumption;
    final Assumption integerModAssumption, floatModAssumption;
    public final Assumption integerCmpAssumption, floatCmpAssumption;

    final Assumption integerLeftShiftAssumption;
    final Assumption integerRightShiftAssumption;
    final Assumption integerBitOrAssumption;
    final Assumption integerBitAndAssumption;

    final Assumption integerEqualAssumption;
    final Assumption integerLessThanAssumption, integerLessOrEqualAssumption;
    final Assumption integerGreaterThanAssumption, integerGreaterOrEqualAssumption;

    final Assumption nilClassIsNilAssumption;

    public final InternalMethod EXCEPTION_BACKTRACE;
    public final InternalMethod BLOCK_GIVEN;
    public final InternalMethod LAMBDA;
    public final InternalMethod BINDING;
    public final InternalMethod NOT;
    public final InternalMethod KERNEL_IS_NIL;
    public final InternalMethod KERNEL_IS_A;
    public final InternalMethod KERNEL_KIND_OF;
    public final InternalMethod STRING_BYTESIZE;
    public final InternalMethod MODULE_CASE_EQUAL;
    public final InternalMethod STRING_EQUAL;

    public CoreMethods(RubyContext context) {
        this.context = context;
        final RubyClass basicObjectClass = context.getCoreLibrary().basicObjectClass;
        final RubyClass exceptionClass = context.getCoreLibrary().exceptionClass;
        final RubyModule kernelModule = context.getCoreLibrary().kernelModule;
        final RubyClass integerClass = context.getCoreLibrary().integerClass;
        final RubyClass floatClass = context.getCoreLibrary().floatClass;
        final RubyClass moduleClass = context.getCoreLibrary().moduleClass;
        final RubyClass nilClass = context.getCoreLibrary().nilClass;
        final RubyClass stringClass = context.getCoreLibrary().stringClass;

        integerNegAssumption = registerAssumption(integerClass, "-@");
        floatNegAssumption = registerAssumption(floatClass, "-@");

        integerAddAssumption = registerAssumption(integerClass, "+");
        floatAddAssumption = registerAssumption(floatClass, "+");

        integerSubAssumption = registerAssumption(integerClass, "-");
        floatSubAssumption = registerAssumption(floatClass, "-");

        integerMulAssumption = registerAssumption(integerClass, "*");
        floatMulAssumption = registerAssumption(floatClass, "*");

        integerDivAssumption = registerAssumption(integerClass, "/");
        floatDivAssumption = registerAssumption(floatClass, "/");

        integerModAssumption = registerAssumption(integerClass, "%");
        floatModAssumption = registerAssumption(floatClass, "%");

        integerCmpAssumption = registerAssumption(integerClass, "<=>");
        floatCmpAssumption = registerAssumption(floatClass, "<=>");

        integerLeftShiftAssumption = registerAssumption(integerClass, "<<");
        integerRightShiftAssumption = registerAssumption(integerClass, ">>");
        integerBitOrAssumption = registerAssumption(integerClass, "|");
        integerBitAndAssumption = registerAssumption(integerClass, "&");

        integerEqualAssumption = registerAssumption(integerClass, "==");
        integerLessThanAssumption = registerAssumption(integerClass, "<");
        integerLessOrEqualAssumption = registerAssumption(integerClass, "<=");
        integerGreaterThanAssumption = registerAssumption(integerClass, ">");
        integerGreaterOrEqualAssumption = registerAssumption(integerClass, ">=");

        nilClassIsNilAssumption = registerAssumption(nilClass, "nil?");

        BLOCK_GIVEN = getMethod(kernelModule, "block_given?");
        LAMBDA = getMethod(kernelModule, "lambda");
        BINDING = getMethod(kernelModule, "binding");
        NOT = getMethod(basicObjectClass, "!");
        EXCEPTION_BACKTRACE = getMethod(exceptionClass, "backtrace");
        KERNEL_IS_NIL = getMethod(kernelModule, "nil?");
        STRING_BYTESIZE = getMethod(stringClass, "bytesize");
        KERNEL_IS_A = getMethod(kernelModule, "is_a?");
        KERNEL_KIND_OF = getMethod(kernelModule, "kind_of?");
        MODULE_CASE_EQUAL = getMethod(moduleClass, "===");
        STRING_EQUAL = getMethod(stringClass, "==");
    }

    private Assumption registerAssumption(RubyClass klass, String methodName) {
        return klass.fields.registerAssumption(methodName);
    }

    private InternalMethod getMethod(RubyModule module, String name) {
        final InternalMethod method = module.fields.getMethod(name);
        if (method == null || method.isUndefined()) {
            throw new AssertionError();
        }
        return method;
    }

    public RubyNode createCallNode(RubyCallNodeParameters callParameters, TranslatorEnvironment environment) {
        if (!context.getOptions().BASICOPS_INLINE ||
                callParameters.isSplatted() || callParameters.isSafeNavigation()) {
            return new RubyCallNode(callParameters);
        }

        final RubyNode self = callParameters.getReceiver();
        final RubyNode[] args = callParameters.getArguments();
        int n = 1 /* self */ + args.length;

        if (callParameters.getBlock() != null) {
            if (callParameters.getMethodName().equals("lambda") && callParameters.isIgnoreVisibility() &&
                    n == 1 && callParameters.getBlock() instanceof BlockDefinitionNode) {
                return InlinedLambdaNodeGen.create(context, callParameters, self, callParameters.getBlock());
            } else {
                // The calls below should all not be given a block
                return new RubyCallNode(callParameters);
            }
        }

        if (n == 1) {
            switch (callParameters.getMethodName()) {
                case "!":
                    return InlinedNotNodeGen.create(context, callParameters, self);
                case "-@":
                    return InlinedNegNodeGen.create(context, callParameters, self);
                case "binding":
                    if (callParameters.isIgnoreVisibility()) {
                        return InlinedBindingNodeGen.create(context, callParameters, self);
                    }
                    break;
                case "block_given?":
                    if (callParameters.isIgnoreVisibility()) {
                        return InlinedBlockGivenNodeGen.create(context, callParameters, environment, self);
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
                case "===":
                    return InlinedCaseEqualNodeGen.create(context, callParameters, self, args[0]);
                case "<":
                    return InlinedLessThanNodeGen.create(context, callParameters, self, args[0]);
                case "<=":
                    return InlinedLessOrEqualNodeGen.create(context, callParameters, self, args[0]);
                case ">":
                    return InlinedGreaterThanNodeGen.create(context, callParameters, self, args[0]);
                case ">=":
                    return InlinedGreaterOrEqualNodeGen.create(context, callParameters, self, args[0]);
                case "is_a?":
                    return InlinedIsANodeGen.create(context, callParameters, self, args[0]);
                case "kind_of?":
                    return InlinedKindOfNodeGen.create(context, callParameters, self, args[0]);
                default:
            }
        }

        return new RubyCallNode(callParameters);
    }


}
