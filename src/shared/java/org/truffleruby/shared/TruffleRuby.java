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

public class TruffleRuby {

    public static final String LANGUAGE_ID = "ruby";
    public static final String ENGINE_ID = "truffleruby";
    public static final String LANGUAGE_VERSION = "2.3.6";
    public static final String LANGUAGE_BASE_VERSION = "2.3.0";
    public static final int LANGUAGE_REVISION = 61260;
    public static final String BOOT_SOURCE_NAME = "main_boot_source";
    public static final String RUBY_COPYRIGHT = "truffleruby - Copyright (c) 2013-2018 Oracle and/or its affiliates";
    public static final boolean PRE_INITIALIZE_CONTEXTS = System.getProperty("polyglot.engine.PreinitializeContexts") != null;

    public static String getVersionString(boolean isGraal, boolean isAOT) {
        return String.format(
                "%s %s, like ruby %s <%s %s %s> [%s-%s]",
                ENGINE_ID,
                getEngineVersion(),
                LANGUAGE_VERSION,
                isAOT ? "native" : System.getProperty("java.vm.name", "unknown JVM"),
                isAOT ? "build" : System.getProperty(
                        "java.runtime.version",
                        System.getProperty("java.version", "unknown runtime version")),
                isGraal ? "with Graal" : "without Graal",
                BasicPlatform.getArchitecture(),
                BasicPlatform.getOSName()
        );
    }

    public static String getEngineVersion() {
        // The property cannot be read in a static initializer, it's set later
        final String systemVersion = System.getProperty("org.graalvm.version");

        // No version information, or just "dev" - use 0.0-commit
        if (systemVersion == null || systemVersion.equals("dev")) {
            return "0.0-" + BuildInformationImpl.INSTANCE.getRevision();
        }

        // A "-dev" version number - append the commit as well
        if (systemVersion.endsWith("-dev")) {
            return systemVersion + "-" + BuildInformationImpl.INSTANCE.getRevision();
        }


        return systemVersion;
    }
}
