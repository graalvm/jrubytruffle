/*
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */

package org.truffleruby.language.loader;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;
import org.jcodings.specific.UTF8Encoding;
import org.truffleruby.Log;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.rope.CodeRange;
import org.truffleruby.core.string.StringNodes;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.control.JavaException;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.CallDispatchHeadNode;
import org.truffleruby.language.methods.DeclarationContext;
import org.truffleruby.parser.ParserContext;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

@NodeChild("feature")
public abstract class RequireNode extends RubyNode {

    @Child private IndirectCallNode callNode = IndirectCallNode.create();
    @Child private CallDispatchHeadNode isInLoadedFeatures = CallDispatchHeadNode.createOnSelf();
    @Child private CallDispatchHeadNode addToLoadedFeatures = CallDispatchHeadNode.createOnSelf();

    @Child private Node isExecutableNode = Message.IS_EXECUTABLE.createNode();
    @Child private Node executeNode = Message.createExecute(0).createNode();

    public static RequireNode create() {
        return RequireNodeGen.create(null);
    }

    public abstract boolean executeRequire(String feature);

    @Specialization
    protected boolean require(String feature,
            @Cached("create()") BranchProfile errorProfile,
            @Cached("createBinaryProfile()") ConditionProfile isLoadedProfile,
            @Cached("create()") StringNodes.MakeStringNode makeStringNode) {
        final FeatureLoader featureLoader = getContext().getFeatureLoader();

        final String expandedPath = featureLoader.findFeature(feature);

        if (expandedPath == null) {
            errorProfile.enter();
            throw new RaiseException(getContext().getCoreExceptions().loadErrorCannotLoad(feature, this));
        }

        final DynamicObject pathString = makeStringNode.executeMake(expandedPath, UTF8Encoding.INSTANCE, CodeRange.CR_UNKNOWN);

        if (isLoadedProfile.profile(isFeatureLoaded(pathString))) {
            return false;
        }

        final ReentrantLockFreeingMap<String> fileLocks = featureLoader.getFileLocks();

        while (true) {
            final ReentrantLock lock = fileLocks.get(expandedPath);

            if (lock.isHeldByCurrentThread()) {
                // circular require
                // TODO (pitr-ch 20-Mar-2016): warn user
                return false;
            }

            if (!fileLocks.lock(this, getContext().getThreadManager(), expandedPath, lock)) {
                continue;
            }

            try {
                if (isFeatureLoaded(pathString)) {
                    return false;
                }

                final Source source;
                try {
                    source = getContext().getSourceLoader().load(expandedPath);
                } catch (IOException e) {
                    return false;
                }

                final String mimeType = getSourceMimeType(source);

                if (RubyLanguage.MIME_TYPE.equals(mimeType)) {
                    final RubyRootNode rootNode = getContext().getCodeLoader().parse(
                            source,
                            UTF8Encoding.INSTANCE,
                            ParserContext.TOP_LEVEL,
                            null,
                            true,
                            this);

                    final CodeLoader.DeferredCall deferredCall = getContext().getCodeLoader().prepareExecute(
                            ParserContext.TOP_LEVEL,
                            DeclarationContext.TOP_LEVEL,
                            rootNode,
                            null,
                            coreLibrary().getMainObject());

                    deferredCall.call(callNode);
                } else if (RubyLanguage.CEXT_MIME_TYPE.equals(mimeType)) {
                    requireCExtension(feature, expandedPath);
                } else {
                    errorProfile.enter();

                    if (StringUtils.toLowerCase(expandedPath).endsWith(".su")) {
                        throw new RaiseException(cextSupportNotAvailable(expandedPath));
                    } else {
                        throw new RaiseException(unknownLanguage(expandedPath, mimeType));
                    }
                }

                addToLoadedFeatures(pathString);

                return true;
            } finally {
                fileLocks.unlock(expandedPath, lock);
            }
        }
    }

    @TruffleBoundary
    private void requireCExtension(String feature, String expandedPath) {
        final FeatureLoader featureLoader = getContext().getFeatureLoader();

        try {
            featureLoader.ensureCExtImplementationLoaded(feature, this);

            if (getContext().getOptions().CEXTS_LOG_LOAD) {
                Log.LOGGER.info(String.format("loading cext module %s (requested as %s)", expandedPath, feature));
            }

            featureLoader.loadCExtLibrary(expandedPath);
        } catch (Exception e) {
            handleCExtensionException(feature, e);
            throw e;
        }

        final TruffleObject initFunction = getInitFunction(expandedPath);

        if (!ForeignAccess.sendIsExecutable(isExecutableNode, initFunction)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new UnsupportedOperationException(initFunction + "() is not IS_EXECUTABLE");
        }

        try {
            ForeignAccess.sendExecute(executeNode, initFunction);
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new JavaException(e);
        }
    }

    @TruffleBoundary
    private void handleCExtensionException(String feature, Exception e) {
        final UnsatisfiedLinkError linkErrorException = searchForException(UnsatisfiedLinkError.class, e);

        if (linkErrorException == null) {
            return;
        }

        final String linkError = linkErrorException.getMessage();

        if (getContext().getOptions().CEXTS_LOG_LOAD) {
            Log.LOGGER.info("unsatisfied link error " + linkError);
        }

        final String message;

        if (linkError.indexOf("libc++.") != -1) {
            message = String.format("%s (%s)", "you may need to install LLVM and libc++ - see doc/user/installing-llvm.md", linkError);
        } else if (linkError.indexOf("libc++abi.") != -1) {
            message = String.format("%s (%s)", "you may need to install LLVM and libc++abi - see doc/user/installing-llvm.md", linkError);
        } else if (feature.equals("openssl.so")) {
            message = String.format("%s (%s)", "you may need to install the system OpenSSL library libssl - see doc/user/installing-libssl.md", linkError);
        } else {
            message = linkError;
        }

        throw new RaiseException(getContext().getCoreExceptions().runtimeError(message, this));
    }

    private <T extends Throwable> T searchForException(Class<T> exceptionClass, Throwable exception) {
        while (exception != null) {
            if (exceptionClass.isInstance(exception)) {
                return exceptionClass.cast(exception);
            }
            exception = exception.getCause();
        }

        return null;
    }

    @TruffleBoundary
    private String getSourceMimeType(Source source) {
        return source.getMimeType();
    }

    @TruffleBoundary
    private DynamicObject cextSupportNotAvailable(String expandedPath) {
        return getContext().getCoreExceptions().internalError(
                "cext support is not available to load " + expandedPath, this);
    }

    @TruffleBoundary
    private DynamicObject unknownLanguage(String expandedPath, String mimeType) {
        return getContext().getCoreExceptions().internalError(
                "unknown language " + mimeType + " for " + expandedPath, this);
    }

    @TruffleBoundary
    private TruffleObject getInitFunction(String expandedPath) {
        final String initFunctionName = "Init_" + getBaseName(expandedPath);
        return getContext().getFeatureLoader().getCExtFunction(initFunctionName, expandedPath);
    }

    @TruffleBoundary
    private String getBaseName(String path) {
        final String name = new File(path).getName();
        final int firstDot = name.indexOf('.');
        if (firstDot == -1) {
            return name;
        } else {
            return name.substring(0, firstDot);
        }
    }

    public boolean isFeatureLoaded(DynamicObject feature) {
        final DynamicObject loadedFeatures = getContext().getCoreLibrary().getLoadedFeatures();
        synchronized (getContext().getFeatureLoader().getLoadedFeaturesLock()) {
            return isInLoadedFeatures.callBoolean(null, loadedFeatures, "include?", null, feature);
        }
    }

    private void addToLoadedFeatures(DynamicObject feature) {
        final DynamicObject loadedFeatures = coreLibrary().getLoadedFeatures();
        synchronized (getContext().getFeatureLoader().getLoadedFeaturesLock()) {
            addToLoadedFeatures.call(null, loadedFeatures, "<<", feature);
        }
    }

}
