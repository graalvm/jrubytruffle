package org.truffleruby.core.thread;

import org.truffleruby.builtins.CoreClass;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.core.array.ArrayGuards;
import org.truffleruby.core.array.ArrayOperations;
import org.truffleruby.core.binding.BindingNodes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.object.DynamicObject;

@CoreClass("Truffle::ThreadOperations")
public class TruffleThreadNodes {

    @CoreMethod(names = "ruby_caller", isModuleFunction = true, required = 2, lowerFixnum = 1)
    @ImportStatic(ArrayGuards.class)
    public abstract static class FindRubyCaller extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(guards = "isRubyArray(modules)")
        public DynamicObject findRubyCaller(int skip, DynamicObject modules) {
            Object[] moduleArray = ArrayOperations.toObjectArray(modules);
            Frame rubyCaller = getContext().getCallStack().getCallerFrameNotInModules(moduleArray, skip).getFrame(FrameInstance.FrameAccess.MATERIALIZE);
            return rubyCaller == null ? nil() : BindingNodes.createBinding(getContext(), rubyCaller.materialize());
        }

    }
}
