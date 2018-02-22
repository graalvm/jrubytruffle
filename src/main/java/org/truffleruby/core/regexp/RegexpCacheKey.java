/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.regexp;

import org.jcodings.Encoding;
import org.truffleruby.core.Hashing;
import org.truffleruby.core.rope.NativeRope;
import org.truffleruby.core.rope.Rope;

public class RegexpCacheKey {

    private final Rope rope;
    private final Encoding encoding;
    private final int options;
    private final int hashCode;

    public RegexpCacheKey(Rope rope, Encoding encoding, int options, Hashing hashing) {
        assert !(rope instanceof NativeRope);
        this.rope = rope;
        this.encoding = encoding;
        this.options = options;
        this.hashCode = hashing.hash(rope.hashCode());
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof RegexpCacheKey) {
            final RegexpCacheKey other = (RegexpCacheKey) o;
            return rope.equals(other.rope) && encoding == other.encoding && options == other.options;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return rope.toString();
    }
}
