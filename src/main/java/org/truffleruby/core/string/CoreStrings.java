/*
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.string;

import org.truffleruby.RubyContext;

public class CoreStrings {

    public final CoreString AMPERSAND;
    public final CoreString ASSIGNMENT;
    public final CoreString BACKTRACE_OMITTED_LIMIT;
    public final CoreString BACKTRACE_OMITTED_UNUSED;
    public final CoreString BIND_ALL_IPV4_ADDR;
    public final CoreString CALL;
    public final CoreString CANT_COMPRESS_NEGATIVE;
    public final CoreString CHARACTER_INDEX_NEGATIVE;
    public final CoreString CIRCUMFLEX;
    public final CoreString CLASS;
    public final CoreString CLASS_VARIABLE;
    public final CoreString CONSTANT;
    public final CoreString DIVIDE;
    public final CoreString DIVMOD;
    public final CoreString EMPTY_STRING;
    public final CoreString EVAL_FILENAME_STRING;
    public final CoreString EXPRESSION;
    public final CoreString FAILED_TO_ALLOCATE_MEMORY;
    public final CoreString FALSE;
    public final CoreString GLOBAL_VARIABLE;
    public final CoreString GREATER_THAN;
    public final CoreString GREATER_OR_EQUAL;
    public final CoreString INSTANCE_VARIABLE;
    public final CoreString INVALID_VALUE_FOR_FLOAT;
    public final CoreString LESS_THAN;
    public final CoreString LESS_OR_EQUAL;
    public final CoreString LINE;
    public final CoreString LOCAL_VARIABLE;
    public final CoreString METHOD;
    public final CoreString MINUS;
    public final CoreString MODULO;
    public final CoreString MULTIPLY;
    public final CoreString NEGATIVE_ARRAY_SIZE;
    public final CoreString NIL;
    public final CoreString ONE_HASH_REQUIRED;
    public final CoreString OUT_OF_RANGE;
    public final CoreString PLUS;
    public final CoreString POWER;
    public final CoreString PROC_WITHOUT_BLOCK;
    public final CoreString REPLACEMENT_CHARACTER_SETUP_FAILED;
    public final CoreString RESOURCE_TEMP_UNAVAIL_READ;
    public final CoreString RESOURCE_TEMP_UNAVAIL_WRITE;
    public final CoreString UNKNOWN;
    public final CoreString SELF;
    public final CoreString STACK_LEVEL_TOO_DEEP;
    public final CoreString SUPER;
    public final CoreString TIME_INTERVAL_MUST_BE_POS;
    public final CoreString TO_ARY;
    public final CoreString TO_STR;
    public final CoreString TOO_FEW_ARGUMENTS;
    public final CoreString TRUE;
    public final CoreString TZ;
    public final CoreString UTC;
    public final CoreString WRONG_ARGS_ZERO_PLUS_ONE;
    public final CoreString X_OUTSIDE_OF_STRING;
    public final CoreString YIELD;

    public CoreStrings(RubyContext context) {
        AMPERSAND = new CoreString(context, "&");
        ASSIGNMENT = new CoreString(context, "assignment");
        BACKTRACE_OMITTED_LIMIT = new CoreString(context, "(omitted due to -Xbacktraces.limit)");
        BACKTRACE_OMITTED_UNUSED = new CoreString(context, "(omitted as the rescue expression was pure; use -Xbacktraces.omit_unused=false to disable)");
        BIND_ALL_IPV4_ADDR = new CoreString(context, "0.0.0.0");
        CALL = new CoreString(context, "call");
        CANT_COMPRESS_NEGATIVE = new CoreString(context, "can't compress negative numbers");
        CHARACTER_INDEX_NEGATIVE = new CoreString(context, "character index is negative");
        CIRCUMFLEX = new CoreString(context, "^");
        CLASS = new CoreString(context, "class");
        CLASS_VARIABLE = new CoreString(context, "class variable");
        CONSTANT = new CoreString(context, "constant");
        DIVIDE = new CoreString(context, "/");
        DIVMOD = new CoreString(context, "divmod");
        EMPTY_STRING = new CoreString(context, "");
        EVAL_FILENAME_STRING = new CoreString(context, "(eval)");
        EXPRESSION = new CoreString(context, "expression");
        FAILED_TO_ALLOCATE_MEMORY = new CoreString(context, "failed to allocate memory");
        FALSE = new CoreString(context, "false");
        GLOBAL_VARIABLE = new CoreString(context, "global-variable");
        GREATER_THAN = new CoreString(context, ">");
        GREATER_OR_EQUAL = new CoreString(context, ">=");
        INSTANCE_VARIABLE = new CoreString(context, "instance-variable");
        INVALID_VALUE_FOR_FLOAT = new CoreString(context, "invalid value for Float()");
        LESS_THAN = new CoreString(context, "<");
        LESS_OR_EQUAL = new CoreString(context, "<=");
        LINE = new CoreString(context, "line");
        LOCAL_VARIABLE = new CoreString(context, "local-variable");
        METHOD = new CoreString(context, "method");
        MINUS = new CoreString(context, "-");
        MODULO = new CoreString(context, "%");
        MULTIPLY = new CoreString(context, "*");
        NEGATIVE_ARRAY_SIZE = new CoreString(context, "negative array size");
        NIL = new CoreString(context, "nil");
        ONE_HASH_REQUIRED = new CoreString(context, "one hash required");
        OUT_OF_RANGE = new CoreString(context, "out of range");
        PLUS = new CoreString(context, "+");
        POWER = new CoreString(context, "**");
        PROC_WITHOUT_BLOCK = new CoreString(context, "tried to create Proc object without a block");
        REPLACEMENT_CHARACTER_SETUP_FAILED = new CoreString(context, "replacement character setup failed");
        RESOURCE_TEMP_UNAVAIL_READ = new CoreString(context, "Resource temporarily unavailable - read would block");
        RESOURCE_TEMP_UNAVAIL_WRITE = new CoreString(context, "Resource temporarily unavailable - write would block");
        UNKNOWN = new CoreString(context, "(unknown)");
        SELF = new CoreString(context, "self");
        STACK_LEVEL_TOO_DEEP = new CoreString(context, "stack level too deep");
        SUPER = new CoreString(context, "super");
        TIME_INTERVAL_MUST_BE_POS = new CoreString(context, "time interval must be positive");
        TO_ARY = new CoreString(context, "to_ary");
        TO_STR = new CoreString(context, "to_str");
        TOO_FEW_ARGUMENTS = new CoreString(context, "too few arguments");
        TRUE = new CoreString(context, "true");
        TZ = new CoreString(context, "TZ");
        UTC = new CoreString(context, "UTC");
        WRONG_ARGS_ZERO_PLUS_ONE = new CoreString(context, "wrong number of arguments (0 for 1+)");
        X_OUTSIDE_OF_STRING = new CoreString(context, "X outside of string");
        YIELD = new CoreString(context, "yield");
    }

}
