/*
 * Copyright (c) 2014, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.regexp;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.object.DynamicObject;
import org.jcodings.specific.ASCIIEncoding;
import org.jcodings.specific.USASCIIEncoding;
import org.truffleruby.Layouts;
import org.truffleruby.core.rope.Rope;
import org.truffleruby.core.rope.RopeBuilder;
import org.truffleruby.core.rope.RopeOperations;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.CallDispatchHeadNode;
import org.truffleruby.language.dispatch.DispatchHeadNodeFactory;
import org.truffleruby.parser.BodyTranslator;

public class InterpolatedRegexpNode extends RubyNode {

    @Children private final RubyNode[] children;
    private final RegexpOptions options;
    @Child private CallDispatchHeadNode toS = DispatchHeadNodeFactory.createMethodCall();

    public InterpolatedRegexpNode(RubyNode[] children, RegexpOptions options) {
        this.children = children;
        this.options = options;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return createRegexp(executeChildren(frame));
    }

    @TruffleBoundary
    private DynamicObject createRegexp(DynamicObject[] parts) {
        final Rope[] strings = new Rope[children.length];

        for (int n = 0; n < children.length; n++) {
            strings[n] = StringOperations.rope(parts[n]);
        }

        final RopeBuilder preprocessed = ClassicRegexp.preprocessDRegexp(getContext(), strings, options);

        final DynamicObject regexp = RegexpNodes.createRubyRegexp(getContext(), this, coreLibrary().getRegexpFactory(),
                RopeOperations.ropeFromByteList(preprocessed), options);

        if (options.isEncodingNone()) {
            final Rope source = Layouts.REGEXP.getSource(regexp);

            if (!BodyTranslator.all7Bit(preprocessed.getBytes())) {
                Layouts.REGEXP.setSource(regexp, RopeOperations.withEncodingVerySlow(source, ASCIIEncoding.INSTANCE));
            } else {
                Layouts.REGEXP.setSource(regexp, RopeOperations.withEncodingVerySlow(source, USASCIIEncoding.INSTANCE));
            }
        }

        return regexp;
    }

    @ExplodeLoop
    protected DynamicObject[] executeChildren(VirtualFrame frame) {
        DynamicObject[] values = new DynamicObject[children.length];
        for (int i = 0; i < children.length; i++) {
            final Object value = children[i].execute(frame);
            values[i] = (DynamicObject) toS.call(frame, value, "to_s");
        }
        return values;
    }

}
