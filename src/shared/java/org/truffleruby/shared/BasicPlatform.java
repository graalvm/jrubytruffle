/*
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 *
 * Version: EPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2008 JRuby project
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the EPL, the GPL or the LGPL.
 */
package org.truffleruby.shared;

import java.util.Locale;

public abstract class BasicPlatform {

    public enum OS_TYPE {

        LINUX("linux"),
        DARWIN("darwin"),
        SOLARIS("solaris"),
        AIX("aix"),
        FREEBSD("freebsd"),
        NETBSD("netbsd"),
        OPENBSD("openbsd"),
        WINDOWS("mswin32");

        private final String rubyName;

        private OS_TYPE(String rubyName) {
            this.rubyName = rubyName;
        }
    }

    public static final OS_TYPE OS = determineOS();

    public static String getOSName() {
        return OS.rubyName;
    }

    public static OS_TYPE determineOS() {
        final String osName = System.getProperty("os.name");

        final String lowerOSName = osName.toLowerCase();
        if (lowerOSName.contains("windows")) {
            return OS_TYPE.WINDOWS;
        }

        if (lowerOSName.startsWith("mac") || lowerOSName.startsWith("darwin")) {
            return OS_TYPE.DARWIN;
        } else if (lowerOSName.startsWith("sunos") || lowerOSName.startsWith("solaris")) {
            return OS_TYPE.SOLARIS;
        }

        final String upperOSName = osName.toUpperCase(Locale.ENGLISH);
        for (OS_TYPE os : OS_TYPE.values()) {
            if (upperOSName.startsWith(os.name())) {
                return os;
            }
        }

        throw new UnsupportedOperationException("Unknown platform: " + osName);
    }

    public static String getArchitecture() {
        String architecture = System.getProperty("os.arch");

        if (architecture == null) {
            architecture = "unknown";
        }
        if (architecture.equals("amd64")) {
            architecture = "x86_64";
        }

        return architecture;
    }
}
