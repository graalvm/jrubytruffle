/*
 * Copyright (c) 2013, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.thread;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;

import jnr.constants.platform.Errno;
import jnr.constants.platform.Signal;
import jnr.posix.DefaultNativeTimeval;
import jnr.posix.Timeval;
import org.truffleruby.Layouts;
import org.truffleruby.Log;
import org.truffleruby.RubyContext;
import org.truffleruby.core.InterruptMode;
import org.truffleruby.core.fiber.FiberManager;
import org.truffleruby.core.fiber.FiberNodes;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.SafepointAction;
import org.truffleruby.language.SafepointManager;
import org.truffleruby.language.backtrace.BacktraceFormatter;
import org.truffleruby.language.control.ExitException;
import org.truffleruby.language.control.JavaException;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.control.ReturnException;
import org.truffleruby.language.control.ThreadExitException;
import org.truffleruby.language.objects.AllocateObjectNode;
import org.truffleruby.language.objects.ReadObjectFieldNode;
import org.truffleruby.language.objects.shared.SharedObjects;
import org.truffleruby.platform.Pointer;
import org.truffleruby.platform.TruffleNFIPlatform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

public class ThreadManager {

    private final RubyContext context;

    private final DynamicObject rootThread;
    private final ThreadLocal<DynamicObject> currentThread = new ThreadLocal<>();

    private final Set<DynamicObject> runningRubyThreads
            = Collections.newSetFromMap(new ConcurrentHashMap<DynamicObject, Boolean>());

    private final Map<Thread, UnblockingAction> unblockingActions = new ConcurrentHashMap<>();
    private static final UnblockingAction EMPTY_UNBLOCKING_ACTION = () -> {
    };

    private final ThreadLocal<UnblockingAction> blockingNativeCallUnblockingAction = new ThreadLocal<>();

    public ThreadManager(RubyContext context) {
        this.context = context;
        this.rootThread = createRubyThread("main");
        if (context.getOptions().NATIVE_INTERRUPT) {
            setupSignalHandler(context);
        }

        start(rootThread);
    }

    private static final InterruptMode DEFAULT_INTERRUPT_MODE = InterruptMode.IMMEDIATE;
    private static final ThreadStatus DEFAULT_STATUS = ThreadStatus.RUN;

    public DynamicObject createRubyThread(String info) {
        final DynamicObject thread = context.getCoreLibrary().getThreadFactory().newInstance(Layouts.THREAD.build(
                createThreadLocals(context),
                DEFAULT_INTERRUPT_MODE,
                DEFAULT_STATUS,
                new ArrayList<>(),
                null,
                new CountDownLatch(1),
                getGlobalAbortOnException(context),
                null,
                null,
                null,
                new AtomicBoolean(false),
                Thread.NORM_PRIORITY,
                context.getCoreLibrary().getNil(),
                info,
                context.getCoreLibrary().getNil()));

        Layouts.THREAD.setFiberManagerUnsafe(thread, new FiberManager(context, thread)); // Because it is cyclic

        return thread;
    }

    public DynamicObject createThread(DynamicObject rubyClass, AllocateObjectNode allocateObjectNode, ReadObjectFieldNode readAbortOnException) {
        final DynamicObject currentGroup = Layouts.THREAD.getThreadGroup(getCurrentThread());
        final boolean abortOnException = (boolean) readAbortOnException.execute(context.getCoreLibrary().getThreadClass());

        final DynamicObject thread = allocateObjectNode.allocate(rubyClass, Layouts.THREAD.build(
                createThreadLocals(context),
                DEFAULT_INTERRUPT_MODE,
                DEFAULT_STATUS,
                new ArrayList<>(),
                null,
                new CountDownLatch(1),
                abortOnException,
                null,
                null,
                null,
                new AtomicBoolean(false),
                Thread.NORM_PRIORITY,
                currentGroup,
                "<uninitialized>",
                context.getCoreLibrary().getNil()));

        Layouts.THREAD.setFiberManagerUnsafe(thread, new FiberManager(context, thread)); // Because it is cyclic

        return thread;
    }

    private static void setupSignalHandler(RubyContext context) {
        TruffleNFIPlatform nfi = context.getNativePlatform().getTruffleNFI();
        if (nfi != null) {
            if (!Signal.SIGVTALRM.defined()) {
                throw new UnsupportedOperationException("SIGVTALRM not defined");
            }

            TruffleObject libC = nfi.getDefaultLibrary();
            // We use abs() as a function taking a int and having no side effects

            TruffleObject abs;

            try {
                abs = nfi.lookup(libC, "abs");
            } catch (JavaException e) {
                if (e.getCause() instanceof UnknownIdentifierException) {
                    Log.LOGGER.warning("not able to set up a native signal handler - maybe the NFI was not available");
                    return;
                }

                throw e;
            }

            TruffleObject sigaction = (TruffleObject) nfi.invoke(nfi.lookup(libC, "sigaction"), "bind", "(SINT32,POINTER,POINTER):SINT32");

            // flags = 0 is OK as we want no SA_RESTART so we can interrupt blocking syscalls.
            Pointer structSigAction = context.getNativePlatform().createSigAction(nfi.asPointer(abs));
            try {
                int result = (int) nfi.execute(sigaction, Signal.SIGVTALRM.intValue(), structSigAction.getAddress(), 0L);
                if (result != 0) {
                    throw new UnsupportedOperationException("sigaction() failed: errno=" + context.getNativePlatform().getPosix().errno());
                }
            } finally {
                structSigAction.free();
            }
        }
    }

    private static boolean getGlobalAbortOnException(RubyContext context) {
        final DynamicObject threadClass = context.getCoreLibrary().getThreadClass();
        return (boolean) ReadObjectFieldNode.read(threadClass, "@abort_on_exception", null);
    }

    private static DynamicObject createThreadLocals(RubyContext context) {
        final DynamicObject threadLocals = Layouts.BASIC_OBJECT.createBasicObject(context.getCoreLibrary().getObjectFactory());
        threadLocals.define("$!", context.getCoreLibrary().getNil());
        threadLocals.define("$?", context.getCoreLibrary().getNil());
        return threadLocals;
    }

    public static void initialize(DynamicObject thread, RubyContext context, Node currentNode, String info, Runnable task) {
        assert RubyGuards.isRubyThread(thread);
        new Thread(() -> run(thread, context, currentNode, info, task)).start();

        FiberNodes.waitForInitialization(context, Layouts.THREAD.getFiberManager(thread).getRootFiber(), currentNode);
    }

    public static void run(DynamicObject thread, RubyContext context, Node currentNode, String info, Runnable task) {
        assert RubyGuards.isRubyThread(thread);

        Layouts.THREAD.setSourceLocation(thread, info);
        final String name = "Ruby Thread@" + info;
        Thread.currentThread().setName(name);

        context.getThreadManager().start(thread);
        try {
            task.run();
        } catch (ThreadExitException e) {
            setThreadValue(context, thread, context.getCoreLibrary().getNil());
        } catch (RaiseException e) {
            setException(context, thread, e.getException(), currentNode);
        } catch (ReturnException e) {
            setException(context, thread, context.getCoreExceptions().unexpectedReturn(currentNode), currentNode);
        } catch (ExitException e) {
            rethrowOnMainThread(context, currentNode, e);
            setThreadValue(context, thread, context.getCoreLibrary().getNil());
        } finally {
            context.getThreadManager().cleanup(thread);
            assert Layouts.THREAD.getValue(thread) != null || Layouts.THREAD.getException(thread) != null;
        }
    }

    private static void rethrowOnMainThread(RubyContext context, Node currentNode, ExitException e) {
        final Thread rootThread = Layouts.FIBER.getThread(Layouts.THREAD.getFiberManager(context.getThreadManager().getRootThread()).getCurrentFiber());
        context.getSafepointManager().pauseThreadAndExecute(rootThread, currentNode, (actionThread, actionCurrentNode) -> {
            throw e;
        });
    }

    private static void setThreadValue(RubyContext context, DynamicObject thread, Object value) {
        // A Thread is always shared (Thread.list)
        SharedObjects.propagate(context, thread, value);
        Layouts.THREAD.setValue(thread, value);
    }

    private static void setException(RubyContext context, DynamicObject thread, DynamicObject exception, Node currentNode) {
        // A Thread is always shared (Thread.list)
        SharedObjects.propagate(context, thread, exception);
        final DynamicObject mainThread = context.getThreadManager().getRootThread();
        final boolean isSystemExit = Layouts.BASIC_OBJECT.getLogicalClass(exception) == context.getCoreLibrary().getSystemExitClass();
        if (thread != mainThread && (isSystemExit || Layouts.THREAD.getAbortOnException(thread))) {
            ThreadNodes.ThreadRaisePrimitiveNode.raiseInThread(context, mainThread, exception, currentNode);
        }
        Layouts.THREAD.setException(thread, exception);
    }

    public void start(DynamicObject thread) {
        Layouts.THREAD.setThread(thread, Thread.currentThread());
        registerThread(thread);

        final DynamicObject rootFiber = Layouts.THREAD.getFiberManager(thread).getRootFiber();
        FiberNodes.start(context, this, rootFiber);
    }

    public void cleanup(DynamicObject thread) {
        assert RubyGuards.isRubyThread(thread);

        // First mark as dead for Thread#status
        Layouts.THREAD.setStatus(thread, ThreadStatus.DEAD);

        final DynamicObject rootFiber = Layouts.THREAD.getFiberManager(thread).getRootFiber();
        FiberNodes.cleanup(context, this, rootFiber);

        unregisterThread(thread);
        Layouts.THREAD.setThread(thread, null);
        assert RubyGuards.isRubyThread(thread);
        for (Lock lock : Layouts.THREAD.getOwnedLocks(thread)) {
            lock.unlock();
        }
        Layouts.THREAD.getFinishedLatch(thread).countDown();
    }

    public static void shutdown(RubyContext context, DynamicObject thread, Node currentNode) {
        assert RubyGuards.isRubyThread(thread);
        Layouts.THREAD.getFiberManager(thread).shutdown();

        if (thread == context.getThreadManager().getRootThread()) {
            throw new RaiseException(context.getCoreExceptions().systemExit(0, currentNode));
        } else {
            Layouts.THREAD.setStatus(thread, ThreadStatus.ABORTING);
            throw new ThreadExitException();
        }
    }

    public DynamicObject getRootThread() {
        return rootThread;
    }

    public interface BlockingAction<T> {
        boolean SUCCESS = true;

        T block() throws InterruptedException;
    }

    public interface BlockingTimeoutAction<T> {
        T block(Timeval timeoutToUse) throws InterruptedException;
    }

    public interface UnblockingAction {
        void unblock();
    }

    /**
     * Runs {@code action} until it returns a non-null value.
     * The given action should throw an {@link InterruptedException} when {@link Thread#interrupt()} is called.
     * Otherwise, the {@link SafepointManager} will not be able to interrupt this action.
     * See {@link ThreadManager#runBlockingSystemCallUntilResult(Node, BlockingAction)} for blocking native calls.
     * If the action throws an {@link InterruptedException},
     * it will be retried until it returns a non-null value.
     *
     * @param action must not touch any Ruby state
     * @return the first non-null return value from {@code action}
     */
    @TruffleBoundary
    public <T> T runUntilResult(Node currentNode, BlockingAction<T> action) {
        final DynamicObject runningThread = getCurrentThread();
        T result = null;

        do {
            final ThreadStatus status = Layouts.THREAD.getStatus(runningThread);
            Layouts.THREAD.setStatus(runningThread, ThreadStatus.SLEEP);

            try {
                try {
                    result = action.block();
                } finally {
                    Layouts.THREAD.setStatus(runningThread, status);
                }
            } catch (InterruptedException e) {
                // We were interrupted, possibly by the SafepointManager.
                context.getSafepointManager().pollFromBlockingCall(currentNode);
            }
        } while (result == null);

        return result;
    }

    /**
     * Runs {@code action} until it returns a non-null value. The blocking action might be
     * {@link Thread#interrupted()}, for instance by the {@link SafepointManager}, in which case it
     * will be run again. The unblocking action is registered with the thread manager and will be
     * invoked if the {@link SafepointManager} needs to interrupt the thread. If the blocking action
     * is making a native call, simply interrupting the thread will not unblock the action. It is
     * the responsibility of the unblocking action to break out of the native call so the thread can
     * be interrupted.
     *
     * @param blockingAction must not touch any Ruby state
     * @param unblockingAction must not touch any Ruby state
     * @return the first non-null return value from {@code action}
     */
    @TruffleBoundary
    public <T> T runUntilResult(Node currentNode, BlockingAction<T> blockingAction, UnblockingAction unblockingAction) {
        assert unblockingAction != null;
        final Thread thread = Thread.currentThread();

        final UnblockingAction oldUnblockingAction = unblockingActions.put(thread, unblockingAction);
        try {
            return runUntilResult(currentNode, blockingAction);
        } finally {
            unblockingActions.put(thread, oldUnblockingAction);
        }
    }

    /**
     * Similar to {@link ThreadManager#runUntilResult(Node, BlockingAction)} but purposed for
     * blocking native calls. If the {@link SafepointManager} needs to interrupt the thread, it will
     * send a SIGVTALRM to abort the blocking syscall which will return with a value < 0 and
     * errno=EINTR.
     */
    @TruffleBoundary
    public int runBlockingSystemCallUntilResult(Node currentNode, BlockingAction<Integer> action) {
        assert Errno.EINTR.defined();
        int EINTR = Errno.EINTR.intValue();

        return runUntilResult(currentNode, () -> {
            int result = action.block();
            if (result < 0 && context.getNativePlatform().getPosix().errno() == EINTR) {
                throw new InterruptedException("EINTR");
            }
            return result;
        }, blockingNativeCallUnblockingAction.get());
    }

    @TruffleBoundary
    public <T> T runUntilSuccessKeepRunStatus(Node currentNode, BlockingAction<T> action) {
        T result = null;

        do {
            try {
                result = action.block();
            } catch (InterruptedException e) {
                // We were interrupted, possibly by the SafepointManager.
                context.getSafepointManager().poll(currentNode);
            }
        } while (result == null);

        return result;
    }

    public interface ResultOrTimeout<T> {
    }

    public static class ResultWithinTime<T> implements ResultOrTimeout<T> {

        private final T value;

        public ResultWithinTime(T value) {
            this.value = value;
        }

        public T getValue() {
            return value;
        }

    }

    public static class TimedOut<T> implements ResultOrTimeout<T> {
    }

    public <T> ResultOrTimeout<T> runUntilTimeout(Node currentNode, int timeoutMicros, final BlockingTimeoutAction<T> action) {
        final Timeval timeoutToUse = new DefaultNativeTimeval(jnr.ffi.Runtime.getSystemRuntime());

        if (timeoutMicros == 0) {
            timeoutToUse.setTime(new long[]{0, 0});

            return new ResultWithinTime<>(runUntilResult(currentNode, () -> action.block(timeoutToUse)));
        } else {
            final int pollTime = 500_000_000;
            final long requestedTimeoutAt = System.nanoTime() + timeoutMicros * 1_000L;

            return runUntilResult(currentNode, new BlockingAction<ResultOrTimeout<T>>() {

                @Override
                public ResultOrTimeout<T> block() throws InterruptedException {
                    final long timeUntilRequestedTimeout = requestedTimeoutAt - System.nanoTime();

                    if (timeUntilRequestedTimeout <= 0) {
                        return new TimedOut<>();
                    }

                    final boolean timeoutForPoll = pollTime <= timeUntilRequestedTimeout;
                    final long effectiveTimeout = Math.min(pollTime, timeUntilRequestedTimeout);
                    final long effectiveTimeoutMicros = effectiveTimeout / 1_000;
                    timeoutToUse.setTime(new long[] {
                            effectiveTimeoutMicros / 1_000_000,
                            effectiveTimeoutMicros % 1_000_000
                    });

                    final T result = action.block(timeoutToUse);

                    if (result == null) {
                        if (timeoutForPoll && (requestedTimeoutAt - System.nanoTime()) > 0) {
                            throw new InterruptedException();
                        } else {
                            return new TimedOut<>();
                        }
                    }

                    return new ResultWithinTime<>(result);
                }

            });
        }
    }

    public void initializeValuesBasedOnCurrentJavaThread(DynamicObject thread, long pThreadID) {
        assert RubyGuards.isRubyThread(thread);
        currentThread.set(thread);

        final int SIGVTALRM = jnr.constants.platform.Signal.SIGVTALRM.intValue();

        blockingNativeCallUnblockingAction.set(() -> {
            context.getNativePlatform().getThreads().pthread_kill(pThreadID, SIGVTALRM);
        });

        unblockingActions.put(Thread.currentThread(), EMPTY_UNBLOCKING_ACTION);
    }

    public void cleanupValuesBasedOnCurrentJavaThread() {
        unblockingActions.remove(Thread.currentThread());
    }

    @TruffleBoundary
    public DynamicObject getCurrentThread() {
        return currentThread.get();
    }

    public synchronized void registerThread(DynamicObject thread) {
        assert RubyGuards.isRubyThread(thread);
        runningRubyThreads.add(thread);

        if (context.getOptions().SHARED_OBJECTS_ENABLED && runningRubyThreads.size() > 1) {
            context.getSharedObjects().startSharing();
            SharedObjects.writeBarrier(context, thread);
        }
    }

    public synchronized void unregisterThread(DynamicObject thread) {
        assert RubyGuards.isRubyThread(thread);
        runningRubyThreads.remove(thread);
        currentThread.set(null);
    }

    @TruffleBoundary
    public void shutdown() {
        try {
            if (runningRubyThreads.size() > 1) {
                killOtherThreads();
            }
        } finally {
            Layouts.THREAD.getFiberManager(rootThread).shutdown();
            cleanup(rootThread);
        }
    }

    @TruffleBoundary
    public Object[] getThreadList() {
        return runningRubyThreads.toArray(new Object[runningRubyThreads.size()]);
    }

    @TruffleBoundary
    public Iterable<DynamicObject> iterateThreads() {
        return runningRubyThreads;
    }

    @TruffleBoundary
    private void killOtherThreads() {
        while (true) {
            try {
                context.getSafepointManager().pauseAllThreadsAndExecute(null, false, new SafepointAction() {
                    @Override
                    public synchronized void accept(DynamicObject thread, Node currentNode) {
                        if (thread != rootThread && Thread.currentThread() == Layouts.THREAD.getThread(thread)) {
                            shutdown(context, thread, currentNode);
                        }
                    }
                });
                break; // Successfully executed the safepoint and sent the exceptions.
            } catch (RaiseException e) {
                final DynamicObject rubyException = e.getException();
                BacktraceFormatter.createDefaultFormatter(context).printBacktrace(context, rubyException, Layouts.EXCEPTION.getBacktrace(rubyException));
            }
        }
    }

    @TruffleBoundary
    public void interrupt(Thread thread) {
        final UnblockingAction action = unblockingActions.get(thread);

        if (action != null) {
            action.unblock();
        }

        thread.interrupt();
    }

}
