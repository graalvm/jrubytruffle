/*
 * Copyright (c) 2013, 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.interop;

import com.oracle.truffle.api.interop.TruffleObject;
import org.truffleruby.RubyContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InteropManager {

    private final RubyContext context;

    private final Map<String, TruffleObject> exported = new ConcurrentHashMap<>();

    public InteropManager(RubyContext context) {
        this.context = context;
    }

    public void exportObject(String name, TruffleObject object) {
        exported.put(name, object);
        context.getEnv().exportSymbol(name, object);
    }

    public Object findExportedObject(String name) {
        return exported.get(name);
    }

    public Object importObject(String name) {
        return context.getEnv().importSymbol(name);
    }

}
