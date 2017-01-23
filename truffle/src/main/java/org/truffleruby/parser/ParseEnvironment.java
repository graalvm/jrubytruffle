/*
 * Copyright (c) 2014, 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.parser;

import org.truffleruby.RubyContext;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.control.BreakID;
import org.truffleruby.language.control.ReturnID;

import java.io.File;

/**
 * Translator environment, unique per parse/translation.
 */
public class ParseEnvironment {

    private LexicalScope lexicalScope = null;
    private boolean dynamicConstantLookup = false;
    private final String corePath;

    public ParseEnvironment(RubyContext context) {
        if (context != null) {
            corePath = context.getCoreLibrary().getCoreLoadPath() + File.separator + "core" + File.separator;
        } else {
            corePath = null;
        }
    }

    public String getCorePath() {
        return corePath;
    }

    public void resetLexicalScope(LexicalScope lexicalScope) {
        this.lexicalScope = lexicalScope;
    }

    public LexicalScope getLexicalScope() {
        // TODO (eregon, 4 Dec. 2016): assert !dynamicConstantLookup;
        return lexicalScope;
    }

    public LexicalScope pushLexicalScope() {
        return lexicalScope = new LexicalScope(getLexicalScope());
    }

    public void popLexicalScope() {
        lexicalScope = getLexicalScope().getParent();
    }

    public boolean isDynamicConstantLookup() {
        return dynamicConstantLookup;
    }

    public void setDynamicConstantLookup(boolean dynamicConstantLookup) {
        this.dynamicConstantLookup = dynamicConstantLookup;
    }

    public ReturnID allocateReturnID() {
        return new ReturnID();
    }

    public BreakID allocateBreakID() {
        return new BreakID();
    }

}
