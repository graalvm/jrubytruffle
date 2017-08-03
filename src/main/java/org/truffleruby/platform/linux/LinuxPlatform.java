/*
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.platform.linux;

import jnr.ffi.LibraryLoader;
import jnr.ffi.Runtime;
import jnr.ffi.provider.MemoryManager;
import jnr.posix.POSIXFactory;
import org.truffleruby.RubyContext;
import org.truffleruby.platform.DefaultRubiniusConfiguration;
import org.truffleruby.platform.FDSet;
import org.truffleruby.platform.NativePlatform;
import org.truffleruby.platform.ProcessName;
import org.truffleruby.platform.RubiniusConfiguration;
import org.truffleruby.platform.TruffleNFIPlatform;
import org.truffleruby.platform.java.JavaProcessName;
import org.truffleruby.platform.posix.ClockGetTime;
import org.truffleruby.platform.posix.JNRTrufflePosix;
import org.truffleruby.platform.posix.NoopThreads;
import org.truffleruby.platform.posix.Threads;
import org.truffleruby.platform.posix.PosixFDSet4Bytes;
import org.truffleruby.platform.posix.Sockets;
import org.truffleruby.platform.posix.TrufflePosix;
import org.truffleruby.platform.posix.TrufflePosixHandler;
import org.truffleruby.platform.signal.SignalManager;
import org.truffleruby.platform.sunmisc.SunMiscSignalManager;

public class LinuxPlatform implements NativePlatform {

    private final TruffleNFIPlatform nfi;
    private final TrufflePosix posix;
    private final MemoryManager memoryManager;
    private final SignalManager signalManager;
    private final ProcessName processName;
    private final Sockets sockets;
    private final Threads threads;
    private final ClockGetTime clockGetTime;
    private final RubiniusConfiguration rubiniusConfiguration;

    public LinuxPlatform(RubyContext context) {
        nfi = context.getOptions().NATIVE_INTERRUPT ? new TruffleNFIPlatform(context) : null;
        posix = new JNRTrufflePosix(context, POSIXFactory.getNativePOSIX(new TrufflePosixHandler(context)));
        memoryManager = Runtime.getSystemRuntime().getMemoryManager();
        signalManager = new SunMiscSignalManager();
        processName = new JavaProcessName();
        sockets = LibraryLoader.create(Sockets.class).library("libc.so.6").load();
        threads = context.getOptions().NATIVE_INTERRUPT ? LibraryLoader.create(Threads.class).library("libc.so.6").library("libpthread.so.0").load() : new NoopThreads();
        clockGetTime = LibraryLoader.create(ClockGetTime.class).library("libc.so.6").library("librt.so.1").load();
        rubiniusConfiguration = new RubiniusConfiguration();
        DefaultRubiniusConfiguration.load(rubiniusConfiguration, context);
        LinuxRubiniusConfiguration.load(rubiniusConfiguration, context);
    }

    @Override
    public TruffleNFIPlatform getTruffleNFI() {
        return nfi;
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
    public Threads getThreads() {
        return threads;
    }

    @Override
    public ClockGetTime getClockGetTime() {
        return clockGetTime;
    }

    @Override
    public RubiniusConfiguration getRubiniusConfiguration() {
        return rubiniusConfiguration;
    }

    @Override
    public FDSet createFDSet() {
        return new PosixFDSet4Bytes();
    }

    @Override
    public long createSigAction(long handler) {
        long structSigAction = nfi.allocate(152); // sizeof(struct sigaction)
        nfi.putLong(structSigAction + 0, handler); // offsetof(struct sigaction, sa_handler)
        return structSigAction;
    }

}
