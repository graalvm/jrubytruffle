/*
 * Copyright (c) 2015, 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.dir;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectFactory;
import com.oracle.truffle.api.object.dsl.Layout;
import com.oracle.truffle.api.object.dsl.Nullable;
import org.truffleruby.core.basicobject.BasicObjectLayout;

@Layout
public interface DirLayout extends BasicObjectLayout {

    DynamicObjectFactory createDirShape(DynamicObject logicalClass,
                                        DynamicObject metaClass);

    DynamicObject createDir(DynamicObjectFactory factory,
                            @Nullable String[] contents,
                            int position);

    String[] getContents(DynamicObject object);
    void setContents(DynamicObject object, String[] value);

    int getPosition(DynamicObject object);
    void setPosition(DynamicObject object, int value);

}
