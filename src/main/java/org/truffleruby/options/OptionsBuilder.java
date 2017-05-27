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

import org.truffleruby.Log;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

public class OptionsBuilder {

    public static final String PREFIX = "truffleruby.";

    public static final Collection<String> ignoredProperties = Arrays.asList(
            PREFIX + "metrics.memory_used_on_exit",
            PREFIX + "metrics.time"
    );

    private final Map<OptionDescription<?>, Object> options = new HashMap<>();

    public void set(Properties properties) {
        for (Map.Entry<Object, Object> property : properties.entrySet()) {
            final String name = (String) property.getKey();

            if (name.startsWith(PREFIX) && !ignoredProperties.contains(name)) {
                set(name.substring(PREFIX.length()), property.getValue());
            }
        }
    }

    public void set(Map<String, Object> properties) {
        for (Map.Entry<String, Object> property : properties.entrySet()) {
            set(property.getKey(), property.getValue());
        }
    }

    private void set(String name, Object value) {
        final OptionDescription<?> description = OptionsCatalog.fromName(name);

        if (description == null) {
            throw new UnknownOptionException(name);
        }

        options.put(description, description.checkValue(value));
    }

    public Options build() {
        final Options options = new Options(this);

        if (options.OPTIONS_LOG && Log.LOGGER.isLoggable(Level.CONFIG)) {
            for (OptionDescription<?> option : OptionsCatalog.allDescriptions()) {
                Log.LOGGER.config("option " + option.getName() + "=" + option.toString(options.fromDescription(option)));
            }
        }

        return options;
    }

    <T> T getOrDefault(OptionDescription<T> description) {
        final T value = description.cast(options.get(description));

        if (value == null) {
            return description.getDefaultValue();
        } else {
            return value;
        }
    }

    <T> T getOrDefault(OptionDescription<T> description, T defaultValue) {
        final T value = description.cast(options.get(description));

        if (value == null) {
            return defaultValue;
        } else {
            return value;
        }
    }

}
