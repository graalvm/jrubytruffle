/*
 * Copyright (c) 2013, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import org.truffleruby.builtins.CoreClass;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.core.module.ModuleNodes;
import org.truffleruby.core.module.ModuleNodesFactory;
import org.truffleruby.language.Visibility;

@CoreClass("main")
public abstract class MainNodes {

    @CoreMethod(names = "public", rest = true, needsSelf = false, visibility = Visibility.PRIVATE)
    public abstract static class PublicNode extends CoreMethodArrayArgumentsNode {

        @Child private ModuleNodes.PublicNode publicNode = ModuleNodesFactory.PublicNodeFactory.create(null);

        @Specialization
        public DynamicObject doPublic(VirtualFrame frame, Object[] args) {
            final DynamicObject object = coreLibrary().getObjectClass();
            return publicNode.executePublic(frame, object, args);
        }
    }

    @CoreMethod(names = "private", rest = true, needsSelf = false, visibility = Visibility.PRIVATE)
    public abstract static class PrivateNode extends CoreMethodArrayArgumentsNode {

        @Child private ModuleNodes.PrivateNode privateNode = ModuleNodesFactory.PrivateNodeFactory.create(null);

        @Specialization
        public DynamicObject doPrivate(VirtualFrame frame, Object[] args) {
            final DynamicObject object = coreLibrary().getObjectClass();
            return privateNode.executePrivate(frame, object, args);
        }
    }

}
