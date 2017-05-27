/*
 * Copyright (c) 2013, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.language.objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.Layouts;
import org.truffleruby.core.klass.ClassNodes;
import org.truffleruby.language.RubyConstant;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.CallDispatchHeadNode;
import org.truffleruby.language.dispatch.DispatchHeadNodeFactory;

public class DefineClassNode extends RubyNode {

    protected final String name;

    @Child private RubyNode superClassNode;
    @Child private RubyNode lexicalParentModule;
    @Child private LookupForExistingModuleNode lookupForExistingModuleNode;
    @Child private CallDispatchHeadNode inheritedNode;

    private final ConditionProfile needToDefineProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile noSuperClassSupplied = ConditionProfile.createBinaryProfile();
    private final BranchProfile errorProfile = BranchProfile.create();

    public DefineClassNode(
            String name,
            RubyNode lexicalParent, RubyNode superClass) {
        this.name = name;
        this.lexicalParentModule = lexicalParent;
        this.superClassNode = superClass;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object lexicalParentObject = lexicalParentModule.execute(frame);

        if (!RubyGuards.isRubyModule(lexicalParentObject)) {
            errorProfile.enter();
            throw new RaiseException(coreExceptions().typeErrorIsNotA(lexicalParentObject, "module", this));
        }

        final DynamicObject lexicalParentModule = (DynamicObject) lexicalParentObject;
        final DynamicObject suppliedSuperClass = executeSuperClass(frame);
        final RubyConstant constant = lookupForExistingModule(frame, name, lexicalParentModule);

        final DynamicObject definedClass;

        if (needToDefineProfile.profile(constant == null)) {
            final DynamicObject superClass;
            if (noSuperClassSupplied.profile(suppliedSuperClass == null)) {
                superClass = getContext().getCoreLibrary().getObjectClass();
            } else {
                superClass = suppliedSuperClass;
            }
            definedClass = ClassNodes.createInitializedRubyClass(getContext(), getEncapsulatingSourceSection(), lexicalParentModule, superClass, name);
            callInherited(frame, superClass, definedClass);
        } else {
            if (!RubyGuards.isRubyClass(constant.getValue())) {
                errorProfile.enter();
                throw new RaiseException(coreExceptions().typeErrorIsNotA(constant.getValue(), "class", this));
            }

            definedClass = (DynamicObject) constant.getValue();

            final DynamicObject currentSuperClass = ClassNodes.getSuperClass(definedClass);

            if (suppliedSuperClass != null && currentSuperClass != suppliedSuperClass) { // bug-compat with MRI https://bugs.ruby-lang.org/issues/12367
                errorProfile.enter();
                throw new RaiseException(coreExceptions().superclassMismatch(
                        Layouts.MODULE.getFields(definedClass).getName(), this));
            }
        }

        return definedClass;
    }

    private DynamicObject executeSuperClass(VirtualFrame frame) {
        if (superClassNode == null) {
            return null;
        }
        final Object superClassObject = superClassNode.execute(frame);

        if (!RubyGuards.isRubyClass(superClassObject)) {
            errorProfile.enter();
            throw new RaiseException(coreExceptions().typeError("superclass must be a Class", this));
        }

        final DynamicObject superClass = (DynamicObject) superClassObject;

        if (Layouts.CLASS.getIsSingleton(superClass)) {
            errorProfile.enter();
            throw new RaiseException(coreExceptions().typeError("can't make subclass of virtual class", this));
        }

        return superClass;
    }

    private void callInherited(VirtualFrame frame, DynamicObject superClass, DynamicObject childClass) {
        if (inheritedNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            inheritedNode = insert(DispatchHeadNodeFactory.createMethodCallOnSelf());
        }
        inheritedNode.call(frame, superClass, "inherited", childClass);
    }

    private RubyConstant lookupForExistingModule(VirtualFrame frame, String name, DynamicObject lexicalParent) {
        if (lookupForExistingModuleNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            lookupForExistingModuleNode = insert(LookupForExistingModuleNodeGen.create(null, null));
        }
        return lookupForExistingModuleNode.executeLookupForExistingModule(frame, name, lexicalParent);
    }

}
