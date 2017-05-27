/*
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.options;

public class StringOptionDescription extends OptionDescription<String> {

    private final String defaultValue;

    public StringOptionDescription(String name, String description, String defaultValue) {
        super(name, description);
        this.defaultValue = defaultValue;
    }

    @Override
    public String getDefaultValue() {
        return defaultValue;
    }

    @Override
    public String checkValue(Object value) {
        if (value == null) {
            return null;
        } else {
            return value.toString();
        }
    }

}
