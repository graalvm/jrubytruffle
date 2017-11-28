/*
 * Copyright (c) 2015, 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.platform.sunmisc;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import jnr.constants.platform.Signal;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

@SuppressWarnings("restriction")
public class SunMiscSignalManager {

    private static final Set<String> RUBY_SIGNALS = new HashSet<>(Arrays.asList(new String[]{
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
    }));

    public static final Map<String, Integer> SIGNALS_LIST = Collections.unmodifiableMap(list());

    public static final Consumer<sun.misc.Signal> IGNORE_HANDLER = signal -> {
        // Just ignore the signal.
    };

    private static Map<String, Integer> list() {
        Map<String, Integer> signals = new HashMap<>();

        for (Signal s : Signal.values()) {
            if (!s.defined())
                continue;

            final String name = signameWithoutPrefix(s.description());
            if (!RUBY_SIGNALS.contains(name))
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

    private static String signameWithoutPrefix(String signame) {
        return signame.startsWith("SIG") ? signame.substring(3) : signame;
    }

    private final ConcurrentMap<sun.misc.Signal, sun.misc.SignalHandler> DEFAULT_HANDLERS = new ConcurrentHashMap<>();

    public void watchSignal(sun.misc.Signal signal, Consumer<sun.misc.Signal> newHandler) throws IllegalArgumentException {
        handle(signal, newHandler);
    }

    public void watchDefaultForSignal(sun.misc.Signal signal) throws IllegalArgumentException {
        handleDefault(signal);
    }

    @TruffleBoundary
    public void handle(final sun.misc.Signal signal, final Consumer<sun.misc.Signal> newHandler) throws IllegalArgumentException {
        final sun.misc.SignalHandler oldSunHandler = sun.misc.Signal.handle(
                signal, wrapHandler(signal, newHandler));

        DEFAULT_HANDLERS.putIfAbsent(signal, oldSunHandler);
    }

    @TruffleBoundary
    public void handleDefault(final sun.misc.Signal signal) throws IllegalArgumentException {
        final sun.misc.SignalHandler defaultHandler = DEFAULT_HANDLERS.get(signal);
        if (defaultHandler != null) { // otherwise it is already the default signal
            sun.misc.Signal.handle(signal, defaultHandler);
        }
    }

    @TruffleBoundary
    private sun.misc.SignalHandler wrapHandler(final sun.misc.Signal signal, final Consumer<sun.misc.Signal> newHandler) {
        return wrappedSignal -> newHandler.accept(signal);
    }

    @TruffleBoundary
    public void raise(sun.misc.Signal signal) throws IllegalArgumentException {
        sun.misc.Signal.raise((signal));
    }

}
