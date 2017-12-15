/*
 * Copyright (c) 2013, 2017 Oracle and/or its affiliates. All rights reserved. This
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
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2005 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2006 Nick Sieger
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
package org.truffleruby.core;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import org.jcodings.Encoding;
import org.jcodings.specific.UTF8Encoding;
import org.truffleruby.Log;
import org.truffleruby.builtins.CoreClass;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.CoreMethodNode;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.builtins.YieldingCoreMethodNode;
import org.truffleruby.core.rope.CodeRange;
import org.truffleruby.core.string.StringNodes;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.symbol.SymbolTable;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.platform.Platform;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.logging.Level;

@CoreClass("Truffle::System")
public abstract class TruffleSystemNodes {

    @CoreMethod(names = "initial_environment_variables", onSingleton = true)
    public abstract static class InitEnvVarsNode extends CoreMethodNode {

        @Child private StringNodes.MakeStringNode makeStringNode = StringNodes.MakeStringNode.create();

        @TruffleBoundary
        @Specialization
        public DynamicObject envVars() {
            final Set<String> variables = System.getenv().keySet();
            final int size = variables.size();
            final Encoding localeEncoding = getContext().getEncodingManager().getLocaleEncoding();
            final Object[] store = new Object[size];
            int i = 0;
            for (String variable : variables) {
                store[i++] = makeStringNode.executeMake(variable, localeEncoding, CodeRange.CR_UNKNOWN);
            }
            return createArray(store, size);
        }

    }

    @CoreMethod(names = "get_java_property", onSingleton = true, required = 1)
    public abstract static class GetJavaPropertyNode extends CoreMethodArrayArgumentsNode {

        @Child private StringNodes.MakeStringNode makeStringNode = StringNodes.MakeStringNode.create();

        @Specialization(guards = "isRubyString(property)")
        public DynamicObject getJavaProperty(DynamicObject property) {
            String value = System.getProperty(StringOperations.getString(property));
            if (value == null) {
                return nil();
            } else {
                return makeStringNode.executeMake(value, UTF8Encoding.INSTANCE, CodeRange.CR_UNKNOWN);
            }
        }

    }

    @CoreMethod(names = "host_cpu", onSingleton = true)
    public abstract static class HostCPUNode extends CoreMethodNode {

        @Child private StringNodes.MakeStringNode makeStringNode = StringNodes.MakeStringNode.create();

        @Specialization
        public DynamicObject hostCPU() {
            return makeStringNode.executeMake(Platform.getArchitecture(), UTF8Encoding.INSTANCE, CodeRange.CR_UNKNOWN);
        }

    }

    @CoreMethod(names = "host_os", onSingleton = true)
    public abstract static class HostOSNode extends CoreMethodNode {

        @Child private StringNodes.MakeStringNode makeStringNode = StringNodes.MakeStringNode.create();

        @Specialization
        public DynamicObject hostOS() {
            return makeStringNode.executeMake(Platform.getOSName(), UTF8Encoding.INSTANCE, CodeRange.CR_7BIT);
        }

    }

    @CoreMethod(names = "synchronized", isModuleFunction = true, required = 1, needsBlock = true)
    public abstract static class SynchronizedPrimitiveNode extends YieldingCoreMethodNode {

        // We must not allow to synchronize on boxed primitives.
        @Specialization
        public Object synchronize(DynamicObject self, DynamicObject block) {
            synchronized (self) {
                return yield(block);
            }
        }
    }

    // Used by concurrent-ruby
    @CoreMethod(names = "full_memory_barrier", isModuleFunction = true)
    public abstract static class FullMemoryBarrierPrimitiveNode extends CoreMethodNode {

        @Specialization
        public Object fullMemoryBarrier() {
            if (TruffleOptions.AOT) {
                throw new UnsupportedOperationException();
            } else {
                U.fullFence();
            }

            return nil();
        }

        private static final sun.misc.Unsafe U = loadUnsafe();

        private static sun.misc.Unsafe loadUnsafe() {
            if (TruffleOptions.AOT) {
                return null;
            } else {
                try {
                    Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                    Field f = unsafeClass.getDeclaredField("theUnsafe");
                    f.setAccessible(true);
                    return (sun.misc.Unsafe) f.get(null);
                } catch (Throwable e) {
                    throw new UnsupportedOperationException(e);
                }
            }
        }
    }

    @CoreMethod(names = "log", isModuleFunction = true, required = 2)
    public abstract static class LogNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = {"isRubySymbol(level)", "isRubyString(message)", "level == cachedLevel"})
        public Object logCached(DynamicObject level, DynamicObject message,
                        @Cached("level") DynamicObject cachedLevel,
                        @Cached("getLevel(cachedLevel)") Level javaLevel) {
            log(javaLevel, StringOperations.getString(message));
            return nil();
        }

        @Specialization(guards = {"isRubySymbol(level)", "isRubyString(message)"}, replaces="logCached")
        public Object log(DynamicObject level, DynamicObject message) {
            log(getLevel(level), StringOperations.getString(message));
            return nil();
        }

        @TruffleBoundary
        protected Level getLevel(DynamicObject level) {
            assert RubyGuards.isRubySymbol(level);
            final SymbolTable symbolTable = getContext().getSymbolTable();

            for (Level javaLevel : Log.LEVELS) {
                if (symbolTable.getSymbol(javaLevel.toString()) == level) {
                    return javaLevel;
                }
            }

            throw new RaiseException(getContext().getCoreExceptions().argumentError(
                            "Could not find log level for: " + level + " known errors are: " + Arrays.toString(Log.LEVELS),
                            this));
        }

        @TruffleBoundary
        public static void log(Level level, String message) {
            Log.LOGGER.log(level, message);
        }

    }

    @CoreMethod(names = "aot_set_process_title", isModuleFunction = true, required = 1)
    public abstract static class SetProcessTitleNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(guards = "isRubyString(name)")
        protected DynamicObject setProcessTitle(DynamicObject name) {
            if (TruffleOptions.AOT) {
                Compiler.command(new Object[] { "com.oracle.svm.core.JavaMainWrapper.setCRuntimeArgument0(String)boolean", StringOperations.getString(name) });
            } else {
                throw new UnsupportedOperationException();
            }
            return name;
        }

    }

}
