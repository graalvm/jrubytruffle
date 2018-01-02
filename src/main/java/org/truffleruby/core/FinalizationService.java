/*
 * Copyright (c) 2013, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.truffleruby.RubyContext;
import org.truffleruby.core.thread.ThreadManager;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.control.TerminationException;

import com.oracle.truffle.api.object.DynamicObject;

public class FinalizationService {

    private static class Finalizer {

        private final Class<?> owner;
        private final Runnable action;
        private final List<DynamicObject> roots;

        public Finalizer(Class<?> owner, Runnable action, List<DynamicObject> roots) {
            this.owner = owner;
            this.action = action;
            this.roots = roots;
        }

        public Class<?> getOwner() {
            return owner;
        }

        public Runnable getAction() {
            return action;
        }

        public Stream<DynamicObject> getRoots() {
            return roots.stream();
        }
    }

    private static class FinalizerReference extends WeakReference<Object> {

        public List<Finalizer> finalizers = new LinkedList<>();

        public FinalizerReference(Object object, ReferenceQueue<? super Object> queue) {
            super(object, queue);
        }

        public void addFinalizer(Class<?> owner, Runnable action, List<DynamicObject> roots) {
            finalizers.add(new Finalizer(owner, action, roots));
        }

        public void removeFinalizers(Class<?> owner) {
            finalizers.removeIf(f -> f.getOwner() == owner);
        }

        public List<Runnable> getFinalizerActions() {
            return finalizers.stream().map(Finalizer::getAction).collect(Collectors.toList());
        }

        public Stream<DynamicObject> getRoots() {
            return finalizers.stream().flatMap(Finalizer::getRoots);
        }
    }

    private final RubyContext context;

    private final Map<Object, FinalizerReference> finalizerReferences = new WeakHashMap<>();
    private final ReferenceQueue<Object> finalizerQueue = new ReferenceQueue<>();

    private DynamicObject finalizerThread;

    public FinalizationService(RubyContext context) {
        this.context = context;
    }

    public void addFinalizer(Object object, Class<?> owner, Runnable action) {
        addFinalizer(object, owner, action, Collections.emptyList());
    }

    public synchronized void addFinalizer(Object object, Class<?> owner, Runnable action, List<DynamicObject> roots) {
        FinalizerReference finalizerReference = finalizerReferences.get(object);

        if (finalizerReference == null) {
            finalizerReference = new FinalizerReference(object, finalizerQueue);
            finalizerReferences.put(object, finalizerReference);
        }

        finalizerReference.addFinalizer(owner, action, roots);

        if (context.getLanguage().SINGLE_THREADED) {

            drainFinalizationQueue();

        } else {

            /*
             * We can't create a new thread while the context is initializing or finalizing, as the
             * polyglot API locks on creating new threads, and some core loading does things such as
             * stat files which could allocate memory that is marked to be automatically freed and so
             * would want to start the finalization thread. So don't start the finalization thread if we
             * are initializing. We will rely on some other finalizer to be created to ever free this
             * memory allocated during startup, but that's a reasonable assumption and a low risk of
             * leaking a tiny number of bytes if it doesn't hold.
             */

            if (finalizerThread == null && context.isInitialized() && !context.isFinalizing()) {
                createFinalizationThread();
            }

        }
    }

    private final void drainFinalizationQueue() {
        while (true) {
            final FinalizerReference finalizerReference = (FinalizerReference) finalizerQueue.poll();

            if (finalizerReference == null) {
                break;
            }

            runFinalizer(finalizerReference);
        }
    }

    private void createFinalizationThread() {
        final ThreadManager threadManager = context.getThreadManager();
        finalizerThread = threadManager.createBootThread("finalizer");
        context.send(finalizerThread, "internal_thread_initialize", null);

        threadManager.initialize(finalizerThread, null, "finalizer", () -> {
            while (true) {
                final FinalizerReference finalizerReference = (FinalizerReference) threadManager.runUntilResult(null,
                        finalizerQueue::remove);

                runFinalizer(finalizerReference);
            }
        });
    }

    private void runFinalizer(FinalizerReference finalizerReference) {
        try {
            finalizerReference.getFinalizerActions().forEach(Runnable::run);
        } catch (TerminationException e) {
            throw e;
        } catch (RaiseException e) {
            context.getCoreExceptions().showExceptionIfDebug(e.getException());
        } catch (Exception e) {
            // Do nothing, the finalizer thread must continue to process objects.
            if (context.getCoreLibrary().getDebug() == Boolean.TRUE) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void removeFinalizers(Object object, Class<?> owner) {
        final FinalizerReference finalizerReference = finalizerReferences.get(object);

        if (finalizerReference != null) {
            finalizerReference.removeFinalizers(owner);
        }
    }

    public synchronized Stream<DynamicObject> getRoots() {
        return finalizerReferences.values().stream().flatMap(FinalizerReference::getRoots);
    }

}
