/*
 * Copyright (c) 2013, 2018 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.symbol;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectFactory;
import org.jcodings.specific.USASCIIEncoding;
import org.jcodings.specific.UTF8Encoding;
import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.core.Hashing;
import org.truffleruby.core.rope.Rope;
import org.truffleruby.core.rope.RopeKey;
import org.truffleruby.core.rope.RopeOperations;
import org.truffleruby.core.rope.RopeCache;
import org.truffleruby.core.rope.StringKey;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.parser.Identifiers;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SymbolTable {

    private final RopeCache ropeCache;
    private final DynamicObjectFactory symbolFactory;
    private final Hashing hashing;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // Cache searches based on String
    private final Map<StringKey, Reference<DynamicObject>> stringSymbolMap = new WeakHashMap<>();
    // Cache searches based on Rope
    private final Map<RopeKey, Reference<DynamicObject>> ropeSymbolMap = new WeakHashMap<>();
    // Weak set of Symbols, SymbolEquality implements equality based on inner rope, to be able to
    // deduplicate symbols
    private final Map<SymbolEquality, Reference<DynamicObject>> symbolSet = new WeakHashMap<>();

    public SymbolTable(RopeCache ropeCache, DynamicObjectFactory symbolFactory, Hashing hashing) {
        this.ropeCache = ropeCache;
        this.symbolFactory = symbolFactory;
        this.hashing = hashing;
    }

    @TruffleBoundary
    public DynamicObject getSymbol(String string) {
        final StringKey stringKey = new StringKey(string, hashing);
        lock.readLock().lock();
        DynamicObject symbol;
        try {
            symbol = readRef(stringSymbolMap, stringKey);
            if (symbol != null) {
                return symbol;
            }
        } finally {
            lock.readLock().unlock();
        }

        lock.writeLock().lock();
        try {
            symbol = readRef(stringSymbolMap, stringKey);
            if (symbol != null) {
                return symbol;
            }

            final Rope rope;
            if (StringOperations.isASCIIOnly(string)) {
                rope = RopeOperations.encodeAscii(string, USASCIIEncoding.INSTANCE);
            } else {
                rope = StringOperations.encodeRope(string, UTF8Encoding.INSTANCE);
            }

            symbol = getDeduplicatedSymbol(rope);

            stringSymbolMap.put(stringKey, new WeakReference<>(symbol));
        } finally {
            lock.writeLock().unlock();
        }

        return symbol;
    }

    @TruffleBoundary
    public DynamicObject getSymbol(Rope rope) {
        final RopeKey ropeKey = new RopeKey(rope, hashing);
        lock.readLock().lock();
        DynamicObject symbol;
        try {
            symbol = readRef(ropeSymbolMap, ropeKey);
            if (symbol != null) {
                return symbol;
            }
        } finally {
            lock.readLock().unlock();
        }

        lock.writeLock().lock();
        try {
            symbol = readRef(ropeSymbolMap, ropeKey);
            if (symbol != null) {
                return symbol;
            }

            final Rope flatRope = RopeOperations.flatten(rope);
            symbol = getDeduplicatedSymbol(flatRope);

            ropeSymbolMap.put(new RopeKey(flatRope, hashing), new WeakReference<>(symbol));
        } finally {
            lock.writeLock().unlock();
        }

        return symbol;
    }

    private DynamicObject getDeduplicatedSymbol(Rope rope) {
        final DynamicObject newSymbol = createSymbol(rope);
        final SymbolEquality newKey = Layouts.SYMBOL.getEqualityWrapper(newSymbol);
        final DynamicObject currentSymbol = readRef(symbolSet, newKey);

        if (currentSymbol == null) {
            symbolSet.put(newKey, new WeakReference<>(newSymbol));
            return newSymbol;
        } else {
            return currentSymbol;
        }
    }

    private static final int CLASS_SALT = 92021474; // random number, stops hashes for similar values but different classes being the same, static because we want deterministic hashes

    private DynamicObject createSymbol(Rope rope) {
        final String string = RopeOperations.decodeRope(rope);
        // Symbol has to have reference to its SymbolEquality otherwise it would be GCed.
        final SymbolEquality equalityWrapper = new SymbolEquality();
        final DynamicObject symbol = Layouts.SYMBOL.createSymbol(
                symbolFactory,
                string,
                ropeCache.getRope(rope),
                hashing.hash(CLASS_SALT, string.hashCode()),
                equalityWrapper);

        equalityWrapper.setSymbol(symbol);
        return symbol;
    }

    private <K, V> V readRef(Map<K, Reference<V>> map, K key) {
        Reference<V> reference = map.get(key);
        return reference == null ? null : reference.get();
    }

    @TruffleBoundary
    public Collection<DynamicObject> allSymbols() {
        final Collection<Reference<DynamicObject>> symbolReferences;

        lock.readLock().lock();
        try {
            symbolReferences = symbolSet.values();
        } finally {
            lock.readLock().unlock();
        }

        final Collection<DynamicObject> symbols = new ArrayList<>(symbolReferences.size());

        for (Reference<DynamicObject> reference : symbolReferences) {
            final DynamicObject symbol = reference.get();

            if (symbol != null) {
                symbols.add(symbol);
            }
        }

        return symbols;
    }

    // TODO (eregon, 10/10/2015): this check could be done when a Symbol is created to be much cheaper
    @TruffleBoundary(transferToInterpreterOnException = false)
    public static String checkInstanceVariableName(
            RubyContext context,
            String name,
            Object receiver,
            Node currentNode) {
        if (!Identifiers.isValidInstanceVariableName(name)) {
            throw new RaiseException(context.getCoreExceptions().nameErrorInstanceNameNotAllowable(
                    name,
                    receiver,
                    currentNode));
        }
        return name;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public static String checkClassVariableName(
            RubyContext context,
            String name,
            Object receiver,
            Node currentNode) {
        if (!Identifiers.isValidClassVariableName(name)) {
            throw new RaiseException(context.getCoreExceptions().nameErrorInstanceNameNotAllowable(
                    name,
                    receiver,
                    currentNode));
        }
        return name;
    }

}
