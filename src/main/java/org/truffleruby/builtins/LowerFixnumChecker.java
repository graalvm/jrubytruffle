/*
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.builtins;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.Log;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.language.RubyNode;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

public class LowerFixnumChecker {

    public static boolean SUCCESS = true;

    public static void checkLowerFixnumArguments(NodeFactory<? extends RubyNode> nodeFactory, int initialSkip, int[] lowerFixnum) {
        final Class<? extends RubyNode> nodeClass = nodeFactory.getNodeClass();
        byte[] lowerArgs = null;
        for (Method specialization : nodeClass.getDeclaredMethods()) {
            if (specialization.isAnnotationPresent(Specialization.class)) {
                Class<?>[] argumentTypes = specialization.getParameterTypes();
                int skip = initialSkip;
                if (argumentTypes.length > 0 && argumentTypes[0] == VirtualFrame.class) {
                    skip++;
                }
                int end = argumentTypes.length;
                Annotation[][] annos = specialization.getParameterAnnotations();
                for (int i = end - 1; i >= skip; i--) {
                    boolean cached = false;
                    for (Annotation anno : annos[i]) {
                        cached |= anno instanceof Cached;
                    }
                    if (cached) {
                        end--;
                    } else {
                        break;
                    }
                }
                if (lowerArgs == null) {
                    if (end < skip) {
                        reportError(nodeFactory, "should have needsSelf = false");
                        return;
                    }
                    lowerArgs = new byte[end - skip];
                } else {
                    assert lowerArgs.length == end - skip;
                }
                for (int i = skip; i < end; i++) {
                    Class<?> argumentType = argumentTypes[i];
                    if (argumentType == int.class) {
                        lowerArgs[i - skip] |= 0b01;
                    } else if (argumentType == long.class) {
                        lowerArgs[i - skip] |= 0b10;
                    }
                }
            }
        }

        // Verify against the lowerFixnum annotation
        for (int i = 0; i < lowerArgs.length; i++) {
            boolean shouldLower = lowerArgs[i] == 0b01; // int without long
            if (shouldLower && !ArrayUtils.contains(lowerFixnum, i + 1)) {
                reportError(nodeFactory, "should use lowerFixnum for argument " + (i + 1));
            }
        }
    }

    private static void reportError(NodeFactory<? extends RubyNode> nodeFactory, String message) {
        SUCCESS = false;
        Log.LOGGER.warning("node " + nodeFactory.getNodeClass().getCanonicalName() + " " + message);
    }
}
