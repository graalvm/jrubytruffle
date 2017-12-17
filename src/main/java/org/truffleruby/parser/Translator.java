/*
 * Copyright (c) 2014, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.parser;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.RubyContext;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.SourceIndexLength;
import org.truffleruby.language.arguments.CheckArityNode;
import org.truffleruby.language.arguments.CheckKeywordArityNode;
import org.truffleruby.language.arguments.ProfileArgumentNodeGen;
import org.truffleruby.language.arguments.ReadSelfNode;
import org.truffleruby.language.control.SequenceNode;
import org.truffleruby.language.literal.NilLiteralNode;
import org.truffleruby.language.locals.WriteLocalVariableNode;
import org.truffleruby.language.methods.Arity;
import org.truffleruby.language.objects.SelfNode;
import org.truffleruby.parser.ast.NilImplicitParseNode;
import org.truffleruby.parser.ast.ParseNode;
import org.truffleruby.parser.ast.visitor.AbstractNodeVisitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class Translator extends AbstractNodeVisitor<RubyNode> {

    protected final Node currentNode;
    protected final RubyContext context;
    protected final Source source;
    protected final ParserContext parserContext;

    public Translator(Node currentNode, RubyContext context, Source source, ParserContext parserContext) {
        this.currentNode = currentNode;
        this.context = context;
        this.source = source;
        this.parserContext = parserContext;
    }

    public static RubyNode sequence(SourceIndexLength sourceSection, List<RubyNode> sequence) {
        final List<RubyNode> flattened = flatten(sequence, true);

        if (flattened.isEmpty()) {
            final RubyNode literal = new NilLiteralNode(true);
            literal.unsafeSetSourceSection(sourceSection);
            return literal;
        } else if (flattened.size() == 1) {
            return flattened.get(0);
        } else {
            final RubyNode[] flatSequence = flattened.toArray(new RubyNode[flattened.size()]);

            final SourceIndexLength enclosingSourceSection = enclosing(sourceSection, flatSequence);
            return withSourceSection(enclosingSourceSection, new SequenceNode(flatSequence));
        }
    }

    public static SourceIndexLength enclosing(SourceIndexLength base, RubyNode... sequence) {
        if (base == null) {
            return base;
        }

        int start = base.getCharIndex();
        int end = base.getCharEnd();

        for (RubyNode node : sequence) {
            final SourceIndexLength sourceSection = node.getSourceIndexLength();

            if (sourceSection != null) {
                start = Integer.min(start, sourceSection.getCharIndex());
                end = Integer.max(end, sourceSection.getCharEnd());
            }
        }

        return new SourceIndexLength(start, end - start);
    }

    private static List<RubyNode> flatten(List<RubyNode> sequence, boolean allowTrailingNil) {
        final List<RubyNode> flattened = new ArrayList<>();

        for (int n = 0; n < sequence.size(); n++) {
            final boolean lastNode = n == sequence.size() - 1;
            final RubyNode node = sequence.get(n);

            if (node instanceof NilLiteralNode && ((NilLiteralNode) node).isImplicit()) {
                if (allowTrailingNil && lastNode) {
                    flattened.add(node);
                }
            } else if (node instanceof SequenceNode) {
                flattened.addAll(flatten(Arrays.asList(((SequenceNode) node).getSequence()), lastNode));
            } else {
                flattened.add(node);
            }
        }

        return flattened;
    }

    protected RubyNode nilNode(SourceIndexLength sourceSection) {
        final RubyNode literal = new NilLiteralNode(false);
        literal.unsafeSetSourceSection(sourceSection);
        return literal;
    }

    protected RubyNode translateNodeOrNil(SourceIndexLength sourceSection, ParseNode node) {
        final RubyNode rubyNode;
        if (node == null || node instanceof NilImplicitParseNode) {
            rubyNode = nilNode(sourceSection);
        } else {
            rubyNode = node.accept(this);
        }
        return rubyNode;
    }

    public static RubyNode createCheckArityNode(Arity arity) {
        if (!arity.acceptsKeywords()) {
            return new CheckArityNode(arity);
        } else {
            return new CheckKeywordArityNode(arity);
        }
    }

    public SourceSection translateSourceSection(Source source, SourceIndexLength sourceSection) {
        if (sourceSection == null) {
            return null;
        } else {
            return sourceSection.toSourceSection(source);
        }
    }

    public static RubyNode loadSelf(RubyContext context, TranslatorEnvironment environment) {
        final FrameSlot slot = environment.getFrameDescriptor().findOrAddFrameSlot(SelfNode.SELF_IDENTIFIER);
        return new WriteLocalVariableNode(slot, ProfileArgumentNodeGen.create(new ReadSelfNode()));
    }

    public static <T extends RubyNode> T withSourceSection(SourceIndexLength sourceSection, T node) {
        if (sourceSection != null) {
            node.unsafeSetSourceSection(sourceSection);
        }
        return node;
    }

}
