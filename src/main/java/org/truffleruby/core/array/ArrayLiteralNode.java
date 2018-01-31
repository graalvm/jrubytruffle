/*
 * Copyright (c) 2013, 2018 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.array;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.object.DynamicObject;
import org.truffleruby.Layouts;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.objects.AllocateObjectNode;
import org.truffleruby.language.objects.AllocateObjectNodeGen;

public abstract class ArrayLiteralNode extends RubyNode {

    public static ArrayLiteralNode create(RubyNode[] values) {
        return new UninitialisedArrayLiteralNode(values);
    }

    @Children protected final RubyNode[] values;
    @Child private AllocateObjectNode allocateObjectNode;

    public ArrayLiteralNode(RubyNode[] values) {
        this.values = values;
    }

    protected DynamicObject makeGeneric(VirtualFrame frame, Object[] alreadyExecuted) {
        CompilerAsserts.neverPartOfCompilation();

        final ArrayLiteralNode newNode = new ObjectArrayLiteralNode(values);
        newNode.unsafeSetSourceSection(getSourceIndexLength());
        replace(newNode);

        final Object[] executedValues = new Object[values.length];

        for (int n = 0; n < values.length; n++) {
            if (n < alreadyExecuted.length) {
                executedValues[n] = alreadyExecuted[n];
            } else {
                executedValues[n] = values[n].execute(frame);
            }
        }

        return createArray(executedValues, executedValues.length);
    }

    @Override
    protected DynamicObject createArray(Object store, int size) {
        if (allocateObjectNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            allocateObjectNode = insert(AllocateObjectNodeGen.create(false, null, null));
        }
        return allocateObjectNode.allocate(coreLibrary().getArrayClass(), store, size);
    }

    @Override
    public abstract Object execute(VirtualFrame frame);

    @ExplodeLoop
    @Override
    public void doExecuteVoid(VirtualFrame frame) {
        for (RubyNode value : values) {
            value.doExecuteVoid(frame);
        }
    }

    @ExplodeLoop
    @Override
    public Object isDefined(VirtualFrame frame) {
        for (RubyNode value : values) {
            if (value.isDefined(frame) == nil()) {
                return nil();
            }
        }

        return super.isDefined(frame);
    }

    public int getSize() {
        return values.length;
    }

    public RubyNode stealNode(int index) {
        final RubyNode node = values[index];
        // Nullify it here so we make sure it's only referenced by the caller.
        values[index] = null;
        return node;
    }

    private static class EmptyArrayLiteralNode extends ArrayLiteralNode {

        public EmptyArrayLiteralNode(RubyNode[] values) {
            super(values);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return createArray(null, 0);
        }

    }

    private static class FloatArrayLiteralNode extends ArrayLiteralNode {

        public FloatArrayLiteralNode(RubyNode[] values) {
            super(values);
        }

        @ExplodeLoop
        @Override
        public Object execute(VirtualFrame frame) {
            final double[] executedValues = new double[values.length];

            for (int n = 0; n < values.length; n++) {
                final Object value = values[n].execute(frame);
                if (value instanceof Double) {
                    executedValues[n] = (double) value;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    return makeGeneric(frame, executedValues, n);
                }
            }

            return createArray(executedValues, values.length);
        }

        private DynamicObject makeGeneric(VirtualFrame frame, final double[] executedValues, int n) {
            final Object[] executedObjects = new Object[n];

            for (int i = 0; i < n; i++) {
                executedObjects[i] = executedValues[i];
            }

            return makeGeneric(frame, executedObjects);
        }

    }

    private static class IntegerArrayLiteralNode extends ArrayLiteralNode {

        public IntegerArrayLiteralNode(RubyNode[] values) {
            super(values);
        }

        @ExplodeLoop
        @Override
        public Object execute(VirtualFrame frame) {
            final int[] executedValues = new int[values.length];

            for (int n = 0; n < values.length; n++) {
                final Object value = values[n].execute(frame);
                if (value instanceof Integer) {
                    executedValues[n] = (int) value;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    return makeGeneric(frame, executedValues, n);
                }
            }

            return createArray(executedValues, values.length);
        }

        private DynamicObject makeGeneric(VirtualFrame frame, final int[] executedValues, int n) {
            final Object[] executedObjects = new Object[n];

            for (int i = 0; i < n; i++) {
                executedObjects[i] = executedValues[i];
            }

            return makeGeneric(frame, executedObjects);
        }

    }

    private static class LongArrayLiteralNode extends ArrayLiteralNode {

        public LongArrayLiteralNode(RubyNode[] values) {
            super(values);
        }

        @ExplodeLoop
        @Override
        public Object execute(VirtualFrame frame) {
            final long[] executedValues = new long[values.length];

            for (int n = 0; n < values.length; n++) {
                final Object value = values[n].execute(frame);
                if (value instanceof Long) {
                    executedValues[n] = (long) value;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    return makeGeneric(frame, executedValues, n);
                }
            }

            return createArray(executedValues, values.length);
        }

        private DynamicObject makeGeneric(VirtualFrame frame, final long[] executedValues, int n) {
            final Object[] executedObjects = new Object[n];

            for (int i = 0; i < n; i++) {
                executedObjects[i] = executedValues[i];
            }

            return makeGeneric(frame, executedObjects);
        }

    }

    private static class ObjectArrayLiteralNode extends ArrayLiteralNode {

        public ObjectArrayLiteralNode(RubyNode[] values) {
            super(values);
        }

        @ExplodeLoop
        @Override
        public Object execute(VirtualFrame frame) {
            final Object[] executedValues = new Object[values.length];

            for (int n = 0; n < values.length; n++) {
                executedValues[n] = values[n].execute(frame);
            }

            return createArray(executedValues, values.length);
        }

    }

    private static class UninitialisedArrayLiteralNode extends ArrayLiteralNode {

        public UninitialisedArrayLiteralNode(RubyNode[] values) {
            super(values);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            CompilerDirectives.transferToInterpreterAndInvalidate();

            final Object[] executedValues = new Object[values.length];

            for (int n = 0; n < values.length; n++) {
                executedValues[n] = values[n].execute(frame);
            }

            final DynamicObject array = createArray(storeSpecialisedFromObjects(executedValues), executedValues.length);
            final Object store = Layouts.ARRAY.getStore(array);

            final RubyNode newNode;

            if (store == null) {
                newNode = new EmptyArrayLiteralNode(values);
            } else if (store instanceof int[]) {
                newNode = new IntegerArrayLiteralNode(values);
            } else if (store instanceof long[]) {
                newNode = new LongArrayLiteralNode(values);
            } else if (store instanceof double[]) {
                newNode = new FloatArrayLiteralNode(values);
            } else {
                newNode = new ObjectArrayLiteralNode(values);
            }

            newNode.unsafeSetSourceSection(getSourceIndexLength());
            replace(newNode);

            return array;
        }

        public Object storeSpecialisedFromObjects(Object... objects) {
            if (objects.length == 0) {
                return null;
            }

            boolean canUseInteger = true;
            boolean canUseLong = true;
            boolean canUseDouble = true;

            for (Object object : objects) {
                if (object instanceof Integer) {
                    canUseDouble = false;
                } else if (object instanceof Long) {
                    canUseInteger = canUseInteger && CoreLibrary.fitsIntoInteger((long) object);
                    canUseDouble = false;
                } else if (object instanceof Double) {
                    canUseInteger = false;
                    canUseLong = false;
                } else {
                    canUseInteger = false;
                    canUseLong = false;
                    canUseDouble = false;
                }
            }

            if (canUseInteger) {
                final int[] store = new int[objects.length];

                for (int n = 0; n < objects.length; n++) {
                    final Object object = objects[n];
                    if (object instanceof Integer) {
                        store[n] = (int) object;
                    } else if (object instanceof Long) {
                        store[n] = (int) (long) object;
                    } else {
                        throw new UnsupportedOperationException();
                    }
                }

                return store;
            } else if (canUseLong) {
                final long[] store = new long[objects.length];

                for (int n = 0; n < objects.length; n++) {
                    final Object object = objects[n];
                    if (object instanceof Integer) {
                        store[n] = (int) object;
                    } else if (object instanceof Long) {
                        store[n] = (long) object;
                    } else {
                        throw new UnsupportedOperationException();
                    }
                }

                return store;
            } else if (canUseDouble) {
                final double[] store = new double[objects.length];

                for (int n = 0; n < objects.length; n++) {
                    store[n] = CoreLibrary.toDouble(objects[n], nil());
                }

                return store;
            } else {
                return objects;
            }
        }

    }
}
