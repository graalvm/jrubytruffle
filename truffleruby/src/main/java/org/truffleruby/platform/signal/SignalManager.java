/*
 * Copyright (c) 2015, 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.platform.signal;

import jnr.constants.platform.Signal;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public interface SignalManager {

    Set<String> RUBY_18_SIGNALS = get18Signals();

    static Set<String> get18Signals() {
        Set<String> RUBY_18_SIGNALS = new HashSet<>();
        for (String name : new String[] {
                "EXIT",
                "HUP",
                "INT",
                "QUIT",
                "ILL",
                "TRAP",
                "IOT",
                "ABRT",
                "EMT",
                "FPE",
                "KILL",
                "BUS",
                "SEGV",
                "SYS",
                "PIPE",
                "ALRM",
                "TERM",
                "URG",
                "STOP",
                "TSTP",
                "CONT",
                "CHLD",
                "CLD",
                "TTIN",
                "TTOU",
                "IO",
                "XCPU",
                "XFSZ",
                "VTALRM",
                "PROF",
                "WINCH",
                "USR1",
                "USR2",
                "LOST",
                "MSG",
                "PWR",
                "POLL",
                "DANGER",
                "MIGRATE",
                "PRE",
                "GRANT",
                "RETRACT",
                "SOUND",
                "INFO",
        }) {
            RUBY_18_SIGNALS.add(name);
        }
        return RUBY_18_SIGNALS;
    }

    Map<String, Integer> SIGNALS_LIST = Collections.unmodifiableMap(list());

    SignalHandler IGNORE_HANDLER = signal -> {
        // Just ignore the signal.
    };

    org.truffleruby.platform.signal.Signal createSignal(String name);

    void watchSignal(org.truffleruby.platform.signal.Signal signal, SignalHandler newHandler) throws IllegalArgumentException;

    void watchDefaultForSignal(org.truffleruby.platform.signal.Signal signal) throws IllegalArgumentException;

    void handle(final org.truffleruby.platform.signal.Signal signal, final SignalHandler newHandler) throws IllegalArgumentException;

    void handleDefault(final org.truffleruby.platform.signal.Signal signal) throws IllegalArgumentException;

    void raise(org.truffleruby.platform.signal.Signal signal) throws IllegalArgumentException;

    static Map<String, Integer> list() {
        Map<String, Integer> signals = new HashMap<>();

        for (Signal s : Signal.values()) {
            if (!s.defined())
                continue;

            final String name = signmWithoutPrefix(s.description());
            if (!RUBY_18_SIGNALS.contains(name))
                continue;

            int signo = s.intValue();
            // omit unsupported signals
            if (signo >= 20000)
                continue;

            signals.put(name, signo);
        }

        if (!Signal.SIGCLD.defined() && Signal.SIGCHLD.defined()) {
            signals.put("CLD", Signal.SIGCHLD.intValue());
        }

        return signals;
    }

    static String signmWithoutPrefix(String nm) {
        return (nm.startsWith(SIGNAME_PREFIX)) ? nm.substring(SIGNAME_PREFIX.length()) : nm;
    }

    String SIGNAME_PREFIX = "SIG";

}
