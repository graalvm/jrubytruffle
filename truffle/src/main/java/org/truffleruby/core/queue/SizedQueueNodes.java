/*
 * Copyright (c) 2015, 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.queue;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CreateCast;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import org.truffleruby.Layouts;
import org.truffleruby.builtins.CoreClass;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.CoreMethodNode;
import org.truffleruby.core.cast.BooleanCastWithDefaultNodeGen;
import org.truffleruby.core.thread.ThreadManager.BlockingAction;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.Visibility;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.objects.AllocateObjectNode;
import org.truffleruby.language.objects.shared.PropagateSharingNode;

/**
 * We do not reuse much of class Queue since we need to be able to replace the queue in this case
 * and methods are small anyway.
 */
@CoreClass("SizedQueue")
public abstract class SizedQueueNodes {

    @CoreMethod(names = "allocate", constructor = true)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Child private AllocateObjectNode allocateNode = AllocateObjectNode.create();

        @Specialization
        public DynamicObject allocate(DynamicObject rubyClass) {
            Object queue = null;
            return allocateNode.allocate(rubyClass, queue);
        }

    }

    @CoreMethod(names = "initialize", visibility = Visibility.PRIVATE, required = 1, lowerFixnum = 1)
    public abstract static class InitializeNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        public DynamicObject initialize(DynamicObject self, int capacity) {
            if (capacity <= 0) {
                throw new RaiseException(coreExceptions().argumentError("queue size must be positive", this));
            }

            final SizedQueue blockingQueue = new SizedQueue(capacity);
            Layouts.SIZED_QUEUE.setQueue(self, blockingQueue);
            return self;
        }

    }

    @CoreMethod(names = "max=", required = 1, lowerFixnum = 1)
    public abstract static class SetMaxNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        public int setMax(DynamicObject self, int newCapacity) {
            if (newCapacity <= 0) {
                throw new RaiseException(coreExceptions().argumentError("queue size must be positive", this));
            }

            final SizedQueue queue = Layouts.SIZED_QUEUE.getQueue(self);
            queue.changeCapacity(newCapacity);
            return newCapacity;
        }

    }

    @CoreMethod(names = "max")
    public abstract static class MaxNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        public int max(DynamicObject self) {
            final SizedQueue queue = Layouts.SIZED_QUEUE.getQueue(self);
            return queue.getCapacity();
        }

    }

    @CoreMethod(names = { "push", "<<", "enq" }, required = 1, optional = 1)
    @NodeChildren({
            @NodeChild(type = RubyNode.class, value = "queue"),
            @NodeChild(type = RubyNode.class, value = "value"),
            @NodeChild(type = RubyNode.class, value = "nonBlocking")
    })
    public abstract static class PushNode extends CoreMethodNode {

        @Child PropagateSharingNode propagateSharingNode = PropagateSharingNode.create();

        @CreateCast("nonBlocking")
        public RubyNode coerceToBoolean(RubyNode nonBlocking) {
            return BooleanCastWithDefaultNodeGen.create(false, nonBlocking);
        }

        @Specialization(guards = "!nonBlocking")
        public DynamicObject pushBlocking(DynamicObject self, final Object value, boolean nonBlocking) {
            final SizedQueue queue = Layouts.SIZED_QUEUE.getQueue(self);

            propagateSharingNode.propagate(self, value);
            doPushBlocking(value, queue);

            return self;
        }

        @TruffleBoundary
        private void doPushBlocking(final Object value, final SizedQueue queue) {
            getContext().getThreadManager().runUntilResult(this, new BlockingAction<Boolean>() {
                @Override
                public Boolean block() throws InterruptedException {
                    queue.put(value);
                    return SUCCESS;
                }
            });
        }

        @Specialization(guards = "nonBlocking")
        public DynamicObject pushNonBlock(DynamicObject self, final Object value, boolean nonBlocking,
                @Cached("create()") BranchProfile errorProfile) {
            final SizedQueue queue = Layouts.SIZED_QUEUE.getQueue(self);

            propagateSharingNode.propagate(self, value);
            final boolean pushed = doOffer(value, queue);
            if (!pushed) {
                errorProfile.enter();
                throw new RaiseException(coreExceptions().threadError("queue full", this));
            }

            return self;
        }

        @TruffleBoundary
        private boolean doOffer(final Object value, final SizedQueue queue) {
            return queue.offer(value);
        }

    }

    @CoreMethod(names = { "pop", "shift", "deq" }, optional = 1)
    @NodeChildren({
            @NodeChild(type = RubyNode.class, value = "queue"),
            @NodeChild(type = RubyNode.class, value = "nonBlocking")
    })
    public abstract static class PopNode extends CoreMethodNode {

        @CreateCast("nonBlocking")
        public RubyNode coerceToBoolean(RubyNode nonBlocking) {
            return BooleanCastWithDefaultNodeGen.create(false, nonBlocking);
        }

        @Specialization(guards = "!nonBlocking")
        public Object popBlocking(DynamicObject self, boolean nonBlocking) {
            final SizedQueue queue = Layouts.SIZED_QUEUE.getQueue(self);

            return doPop(queue);
        }

        @TruffleBoundary
        private Object doPop(final SizedQueue queue) {
            return getContext().getThreadManager().runUntilResult(this, () -> queue.take());
        }

        @Specialization(guards = "nonBlocking")
        public Object popNonBlock(DynamicObject self, boolean nonBlocking,
                @Cached("create()") BranchProfile errorProfile) {
            final SizedQueue queue = Layouts.SIZED_QUEUE.getQueue(self);

            final Object value = doPoll(queue);
            if (value == null) {
                errorProfile.enter();
                throw new RaiseException(coreExceptions().threadError("queue empty", this));
            }

            return value;
        }

        @TruffleBoundary
        private Object doPoll(final SizedQueue queue) {
            return queue.poll();
        }

    }

    @CoreMethod(names = "empty?")
    public abstract static class EmptyNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        public boolean empty(DynamicObject self) {
            final SizedQueue queue = Layouts.SIZED_QUEUE.getQueue(self);
            return queue.isEmpty();
        }

    }

    @CoreMethod(names = { "size", "length" })
    public abstract static class SizeNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        public int size(DynamicObject self) {
            final SizedQueue queue = Layouts.SIZED_QUEUE.getQueue(self);
            return queue.size();
        }

    }

    @CoreMethod(names = "clear")
    public abstract static class ClearNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        public DynamicObject clear(DynamicObject self) {
            final SizedQueue queue = Layouts.SIZED_QUEUE.getQueue(self);
            queue.clear();
            return self;
        }

    }

    @CoreMethod(names = "num_waiting")
    public abstract static class NumWaitingNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        public int num_waiting(DynamicObject self) {
            final SizedQueue queue = Layouts.SIZED_QUEUE.getQueue(self);
            return queue.getNumberWaiting();
        }

    }

}
