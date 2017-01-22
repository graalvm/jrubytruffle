/*
 * Copyright (c) 2015, 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.language.objects.shared;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.Layouts;
import org.truffleruby.Log;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.options.OptionsBuilder;
import org.truffleruby.options.OptionsCatalog;

import java.util.ArrayDeque;
import java.util.Deque;

public class SharedObjects {

    private final RubyContext context;
    // No need for volatile since we change this before starting the 2nd Thread
    private boolean sharing = false;

    public SharedObjects(RubyContext context) {
        this.context = context;
    }

    public boolean isSharing() {
        return sharing;
    }

    public void startSharing() {
        sharing = true;
        shareContextRoots(context);
    }

    private static void shareContextRoots(RubyContext context) {
        final Deque<DynamicObject> stack = new ArrayDeque<>();

        // Share global variables (including new ones)
        for (DynamicObject object : context.getCoreLibrary().getGlobalVariables().dynamicObjectValues()) {
            stack.push(object);
        }

        // Share all named modules and constants (including the shared TOPLEVEL_BINDING)
        stack.push(context.getCoreLibrary().getObjectClass());

        // Share all threads since they are accessible via Thread.list
        for (DynamicObject thread : context.getThreadManager().iterateThreads()) {
            stack.push(thread);
        }

        long t0 = System.currentTimeMillis();
        shareObjects(stack);
        if (context.getOptions().SHARED_OBJECTS_DEBUG) {
            Log.LOGGER.info("sharing roots took " + (System.currentTimeMillis() - t0) + " ms");
        }
    }

    public static void shareDeclarationFrame(DynamicObject block) {
        final Deque<DynamicObject> stack = new ArrayDeque<>();

        if (RubyContext.getInstance().getOptions().SHARED_OBJECTS_DEBUG) {
            final SourceSection sourceSection = Layouts.PROC.getSharedMethodInfo(block).getSourceSection();
            Log.LOGGER.info("sharing decl frame of " + RubyLanguage.fileLine(sourceSection));
        }

        final MaterializedFrame declarationFrame = Layouts.PROC.getDeclarationFrame(block);
        stack.addAll(ObjectGraph.getObjectsInFrame(declarationFrame));

        shareObjects(stack);
    }

    private static void shareObjects(Deque<DynamicObject> stack) {
        while (!stack.isEmpty()) {
            final DynamicObject object = stack.pop();

            if (share(object)) {
                stack.addAll(ObjectGraph.getAdjacentObjects(object));
            }
        }
    }

    @TruffleBoundary
    private static void shareObject(Object value) {
        final Deque<DynamicObject> stack = new ArrayDeque<>();
        stack.add((DynamicObject) value);
        shareObjects(stack);
    }

    public static boolean isShared(DynamicObject object) {
        return isShared(object.getShape());
    }

    public static boolean isShared(Shape shape) {
        return RubyContext.getInstance().getOptions().SHARED_OBJECTS_ENABLED && (RubyContext.getInstance().getOptions().SHARED_OBJECTS_SHARE_ALL || shape.isShared());
    }

    public static void writeBarrier(Object value) {
        if (RubyContext.getInstance().getOptions().SHARED_OBJECTS_ENABLED && value instanceof DynamicObject && !isShared((DynamicObject) value)) {
            shareObject(value);
        }
    }

    public static void propagate(DynamicObject source, Object value) {
        if (isShared(source)) {
            writeBarrier(value);
        }
    }

    private static boolean share(DynamicObject object) {
        if (isShared(object)) {
            return false;
        }

        object.updateShape();
        final Shape oldShape = object.getShape();
        final Shape newShape = oldShape.makeSharedShape();
        object.setShapeAndGrow(oldShape, newShape);
        return true;
    }

}
