/*
 * Copyright (c) 2013, 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.language;

import org.truffleruby.RubyContext;
import org.truffleruby.language.methods.SharedMethodInfo;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerOptions;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.CyclicAssumption;

public class RubyRootNode extends RubyBaseRootNode {

    private final RubyContext context;
    private final SourceSection sourceSection;
    private final SharedMethodInfo sharedMethodInfo;

    @Child private RubyNode body;

    private CyclicAssumption needsCallerAssumption = new CyclicAssumption("needs caller frame");

    public RubyRootNode(RubyContext context, SourceSection sourceSection, FrameDescriptor frameDescriptor,
                        SharedMethodInfo sharedMethodInfo, RubyNode body) {
        super(context.getLanguage(), frameDescriptor);
        assert sourceSection != null;
        assert body != null;
        this.context = context;
        this.sourceSection = sourceSection;
        this.sharedMethodInfo = sharedMethodInfo;
        this.body = body;

        body.unsafeSetIsCall();
        body.unsafeSetIsRoot();
    }

    @Override
    public Object execute(VirtualFrame frame) {
        context.getSafepointManager().poll(this);
        return body.execute(frame);
    }

    @Override
    public SourceSection getSourceSection() {
        return sourceSection;
    }

    @Override
    public CompilerOptions getCompilerOptions() {
        return context.getCompilerOptions();
    }

    @Override
    public boolean isCloningAllowed() {
        return true;
    }

    @Override
    public String getName() {
        if (sharedMethodInfo.getName() != null) {
            return sharedMethodInfo.getName();
        } else {
            return sharedMethodInfo.getNotes();
        }
    }

    @Override
    public String toString() {
        return sharedMethodInfo.getDescriptiveNameAndSource();
    }

    public SharedMethodInfo getSharedMethodInfo() {
        return sharedMethodInfo;
    }

    public RubyNode getBody() {
        return body;
    }

    public RubyContext getContext() {
        return context;
    }

    public Assumption getNeedsCallerAssumption() {
        return needsCallerAssumption.getAssumption();
    }

    public void invalidateNeedsCallerAssumption() {
        needsCallerAssumption.invalidate();
    }

    @Override
    public Node copy() {
        RubyRootNode root = (RubyRootNode) super.copy();
        root.needsCallerAssumption = new CyclicAssumption("needs caller frame");
        return root;
    }


}
