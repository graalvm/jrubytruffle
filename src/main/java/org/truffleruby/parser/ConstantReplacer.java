/*
 * Copyright (c) 2015, 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */

package org.truffleruby.parser;

import com.oracle.truffle.api.source.Source;

/**
 * Some 3rd party code make assumptions about the Ruby runtime based upon the values in some
 * constants. Where the TruffleRuby runtime diverges from the behavior of MRI or JRuby, we may need
 * to replace the value of these constants in order to lead the code down a different branch.
 */
public class ConstantReplacer {

    public static String replacementName(Source source, String name) {
        // The highline gem has special handling for readline, which isn't fully supported in TruffleRuby. Our readline
        // support matches very closely to JRuby's, since we both use jline as the core of the implementation. Highline
        // detects when running on JRuby and alters its readline tests to avoid some problematic areas. Since we offer
        // the same functionality as JRuby (and suffer from the same pitfalls), we pretend that we're JRuby in order
        // to get the same set of workarounds.
        if (source.getName().endsWith("/lib/highline/terminal.rb")) {
            if (name.equals("RUBY_ENGINE")) {
                return name + "_FAKE_JRUBY";
            }
        }

        return name;
    }

}
