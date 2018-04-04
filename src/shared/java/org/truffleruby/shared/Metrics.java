/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */

package org.truffleruby.shared;

import java.lang.management.ManagementFactory;

public class Metrics {

    // These system properties are used before outside the SDK option system
    private static boolean METRICS_TIME;
    private static final boolean METRICS_MEMORY_USED_ON_EXIT =
            Boolean.getBoolean("truffleruby.metrics.memory_used_on_exit");

    public static void printTime(String id) {
        if (METRICS_TIME) {
            final long millis = System.currentTimeMillis();
            System.err.printf("%s %d.%03d%n", id, millis / 1000, millis % 1000);
        }
    }

    private static void printMemory(boolean isAOT) {
        // Memory stats aren't available in native.
        if (!isAOT && METRICS_MEMORY_USED_ON_EXIT) {
            for (int n = 0; n < 10; n++) {
                System.gc();
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            System.err.printf("allocated %d%n", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        }
    }

    public static void end(boolean isAOT) {
        printTime("after-main");
        printMemory(isAOT);
    }

    public static void begin() {
        // Assigned here so it's available on SVM as well
        METRICS_TIME = Boolean.getBoolean("truffleruby.metrics.time");

        printTime("before-main");
    }

    public static boolean getMetricsTime() {
        return METRICS_TIME;
    }
}
