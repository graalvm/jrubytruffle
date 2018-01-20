/*
 * Copyright (c) 2013, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.language.arguments;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import org.truffleruby.Log;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.CallDispatchHeadNode;

public class ReadRestArgumentNode extends RubyNode {

    private final int startIndex;
    private final int indexFromCount;
    private final boolean keywordArguments;

    private final BranchProfile noArgumentsLeftProfile = BranchProfile.create();
    private final BranchProfile subsetOfArgumentsProfile = BranchProfile.create();

    @Child private ReadUserKeywordsHashNode readUserKeywordsHashNode;
    @Child private CallDispatchHeadNode addRejectedNode = CallDispatchHeadNode.createOnSelf();

    public ReadRestArgumentNode(int startIndex, int indexFromCount,
                                boolean keywordArguments, int minimumForKWargs) {
        this.startIndex = startIndex;
        this.indexFromCount = indexFromCount;
        this.keywordArguments = keywordArguments;

        if (keywordArguments) {
            readUserKeywordsHashNode = new ReadUserKeywordsHashNode(minimumForKWargs);
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {
        int endIndex = RubyArguments.getArgumentsCount(frame) - indexFromCount;

        if (keywordArguments) {
            final int argumentCount = RubyArguments.getArgumentsCount(frame);
            final Object lastArgument = argumentCount > 0 ? RubyArguments.getArgument(frame, argumentCount - 1) : null;

            if (RubyGuards.isRubyHash(lastArgument)) {
                endIndex -= 1;
            }
        }

        final int length = endIndex - startIndex;

        final Object resultStore;
        final int resultLength;

        if (startIndex == 0) {
            final Object[] arguments = RubyArguments.getArguments(frame);
            resultStore = arguments;
            resultLength = length;
        } else {
            if (startIndex >= endIndex) {
                noArgumentsLeftProfile.enter();
                resultStore = null;
                resultLength = 0;
            } else {
                subsetOfArgumentsProfile.enter();
                final Object[] arguments = RubyArguments.getArguments(frame);
                resultStore = ArrayUtils.extractRange(arguments, startIndex, endIndex);
                resultLength = length;
            }
        }

        final DynamicObject rest = createArray(resultStore, resultLength);

        if (keywordArguments) {
            Log.notOptimizedOnce(Log.KWARGS_NOT_OPTIMIZED_YET);

            Object kwargsHash = readUserKeywordsHashNode.execute(frame);

            if (kwargsHash == null) {
                kwargsHash = nil();
            }

            addRejectedNode.call(frame, coreLibrary().getTruffleModule(), "add_rejected_kwargs_to_rest", rest, kwargsHash);
        }

        return rest;
    }
}
