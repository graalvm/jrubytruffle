/*
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.tracepoint;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.object.DynamicObject;

import org.jcodings.specific.UTF8Encoding;
import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.builtins.CoreClass;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.UnaryCoreMethodNode;
import org.truffleruby.builtins.YieldingCoreMethodNode;
import org.truffleruby.core.binding.BindingNodes;
import org.truffleruby.core.kernel.TraceManager;
import org.truffleruby.core.rope.CodeRange;
import org.truffleruby.core.string.StringNodes.MakeStringNode;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.Visibility;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.objects.AllocateObjectNode;

@CoreClass("TracePoint")
public abstract class TracePointNodes {

    @CoreMethod(names = "__allocate__", constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends UnaryCoreMethodNode {

        @Child private AllocateObjectNode allocateNode = AllocateObjectNode.create();

        @Specialization
        public DynamicObject allocate(DynamicObject rubyClass) {
            return allocateNode.allocate(rubyClass, null, null, null, 0, null, null, null, false);
        }

    }

    @CoreMethod(names = "initialize", rest = true, needsBlock = true)
    public abstract static class InitializeNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isTracePoint(tracePoint)")
        public DynamicObject initialize(DynamicObject tracePoint, Object[] args, DynamicObject block) {
            Layouts.TRACE_POINT.setTags(tracePoint, new Class<?>[]{TraceManager.LineTag.class});
            Layouts.TRACE_POINT.setProc(tracePoint, block);
            return tracePoint;
        }

    }

    @CoreMethod(names = "enable", needsBlock = true)
    public abstract static class EnableNode extends YieldingCoreMethodNode {

        @Specialization(guards = "isTracePoint(tracePoint)")
        public boolean enable(DynamicObject tracePoint, NotProvided block) {
            EventBinding<?> eventBinding = (EventBinding<?>) Layouts.TRACE_POINT.getEventBinding(tracePoint);
            final boolean alreadyEnabled = eventBinding != null;

            if (!alreadyEnabled) {
                eventBinding = createEventBinding(getContext(), tracePoint);
                Layouts.TRACE_POINT.setEventBinding(tracePoint, eventBinding);
            }

            return alreadyEnabled;
        }

        @Specialization(guards = "isTracePoint(tracePoint)")
        public Object enable(DynamicObject tracePoint, DynamicObject block) {
            EventBinding<?> eventBinding = (EventBinding<?>) Layouts.TRACE_POINT.getEventBinding(tracePoint);
            final boolean alreadyEnabled = eventBinding != null;

            if (!alreadyEnabled) {
                eventBinding = createEventBinding(getContext(), tracePoint);
                Layouts.TRACE_POINT.setEventBinding(tracePoint, eventBinding);
            }

            try {
                return yield(block);
            } finally {
                if (!alreadyEnabled) {
                    dispose(eventBinding);
                    Layouts.TRACE_POINT.setEventBinding(tracePoint, null);
                }
            }
        }

        @TruffleBoundary
        public static EventBinding<?> createEventBinding(RubyContext context, DynamicObject tracePoint) {
            return context.getInstrumenter().attachExecutionEventFactory(
                    SourceSectionFilter.newBuilder()
                    .mimeTypeIs(RubyLanguage.MIME_TYPE)
                    .tagIs((Class<?>[]) Layouts.TRACE_POINT.getTags(tracePoint))
                    .build(), eventContext -> new TracePointEventNode(context, eventContext, tracePoint));
        }

        @TruffleBoundary
        public static void dispose(EventBinding<?> eventBinding) {
            eventBinding.dispose();
        }

    }

    @CoreMethod(names = "disable", needsBlock = true)
    public abstract static class DisableNode extends YieldingCoreMethodNode {

        @Specialization(guards = "isTracePoint(tracePoint)")
        public boolean disable(DynamicObject tracePoint, NotProvided block) {
            return disable(tracePoint, (DynamicObject) null);
        }

        @Specialization(guards = "isTracePoint(tracePoint)")
        public boolean disable(DynamicObject tracePoint, DynamicObject block) {
            EventBinding<?> eventBinding = (EventBinding<?>) Layouts.TRACE_POINT.getEventBinding(tracePoint);
            final boolean alreadyEnabled = eventBinding != null;

            if (alreadyEnabled) {
                EnableNode.dispose(eventBinding);
                Layouts.TRACE_POINT.setEventBinding(tracePoint, null);
            }

            if (block != null) {
                try {
                    yield(block);
                } finally {
                    if (alreadyEnabled) {
                        eventBinding = EnableNode.createEventBinding(getContext(), tracePoint);
                        Layouts.TRACE_POINT.setEventBinding(tracePoint, eventBinding);
                    }
                }
            }

            return alreadyEnabled;
        }

    }

    @CoreMethod(names = "enabled?")
    public abstract static class EnabledNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isTracePoint(tracePoint)")
        public boolean enabled(DynamicObject tracePoint) {
            return Layouts.TRACE_POINT.getEventBinding(tracePoint) != null;
        }

    }

    @CoreMethod(names = "event")
    public abstract static class EventNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isTracePoint(tracePoint)")
        public DynamicObject event(DynamicObject tracePoint) {
            return Layouts.TRACE_POINT.getEvent(tracePoint);
        }

    }

    @CoreMethod(names = "path")
    public abstract static class PathNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isTracePoint(tracePoint)")
        public DynamicObject path(DynamicObject tracePoint) {
            return Layouts.TRACE_POINT.getPath(tracePoint);
        }

    }

    @CoreMethod(names = "lineno")
    public abstract static class LineNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isTracePoint(tracePoint)")
        public int line(DynamicObject tracePoint) {
            return Layouts.TRACE_POINT.getLine(tracePoint);
        }

    }

    @CoreMethod(names = "method_id")
    public abstract static class MethodIDNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isTracePoint(tracePoint)")
        public DynamicObject methodId(DynamicObject tracePoint,
                @Cached("create()") MakeStringNode makeStringNode) {
            final DynamicObject binding = Layouts.TRACE_POINT.getBinding(tracePoint);
            final InternalMethod method = RubyArguments.getMethod(BindingNodes.getFrame(binding));
            return makeStringNode.executeMake(method.getName(), UTF8Encoding.INSTANCE, CodeRange.CR_UNKNOWN);
        }

    }

    @CoreMethod(names = "self")
    public abstract static class SelfNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isTracePoint(tracePoint)")
        public Object self(DynamicObject tracePoint) {
            final DynamicObject binding = Layouts.TRACE_POINT.getBinding(tracePoint);
            return RubyArguments.getSelf(BindingNodes.getFrame(binding));
        }

    }

    @CoreMethod(names = "binding")
    public abstract static class BindingNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isTracePoint(tracePoint)")
        public DynamicObject binding(DynamicObject tracePoint) {
            return Layouts.TRACE_POINT.getBinding(tracePoint);
        }

    }

}
