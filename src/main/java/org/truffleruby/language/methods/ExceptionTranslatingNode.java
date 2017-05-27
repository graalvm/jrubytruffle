/*
 * Copyright (c) 2014, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.language.methods;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import org.truffleruby.Layouts;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.JavaException;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.control.TruffleFatalException;

import java.util.Arrays;
import java.util.stream.Stream;

public class ExceptionTranslatingNode extends RubyNode {

    private final UnsupportedOperationBehavior unsupportedOperationBehavior;

    @Child private RubyNode child;

    private final BranchProfile controlProfile = BranchProfile.create();
    private final BranchProfile arithmeticProfile = BranchProfile.create();
    private final BranchProfile unsupportedProfile = BranchProfile.create();
    private final BranchProfile errorProfile = BranchProfile.create();

    public ExceptionTranslatingNode(RubyNode child,
                                    UnsupportedOperationBehavior unsupportedOperationBehavior) {
        this.child = child;
        this.unsupportedOperationBehavior = unsupportedOperationBehavior;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            return child.execute(frame);
        } catch (ControlFlowException exception) {
            controlProfile.enter();
            throw exception;
        } catch (ArithmeticException exception) {
            arithmeticProfile.enter();
            throw new RaiseException(translate(exception));
        } catch (UnsupportedSpecializationException exception) {
            unsupportedProfile.enter();
            throw new RaiseException(translate(exception));
        } catch (TruffleFatalException exception) {
            errorProfile.enter();
            throw exception;
        } catch (StackOverflowError error) {
            errorProfile.enter();
            throw new RaiseException(translate(error));
        } catch (ThreadDeath death) {
            errorProfile.enter();
            throw death;
        } catch (IllegalArgumentException e) {
            errorProfile.enter();
            throw new RaiseException(translate(e));
        } catch (Throwable exception) {
            errorProfile.enter();
            throw new RaiseException(translate(exception));
        }
    }

    @TruffleBoundary
    private DynamicObject translate(ArithmeticException exception) {
        if (getContext().getOptions().EXCEPTIONS_PRINT_JAVA) {
            exception.printStackTrace();

            if (getContext().getOptions().EXCEPTIONS_PRINT_RUBY_FOR_JAVA) {
                getContext().getCallStack().printBacktrace(this);
            }
        }

        return coreExceptions().zeroDivisionError(this, exception);
    }

    @TruffleBoundary
    private DynamicObject translate(StackOverflowError error) {
        if (getContext().getOptions().EXCEPTIONS_PRINT_JAVA) {
            error.printStackTrace();

            if (getContext().getOptions().EXCEPTIONS_PRINT_RUBY_FOR_JAVA) {
                getContext().getCallStack().printBacktrace(this);
            }
        }

        return coreExceptions().systemStackErrorStackLevelTooDeep(this, error);
    }

    @TruffleBoundary
    private DynamicObject translate(IllegalArgumentException exception) {
        if (getContext().getOptions().EXCEPTIONS_PRINT_JAVA) {
            exception.printStackTrace();

            if (getContext().getOptions().EXCEPTIONS_PRINT_RUBY_FOR_JAVA) {
                getContext().getCallStack().printBacktrace(this);
            }
        }

        String message = exception.getMessage();

        if (message == null) {
            message = exception.toString();
        }

        return coreExceptions().argumentError(message, this, exception);
    }

    @TruffleBoundary
    private DynamicObject translate(UnsupportedSpecializationException exception) {
        if (getContext().getOptions().EXCEPTIONS_PRINT_JAVA) {
            exception.printStackTrace();

            if (getContext().getOptions().EXCEPTIONS_PRINT_RUBY_FOR_JAVA) {
                getContext().getCallStack().printBacktrace(this);
            }
        }

        final StringBuilder builder = new StringBuilder();
        builder.append("Truffle doesn't have a case for the ");
        builder.append(exception.getNode().getClass().getName());
        builder.append(" node with values of type ");

        for (Object value : exception.getSuppliedValues()) {
            builder.append(" ");

            if (value == null) {
                builder.append("null");
            } else if (value instanceof DynamicObject) {
                final DynamicObject dynamicObject = (DynamicObject) value;

                builder.append(Layouts.MODULE.getFields(Layouts.BASIC_OBJECT.getLogicalClass(dynamicObject)).getName());
                builder.append("(");
                builder.append(value.getClass().getName());
                builder.append(")");

                if (RubyGuards.isRubyArray(value)) {
                    final DynamicObject array = (DynamicObject) value;
                    builder.append("[");

                    if (Layouts.ARRAY.getStore(array) == null) {
                        builder.append("null");
                    } else {
                        builder.append(Layouts.ARRAY.getStore(array).getClass().getName());
                    }

                    builder.append(",");
                    builder.append(Layouts.ARRAY.getSize(array));
                    builder.append("]");
                } else if (RubyGuards.isRubyHash(value)) {
                    final Object store = Layouts.HASH.getStore((DynamicObject) value);

                    if (store == null) {
                        builder.append("[null]");
                    } else {
                        builder.append("[");
                        builder.append(store.getClass().getName());
                        builder.append("]");
                    }
                }
            } else {
                builder.append(value.getClass().getName());
            }

            if (value instanceof Number || value instanceof Boolean) {
                builder.append("=");
                builder.append(value.toString());
            }
        }

        switch (unsupportedOperationBehavior) {
            case TYPE_ERROR:
                return coreExceptions().typeError(builder.toString(), this, exception);
            case ARGUMENT_ERROR:
                return coreExceptions().argumentError(builder.toString(), this, exception);
            default:
                throw new UnsupportedOperationException();
        }
    }

    @TruffleBoundary
    public DynamicObject translate(Throwable throwable) {
        if (throwable instanceof AssertionError && !getContext().getOptions().EXCEPTIONS_TRANSLATE_ASSERT) {
            throw (AssertionError) throwable;
        }

        if (getContext().getOptions().EXCEPTIONS_PRINT_JAVA
                || getContext().getOptions().EXCEPTIONS_PRINT_UNCAUGHT_JAVA) {
            throwable.printStackTrace();

            if (getContext().getOptions().EXCEPTIONS_PRINT_RUBY_FOR_JAVA) {
                getContext().getCallStack().printBacktrace(this);
            }
        }

        Throwable t = throwable;
        if (t instanceof JavaException) {
            t = t.getCause();
        }

        final StringBuilder messageBuilder = new StringBuilder();
        int exceptionCount = 0;

        while (t != null) {
            if (exceptionCount > 0) {
                messageBuilder.append("\n\t\tcaused by ");
            }

            String message = t.getMessage();
            Stream<String> extraLines = Stream.empty();

            if (t.getClass().getSimpleName().equals("SulongRuntimeException")) {
                extraLines = Arrays.stream(message.split("\n")).skip(1).map(line -> line.trim());
                message = "error in C extension";
            }

            if (t instanceof RaiseException) {
                /*
                 * If this is already a Ruby exception caused by a Java exception then the message will already include
                 * multiple lines for each caused-by. However, we print the Java exception on the second line. That's
                 * confusing here, as it breaks the first caused-by. Remove the line break.
                 */

                message = message.replaceFirst("\n", "");
            }

            if (message != null) {
                messageBuilder.append(message);
            } else {
                messageBuilder.append("<no message>");
            }

            if (exceptionCount == 0) {
                messageBuilder.append("\n\t");
            } else {
                messageBuilder.append(" ");
            }

            messageBuilder.append(t.getClass().getSimpleName());

            if (t.getStackTrace().length > 0) {
                messageBuilder.append(" ");
                messageBuilder.append(t.getStackTrace()[0].toString());
            }

            extraLines.forEach(line -> {
                messageBuilder.append("\n\t" + line);
            });

            t = t.getCause();
            exceptionCount++;
        }

        return coreExceptions().internalErrorFullMessage(messageBuilder.toString(), this, throwable);
    }

}
