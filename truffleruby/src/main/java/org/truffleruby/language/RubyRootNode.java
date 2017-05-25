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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.ExecutionContext;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import java.util.concurrent.atomic.AtomicLong;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.language.methods.SharedMethodInfo;

public class RubyRootNode extends RootNode {

    private final RubyContext context;
    private final SharedMethodInfo sharedMethodInfo;
    private final boolean needsDeclarationFrame;

    @Child private RubyNode body;

    private volatile Assumption needsCallerAssumption = Truffle.getRuntime().createAssumption();

    public RubyRootNode(RubyContext context, SourceSection sourceSection, FrameDescriptor frameDescriptor,
                        SharedMethodInfo sharedMethodInfo, RubyNode body, boolean needsDeclarationFrame) {
        super(RubyLanguage.class, sourceSection, frameDescriptor);
        assert sourceSection != null;
        assert body != null;
        this.context = context;
        this.sharedMethodInfo = sharedMethodInfo;
        this.needsDeclarationFrame = needsDeclarationFrame;
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
    public ExecutionContext getExecutionContext() {
        return context;
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

    public boolean needsDeclarationFrame() {
        return needsDeclarationFrame;
    }

    public RubyContext getContext() {
        return context;
    }

    public synchronized Assumption getNeedsCallerAssumption() {
        return needsCallerAssumption;
    }

    public synchronized void replaceAndInvalidateNeedsCallerAssumption(Assumption assumption) {
        Assumption oldAssumption = needsCallerAssumption;
        needsCallerAssumption = Truffle.getRuntime().createAssumption();
        oldAssumption.invalidate();
        assumption.invalidate();
    }

    @Override
    public Node copy() {
        RubyRootNode root = (RubyRootNode) super.copy();
        root.needsCallerAssumption = Truffle.getRuntime().createAssumption();
        return root;
    }


}
