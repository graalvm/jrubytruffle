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

public class IntegerOptionDescription extends OptionDescription<Integer> {

    private final int defaultValue;

    public IntegerOptionDescription(String name, String description, int defaultValue) {
        super(name, description);
        this.defaultValue = defaultValue;
    }

    @Override
    public Integer getDefaultValue() {
        return defaultValue;
    }

    @Override
    public Integer checkValue(Object value) {
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                throw new OptionTypeException(getName(), value.toString());
            }
        } else {
            throw new OptionTypeException(getName(), value.toString());
        }
    }

}
