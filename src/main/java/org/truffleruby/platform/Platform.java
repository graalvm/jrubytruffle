/***** BEGIN LICENSE BLOCK *****
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
 ***** END LICENSE BLOCK *****/
package org.truffleruby.platform;

import org.truffleruby.shared.BasicPlatform;

public abstract class Platform extends BasicPlatform {

    public static final String LIBPREFIX = OS == OS_TYPE.WINDOWS ? "" : "lib";
    public static final String LIBSUFFIX = determineLibExt();
    public static final String LIBC = determineLibC();

    public static final boolean IS_WINDOWS = OS.equals(OS_TYPE.WINDOWS);
    public static final boolean IS_BSD = OS.equals(OS_TYPE.FREEBSD) || OS.equals(OS_TYPE.NETBSD) || OS.equals(OS_TYPE.OPENBSD);

    private static final String determineLibC() {
        switch (OS) {
            case WINDOWS:
                return "msvcrt.dll";
            case LINUX:
                return "libc.so.6";
            case AIX:
                if (Integer.getInteger("sun.arch.data.model") == 32) {
                    return "libc.a(shr.o)";
                } else {
                    return "libc.a(shr_64.o)";
                }
            default:
                return LIBPREFIX + "c." + LIBSUFFIX;
        }
    }

    private static final String determineLibExt() {
        switch (OS) {
            case WINDOWS:
                return "dll";
            case AIX:
                return "a";
            case DARWIN:
                return "dylib";
            default:
                return "so";
        }
    }

}
