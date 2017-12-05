/*
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.rubinius;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jcodings.specific.UTF8Encoding;
import org.truffleruby.Layouts;
import org.truffleruby.builtins.CoreClass;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.array.ArrayGuards;
import org.truffleruby.core.array.ArrayStrategy;
import org.truffleruby.core.basicobject.BasicObjectNodes.ReferenceEqualNode;
import org.truffleruby.core.rope.CodeRange;
import org.truffleruby.core.string.StringNodes;
import org.truffleruby.language.objects.IsANode;
import org.truffleruby.language.objects.IsANodeGen;
import org.truffleruby.language.objects.IsTaintedNode;
import org.truffleruby.language.objects.LogicalClassNode;
import org.truffleruby.language.objects.LogicalClassNodeGen;
import org.truffleruby.language.objects.ObjectIVarGetNode;
import org.truffleruby.language.objects.ObjectIVarGetNodeGen;
import org.truffleruby.language.objects.ObjectIVarSetNode;
import org.truffleruby.language.objects.ObjectIVarSetNodeGen;
import org.truffleruby.language.objects.PropertyFlags;
import org.truffleruby.language.objects.TaintNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;

@CoreClass("Rubinius::Type")
public abstract class TypeNodes {

    @CoreMethod(names = "object_kind_of?", onSingleton = true, required = 2)
    public static abstract class ObjectKindOfNode extends CoreMethodArrayArgumentsNode {

        @Child private IsANode isANode = IsANodeGen.create(null, null);

        @Specialization
        public boolean objectKindOf(Object object, DynamicObject rubyClass) {
            return isANode.executeIsA(object, rubyClass);
        }

    }

    @CoreMethod(names = "object_class", onSingleton = true, required = 1)
    public static abstract class VMObjectClassNode extends CoreMethodArrayArgumentsNode {

        @Child private LogicalClassNode classNode = LogicalClassNodeGen.create(null);

        @Specialization
        public DynamicObject objectClass(VirtualFrame frame, Object object) {
            return classNode.executeLogicalClass(object);
        }

    }

    @CoreMethod(names = "object_equal", onSingleton = true, required = 2)
    public static abstract class ObjectEqualNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        public boolean objectEqual(Object a, Object b,
                @Cached("create()") ReferenceEqualNode referenceEqualNode) {
            return referenceEqualNode.executeReferenceEqual(a, b);
        }

    }

    @Primitive(name = "object_ivars")
    public abstract static class ObjectInstanceVariablesNode extends PrimitiveArrayArgumentsNode {

        public abstract DynamicObject executeGetIVars(Object self);

        @TruffleBoundary
        @Specialization(guards = { "!isNil(self)", "!isRubySymbol(self)" })
        public DynamicObject instanceVariables(DynamicObject self) {
            Shape shape = self.getShape();
            List<String> names = new ArrayList<>();

            for (Property property : shape.getProperties()) {
                Object name = property.getKey();
                if (PropertyFlags.isDefined(property) && name instanceof String) {
                    names.add((String) name);
                }
            }

            final int size = names.size();
            final String[] sortedNames = names.toArray(new String[size]);
            Arrays.sort(sortedNames);

            final Object[] nameSymbols = new Object[size];
            for (int i = 0; i < sortedNames.length; i++) {
                nameSymbols[i] = getSymbol(sortedNames[i]);
            }

            return createArray(nameSymbols, size);
        }

        @Specialization
        public DynamicObject instanceVariables(int self) {
            return createArray(null, 0);
        }

        @Specialization
        public DynamicObject instanceVariables(long self) {
            return createArray(null, 0);
        }

        @Specialization
        public DynamicObject instanceVariables(boolean self) {
            return createArray(null, 0);
        }

        @Specialization(guards = "isNil(object)")
        public DynamicObject instanceVariablesNil(DynamicObject object) {
            return createArray(null, 0);
        }

        @Specialization(guards = "isRubySymbol(object)")
        public DynamicObject instanceVariablesSymbol(DynamicObject object) {
            return createArray(null, 0);
        }

        @Fallback
        public DynamicObject instanceVariables(Object object) {
            return createArray(null, 0);
        }

    }

    @Primitive(name = "object_ivar_defined?")
    public abstract static class ObjectIVarIsDefinedNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        public Object ivarIsDefined(DynamicObject object, DynamicObject name) {
            final String ivar = Layouts.SYMBOL.getString(name);
            final Property property = object.getShape().getProperty(ivar);
            return PropertyFlags.isDefined(property);
        }

    }

    @Primitive(name = "object_ivar_get")
    public abstract static class ObjectIVarGetPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        public Object ivarGet(VirtualFrame frame, DynamicObject object, DynamicObject name,
                @Cached("createObjectIVarGetNode()") ObjectIVarGetNode iVarGetNode) {
            return iVarGetNode.executeIVarGet(frame, object, Layouts.SYMBOL.getString(name));
        }

        protected ObjectIVarGetNode createObjectIVarGetNode() {
            return ObjectIVarGetNodeGen.create(false, null, null);
        }

    }

    @Primitive(name = "object_ivar_set")
    public abstract static class ObjectIVarSetPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        public Object ivarSet(DynamicObject object, DynamicObject name, Object value,
                @Cached("createObjectIVarSetNode()") ObjectIVarSetNode iVarSetNode) {
            return iVarSetNode.executeIVarSet(object, Layouts.SYMBOL.getString(name), value);
        }

        protected ObjectIVarSetNode createObjectIVarSetNode() {
            return ObjectIVarSetNodeGen.create(false, null, null, null);
        }

    }

    @Primitive(name = "object_can_contain_object")
    @ImportStatic(ArrayGuards.class)
    public abstract static class CanContainObjectNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = { "isRubyArray(array)", "strategy.matches(array)",
                "!strategy.accepts(nil())" }, limit = "ARRAY_STRATEGIES")
        protected boolean primitiveArray(DynamicObject array,
                @Cached("of(array)") ArrayStrategy strategy) {
            return false;
        }

        @Specialization(guards = { "isRubyArray(array)", "strategy.matches(array)",
                "strategy.accepts(nil())" }, limit = "ARRAY_STRATEGIES")
        protected boolean objectArray(DynamicObject array,
                @Cached("of(array)") ArrayStrategy strategy) {
            return true;
        }

        @Specialization(guards = "!isRubyArray(object)")
        protected boolean other(Object object) {
            return true;
        }

    }

    @CoreMethod(names = "infect", onSingleton = true, required = 2)
    public static abstract class InfectNode extends CoreMethodArrayArgumentsNode {

        @Child private IsTaintedNode isTaintedNode;
        @Child private TaintNode taintNode;

        @Specialization
        public Object infect(Object host, Object source) {
            if (isTaintedNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isTaintedNode = insert(IsTaintedNode.create());
            }

            if (isTaintedNode.executeIsTainted(source)) {
                // This lazy node allocation effectively gives us a branch profile

                if (taintNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    taintNode = insert(TaintNode.create());
                }

                taintNode.executeTaint(host);
            }

            return host;
        }

    }

    @CoreMethod(names = "module_name", onSingleton = true, required = 1)
    public static abstract class ModuleNameNode extends CoreMethodArrayArgumentsNode {

        @Child private StringNodes.MakeStringNode makeStringNode = StringNodes.MakeStringNode.create();

        @Specialization
        public DynamicObject moduleName(DynamicObject module) {
            final String name = Layouts.MODULE.getFields(module).getName();
            return makeStringNode.executeMake(name, UTF8Encoding.INSTANCE, CodeRange.CR_UNKNOWN);
        }

    }

}
