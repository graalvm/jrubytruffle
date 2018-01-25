/*
 * Copyright (c) 2013, 2018 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.exception;

import java.util.EnumSet;

import org.jcodings.specific.UTF8Encoding;
import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.core.array.ArrayHelpers;
import org.truffleruby.core.module.ModuleFields;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.backtrace.Backtrace;
import org.truffleruby.language.backtrace.BacktraceFormatter;
import org.truffleruby.language.backtrace.BacktraceFormatter.FormattingFlags;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;

public abstract class ExceptionOperations {

    public static final String SUPER_METHOD_ERROR = "SUPER_METHOD_ERROR";
    public static final String PROTECTED_METHOD_ERROR = "PROTECTED_METHOD_ERROR";
    public static final String PRIVATE_METHOD_ERROR = "PRIVATE_METHOD_ERROR";
    public static final String NO_METHOD_ERROR = "NO_METHOD_ERROR";

    private static final EnumSet<BacktraceFormatter.FormattingFlags> FORMAT_FLAGS = EnumSet.of(
            FormattingFlags.OMIT_FROM_PREFIX,
            FormattingFlags.OMIT_EXCEPTION);

    @TruffleBoundary
    private static String messageFieldToString(RubyContext context, DynamicObject exception) {
        Object message = Layouts.EXCEPTION.getMessage(exception);
        if (message == null || message == context.getCoreLibrary().getNil()) {
            final ModuleFields exceptionClass = Layouts.MODULE.getFields(Layouts.BASIC_OBJECT.getLogicalClass(exception));
            return exceptionClass.getName(); // What Exception#message would return if no message is set
        } else {
            return message.toString();
        }
    }

    @TruffleBoundary
    public static String messageToString(RubyContext context, DynamicObject exception) {
        try {
            final Object messageObject = context.send(exception, "message");
            if (RubyGuards.isRubyString(messageObject)) {
                return StringOperations.getString((DynamicObject) messageObject);
            }
        } catch (RaiseException e) {
            // Fall back to the internal message field
        }
        return messageFieldToString(context, exception);
    }

    @TruffleBoundary
    public static String[] format(RubyContext context, DynamicObject exception, Backtrace backtrace) {
        final BacktraceFormatter formatter = new BacktraceFormatter(context, FORMAT_FLAGS);
        return formatter.formatBacktrace(context, exception, backtrace);
    }

    public static DynamicObject backtraceAsRubyStringArray(RubyContext context, DynamicObject exception, Backtrace backtrace) {
        final String[] lines = format(context, exception, backtrace);

        final Object[] array = new Object[lines.length];

        for (int n = 0; n < lines.length; n++) {
            array[n] = StringOperations.createString(context,
                    StringOperations.encodeRope(lines[n], UTF8Encoding.INSTANCE));
        }

        return ArrayHelpers.createArray(context, array, array.length);
    }

    // because the factory is not constant
    @TruffleBoundary
    public static DynamicObject createRubyException(RubyContext context, DynamicObject rubyClass, Object message, Node node, Throwable javaException) {
        Backtrace backtrace = context.getCallStack().getBacktraceForException(node, rubyClass, javaException);
        context.getCoreExceptions().showExceptionIfDebug(rubyClass, message, backtrace);
        return Layouts.EXCEPTION.createException(Layouts.CLASS.getInstanceFactory(rubyClass), message, null, backtrace);
    }

    // because the factory is not constant
    @TruffleBoundary
    public static DynamicObject createSystemCallError(RubyContext context, DynamicObject rubyClass, Object message, Node node, int errno) {
        Backtrace backtrace = context.getCallStack().getBacktraceForException(node, rubyClass);
        context.getCoreExceptions().showExceptionIfDebug(rubyClass, message, backtrace);
        return Layouts.SYSTEM_CALL_ERROR.createSystemCallError(Layouts.CLASS.getInstanceFactory(rubyClass), message, null, backtrace, errno);
    }

    public static DynamicObject getFormatter(String name, RubyContext context) {
        return (DynamicObject) Layouts.MODULE.getFields(context.getCoreLibrary().getTruffleExceptionOperationsModule()).getConstant(name).getValue();
    }

}
