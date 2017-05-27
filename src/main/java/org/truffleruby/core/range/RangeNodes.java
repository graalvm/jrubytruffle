/*
 * Copyright (c) 2013, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.range;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CreateCast;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.object.DynamicObject;
import org.truffleruby.Layouts;
import org.truffleruby.builtins.CoreClass;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.CoreMethodNode;
import org.truffleruby.builtins.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.builtins.UnaryCoreMethodNode;
import org.truffleruby.builtins.YieldingCoreMethodNode;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.array.ArrayBuilderNode;
import org.truffleruby.core.cast.BooleanCastWithDefaultNodeGen;
import org.truffleruby.core.cast.ToIntNode;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.Visibility;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.CallDispatchHeadNode;
import org.truffleruby.language.dispatch.DispatchHeadNodeFactory;
import org.truffleruby.language.objects.AllocateObjectNode;


@CoreClass("Range")
public abstract class RangeNodes {

    @CoreMethod(names = { "map", "collect" }, needsBlock = true)
    public abstract static class MapNode extends YieldingCoreMethodNode {

        @Specialization(guards = "isIntRange(range)")
        public DynamicObject map(DynamicObject range, DynamicObject block,
                @Cached("create()") ArrayBuilderNode arrayBuilder) {
            final int begin = Layouts.INT_RANGE.getBegin(range);
            final int end = Layouts.INT_RANGE.getEnd(range);
            final boolean excludedEnd = Layouts.INT_RANGE.getExcludedEnd(range);
            final int direction = begin < end ? +1 : -1;
            final int length = Math.abs((excludedEnd ? end : end + direction) - begin);

            Object store = arrayBuilder.start(length);
            int count = 0;

            try {
                for (int n = 0; n < length; n++) {
                    if (CompilerDirectives.inInterpreter()) {
                        count++;
                    }

                    store = arrayBuilder.appendValue(store, n, yield(block, begin + direction * n));
                }
            } finally {
                if (CompilerDirectives.inInterpreter()) {
                    LoopNode.reportLoopCount(this, count);
                }
            }

            return createArray(arrayBuilder.finish(store, length), length);
        }

    }

    @CoreMethod(names = "each", needsBlock = true, enumeratorSize = "size")
    public abstract static class EachNode extends YieldingCoreMethodNode {

        @Child private CallDispatchHeadNode eachInternalCall;

        @Specialization(guards = "isIntRange(range)")
        public Object eachInt(DynamicObject range, DynamicObject block) {
            int result;
            if (Layouts.INT_RANGE.getExcludedEnd(range)) {
                result = Layouts.INT_RANGE.getEnd(range);
            } else {
                result = Layouts.INT_RANGE.getEnd(range) + 1;
            }
            final int exclusiveEnd = result;

            int count = 0;

            try {
                for (int n = Layouts.INT_RANGE.getBegin(range); n < exclusiveEnd; n++) {
                    if (CompilerDirectives.inInterpreter()) {
                        count++;
                    }

                    yield(block, n);
                }
            } finally {
                if (CompilerDirectives.inInterpreter()) {
                    LoopNode.reportLoopCount(this, count);
                }
            }

            return range;
        }

        @Specialization(guards = "isLongRange(range)")
        public Object eachLong(DynamicObject range, DynamicObject block) {
            long result;
            if (Layouts.LONG_RANGE.getExcludedEnd(range)) {
                result = Layouts.LONG_RANGE.getEnd(range);
            } else {
                result = Layouts.LONG_RANGE.getEnd(range) + 1;
            }
            final long exclusiveEnd = result;

            int count = 0;

            try {
                for (long n = Layouts.LONG_RANGE.getBegin(range); n < exclusiveEnd; n++) {
                    if (CompilerDirectives.inInterpreter()) {
                        count++;
                    }

                    yield(block, n);
                }
            } finally {
                if (CompilerDirectives.inInterpreter()) {
                    LoopNode.reportLoopCount(this, count);
                }
            }

            return range;
        }

        private Object eachInternal(VirtualFrame frame, DynamicObject range, DynamicObject block) {
            if (eachInternalCall == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                eachInternalCall = insert(DispatchHeadNodeFactory.createMethodCall());
            }

            return eachInternalCall.callWithBlock(frame, range, "each_internal", block);
        }

        @Specialization(guards = "isLongRange(range)")
        public Object eachObject(VirtualFrame frame, DynamicObject range, NotProvided block) {
            return eachInternal(frame, range, null);
        }

        @Specialization(guards = "isObjectRange(range)")
        public Object each(VirtualFrame frame, DynamicObject range, NotProvided block) {
            return eachInternal(frame, range, null);
        }

        @Specialization(guards = "isObjectRange(range)")
        public Object each(VirtualFrame frame, DynamicObject range, DynamicObject block) {
            return eachInternal(frame, range, block);
        }

    }

    @CoreMethod(names = "exclude_end?")
    public abstract static class ExcludeEndNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isIntRange(range)")
        public boolean excludeEndInt(DynamicObject range) {
            return Layouts.INT_RANGE.getExcludedEnd(range);
        }

        @Specialization(guards = "isLongRange(range)")
        public boolean excludeEndLong(DynamicObject range) {
            return Layouts.LONG_RANGE.getExcludedEnd(range);
        }

        @Specialization(guards = "isObjectRange(range)")
        public boolean excludeEndObject(DynamicObject range) {
            return Layouts.OBJECT_RANGE.getExcludedEnd(range);
        }

    }

    @CoreMethod(names = "begin")
    public abstract static class BeginNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isIntRange(range)")
        public int eachInt(DynamicObject range) {
            return Layouts.INT_RANGE.getBegin(range);
        }

        @Specialization(guards = "isLongRange(range)")
        public long eachLong(DynamicObject range) {
            return Layouts.LONG_RANGE.getBegin(range);
        }

        @Specialization(guards = "isObjectRange(range)")
        public Object eachObject(DynamicObject range) {
            return Layouts.OBJECT_RANGE.getBegin(range);
        }

    }

    @CoreMethod(names = { "dup", "clone" })
    public abstract static class DupNode extends UnaryCoreMethodNode {

        @Child private AllocateObjectNode allocateObjectNode = AllocateObjectNode.create();

        @Specialization(guards = "isIntRange(range)")
        public DynamicObject dupIntRange(DynamicObject range) {
            return Layouts.INT_RANGE.createIntRange(
                    coreLibrary().getIntRangeFactory(),
                    Layouts.INT_RANGE.getExcludedEnd(range),
                    Layouts.INT_RANGE.getBegin(range),
                    Layouts.INT_RANGE.getEnd(range));
        }

        @Specialization(guards = "isLongRange(range)")
        public DynamicObject dupLongRange(DynamicObject range) {
            return Layouts.LONG_RANGE.createLongRange(
                    coreLibrary().getIntRangeFactory(),
                    Layouts.LONG_RANGE.getExcludedEnd(range),
                    Layouts.LONG_RANGE.getBegin(range),
                    Layouts.LONG_RANGE.getEnd(range));
        }

        @Specialization(guards = "isObjectRange(range)")
        public DynamicObject dup(DynamicObject range) {
            DynamicObject copy = allocateObjectNode.allocate(
                    Layouts.BASIC_OBJECT.getLogicalClass(range),
                    Layouts.OBJECT_RANGE.getExcludedEnd(range),
                    Layouts.OBJECT_RANGE.getBegin(range),
                    Layouts.OBJECT_RANGE.getEnd(range));
            return copy;
        }

    }

    @CoreMethod(names = "end")
    public abstract static class EndNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isIntRange(range)")
        public int lastInt(DynamicObject range) {
            return Layouts.INT_RANGE.getEnd(range);
        }

        @Specialization(guards = "isLongRange(range)")
        public long lastLong(DynamicObject range) {
            return Layouts.LONG_RANGE.getEnd(range);
        }

        @Specialization(guards = "isObjectRange(range)")
        public Object lastObject(DynamicObject range) {
            return Layouts.OBJECT_RANGE.getEnd(range);
        }

    }

    @CoreMethod(names = "step", needsBlock = true, optional = 1, lowerFixnum = 1, returnsEnumeratorIfNoBlock = true)
    public abstract static class StepNode extends YieldingCoreMethodNode {

        @Child private CallDispatchHeadNode stepInternalCall;

        @Specialization(guards = { "isIntRange(range)", "step > 0" })
        public Object stepInt(DynamicObject range, int step, DynamicObject block) {
            int count = 0;

            try {
                int result;
                if (Layouts.INT_RANGE.getExcludedEnd(range)) {
                    result = Layouts.INT_RANGE.getEnd(range);
                } else {
                    result = Layouts.INT_RANGE.getEnd(range) + 1;
                }
                for (int n = Layouts.INT_RANGE.getBegin(range); n < result; n += step) {
                    if (CompilerDirectives.inInterpreter()) {
                        count++;
                    }

                    yield(block, n);
                }
            } finally {
                if (CompilerDirectives.inInterpreter()) {
                    LoopNode.reportLoopCount(this, count);
                }
            }

            return range;
        }

        @Specialization(guards = { "isLongRange(range)", "step > 0" })
        public Object stepLong(DynamicObject range, int step, DynamicObject block) {
            int count = 0;

            try {
                long result;
                if (Layouts.LONG_RANGE.getExcludedEnd(range)) {
                    result = Layouts.LONG_RANGE.getEnd(range);
                } else {
                    result = Layouts.LONG_RANGE.getEnd(range) + 1;
                }
                for (long n = Layouts.LONG_RANGE.getBegin(range); n < result; n += step) {
                    if (CompilerDirectives.inInterpreter()) {
                        count++;
                    }

                    yield(block, n);
                }
            } finally {
                if (CompilerDirectives.inInterpreter()) {
                    LoopNode.reportLoopCount(this, count);
                }
            }

            return range;
        }

        @Fallback
        public Object stepFallback(VirtualFrame frame, Object range, Object step, Object block) {
            if (stepInternalCall == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                stepInternalCall = insert(DispatchHeadNodeFactory.createMethodCall());
            }

            if (step instanceof NotProvided) {
                step = 1;
            }

            final DynamicObject blockProc;
            if (RubyGuards.wasProvided(block)) {
                blockProc = (DynamicObject) block;
            } else {
                blockProc = null;
            }

            return stepInternalCall.callWithBlock(frame, range, "step_internal", blockProc, step);
        }

    }

    @CoreMethod(names = "to_a")
    public abstract static class ToANode extends CoreMethodArrayArgumentsNode {

        @Child private CallDispatchHeadNode toAInternalCall;

        @Specialization(guards = "isIntRange(range)")
        public DynamicObject toA(DynamicObject range) {
            final int begin = Layouts.INT_RANGE.getBegin(range);
            int result;
            if (Layouts.INT_RANGE.getExcludedEnd(range)) {
                result = Layouts.INT_RANGE.getEnd(range);
            } else {
                result = Layouts.INT_RANGE.getEnd(range) + 1;
            }
            final int length = result - begin;

            if (length < 0) {
                return createArray(null, 0);
            } else {
                final int[] values = new int[length];

                for (int n = 0; n < length; n++) {
                    values[n] = begin + n;
                }

                return createArray(values, length);
            }
        }

        @Specialization(guards = "isObjectRange(range)")
        public Object toA(VirtualFrame frame, DynamicObject range) {
            if (toAInternalCall == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toAInternalCall = insert(DispatchHeadNodeFactory.createMethodCall());
            }

            return toAInternalCall.call(frame, range, "to_a_internal");
        }

    }

    @Primitive(name = "range_to_int_range")
    public abstract static class ToIntRangeNode extends PrimitiveArrayArgumentsNode {

        @Child private ToIntNode toIntNode;

        @Specialization(guards = "isIntRange(range)")
        public DynamicObject intRange(DynamicObject range) {
            return range;
        }

        @Specialization(guards = "isLongRange(range)")
        public DynamicObject longRange(VirtualFrame frame, DynamicObject range) {
            int begin = toInt(frame, Layouts.LONG_RANGE.getBegin(range));
            int end = toInt(frame, Layouts.LONG_RANGE.getEnd(range));
            boolean excludedEnd = Layouts.LONG_RANGE.getExcludedEnd(range);
            return Layouts.INT_RANGE.createIntRange(coreLibrary().getIntRangeFactory(), excludedEnd, begin, end);
        }

        @Specialization(guards = "isObjectRange(range)")
        public DynamicObject objectRange(VirtualFrame frame, DynamicObject range) {
            int begin = toInt(frame, Layouts.OBJECT_RANGE.getBegin(range));
            int end = toInt(frame, Layouts.OBJECT_RANGE.getEnd(range));
            boolean excludedEnd = Layouts.OBJECT_RANGE.getExcludedEnd(range);
            return Layouts.INT_RANGE.createIntRange(coreLibrary().getIntRangeFactory(), excludedEnd, begin, end);
        }

        private int toInt(VirtualFrame frame, Object indexObject) {
            if (toIntNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toIntNode = insert(ToIntNode.create());
            }
            return toIntNode.doInt(frame, indexObject);
        }

    }

    @Primitive(name = "range_initialize")
    public abstract static class InitializeNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "isObjectRange(range)")
        public boolean setExcludeEnd(DynamicObject range, Object begin, Object end, boolean excludeEnd) {
            Layouts.OBJECT_RANGE.setBegin(range, begin);
            Layouts.OBJECT_RANGE.setEnd(range, end);
            Layouts.OBJECT_RANGE.setExcludedEnd(range, excludeEnd);
            return excludeEnd;
        }

    }

    @CoreMethod(names = "new", constructor = true, required = 2, optional = 1)
    @NodeChildren({
            @NodeChild(type = RubyNode.class, value = "rubyClass"),
            @NodeChild(type = RubyNode.class, value = "begin"),
            @NodeChild(type = RubyNode.class, value = "end"),
            @NodeChild(type = RubyNode.class, value = "excludeEnd")
    })
    public abstract static class NewNode extends CoreMethodNode {

        protected final DynamicObject rangeClass = getContext().getCoreLibrary().getRangeClass();

        @Child private CallDispatchHeadNode cmpNode;
        @Child private AllocateObjectNode allocateNode;

        @CreateCast("excludeEnd")
        public RubyNode coerceToBoolean(RubyNode excludeEnd) {
            return BooleanCastWithDefaultNodeGen.create(false, excludeEnd);
        }

        @Specialization(guards = "rubyClass == rangeClass")
        public DynamicObject intRange(DynamicObject rubyClass, int begin, int end, boolean excludeEnd) {
            return Layouts.INT_RANGE.createIntRange(
                    coreLibrary().getIntRangeFactory(),
                    excludeEnd,
                    begin,
                    end);
        }

        @Specialization(guards = { "rubyClass == rangeClass", "fitsIntoInteger(begin)", "fitsIntoInteger(end)" })
        public DynamicObject longFittingIntRange(DynamicObject rubyClass, long begin, long end, boolean excludeEnd) {
            return Layouts.INT_RANGE.createIntRange(
                    coreLibrary().getIntRangeFactory(),
                    excludeEnd,
                    (int) begin,
                    (int) end);
        }

        @Specialization(guards = { "rubyClass == rangeClass", "!fitsIntoInteger(begin) || !fitsIntoInteger(end)" })
        public DynamicObject longRange(DynamicObject rubyClass, long begin, long end, boolean excludeEnd) {
            return Layouts.LONG_RANGE.createLongRange(
                    coreLibrary().getLongRangeFactory(),
                    excludeEnd,
                    begin,
                    end);
        }

        @Specialization(guards = { "rubyClass != rangeClass || (!isIntOrLong(begin) || !isIntOrLong(end))" })
        public Object objectRange(
                VirtualFrame frame,
                DynamicObject rubyClass,
                Object begin,
                Object end,
                boolean excludeEnd) {
            if (cmpNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cmpNode = insert(DispatchHeadNodeFactory.createMethodCall());
            }
            if (allocateNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                allocateNode = insert(AllocateObjectNode.create());
            }

            final Object cmpResult;
            try {
                cmpResult = cmpNode.call(frame, begin, "<=>", end);
            } catch (RaiseException e) {
                throw new RaiseException(coreExceptions().argumentError("bad value for range", this));
            }

            if (cmpResult == nil()) {
                throw new RaiseException(coreExceptions().argumentError("bad value for range", this));
            }

            return allocateNode.allocate(rubyClass, excludeEnd, begin, end);
        }

        protected boolean fitsIntoInteger(long value) {
            return CoreLibrary.fitsIntoInteger(value);
        }

        protected boolean isIntOrLong(Object value) {
            return RubyGuards.isInteger(value) || RubyGuards.isLong(value);
        }

    }

    @CoreMethod(names = "__allocate__", constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends UnaryCoreMethodNode {

        @Child private AllocateObjectNode allocateNode = AllocateObjectNode.create();

        @Specialization
        public DynamicObject allocate(DynamicObject rubyClass) {
            return allocateNode.allocate(rubyClass, false, nil(), nil());
        }

    }

}
