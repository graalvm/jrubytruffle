/*
 * Copyright (c) 2014, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.language.methods;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.BreakException;
import org.truffleruby.language.control.BreakID;
import org.truffleruby.language.control.NextException;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.control.RedoException;
import org.truffleruby.language.control.RetryException;
import org.truffleruby.language.control.ReturnException;
import org.truffleruby.language.control.ReturnID;

public class CatchForLambdaNode extends RubyNode {

    private final ReturnID returnID;
    private final BreakID breakID;

    @Child private RubyNode body;

    private final ConditionProfile matchingReturnProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile matchingBreakProfile = ConditionProfile.createBinaryProfile();
    private final BranchProfile retryProfile = BranchProfile.create();
    private final BranchProfile redoProfile = BranchProfile.create();
    private final BranchProfile nextProfile = BranchProfile.create();

    public CatchForLambdaNode(ReturnID returnID, BreakID breakID, RubyNode body) {
        this.returnID = returnID;
        this.breakID = breakID;
        this.body = body;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        while (true) {
            try {
                return body.execute(frame);
            } catch (ReturnException e) {
                if (matchingReturnProfile.profile(e.getReturnID() == returnID)) {
                    return e.getValue();
                } else {
                    throw e;
                }
            } catch (RetryException e) {
                retryProfile.enter();
                throw new RaiseException(coreExceptions().syntaxErrorInvalidRetry(this));
            } catch (RedoException e) {
                redoProfile.enter();
                getContext().getSafepointManager().poll(this);
                continue;
            } catch (NextException e) {
                nextProfile.enter();
                return e.getResult();
            } catch (BreakException e) {
                if (matchingBreakProfile.profile(e.getBreakID() == breakID)) {
                    return e.getResult();
                } else {
                    throw e;
                }
            }
        }
    }

}
