/*
 * Copyright (c) 2015, 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 *
 * This file contains configuration.configuration values translated from Rubinius.
 *
 * Copyright (c) 2007-2014, Evan Phoenix and contributors
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * * Neither the name of Rubinius nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.truffleruby.platform;

import com.oracle.truffle.api.object.DynamicObject;
import jnr.constants.platform.Fcntl;
import jnr.constants.platform.OpenFlags;
import org.jcodings.specific.UTF8Encoding;
import org.truffleruby.RubyContext;
import org.truffleruby.core.numeric.BignumOperations;
import org.truffleruby.core.rope.RopeOperations;
import org.truffleruby.core.string.StringOperations;

import java.math.BigInteger;

public abstract class DefaultRubiniusConfiguration {

    public static void load(RubiniusConfiguration configuration, RubyContext context) {
        for (Fcntl fcntl : Fcntl.values()) {
            if (fcntl.defined()) {
                configuration.config("rbx.platform.fcntl." + fcntl.name(), fcntl.intValue());
            }
        }

        for (OpenFlags openFlag : OpenFlags.values()) {
            if (openFlag.defined()) {
                configuration.config("rbx.platform.file." + openFlag.name(), openFlag.intValue());
            }
        }
    }

    protected static DynamicObject newBignum(RubyContext context, String value) {
        return BignumOperations.createBignum(context, new BigInteger(value));
    }

    protected static DynamicObject string(RubyContext context, String value) {
        return StringOperations.createString(context, RopeOperations.encodeAscii(value, UTF8Encoding.INSTANCE));
    }

}
