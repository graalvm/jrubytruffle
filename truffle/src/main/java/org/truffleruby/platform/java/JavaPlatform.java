/*
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.platform.java;

import jnr.ffi.provider.MemoryManager;
import jnr.posix.POSIXFactory;
import org.truffleruby.RubyContext;
import org.truffleruby.platform.DefaultRubiniusConfiguration;
import org.truffleruby.platform.FDSet;
import org.truffleruby.platform.NativePlatform;
import org.truffleruby.platform.ProcessName;
import org.truffleruby.platform.RubiniusConfiguration;
import org.truffleruby.platform.linux.LinuxRubiniusConfiguration;
import org.truffleruby.platform.posix.ClockGetTime;
import org.truffleruby.platform.posix.MallocFree;
import org.truffleruby.platform.posix.PosixFDSet4Bytes;
import org.truffleruby.platform.posix.Sockets;
import org.truffleruby.platform.posix.TrufflePosix;
import org.truffleruby.platform.posix.TrufflePosixHandler;
import org.truffleruby.platform.signal.SignalManager;
import org.truffleruby.platform.sunmisc.SunMiscSignalManager;

public class JavaPlatform implements NativePlatform {

    private final TrufflePosix posix;
    private final MemoryManager memoryManager;
    private final SignalManager signalManager;
    private final ProcessName processName;
    private final Sockets sockets;
    private final ClockGetTime clockGetTime;
    private final RubiniusConfiguration rubiniusConfiguration;

    public JavaPlatform(RubyContext context) {
        posix = new JavaTrufflePosix(context, POSIXFactory.getJavaPOSIX(new TrufflePosixHandler(context)));
        memoryManager = new JavaMemoryManager();
        signalManager = new SunMiscSignalManager();
        processName = new JavaProcessName();
        sockets = new JavaSockets();
        clockGetTime = new JavaClockGetTime();
        rubiniusConfiguration = new RubiniusConfiguration();
        DefaultRubiniusConfiguration.load(rubiniusConfiguration, context);
        LinuxRubiniusConfiguration.load(rubiniusConfiguration, context); // Just load the Linux one - let errors happen later
    }

    @Override
    public TrufflePosix getPosix() {
        return posix;
    }

    @Override
    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    @Override
    public SignalManager getSignalManager() {
        return signalManager;
    }

    @Override
    public ProcessName getProcessName() {
        return processName;
    }

    @Override
    public Sockets getSockets() {
        return sockets;
    }

    @Override
    public ClockGetTime getClockGetTime() {
        return clockGetTime;
    }

    @Override
    public MallocFree getMallocFree() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RubiniusConfiguration getRubiniusConfiguration() {
        return rubiniusConfiguration;
    }

    @Override
    public FDSet createFDSet() {
        return new PosixFDSet4Bytes();
    }

}
