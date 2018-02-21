/*
 * Copyright (c) 2013, 2018 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.rope;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.jcodings.Encoding;
import org.truffleruby.collections.WeakValuedMap;
import org.truffleruby.core.Hashing;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RopeCache {

    private final Hashing hashing;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final WeakValuedMap<BytesKey, Rope> bytesToRope = new WeakValuedMap<>();

    private int byteArrayReusedCount;
    private int ropesReusedCount;
    private int ropeBytesSaved;

    public RopeCache(Hashing hashing) {
        this.hashing = hashing;
    }

    public Rope getRope(Rope string) {
        return getRope(string.getBytes(), string.getEncoding(), string.getCodeRange());
    }

    public Rope getRope(Rope string, CodeRange codeRange) {
        return getRope(string.getBytes(), string.getEncoding(), codeRange);
    }

    @TruffleBoundary
    public Rope getRope(byte[] bytes, Encoding encoding, CodeRange codeRange) {
        assert encoding != null;

        final BytesKey key = new BytesKey(bytes, encoding, hashing);

        lock.readLock().lock();
        try {
            final Rope rope = bytesToRope.get(key);
            if (rope != null) {
                ++ropesReusedCount;
                ropeBytesSaved += rope.byteLength();

                return rope;
            }
        } finally {
            lock.readLock().unlock();
        }

        lock.writeLock().lock();
        try {
            final Rope ropeInCache = bytesToRope.get(key);
            if (ropeInCache != null) {
                return ropeInCache;
            }

            // At this point, we were unable to find a rope with the same bytes and encoding (i.e., a direct match).
            // However, there may still be a rope with the same byte[] and sharing a direct byte[] can still allow some
            // reference equality optimizations. So, do another search but with a marker encoding. The only guarantee
            // we can make about the resulting rope is that it would have the same logical byte[], but that's good enough
            // for our purposes.
            final Rope ropeWithSameBytesButDifferentEncoding = bytesToRope.get(new BytesKey(bytes, null, hashing));

            final Rope rope;
            if (ropeWithSameBytesButDifferentEncoding != null) {
                rope = RopeOperations.create(ropeWithSameBytesButDifferentEncoding.getBytes(), encoding, codeRange);

                ++byteArrayReusedCount;
                ropeBytesSaved += rope.byteLength();
            } else {
                rope = RopeOperations.create(bytes, encoding, codeRange);
            }

            bytesToRope.put(key, rope);

            return rope;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean contains(Rope rope) {
        final BytesKey key = new BytesKey(rope.getBytes(), rope.getEncoding(), hashing);

        lock.readLock().lock();
        try {
            return bytesToRope.get(key) != null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getByteArrayReusedCount() {
        return byteArrayReusedCount;
    }

    public int getRopesReusedCount() {
        return ropesReusedCount;
    }

    public int getRopeBytesSaved() {
        return ropeBytesSaved;
    }

    public int totalRopes() {
        return bytesToRope.size();
    }

}
