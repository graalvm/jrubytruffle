/*
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.parser.parser;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.jcodings.Encoding;
import org.truffleruby.RubyContext;
import org.truffleruby.core.rope.Rope;
import org.truffleruby.core.rope.RopeNodes;
import org.truffleruby.core.rope.RopeOperations;
import org.truffleruby.language.RubyNode;
import java.util.Arrays;
import static org.truffleruby.core.rope.CodeRange.CR_7BIT;
import static org.truffleruby.core.rope.CodeRange.CR_UNKNOWN;

public class ParserRopeOperations {

    public Rope withEncoding(Rope rope, Encoding encoding) {
        if (isBuildingAOTImage()) {
            if (rope.isAsciiOnly() && encoding.isAsciiCompatible()) {
                return rope.withEncoding(encoding, CR_7BIT);
            }

            return RopeOperations.create(rope.getBytes(), encoding, CR_UNKNOWN);
        } else {
            return ropeNode.getWithEncodingNode().executeWithEncoding(rope, encoding, CR_UNKNOWN);
        }
    }

    public Rope makeShared(Rope rope, int sharedStart, int sharedLength) {
        if (isBuildingAOTImage()) {
            if (sharedLength == rope.byteLength()) {
                return rope;
            }

            return RopeOperations.create(Arrays.copyOfRange(rope.getBytes(), sharedStart, sharedStart + sharedLength), rope.getEncoding(), rope.getCodeRange() == CR_7BIT ? CR_7BIT : CR_UNKNOWN);
        } else {
            return ropeNode.getMakeSubstringNode().executeMake(rope, sharedStart, sharedLength);
        }
    }

    private static boolean isBuildingAOTImage() {
        // We can't use a detached RopeNode during image construction because it trips up the static analysis, but
        // we can use it at runtime. We check if a context has been created as a proxy for whether we're in the AOT
        // image builder or running at runtime.
        return TruffleOptions.AOT && RubyContext.FIRST_INSTANCE == null;
    }

    private static class RopeNode extends RubyNode {

        @Child RopeNodes.MakeSubstringNode makeSubstringNode;
        @Child RopeNodes.WithEncodingNode withEncodingNode;

        public RopeNodes.MakeSubstringNode getMakeSubstringNode() {
            if (makeSubstringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                makeSubstringNode = insert(RopeNodes.MakeSubstringNode.create());
            }

            return makeSubstringNode;
        }

        public RopeNodes.WithEncodingNode getWithEncodingNode() {
            if (withEncodingNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                withEncodingNode = insert(RopeNodes.WithEncodingNode.create());
            }

            return withEncodingNode;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return nil();
        }

    }

    private final RopeNode ropeNode = isBuildingAOTImage() ? null : new RopeNode();

}
