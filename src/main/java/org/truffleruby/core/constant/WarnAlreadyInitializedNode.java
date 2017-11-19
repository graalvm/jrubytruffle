/*
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.constant;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.Layouts;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.WarnNode;

public class WarnAlreadyInitializedNode extends RubyBaseNode {

    @Child private WarnNode warnNode = new WarnNode();

    @TruffleBoundary
    public void warnAlreadyInitialized(DynamicObject module, String name, SourceSection sourceSection) {
        final String moduleName = Layouts.MODULE.getFields(module).getName();
        if (sourceSection != null) {
            warnNode.warn(sourceSection.getSource().getName(), ":", Integer.toString(sourceSection.getStartLine()),
                    ": warning: already initialized constant ", moduleName, "::", name);
        } else {
            warnNode.warn("warning: already initialized constant ", moduleName, "::", name);
        }
    }

}
