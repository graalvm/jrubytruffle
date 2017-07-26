/*
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.mutex;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CreateCast;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.Layouts;
import org.truffleruby.builtins.CoreClass;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.CoreMethodNode;
import org.truffleruby.builtins.UnaryCoreMethodNode;
import org.truffleruby.core.cast.DurationToMillisecondsNodeGen;
import org.truffleruby.core.kernel.KernelNodes;
import org.truffleruby.core.thread.GetCurrentRubyThreadNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.Visibility;
import org.truffleruby.language.objects.AllocateObjectNode;

import java.util.concurrent.locks.ReentrantLock;

@CoreClass("Mutex")
public abstract class MutexNodes {

    @CoreMethod(names = "__allocate__", constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Child private AllocateObjectNode allocateNode = AllocateObjectNode.create();

        @Specialization
        public DynamicObject allocate(DynamicObject rubyClass) {
            return allocateNode.allocate(rubyClass, new ReentrantLock());
        }

    }

    @CoreMethod(names = "lock")
    public abstract static class LockNode extends UnaryCoreMethodNode {

        @Specialization
        public DynamicObject lock(VirtualFrame frame, DynamicObject mutex,
                @Cached("create()") GetCurrentRubyThreadNode getCurrentRubyThreadNode) {
            final ReentrantLock lock = Layouts.MUTEX.getLock(mutex);
            final DynamicObject thread = getCurrentRubyThreadNode.executeGetRubyThread(frame);
            MutexOperations.lock(lock, thread, this);
            return mutex;
        }

    }

    @CoreMethod(names = "locked?")
    public abstract static class IsLockedNode extends UnaryCoreMethodNode {

        @Specialization
        public boolean isLocked(DynamicObject mutex) {
            return Layouts.MUTEX.getLock(mutex).isLocked();
        }

    }

    @CoreMethod(names = "owned?")
    public abstract static class IsOwnedNode extends UnaryCoreMethodNode {

        @Specialization
        public boolean isOwned(DynamicObject mutex) {
            return Layouts.MUTEX.getLock(mutex).isHeldByCurrentThread();
        }

    }

    @CoreMethod(names = "try_lock")
    public abstract static class TryLockNode extends UnaryCoreMethodNode {

        @Specialization
        public boolean tryLock(VirtualFrame frame, DynamicObject mutex,
                @Cached("create()") GetCurrentRubyThreadNode getCurrentRubyThreadNode,
                @Cached("createBinaryProfile()") ConditionProfile heldByCurrentThreadProfile) {
            final ReentrantLock lock = Layouts.MUTEX.getLock(mutex);
            final DynamicObject thread = getCurrentRubyThreadNode.executeGetRubyThread(frame);

            if (heldByCurrentThreadProfile.profile(lock.isHeldByCurrentThread())) {
                return false;
            } else {
                return doTryLock(thread, lock);
            }
        }

        @TruffleBoundary
        private boolean doTryLock(DynamicObject thread, ReentrantLock lock) {
            if (lock.tryLock()) {
                Layouts.THREAD.getOwnedLocks(thread).add(lock);
                return true;
            } else {
                return false;
            }
        }

    }

    @CoreMethod(names = "unlock")
    public abstract static class UnlockNode extends UnaryCoreMethodNode {

        @Specialization
        public DynamicObject unlock(VirtualFrame frame, DynamicObject mutex,
                @Cached("create()") GetCurrentRubyThreadNode getCurrentRubyThreadNode) {
            final ReentrantLock lock = Layouts.MUTEX.getLock(mutex);
            final DynamicObject thread = getCurrentRubyThreadNode.executeGetRubyThread(frame);

            MutexOperations.unlock(lock, thread, this);
            return mutex;
        }

    }

    @NodeChildren({
            @NodeChild(value = "mutex", type = RubyNode.class),
            @NodeChild(value = "duration", type = RubyNode.class)
    })
    @CoreMethod(names = "sleep", optional = 1)
    public abstract static class SleepNode extends CoreMethodNode {

        @CreateCast("duration")
        public RubyNode coerceDuration(RubyNode duration) {
            return DurationToMillisecondsNodeGen.create(true, duration);
        }

        @Specialization
        public long sleep(VirtualFrame frame, DynamicObject mutex, long durationInMillis,
                @Cached("create()") GetCurrentRubyThreadNode getCurrentRubyThreadNode) {
            final ReentrantLock lock = Layouts.MUTEX.getLock(mutex);
            final DynamicObject thread = getCurrentRubyThreadNode.executeGetRubyThread(frame);

            /*
             * Clear the wakeUp flag, following Ruby semantics:
             * it should only be considered if we are inside the sleep when Thread#{run,wakeup} is called.
             * Here we do it before unlocking for providing nice semantics for
             * thread1: mutex.sleep
             * thread2: mutex.synchronize { <ensured that thread1 is sleeping and thread1.wakeup will wake it up> }
             */

            Layouts.THREAD.getWakeUp(thread).set(false);

            MutexOperations.unlock(lock, thread, this);
            try {
                return KernelNodes.SleepNode.sleepFor(this, getContext(), thread, durationInMillis);
            } finally {
                MutexOperations.lockEvenWithExceptions(lock, thread, this);
            }
        }

    }

}
