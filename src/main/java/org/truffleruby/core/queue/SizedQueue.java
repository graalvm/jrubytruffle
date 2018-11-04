/*
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.queue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class SizedQueue {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition canAdd = lock.newCondition();
    private final Condition canTake = lock.newCondition();

    private Object[] items;
    private int addEnd;
    private int takeEnd;
    private int capacity;
    private int size;
    private boolean closed;

    public SizedQueue(int capacity) {
        this.capacity = capacity;
        items = new Object[capacity];
    }

    public int getCapacity() {
        return capacity;
    }

    @TruffleBoundary
    public void changeCapacity(int capacity) {
        lock.lock();

        try {
            this.capacity = capacity;

            if (capacity <= items.length) {
                return;
            }

            final Object[] newItems = new Object[capacity];

            for (int n = 0; n < size; n++) {
                newItems[n] = items[takeEnd];
                takeEnd++;
                if (takeEnd == items.length) {
                    takeEnd = 0;
                }
            }

            items = newItems;
            addEnd = size;
            takeEnd = 0;

            canAdd.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public enum OfferResult {
        SUCCESS,
        FULL,
        CLOSED
    }

    @TruffleBoundary
    public OfferResult offer(Object item) {
        lock.lock();

        try {
            if (size == capacity) {
                if (closed) {
                    return OfferResult.CLOSED;
                } else {
                    return OfferResult.FULL;
                }
            } else {
                if (doAdd(item)) {
                    return OfferResult.SUCCESS;
                } else {
                    return OfferResult.CLOSED;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @TruffleBoundary
    public boolean put(Object item) throws InterruptedException {
        lock.lock();

        try {
            while (size == capacity) {
                if (closed) {
                    return false;
                }

                canAdd.await();
            }

            return doAdd(item);
        } finally {
            lock.unlock();
        }
    }

    private boolean doAdd(Object item) {
        if (closed) {
            return false;
        }

        items[addEnd] = item;
        addEnd++;
        if (addEnd == items.length) {
            addEnd = 0;
        }
        size++;
        if (size >= 1) {
            canTake.signal();
        }

        return true;
    }

    @TruffleBoundary
    public Object poll() {
        lock.lock();

        try {
            if (size == 0) {
                return null;
            } else {
                return doTake();
            }
        } finally {
            lock.unlock();
        }
    }

    public static final Object CLOSED = new Object();

    @TruffleBoundary
    public Object take() throws InterruptedException {
        lock.lock();

        try {
            while (size == 0) {
                if (closed) {
                    return CLOSED;
                }

                canTake.await();
            }

            return doTake();
        } finally {
            lock.unlock();
        }
    }

    private Object doTake() {
        final Object item = items[takeEnd];
        takeEnd++;
        if (takeEnd == items.length) {
            takeEnd = 0;
        }
        size--;
        if (size < capacity) {
            canAdd.signal();
        }
        return item;
    }

    @TruffleBoundary
    public void clear() {
        lock.lock();

        try {
            if (size != 0) {
                size = 0;
                addEnd = 0;
                takeEnd = 0;
                canAdd.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    @TruffleBoundary
    public int size() {
        lock.lock();

        try {
            return size;
        } finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    @TruffleBoundary
    public int getNumberWaiting() {
        lock.lock();

        try {
            return lock.getWaitQueueLength(canTake) + lock.getWaitQueueLength(canAdd);
        } finally {
            lock.unlock();
        }
    }

    @TruffleBoundary
    public Collection<Object> getContents() {
        final Collection<Object> objects = new ArrayList<>();

        lock.lock();
        try {
            int takePoint = takeEnd;

            for (int n = 0; n < size; n++) {
                objects.add(items[takePoint]);
                takePoint++;
                if (takePoint == items.length) {
                    takePoint = 0;
                }
            }
        } finally {
            lock.unlock();
        }

        return objects;
    }

    public void close() {
        lock.lock();

        try {
            closed = true;
        } finally {
            lock.unlock();
        }
    }

    public boolean isClosed() {
        lock.lock();

        try {
            return closed;
        } finally {
            lock.unlock();
        }
    }

}
