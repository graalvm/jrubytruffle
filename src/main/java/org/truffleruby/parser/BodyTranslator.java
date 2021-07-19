/*
 * Copyright (c) 2013, 2020 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.parser;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import org.jcodings.Encoding;
import org.joni.NameEntry;
import org.joni.Regex;
import org.joni.Syntax;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.builtins.PrimitiveNodeConstructor;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.IsNilNode;
import org.truffleruby.core.array.ArrayAppendOneNodeGen;
import org.truffleruby.core.array.ArrayConcatNode;
import org.truffleruby.core.array.ArrayLiteralNode;
import org.truffleruby.core.array.AssignableNode;
import org.truffleruby.core.array.MultipleAssignmentNode;
import org.truffleruby.core.array.NoopAssignableNode;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.core.cast.BooleanCastNodeGen;
import org.truffleruby.core.cast.HashCastNodeGen;
import org.truffleruby.core.cast.SplatCastNode;
import org.truffleruby.core.cast.SplatCastNodeGen;
import org.truffleruby.core.cast.StringToSymbolNodeGen;
import org.truffleruby.core.cast.ToProcNodeGen;
import org.truffleruby.core.cast.ToSNode;
import org.truffleruby.core.cast.ToSNodeGen;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.hash.ConcatHashLiteralNode;
import org.truffleruby.core.hash.HashLiteralNode;
import org.truffleruby.core.kernel.KernelNodesFactory;
import org.truffleruby.core.module.ModuleNodesFactory;
import org.truffleruby.core.numeric.BignumOperations;
import org.truffleruby.core.range.RangeNodesFactory;
import org.truffleruby.core.regexp.EncodingCache;
import org.truffleruby.core.regexp.InterpolatedRegexpNode;
import org.truffleruby.core.regexp.MatchDataNodes.GetIndexNode;
import org.truffleruby.core.regexp.RegexWarnDeferredCallback;
import org.truffleruby.core.regexp.RegexpNodes;
import org.truffleruby.core.regexp.RegexpOptions;
import org.truffleruby.core.regexp.RubyRegexp;
import org.truffleruby.core.regexp.TRegexCache;
import org.truffleruby.core.regexp.TruffleRegexpNodes;
import org.truffleruby.core.rope.LeafRope;
import org.truffleruby.core.rope.Rope;
import org.truffleruby.core.rope.RopeConstants;
import org.truffleruby.core.string.InterpolatedStringNode;
import org.truffleruby.core.support.TypeNodes;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.Nil;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.SourceIndexLength;
import org.truffleruby.language.constants.OrAssignConstantNode;
import org.truffleruby.language.constants.ReadConstantNode;
import org.truffleruby.language.constants.ReadConstantWithDynamicScopeNode;
import org.truffleruby.language.constants.ReadConstantWithLexicalScopeNode;
import org.truffleruby.language.constants.WriteConstantNode;
import org.truffleruby.language.control.AndNode;
import org.truffleruby.language.control.AnyCaseNode;
import org.truffleruby.language.control.BreakID;
import org.truffleruby.language.control.BreakNode;
import org.truffleruby.language.control.DeferredRaiseException;
import org.truffleruby.language.control.DynamicReturnNode;
import org.truffleruby.language.control.FrameOnStackNode;
import org.truffleruby.language.control.IfElseNode;
import org.truffleruby.language.control.IfNode;
import org.truffleruby.language.control.InvalidReturnNode;
import org.truffleruby.language.control.LocalReturnNode;
import org.truffleruby.language.control.NextNode;
import org.truffleruby.language.control.NotNode;
import org.truffleruby.language.control.OnceNode;
import org.truffleruby.language.control.OrLazyValueDefinedNode;
import org.truffleruby.language.control.OrNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.control.RedoNode;
import org.truffleruby.language.control.RetryNode;
import org.truffleruby.language.control.ReturnID;
import org.truffleruby.language.control.UnlessNode;
import org.truffleruby.language.control.WhileNode;
import org.truffleruby.language.defined.DefinedNode;
import org.truffleruby.language.defined.DefinedWrapperNode;
import org.truffleruby.language.dispatch.RubyCallNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;
import org.truffleruby.language.exceptions.EnsureNode;
import org.truffleruby.language.exceptions.RescueStandardErrorNode;
import org.truffleruby.language.exceptions.RescueClassesNode;
import org.truffleruby.language.exceptions.RescueNode;
import org.truffleruby.language.exceptions.RescueSplatNode;
import org.truffleruby.language.exceptions.TryNode;
import org.truffleruby.language.globals.AliasGlobalVarNode;
import org.truffleruby.language.globals.ReadGlobalVariableNodeGen;
import org.truffleruby.language.globals.ReadMatchReferenceNodes;
import org.truffleruby.language.globals.WriteGlobalVariableNodeGen;
import org.truffleruby.language.literal.BooleanLiteralNode;
import org.truffleruby.language.literal.FloatLiteralNode;
import org.truffleruby.language.literal.FrozenStringLiteralNode;
import org.truffleruby.language.literal.IntegerFixnumLiteralNode;
import org.truffleruby.language.literal.LongFixnumLiteralNode;
import org.truffleruby.language.literal.NilLiteralNode;
import org.truffleruby.language.literal.ObjectClassLiteralNode;
import org.truffleruby.language.literal.ObjectLiteralNode;
import org.truffleruby.language.literal.RangeClassLiteralNode;
import org.truffleruby.language.literal.StringLiteralNode;
import org.truffleruby.language.literal.TruffleInternalModuleLiteralNode;
import org.truffleruby.language.literal.TruffleKernelOperationsModuleLiteralNode;
import org.truffleruby.language.locals.DeclarationFlipFlopStateNode;
import org.truffleruby.language.locals.FlipFlopNode;
import org.truffleruby.language.locals.FlipFlopStateNode;
import org.truffleruby.language.locals.InitFlipFlopSlotNode;
import org.truffleruby.language.locals.LocalFlipFlopStateNode;
import org.truffleruby.language.locals.ReadLocalNode;
import org.truffleruby.language.locals.WriteLocalNode;
import org.truffleruby.language.methods.Arity;
import org.truffleruby.language.methods.BlockDefinitionNode;
import org.truffleruby.language.methods.CatchBreakNode;
import org.truffleruby.language.methods.ExceptionTranslatingNode;
import org.truffleruby.language.methods.GetDefaultDefineeNode;
import org.truffleruby.language.methods.LiteralMethodDefinitionNode;
import org.truffleruby.language.methods.ModuleBodyDefinitionNode;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.language.methods.Split;
import org.truffleruby.language.objects.DefineClassNode;
import org.truffleruby.language.objects.DefineModuleNode;
import org.truffleruby.language.objects.DefineModuleNodeGen;
import org.truffleruby.language.objects.DynamicLexicalScopeNode;
import org.truffleruby.language.objects.GetDynamicLexicalScopeNode;
import org.truffleruby.language.objects.InsideModuleDefinitionNode;
import org.truffleruby.language.objects.LexicalScopeNode;
import org.truffleruby.language.objects.classvariables.ReadClassVariableNode;
import org.truffleruby.language.objects.ReadInstanceVariableNode;
import org.truffleruby.language.objects.RunModuleDefinitionNode;
import org.truffleruby.language.objects.SelfNode;
import org.truffleruby.language.objects.SingletonClassNode;
import org.truffleruby.language.objects.SingletonClassNodeGen;
import org.truffleruby.language.objects.classvariables.WriteClassVariableNode;
import org.truffleruby.language.objects.WriteInstanceVariableNode;
import org.truffleruby.language.yield.YieldExpressionNode;
import org.truffleruby.parser.ast.AliasParseNode;
import org.truffleruby.parser.ast.AndParseNode;
import org.truffleruby.parser.ast.ArgsCatParseNode;
import org.truffleruby.parser.ast.ArgsParseNode;
import org.truffleruby.parser.ast.ArgsPushParseNode;
import org.truffleruby.parser.ast.ArgumentParseNode;
import org.truffleruby.parser.ast.ArrayParseNode;
import org.truffleruby.parser.ast.AssignableParseNode;
import org.truffleruby.parser.ast.AttrAssignParseNode;
import org.truffleruby.parser.ast.BackRefParseNode;
import org.truffleruby.parser.ast.BeginParseNode;
import org.truffleruby.parser.ast.BigRationalParseNode;
import org.truffleruby.parser.ast.BignumParseNode;
import org.truffleruby.parser.ast.BlockParseNode;
import org.truffleruby.parser.ast.BlockPassParseNode;
import org.truffleruby.parser.ast.BreakParseNode;
import org.truffleruby.parser.ast.CallParseNode;
import org.truffleruby.parser.ast.CaseInParseNode;
import org.truffleruby.parser.ast.CaseParseNode;
import org.truffleruby.parser.ast.ClassParseNode;
import org.truffleruby.parser.ast.ClassVarAsgnParseNode;
import org.truffleruby.parser.ast.ClassVarParseNode;
import org.truffleruby.parser.ast.Colon2ConstParseNode;
import org.truffleruby.parser.ast.Colon2ImplicitParseNode;
import org.truffleruby.parser.ast.Colon2ParseNode;
import org.truffleruby.parser.ast.Colon3ParseNode;
import org.truffleruby.parser.ast.ComplexParseNode;
import org.truffleruby.parser.ast.ConstDeclParseNode;
import org.truffleruby.parser.ast.ConstParseNode;
import org.truffleruby.parser.ast.DAsgnParseNode;
import org.truffleruby.parser.ast.DRegexpParseNode;
import org.truffleruby.parser.ast.DStrParseNode;
import org.truffleruby.parser.ast.DSymbolParseNode;
import org.truffleruby.parser.ast.DVarParseNode;
import org.truffleruby.parser.ast.DXStrParseNode;
import org.truffleruby.parser.ast.DefinedParseNode;
import org.truffleruby.parser.ast.DefnParseNode;
import org.truffleruby.parser.ast.DefsParseNode;
import org.truffleruby.parser.ast.DotParseNode;
import org.truffleruby.parser.ast.EncodingParseNode;
import org.truffleruby.parser.ast.EnsureParseNode;
import org.truffleruby.parser.ast.EvStrParseNode;
import org.truffleruby.parser.ast.FCallParseNode;
import org.truffleruby.parser.ast.FalseParseNode;
import org.truffleruby.parser.ast.FixnumParseNode;
import org.truffleruby.parser.ast.FlipParseNode;
import org.truffleruby.parser.ast.FloatParseNode;
import org.truffleruby.parser.ast.ForParseNode;
import org.truffleruby.parser.ast.GlobalAsgnParseNode;
import org.truffleruby.parser.ast.GlobalVarParseNode;
import org.truffleruby.parser.ast.HashParseNode;
import org.truffleruby.parser.ast.IArgumentNode;
import org.truffleruby.parser.ast.IfParseNode;
import org.truffleruby.parser.ast.InParseNode;
import org.truffleruby.parser.ast.InstAsgnParseNode;
import org.truffleruby.parser.ast.InstVarParseNode;
import org.truffleruby.parser.ast.IterParseNode;
import org.truffleruby.parser.ast.LambdaParseNode;
import org.truffleruby.parser.ast.ListParseNode;
import org.truffleruby.parser.ast.LiteralParseNode;
import org.truffleruby.parser.ast.LocalAsgnParseNode;
import org.truffleruby.parser.ast.LocalVarParseNode;
import org.truffleruby.parser.ast.Match2ParseNode;
import org.truffleruby.parser.ast.Match3ParseNode;
import org.truffleruby.parser.ast.MatchParseNode;
import org.truffleruby.parser.ast.MethodDefParseNode;
import org.truffleruby.parser.ast.ModuleParseNode;
import org.truffleruby.parser.ast.MultipleAsgnParseNode;
import org.truffleruby.parser.ast.NextParseNode;
import org.truffleruby.parser.ast.NilImplicitParseNode;
import org.truffleruby.parser.ast.NilParseNode;
import org.truffleruby.parser.ast.NodeType;
import org.truffleruby.parser.ast.NthRefParseNode;
import org.truffleruby.parser.ast.OpAsgnAndParseNode;
import org.truffleruby.parser.ast.OpAsgnConstDeclParseNode;
import org.truffleruby.parser.ast.OpAsgnOrParseNode;
import org.truffleruby.parser.ast.OpAsgnParseNode;
import org.truffleruby.parser.ast.OpElementAsgnParseNode;
import org.truffleruby.parser.ast.OrParseNode;
import org.truffleruby.parser.ast.ParseNode;
import org.truffleruby.parser.ast.PostExeParseNode;
import org.truffleruby.parser.ast.PreExeParseNode;
import org.truffleruby.parser.ast.RationalParseNode;
import org.truffleruby.parser.ast.RedoParseNode;
import org.truffleruby.parser.ast.RegexpParseNode;
import org.truffleruby.parser.ast.RescueBodyParseNode;
import org.truffleruby.parser.ast.RescueParseNode;
import org.truffleruby.parser.ast.RetryParseNode;
import org.truffleruby.parser.ast.ReturnParseNode;
import org.truffleruby.parser.ast.SClassParseNode;
import org.truffleruby.parser.ast.SValueParseNode;
import org.truffleruby.parser.ast.SelfParseNode;
import org.truffleruby.parser.ast.SideEffectFree;
import org.truffleruby.parser.ast.SplatParseNode;
import org.truffleruby.parser.ast.StarParseNode;
import org.truffleruby.parser.ast.StrParseNode;
import org.truffleruby.parser.ast.SymbolParseNode;
import org.truffleruby.parser.ast.TrueParseNode;
import org.truffleruby.parser.ast.TruffleFragmentParseNode;
import org.truffleruby.parser.ast.UndefParseNode;
import org.truffleruby.parser.ast.UntilParseNode;
import org.truffleruby.parser.ast.VAliasParseNode;
import org.truffleruby.parser.ast.VCallParseNode;
import org.truffleruby.parser.ast.WhenOneArgParseNode;
import org.truffleruby.parser.ast.WhenParseNode;
import org.truffleruby.parser.ast.WhileParseNode;
import org.truffleruby.parser.ast.XStrParseNode;
import org.truffleruby.parser.ast.YieldParseNode;
import org.truffleruby.parser.ast.ZArrayParseNode;
import org.truffleruby.parser.parser.ParseNodeTuple;
import org.truffleruby.parser.parser.ParserSupport;
import org.truffleruby.parser.scope.StaticScope;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/** A JRuby parser node visitor which translates JRuby AST nodes into truffle Nodes. */
public class BodyTranslator extends Translator {

    public static final ToSNode[] EMPTY_TO_S_NODE_ARRAY = new ToSNode[0];
    public static final RescueNode[] EMPTY_RESCUE_NODE_ARRAY = new RescueNode[0];

    protected final BodyTranslator parent;
    protected final TranslatorEnvironment environment;
    private final RubyDeferredWarnings rubyWarnings;

    public boolean translatingForStatement = false;
    private boolean translatingNextExpression = false;
    private boolean translatingWhile = false;
    protected String currentCallMethodName = null;

    public BodyTranslator(
            RubyLanguage language,
            BodyTranslator parent,
            TranslatorEnvironment environment,
            Source source,
            ParserContext parserContext,
            Node currentNode,
            RubyDeferredWarnings rubyWarnings) {
        super(language, source, parserContext, currentNode);
        this.parent = parent;
        this.environment = environment;
        this.rubyWarnings = rubyWarnings;
    }

    private static RubyNode[] createArray(int size) {
        return size == 0 ? RubyNode.EMPTY_ARRAY : new RubyNode[size];
    }

    private RubyNode translateNameNodeToSymbol(ParseNode node) {
        if (node instanceof LiteralParseNode) {
            return new ObjectLiteralNode(language.getSymbol(((LiteralParseNode) node).getName()));
        } else if (node instanceof SymbolParseNode) {
            return node.accept(this);
        } else {
            throw new UnsupportedOperationException(node.getClass().getName());
        }
    }

    @Override
    public RubyNode visitAliasNode(AliasParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode oldNameNode = translateNameNodeToSymbol(node.getOldName());
        final RubyNode newNameNode = translateNameNodeToSymbol(node.getNewName());

        final RubyNode ret = ModuleNodesFactory.AliasMethodNodeFactory.create(
                TypeNodes.CheckFrozenNode.create(new GetDefaultDefineeNode()),
                newNameNode,
                oldNameNode);

        ret.unsafeSetSourceSection(sourceSection);

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitVAliasNode(VAliasParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final RubyNode ret = new AliasGlobalVarNode(node.getOldName(), node.getNewName());

        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitAndNode(AndParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode x = translateNodeOrNil(sourceSection, node.getFirstNode());
        final RubyNode y = translateNodeOrNil(sourceSection, node.getSecondNode());

        final RubyNode ret = new AndNode(x, y);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitArgsCatNode(ArgsCatParseNode node) {
        final List<ParseNode> nodes = new ArrayList<>();
        collectArgsCatNodes(nodes, node);

        final List<RubyNode> translatedNodes = new ArrayList<>();

        for (ParseNode catNode : nodes) {
            translatedNodes.add(catNode.accept(this));
        }

        final RubyNode ret = new ArrayConcatNode(translatedNodes.toArray(RubyNode.EMPTY_ARRAY));
        ret.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, ret);
    }

    // ArgsCatNodes can be nested - this collects them into a flat list of children
    private void collectArgsCatNodes(List<ParseNode> nodes, ArgsCatParseNode node) {
        if (node.getFirstNode() instanceof ArgsCatParseNode) {
            collectArgsCatNodes(nodes, (ArgsCatParseNode) node.getFirstNode());
        } else {
            nodes.add(node.getFirstNode());
        }

        if (node.getSecondNode() instanceof ArgsCatParseNode) {
            collectArgsCatNodes(nodes, (ArgsCatParseNode) node.getSecondNode());
        } else {
            // ArgsCatParseNode implicitly splat its second argument. See Helpers.argsCat.
            ParseNode secondNode = new SplatParseNode(node.getSecondNode().getPosition(), node.getSecondNode());
            nodes.add(secondNode);
        }
    }

    @Override
    public RubyNode visitArgsPushNode(ArgsPushParseNode node) {
        final RubyNode args = node.getFirstNode().accept(this);
        final RubyNode value = node.getSecondNode().accept(this);
        final RubyNode ret = ArrayAppendOneNodeGen.create(
                KernelNodesFactory.DupNodeFactory.create(new RubyNode[]{ args }),
                value);

        ret.unsafeSetSourceSection(node.getPosition());

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitArrayNode(ArrayParseNode node) {
        final ParseNode[] values = node.children();

        final RubyNode[] translatedValues = createArray(values.length);

        for (int n = 0; n < values.length; n++) {
            translatedValues[n] = values[n].accept(this);
        }

        final RubyNode ret = ArrayLiteralNode.create(language, translatedValues);
        ret.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitAttrAssignNode(AttrAssignParseNode node) {
        final CallParseNode callNode = new CallParseNode(
                node.getPosition(),
                node.getReceiverNode(),
                node.getName(),
                node.getArgsNode(),
                null,
                node.isLazy());

        copyNewline(node, callNode);
        final RubyNode actualCall = translateCallNode(callNode, node.isSelf(), false, true);

        return addNewlineIfNeeded(node, actualCall);
    }

    @Override
    public RubyNode visitBeginNode(BeginParseNode node) {
        final RubyNode ret = node.getBodyNode().accept(this);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitBignumNode(BignumParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        // These aren't always Bignums!

        final BigInteger value = node.getValue();
        final RubyNode ret = bignumOrFixnumNode(value);
        ret.unsafeSetSourceSection(sourceSection);

        return addNewlineIfNeeded(node, ret);
    }

    private RubyNode bignumOrFixnumNode(BigInteger value) {
        if (value.bitLength() >= 64) {
            return new ObjectLiteralNode(BignumOperations.createBignum(value));
        } else {
            return new LongFixnumLiteralNode(value.longValue());
        }
    }

    @Override
    public RubyNode visitBlockNode(BlockParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final List<RubyNode> translatedChildren = new ArrayList<>();

        final int start = node.getPosition().getCharIndex();
        int end = node.getPosition().getCharEnd();

        for (ParseNode child : node.children()) {
            if (child.getPosition().isAvailable()) {
                end = Math.max(end, child.getPosition().getCharEnd());
            }

            final RubyNode translatedChild = translateNodeOrNil(sourceSection, child);

            if (!(translatedChild instanceof DeadNode)) {
                translatedChildren.add(translatedChild);
            }
        }

        final RubyNode ret;

        if (translatedChildren.size() == 1) {
            ret = translatedChildren.get(0);
        } else {
            ret = sequence(new SourceIndexLength(start, end - start), translatedChildren);
        }

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitBreakNode(BreakParseNode node) {
        assert environment.isBlock() || translatingWhile : "The parser did not see an invalid break";
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode resultNode = translateNodeOrNil(sourceSection, node.getValueNode());

        final RubyNode ret = new BreakNode(environment.getBreakID(), translatingWhile, resultNode);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitCallNode(CallParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final ParseNode receiver = node.getReceiverNode();
        final String methodName = node.getName();

        if (receiver instanceof StrParseNode && methodName.equals("freeze")) {
            final StrParseNode strNode = (StrParseNode) receiver;
            final Rope nodeRope = strNode.getValue();
            final ImmutableRubyString frozenString = language
                    .getFrozenStringLiteral(nodeRope.getBytes(), nodeRope.getEncoding(), strNode.getCodeRange());
            return addNewlineIfNeeded(node, withSourceSection(
                    sourceSection,
                    new FrozenStringLiteralNode(frozenString, language.coreStrings.METHOD)));
        }

        if (environment.getParseEnvironment().canUsePrimitives() &&
                receiver instanceof ConstParseNode &&
                ((ConstParseNode) receiver).getName().equals("Primitive")) {
            final RubyNode ret = translateInvokePrimitive(sourceSection, node);
            return addNewlineIfNeeded(node, ret);
        }

        // If the receiver is a literal 'self' then we can call private methods
        final boolean ignoreVisibility = receiver instanceof SelfParseNode;

        final RubyNode translated = translateCallNode(node, ignoreVisibility, false, false);

        // TODO CS 23-Apr-19 I've tried to design logic so we never try to assign source sections twice
        //  but can't figure it out here
        if (!translated.hasSource()) {
            translated.unsafeSetSourceSection(sourceSection);
        }

        return addNewlineIfNeeded(node, translated);
    }

    private RubyNode translateInvokePrimitive(SourceIndexLength sourceSection, CallParseNode node) {
        /* Translates something that looks like
         *
         * Primitive.foo arg1, arg2, argN
         *
         * into
         *
         * InvokePrimitiveNode(FooNode(arg1, arg2, ..., argN)) */

        final String primitiveName = node.getName();

        final PrimitiveNodeConstructor primitive = language.primitiveManager.getPrimitive(primitiveName);

        final ArrayParseNode args = (ArrayParseNode) node.getArgsNode();
        final int size = args != null ? args.size() : 0;
        final RubyNode[] arguments = new RubyNode[size];
        for (int n = 0; n < size; n++) {
            arguments[n] = args.get(n).accept(this);
        }

        return primitive.createInvokePrimitiveNode(source, sourceSection, arguments);
    }

    private RubyNode translateCallNode(CallParseNode node, boolean ignoreVisibility, boolean isVCall,
            boolean isAttrAssign) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode receiver = node.getReceiverNode().accept(this);

        ParseNode args = node.getArgsNode();
        ParseNode block = node.getIterNode();

        if (block == null && args instanceof IterParseNode) {
            block = args;
            args = null;
        }

        final String methodName = node.getName();
        final ArgumentsAndBlockTranslation argumentsAndBlock = translateArgumentsAndBlock(
                sourceSection,
                block,
                args,
                methodName);

        final List<RubyNode> children = new ArrayList<>();

        if (argumentsAndBlock.getBlock() != null) {
            children.add(argumentsAndBlock.getBlock());
        }

        children.addAll(Arrays.asList(argumentsAndBlock.getArguments()));

        final SourceIndexLength enclosingSourceSection = enclosing(
                sourceSection,
                children.toArray(RubyNode.EMPTY_ARRAY));

        RubyCallNodeParameters callParameters = new RubyCallNodeParameters(
                receiver,
                methodName,
                argumentsAndBlock.getBlock(),
                argumentsAndBlock.getArguments(),
                argumentsAndBlock.isSplatted(),
                ignoreVisibility,
                isVCall,
                node.isLazy(),
                isAttrAssign);
        RubyNode translated = Translator.withSourceSection(
                enclosingSourceSection,
                language.coreMethodAssumptions.createCallNode(callParameters, environment));

        translated = wrapCallWithLiteralBlock(argumentsAndBlock, translated);

        return addNewlineIfNeeded(node, translated);
    }

    protected RubyNode wrapCallWithLiteralBlock(ArgumentsAndBlockTranslation argumentsAndBlock, RubyNode callNode) {
        if (argumentsAndBlock.getBlock() instanceof BlockDefinitionNode) { // if we have a literal block, break breaks out of this call site
            callNode = new FrameOnStackNode(callNode, argumentsAndBlock.getFrameOnStackMarkerSlot());
            final BlockDefinitionNode blockDef = (BlockDefinitionNode) argumentsAndBlock.getBlock();
            return new CatchBreakNode(blockDef.getBreakID(), callNode, false);
        } else {
            return callNode;
        }
    }

    protected static class ArgumentsAndBlockTranslation {

        private final RubyNode block;
        private final RubyNode[] arguments;
        private final boolean isSplatted;
        private final FrameSlot frameOnStackMarkerSlot;

        public ArgumentsAndBlockTranslation(
                RubyNode block,
                RubyNode[] arguments,
                boolean isSplatted,
                FrameSlot frameOnStackMarkerSlot) {
            super();
            this.block = block;
            this.arguments = arguments;
            this.isSplatted = isSplatted;
            this.frameOnStackMarkerSlot = frameOnStackMarkerSlot;
        }

        public RubyNode getBlock() {
            return block;
        }

        public RubyNode[] getArguments() {
            return arguments;
        }

        public boolean isSplatted() {
            return isSplatted;
        }

        public FrameSlot getFrameOnStackMarkerSlot() {
            return frameOnStackMarkerSlot;
        }
    }

    public static final Object BAD_FRAME_SLOT = new Object();
    private static final ParseNode[] EMPTY_ARGUMENTS = ParseNode.EMPTY_ARRAY;

    public Deque<Object> frameOnStackMarkerSlotStack = new ArrayDeque<>();

    protected ArgumentsAndBlockTranslation translateArgumentsAndBlock(SourceIndexLength sourceSection,
            ParseNode iterNode, ParseNode argsNode, String nameToSetWhenTranslatingBlock) {
        assert !(argsNode instanceof IterParseNode);

        final ParseNode[] arguments;
        boolean isSplatted = false;

        if (argsNode == null) {
            // No arguments
            arguments = EMPTY_ARGUMENTS;
        } else if (argsNode instanceof ArrayParseNode) {
            // Multiple arguments
            arguments = ((ArrayParseNode) argsNode).children();
        } else if (argsNode instanceof SplatParseNode || argsNode instanceof ArgsCatParseNode ||
                argsNode instanceof ArgsPushParseNode) {
            isSplatted = true;
            arguments = new ParseNode[]{ argsNode };
        } else {
            throw new UnsupportedOperationException("Unknown argument node type: " + argsNode.getClass());
        }

        final RubyNode[] argumentsTranslated = createArray(arguments.length);
        for (int i = 0; i < arguments.length; i++) {
            argumentsTranslated[i] = arguments[i].accept(this);
        }

        // If the last argument is a splat, do not copy the array, to support m(*args, &args.pop)
        if (isSplatted) {
            final RubyNode last = argumentsTranslated[argumentsTranslated.length - 1];
            if (last instanceof SplatCastNode) {
                ((SplatCastNode) last).doNotCopy();
            }
        }

        ParseNode blockPassNode = null;
        if (iterNode instanceof BlockPassParseNode) {
            blockPassNode = ((BlockPassParseNode) iterNode).getBodyNode();
        }

        currentCallMethodName = nameToSetWhenTranslatingBlock;

        final FrameSlot frameOnStackMarkerSlot;
        RubyNode blockTranslated;

        if (blockPassNode != null) {
            blockTranslated = ToProcNodeGen.create(blockPassNode.accept(this));
            blockTranslated.unsafeSetSourceSection(sourceSection);
            frameOnStackMarkerSlot = null;
        } else if (iterNode != null) {
            frameOnStackMarkerSlot = environment.declareVar(environment.allocateLocalTemp("frame_on_stack_marker"));

            frameOnStackMarkerSlotStack.push(frameOnStackMarkerSlot);
            try {
                blockTranslated = iterNode.accept(this);
            } finally {
                frameOnStackMarkerSlotStack.pop();
            }

            if (blockTranslated instanceof ObjectLiteralNode &&
                    ((ObjectLiteralNode) blockTranslated).getObject() == Nil.INSTANCE) {
                blockTranslated = null;
            }
        } else {
            blockTranslated = null;
            frameOnStackMarkerSlot = null;
        }

        currentCallMethodName = null;

        return new ArgumentsAndBlockTranslation(
                blockTranslated,
                argumentsTranslated,
                isSplatted,
                frameOnStackMarkerSlot);
    }

    @Override
    public RubyNode visitCaseNode(CaseParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        RubyNode elseNode = translateNodeOrNil(sourceSection, node.getElseNode());

        /* There are two sorts of case - one compares a list of expressions against a value, the other just checks a
         * list of expressions for truth. */

        final RubyNode ret;

        if (node.getCaseNode() != null) {
            // Evaluate the case expression and store it in a local

            final String tempName = environment.allocateLocalTemp("case");
            final ReadLocalNode readTemp = environment.findLocalVarNode(tempName, sourceSection);
            final RubyNode assignTemp = readTemp.makeWriteNode(node.getCaseNode().accept(this));

            final RubyNode[] conditions = new RubyNode[node.getCases().size()];
            final RubyNode[] bodies = new RubyNode[node.getCases().size()];

            for (int n = 0; n < node.getCases().size(); n++) {
                final WhenParseNode when = (WhenParseNode) node.getCases().get(n);

                // JRuby AST always gives WhenParseNode with only one expression.
                // "when 1,2; body" gets translated to 2 WhenParseNode.
                final ParseNode expressionNode = when.getExpressionNodes();
                final RubyNode rubyExpression = expressionNode.accept(this);

                final RubyNode receiver;
                final String method;
                final RubyNode[] arguments;
                if (when instanceof WhenOneArgParseNode) {
                    receiver = rubyExpression;
                    method = "===";
                    arguments = new RubyNode[]{ NodeUtil.cloneNode(readTemp) };
                } else {
                    receiver = new TruffleInternalModuleLiteralNode();
                    receiver.unsafeSetSourceSection(sourceSection);
                    method = "when_splat";
                    arguments = new RubyNode[]{ rubyExpression, NodeUtil.cloneNode(readTemp) };
                }
                final RubyCallNodeParameters callParameters = new RubyCallNodeParameters(
                        receiver,
                        method,
                        null,
                        arguments,
                        false,
                        true);
                final RubyNode conditionNode = language.coreMethodAssumptions
                        .createCallNode(callParameters, environment);

                // Create the if node
                final RubyNode thenNode = translateNodeOrNil(sourceSection, when.getBodyNode());

                conditions[n] = conditionNode;
                bodies[n] = thenNode;
            }

            final WhenParseNode firstWhen = (WhenParseNode) node.getCases().get(0);
            final boolean anyCase = firstWhen instanceof WhenOneArgParseNode
                    && firstWhen.getExpressionNodes() instanceof SymbolParseNode
                    && ((SymbolParseNode) firstWhen.getExpressionNodes()).getName().equals("truffleruby_any_case");

            if (anyCase) {
                final BooleanCastNode[] conditionCasts = new BooleanCastNode[conditions.length];
                for (int n = 0; n < conditionCasts.length; n++) {
                    conditionCasts[n] = BooleanCastNodeGen.create(conditions[n]);
                }

                ret = sequence(sourceSection, Arrays.asList(assignTemp, new AnyCaseNode(conditionCasts, bodies, elseNode)));
            } else {
                /* Build an if expression from the whens and else. Work backwards because the first if contains all the
                 * others in its else clause. */

                for (int n = node.getCases().size() - 1; n >= 0; n--) {
                    // Create the if node
                    final RubyNode thenNode = bodies[n];
                    final IfElseNode ifNode = new IfElseNode(conditions[n], thenNode, elseNode);

                    // This if becomes the else for the next if
                    elseNode = ifNode;
                }

                final RubyNode ifNode = elseNode;

                // A top-level block assigns the temp then runs the if
                ret = sequence(sourceSection, Arrays.asList(assignTemp, ifNode));
            }
        } else {
            for (int n = node.getCases().size() - 1; n >= 0; n--) {
                final WhenParseNode when = (WhenParseNode) node.getCases().get(n);

                // JRuby AST always gives WhenParseNode with only one expression.
                // "when 1,2; body" gets translated to 2 WhenParseNode.
                final ParseNode expressionNode = when.getExpressionNodes();
                final RubyNode conditionNode = expressionNode.accept(this);

                // Create the if node
                final RubyNode thenNode = when.getBodyNode().accept(this);
                final IfElseNode ifNode = new IfElseNode(conditionNode, thenNode, elseNode);

                // This if becomes the else for the next if
                elseNode = ifNode;
            }

            ret = elseNode;
        }

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitCaseInNode(CaseInParseNode node) {
        if (!RubyLanguage.getCurrentContext().getOptions().PATTERN_MATCHING) {
            final RubyContext context = RubyLanguage.getCurrentContext();
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().syntaxError(
                            "syntax error, unexpected keyword_in",
                            currentNode,
                            node.getPosition().toSourceSection(source)));
        }

        final SourceIndexLength sourceSection = node.getPosition();

        RubyNode elseNode = translateNodeOrNil(sourceSection, node.getElseNode());

        final RubyNode ret;

        // Evaluate the case expression and store it in a local

        final String tempName = environment.allocateLocalTemp("case");
        final ReadLocalNode readTemp = environment.findLocalVarNode(tempName, sourceSection);
        final RubyNode assignTemp = readTemp.makeWriteNode(node.getCaseNode().accept(this));

        /* Build an if expression from the ins and else. Work backwards because the first if contains all the others in
         * its else clause. */

        for (int n = node.getCases().size() - 1; n >= 0; n--) {
            final InParseNode in = (InParseNode) node.getCases().get(n);

            // JRuby AST always gives InParseNode with only one expression.
            // "in 1,2; body" gets translated to 2 InParseNode. This is a bug from
            // us we-using the 'when' parser for 'in' temporarily.
            final ParseNode patternNode = in.getExpressionNodes();

            final RubyNode conditionNode = caseInPatternMatch(patternNode, node.getCaseNode(), readTemp, sourceSection);

            // Create the if node
            final RubyNode thenNode = translateNodeOrNil(sourceSection, in.getBodyNode());
            final IfElseNode ifNode = new IfElseNode(conditionNode, thenNode, elseNode);

            // This if becomes the else for the next if
            elseNode = ifNode;
        }

        final RubyNode ifNode = elseNode;

        // A top-level block assigns the temp then runs the if
        ret = sequence(sourceSection, Arrays.asList(assignTemp, ifNode));

        return addNewlineIfNeeded(node, ret);
    }

    private RubyNode caseInPatternMatch(ParseNode patternNode, ParseNode expressionNode, RubyNode expressionValue,
            SourceIndexLength sourceSection) {
        final RubyCallNodeParameters deconstructCallParameters;
        final RubyCallNodeParameters matcherCallParameters;
        final RubyNode receiver;
        final RubyNode deconstructed;

        switch (patternNode.getNodeType()) {
            case ARRAYNODE:
                // Pattern-match element-wise recursively if possible.
                final int size = ((ArrayParseNode) patternNode).size();
                if (expressionNode.getNodeType() == NodeType.ARRAYNODE &&
                        ((ArrayParseNode) expressionNode).size() == size) {
                    final ParseNode[] patternElements = ((ArrayParseNode) patternNode).children();
                    final ParseNode[] expressionElements = ((ArrayParseNode) expressionNode).children();

                    final RubyNode[] matches = new RubyNode[size];

                    // For each element of the case expression, evaluate and assign it, then run the pattern-matching
                    // on the element
                    for (int n = 0; n < size; n++) {
                        final String tempName = environment.allocateLocalTemp("caseElem" + n);
                        final ReadLocalNode readTemp = environment.findLocalVarNode(tempName, sourceSection);
                        final RubyNode assignTemp = readTemp.makeWriteNode(expressionElements[n].accept(this));
                        matches[n] = sequence(sourceSection, Arrays.asList(
                                assignTemp,
                                caseInPatternMatch(
                                        patternElements[n],
                                        expressionElements[n],
                                        readTemp,
                                        sourceSection)));
                    }

                    // Incorporate the element-wise pattern-matching into the AST, with the longer right leg since
                    // AndNode is visited left to right
                    RubyNode match = matches[size - 1];
                    for (int n = size - 2; n >= 0; n--) {
                        match = new AndNode(matches[n], match);
                    }
                    return match;
                }

                deconstructCallParameters = new RubyCallNodeParameters(
                        expressionValue,
                        "deconstruct",
                        null,
                        RubyNode.EMPTY_ARRAY,
                        false,
                        true);
                deconstructed = language.coreMethodAssumptions
                        .createCallNode(deconstructCallParameters, environment);

                receiver = new TruffleInternalModuleLiteralNode();
                receiver.unsafeSetSourceSection(sourceSection);

                matcherCallParameters = new RubyCallNodeParameters(
                        receiver,
                        "array_pattern_matches?",
                        null,
                        new RubyNode[]{ patternNode.accept(this), NodeUtil.cloneNode(deconstructed) },
                        false,
                        true);
                return language.coreMethodAssumptions
                        .createCallNode(matcherCallParameters, environment);
            case HASHNODE:
                deconstructCallParameters = new RubyCallNodeParameters(
                        expressionValue,
                        "deconstruct_keys",
                        null,
                        new RubyNode[]{ new NilLiteralNode(true) },
                        false,
                        true);
                deconstructed = language.coreMethodAssumptions
                        .createCallNode(deconstructCallParameters, environment);

                receiver = new TruffleInternalModuleLiteralNode();
                receiver.unsafeSetSourceSection(sourceSection);

                matcherCallParameters = new RubyCallNodeParameters(
                        receiver,
                        "hash_pattern_matches?",
                        null,
                        new RubyNode[]{ patternNode.accept(this), NodeUtil.cloneNode(deconstructed) },
                        false,
                        true);
                return language.coreMethodAssumptions
                        .createCallNode(matcherCallParameters, environment);
            case LOCALVARNODE:
                // Assigns the value of an existing variable pattern as the value of the expression.
                // May need to add a case with same/similar logic for new variables.
                final RubyNode assignmentNode = new LocalAsgnParseNode(
                        patternNode.getPosition(),
                        ((LocalVarParseNode) patternNode).getName(),
                        ((LocalVarParseNode) patternNode).getDepth(),
                        expressionNode).accept(this);
                return new OrNode(assignmentNode, new BooleanLiteralNode(true)); // TODO refactor to remove "|| true"
            default:
                matcherCallParameters = new RubyCallNodeParameters(
                        patternNode.accept(this),
                        "===",
                        null,
                        new RubyNode[]{ NodeUtil.cloneNode(expressionValue) },
                        false,
                        true);
                return language.coreMethodAssumptions
                        .createCallNode(matcherCallParameters, environment);
        }
    }

    private RubyNode openModule(SourceIndexLength sourceSection, RubyNode defineOrGetNode, String moduleName,
            ParseNode bodyNode, OpenModule type, boolean dynamicConstantLookup) {
        final SourceSection fullSourceSection = sourceSection.toSourceSection(source);
        final String methodName = type.format(moduleName);

        final LexicalScope newLexicalScope = dynamicConstantLookup
                ? null
                : new LexicalScope(environment.getStaticLexicalScope());

        final SharedMethodInfo sharedMethodInfo = new SharedMethodInfo(
                fullSourceSection,
                newLexicalScope,
                Arity.NO_ARGUMENTS,
                methodName,
                0,
                methodName,
                null,
                null);

        final String modulePath;
        if (type == OpenModule.SINGLETON_CLASS) {
            modulePath = moduleName;
        } else {
            modulePath = TranslatorEnvironment.composeModulePath(environment.modulePath, moduleName);
        }
        final TranslatorEnvironment newEnvironment = new TranslatorEnvironment(
                environment,
                environment.getParseEnvironment(),
                ReturnID.MODULE_BODY,
                true,
                true,
                true,
                sharedMethodInfo,
                methodName,
                0,
                null,
                TranslatorEnvironment.newFrameDescriptor(),
                modulePath);

        final BodyTranslator moduleTranslator = new BodyTranslator(
                language,
                this,
                newEnvironment,
                source,
                parserContext,
                currentNode,
                rubyWarnings);

        final ModuleBodyDefinitionNode definition = moduleTranslator.compileClassNode(sourceSection, bodyNode, type);

        return Translator.withSourceSection(sourceSection, new RunModuleDefinitionNode(definition, defineOrGetNode));
    }

    /** Translates module and class nodes.
     * <p>
     * In Ruby, a module or class definition is somewhat like a method. It has a local scope and a value for self, which
     * is the module or class object that is being defined. Therefore for a module or class definition we translate into
     * a special method. We run that method with self set to be the newly allocated module or class.
     * </p>
    */
    private ModuleBodyDefinitionNode compileClassNode(SourceIndexLength sourceSection, ParseNode bodyNode,
            OpenModule type) {
        RubyNode body = translateNodeOrNil(sourceSection, bodyNode);

        body = new InsideModuleDefinitionNode(body);
        body.unsafeSetSourceSection(sourceSection);

        if (environment.getFlipFlopStates().size() > 0) {
            body = sequence(sourceSection, Arrays.asList(initFlipFlopStates(environment, sourceSection), body));
        }

        final RubyNode writeSelfNode = loadSelf(language, environment);
        body = sequence(sourceSection, Arrays.asList(writeSelfNode, body));

        final SourceSection fullSourceSection = sourceSection.toSourceSection(source);

        final RubyRootNode rootNode = new RubyRootNode(
                language,
                fullSourceSection,
                environment.getFrameDescriptor(),
                environment.getSharedMethodInfo(),
                body,
                Split.NEVER,
                environment.getReturnID());

        return new ModuleBodyDefinitionNode(
                environment.getSharedMethodInfo().getBacktraceName(),
                environment.getSharedMethodInfo(),
                Truffle.getRuntime().createCallTarget(rootNode),
                type == OpenModule.SINGLETON_CLASS,
                environment.getStaticLexicalScopeOrNull());
    }

    @Override
    public RubyNode visitClassNode(ClassParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final String name = node.getCPath().getName();

        RubyNode lexicalParent = translateCPath(sourceSection, node.getCPath());

        final RubyNode superClass = node.getSuperNode() != null ? node.getSuperNode().accept(this) : null;
        final DefineClassNode defineOrGetClass = new DefineClassNode(name, lexicalParent, superClass);

        final RubyNode ret = openModule(
                sourceSection,
                defineOrGetClass,
                name,
                node.getBodyNode(),
                OpenModule.CLASS,
                environment.isDynamicConstantLookup());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitClassVarAsgnNode(ClassVarAsgnParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final RubyNode rhs = node.getValueNode().accept(this);

        final RubyNode ret = new WriteClassVariableNode(
                getLexicalScopeNode("set dynamic class variable", sourceSection),
                node.getName(),
                rhs);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitClassVarNode(ClassVarParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final RubyNode ret = new ReadClassVariableNode(
                getLexicalScopeNode("class variable lookup", sourceSection),
                node.getName());
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitColon2Node(Colon2ParseNode node) {
        // Qualified constant access, as in Mod::CONST
        if (!(node instanceof Colon2ConstParseNode)) {
            throw new UnsupportedOperationException(node.toString());
        }

        final SourceIndexLength sourceSection = node.getPosition();
        final String name = node.getName();

        final RubyNode lhs = node.getLeftNode().accept(this);

        final RubyNode ret = new ReadConstantNode(lhs, name);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitColon3Node(Colon3ParseNode node) {
        // Root namespace constant access, as in ::Foo

        final SourceIndexLength sourceSection = node.getPosition();
        final String name = node.getName();

        final ObjectClassLiteralNode root = new ObjectClassLiteralNode();
        root.unsafeSetSourceSection(sourceSection);

        final RubyNode ret = new ReadConstantNode(root, name);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    private RubyNode translateCPath(SourceIndexLength sourceSection, Colon3ParseNode node) {
        final RubyNode ret;

        if (node instanceof Colon2ImplicitParseNode) { // use current lexical scope
            ret = getLexicalScopeModuleNode("dynamic constant lookup", sourceSection);
            ret.unsafeSetSourceSection(sourceSection);
        } else if (node instanceof Colon2ConstParseNode) { // A::B
            ret = ((Colon2ConstParseNode) node).getLeftNode().accept(this);
        } else { // Colon3ParseNode: on top-level (Object)
            ret = new ObjectClassLiteralNode();
            ret.unsafeSetSourceSection(sourceSection);
        }

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitComplexNode(ComplexParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode ret = translateRationalComplex(
                sourceSection,
                "Complex",
                new IntegerFixnumLiteralNode(0),
                node.getNumber().accept(this));

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitConstDeclNode(ConstDeclParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        RubyNode rhs = node.getValueNode().accept(this);

        final RubyNode moduleNode;
        ParseNode constNode = node.getConstNode();
        if (constNode == null || constNode instanceof Colon2ImplicitParseNode) {
            moduleNode = getLexicalScopeModuleNode("set dynamic constant", sourceSection);
            moduleNode.unsafeSetSourceSection(sourceSection);
        } else if (constNode instanceof Colon2ConstParseNode) {
            constNode = ((Colon2ParseNode) constNode).getLeftNode(); // Misleading doc, we only want the defined part.
            moduleNode = constNode.accept(this);
        } else if (constNode instanceof Colon3ParseNode) {
            moduleNode = new ObjectClassLiteralNode();
        } else {
            throw CompilerDirectives.shouldNotReachHere();
        }

        final RubyNode ret = new WriteConstantNode(node.getName(), moduleNode, rhs);
        ret.unsafeSetSourceSection(sourceSection);

        return addNewlineIfNeeded(node, ret);
    }

    private RubyNode getLexicalScopeModuleNode(String kind, SourceIndexLength sourceSection) {
        if (environment.isDynamicConstantLookup()) {
            if (language.options.LOG_DYNAMIC_CONSTANT_LOOKUP) {
                RubyLanguage.LOGGER
                        .info(() -> kind + " at " + RubyLanguage.fileLine(sourceSection.toSourceSection(source)));
            }
            return new DynamicLexicalScopeNode();
        } else {
            return new LexicalScopeNode(environment.getStaticLexicalScope());
        }
    }

    private RubyNode getLexicalScopeNode(String kind, SourceIndexLength sourceSection) {
        if (environment.isDynamicConstantLookup()) {
            if (language.options.LOG_DYNAMIC_CONSTANT_LOOKUP) {
                RubyLanguage.LOGGER
                        .info(() -> kind + " at " + RubyLanguage.fileLine(sourceSection.toSourceSection(source)));
            }
            return new GetDynamicLexicalScopeNode();
        } else {
            return new ObjectLiteralNode(environment.getStaticLexicalScope());
        }
    }

    @Override
    public RubyNode visitConstNode(ConstParseNode node) {
        // Unqualified constant access, as in CONST
        final SourceIndexLength sourceSection = node.getPosition();
        final String name = node.getName();

        final RubyNode ret;
        if (environment.isDynamicConstantLookup()) {
            if (language.options.LOG_DYNAMIC_CONSTANT_LOOKUP) {
                RubyLanguage.LOGGER.info(
                        () -> "dynamic constant lookup at " +
                                RubyLanguage.fileLine(sourceSection.toSourceSection(source)));
            }
            ret = new ReadConstantWithDynamicScopeNode(name);
        } else {
            final LexicalScope lexicalScope = environment.getStaticLexicalScope();
            ret = new ReadConstantWithLexicalScopeNode(lexicalScope, name);
        }
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);

    }

    @Override
    public WriteLocalNode visitDAsgnNode(DAsgnParseNode node) {
        final WriteLocalNode ret = visitLocalAsgnNode(new LocalAsgnParseNode(
                node.getPosition(),
                node.getName(),
                node.getDepth(),
                node.getValueNode()));
        addNewlineIfNeeded(node, ret);
        return ret;
    }

    @Override
    public RubyNode visitDRegxNode(DRegexpParseNode node) {
        SourceIndexLength sourceSection = node.getPosition();

        final List<ToSNode> children = new ArrayList<>();

        for (ParseNode child : node.children()) {
            children.add(ToSNodeGen.create(child.accept(this)));
        }

        final InterpolatedRegexpNode i = new InterpolatedRegexpNode(
                children.toArray(EMPTY_TO_S_NODE_ARRAY),
                node.getOptions());
        i.unsafeSetSourceSection(sourceSection);

        if (node.getOptions().isOnce()) {
            final RubyNode ret = new OnceNode(i);
            ret.unsafeSetSourceSection(sourceSection);
            return addNewlineIfNeeded(node, ret);
        }

        return addNewlineIfNeeded(node, i);
    }

    @Override
    public RubyNode visitDStrNode(DStrParseNode node) {
        final RubyNode ret = translateInterpolatedString(
                node.getPosition(),
                node.getEncoding(),
                node.children(),
                node.isFrozen() && !environment.getParseEnvironment().inCore());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitDSymbolNode(DSymbolParseNode node) {
        SourceIndexLength sourceSection = node.getPosition();

        final RubyNode stringNode = translateInterpolatedString(
                sourceSection,
                node.getEncoding(),
                node.children(),
                false);

        final RubyNode ret = StringToSymbolNodeGen.create(stringNode);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    private RubyNode translateInterpolatedString(SourceIndexLength sourceSection,
            Encoding encoding, ParseNode[] childNodes, boolean frozen) {
        final ToSNode[] children = new ToSNode[childNodes.length];

        for (int i = 0; i < childNodes.length; i++) {
            children[i] = ToSNodeGen.create(childNodes[i].accept(this));
        }

        final RubyNode ret = new InterpolatedStringNode(children, encoding, frozen);
        ret.unsafeSetSourceSection(sourceSection);
        return ret;
    }

    @Override
    public RubyNode visitDVarNode(DVarParseNode node) {
        final String name = node.getName();
        RubyNode readNode = environment.findLocalVarNode(name, node.getPosition());

        if (readNode == null) {
            // If we haven't seen this dvar before it's possible that it's a block local variable

            final int depth = node.getDepth();

            TranslatorEnvironment e = environment;

            for (int n = 0; n < depth; n++) {
                e = e.getParent();
            }

            e.declareVar(name);

            // Searching for a local variable must start at the base environment, even though we may have determined
            // the variable should be declared in a parent frame descriptor.  This is so the search can determine
            // whether to return a ReadLocalVariableNode or a ReadDeclarationVariableNode and potentially record the
            // fact that a declaration frame is needed.
            readNode = environment.findLocalVarNode(name, node.getPosition());
        }

        return addNewlineIfNeeded(node, readNode);
    }

    @Override
    public RubyNode visitDXStrNode(DXStrParseNode node) {
        final DStrParseNode string = new DStrParseNode(node.getPosition(), node.getEncoding(), false);
        string.addAll(node);
        final ParseNode argsNode = buildArrayNode(node.getPosition(), string);
        final ParseNode callNode = new FCallParseNode(node.getPosition(), "`", argsNode, null);
        copyNewline(node, callNode);
        final RubyNode ret = callNode.accept(this);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitDefinedNode(DefinedParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final RubyNode ret = new DefinedNode(node.getExpressionNode().accept(this));
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitDefnNode(DefnParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final RubyNode moduleNode = TypeNodes.CheckFrozenNode.create(new GetDefaultDefineeNode());
        final RubyNode ret = translateMethodDefinition(
                sourceSection,
                moduleNode,
                node.getName(),
                node.getArgsNode(),
                node,
                node.getBodyNode(),
                false,
                false);

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitDefsNode(DefsParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final boolean isReceiverSelf = node.getReceiverNode() instanceof SelfParseNode;
        final RubyNode objectNode = node.getReceiverNode().accept(this);

        final SingletonClassNode singletonClassNode = SingletonClassNodeGen.create(objectNode);
        singletonClassNode.unsafeSetSourceSection(sourceSection);

        final RubyNode ret = translateMethodDefinition(
                sourceSection,
                singletonClassNode,
                node.getName(),
                node.getArgsNode(),
                node,
                node.getBodyNode(),
                true,
                isReceiverSelf);

        return addNewlineIfNeeded(node, ret);
    }

    public String modulePathAndMethodName(String methodName, boolean onSingleton, boolean isReceiverSelf) {
        String modulePath = environment.modulePath;
        if (modulePath == null) {
            if (onSingleton) {
                if (environment.isTopLevelObjectScope() && isReceiverSelf) {
                    modulePath = "main";
                } else {
                    modulePath = "<singleton class>"; // method of an unknown singleton class
                    onSingleton = false;
                }
            } else {
                if (environment.isTopLevelObjectScope()) {
                    modulePath = "Object";
                } else {
                    modulePath = ""; // instance method of an unknown module
                }
            }
        }

        if (modulePath.endsWith("::<singleton class>") && !onSingleton) {
            modulePath = modulePath.substring(0, modulePath.length() - "::<singleton class>".length());
            onSingleton = true;
        }

        return SharedMethodInfo.modulePathAndMethodName(modulePath, methodName, onSingleton);
    }

    protected RubyNode translateMethodDefinition(SourceIndexLength sourceSection, RubyNode moduleNode,
            String methodName, ArgsParseNode argsNode, MethodDefParseNode defNode, ParseNode bodyNode, boolean isDefs,
            boolean isReceiverSelf) {
        final Arity arity = argsNode.getArity();
        final ArgumentDescriptor[] argumentDescriptors = Helpers.argsNodeToArgumentDescriptors(argsNode);

        final String parseName = modulePathAndMethodName(methodName, isDefs, isReceiverSelf);

        final SharedMethodInfo sharedMethodInfo = new SharedMethodInfo(
                sourceSection.toSourceSection(source),
                environment.getStaticLexicalScopeOrNull(),
                arity,
                methodName,
                0,
                parseName,
                null,
                argumentDescriptors);

        final TranslatorEnvironment newEnvironment = new TranslatorEnvironment(
                environment,
                environment.getParseEnvironment(),
                environment.getParseEnvironment().allocateReturnID(),
                true,
                true,
                false,
                sharedMethodInfo,
                methodName,
                0,
                null,
                TranslatorEnvironment.newFrameDescriptor(),
                environment.modulePath);

        // ownScopeForAssignments is the same for the defined method as the current one.

        final MethodTranslator methodCompiler = new MethodTranslator(
                language,
                this,
                newEnvironment,
                false,
                source,
                parserContext,
                currentNode,
                argsNode,
                null,
                rubyWarnings);

        return withSourceSection(sourceSection, new LiteralMethodDefinitionNode(
                moduleNode,
                methodName,
                sharedMethodInfo,
                isDefs,
                methodCompiler.buildMethodNodeCompiler(sourceSection, defNode, bodyNode)));
    }

    @Override
    public RubyNode visitDotNode(DotParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final RubyNode begin = node.getBeginNode().accept(this);
        final RubyNode end = node.getEndNode().accept(this);
        final RubyNode rangeClass = new RangeClassLiteralNode();
        final RubyNode isExclusive = new ObjectLiteralNode(node.isExclusive());

        final RubyNode ret = RangeNodesFactory.NewNodeFactory.create(rangeClass, begin, end, isExclusive);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitEncodingNode(EncodingParseNode node) {
        SourceIndexLength sourceSection = node.getPosition();
        final RubyNode ret = new ObjectLiteralNode(
                Encodings.getBuiltInEncoding(node.getEncoding().getIndex()));
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitEnsureNode(EnsureParseNode node) {
        final RubyNode tryPart = node.getBodyNode().accept(this);
        final RubyNode ensurePart = node.getEnsureNode().accept(this);
        final RubyNode ret = new EnsureNode(tryPart, ensurePart);
        ret.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitEvStrNode(EvStrParseNode node) {
        final RubyNode ret;

        if (node.getBody() == null) { // "#{}"
            final SourceIndexLength sourceSection = node.getPosition();
            ret = new ObjectLiteralNode(language.getFrozenStringLiteral(RopeConstants.EMPTY_ASCII_8BIT_ROPE));
            ret.unsafeSetSourceSection(sourceSection);
        } else {
            ret = node.getBody().accept(this);
        }

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitFCallNode(FCallParseNode node) {
        final ParseNode receiver = new SelfParseNode(node.getPosition());
        final CallParseNode callNode = new CallParseNode(
                node.getPosition(),
                receiver,
                node.getName(),
                node.getArgsNode(),
                node.getIterNode());
        copyNewline(node, callNode);
        return translateCallNode(callNode, true, false, false);
    }

    @Override
    public RubyNode visitFalseNode(FalseParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final RubyNode ret = new BooleanLiteralNode(false);
        ret.unsafeSetSourceSection(sourceSection);

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitFixnumNode(FixnumParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final long value = node.getValue();
        final RubyNode ret;

        if (CoreLibrary.fitsIntoInteger(value)) {
            ret = new IntegerFixnumLiteralNode((int) value);
        } else {
            ret = new LongFixnumLiteralNode(value);
        }
        ret.unsafeSetSourceSection(sourceSection);

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitFlipNode(FlipParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode begin = node.getBeginNode().accept(this);
        final RubyNode end = node.getEndNode().accept(this);

        final FlipFlopStateNode stateNode = createFlipFlopState(sourceSection, 0);

        final RubyNode ret = new FlipFlopNode(begin, end, stateNode, node.isExclusive());
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    protected FlipFlopStateNode createFlipFlopState(SourceIndexLength sourceSection, int depth) {
        final FrameSlot frameSlot = environment.declareVar(environment.allocateLocalTemp("flipflop"));
        environment.getFlipFlopStates().add(frameSlot);

        if (depth == 0) {
            return new LocalFlipFlopStateNode(frameSlot);
        } else {
            return new DeclarationFlipFlopStateNode(depth, frameSlot);
        }
    }

    @Override
    public RubyNode visitFloatNode(FloatParseNode node) {
        final RubyNode ret = new FloatLiteralNode(node.getValue());
        ret.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitForNode(ForParseNode node) {
        /** A Ruby for-loop, such as:
         *
         * <pre>
         * for x in y
         *     z = x
         *     puts z
         * end
         * </pre>
         *
         * naively desugars to:
         *
         * <pre>
         * y.each do |x|
         *     z = x
         *     puts z
         * end
         * </pre>
         *
         * The main difference is that z is always going to be local to the scope outside the block, so it's a bit more
         * like:
         *
         * <pre>
         * z = nil unless z is already defined
         * y.each do |x|
         *    z = x
         *    puts x
         * end
         * </pre>
         *
         * Which forces z to be defined in the correct scope. The parser already correctly calls z a local, but then
         * that causes us a problem as if we're going to translate to a block we need a formal parameter - not a local
         * variable. My solution to this is to add a temporary:
         *
         * <pre>
         * z = nil unless z is already defined
         * y.each do |temp|
         *    x = temp
         *    z = x
         *    puts x
         * end
         * </pre>
         *
         * We also need that temp because the expression assigned in the for could be index assignment, multiple
         * assignment, or whatever:
         *
         * <pre>
         * for x[0] in y
         *     z = x[0]
         *     puts z
         * end
         * </pre>
         *
         * http://blog.grayproductions.net/articles/the_evils_of_the_for_loop
         * http://stackoverflow.com/questions/3294509/for-vs-each-in-ruby
         *
         * The other complication is that normal locals should be defined in the enclosing scope, unlike a normal block.
         * We do that by setting a flag on this translator object when we visit the new iter, translatingForStatement,
         * which we recognise when visiting an iter node.
         *
         * Finally, note that JRuby's terminology is strange here. Normally 'iter' is a different term for a block.
         * Here, JRuby calls the object being iterated over the 'iter'. */

        final String temp = environment.allocateLocalTemp("for");

        final ParseNode receiver = node.getIterNode();

        /* The x in for x in ... is like the nodes in multiple assignment - it has a dummy RHS which we need to replace
         * with our temp. Just like in multiple assignment this is really awkward with the JRuby AST. */

        final LocalVarParseNode readTemp = new LocalVarParseNode(node.getPosition(), 0, temp);
        final ParseNode forVar = node.getVarNode();
        final ParseNode assignTemp = setRHS(forVar, readTemp);

        final BlockParseNode bodyWithTempAssign = new BlockParseNode(node.getPosition());
        bodyWithTempAssign.add(assignTemp);
        bodyWithTempAssign.add(node.getBodyNode());

        final ArgumentParseNode blockVar = new ArgumentParseNode(node.getPosition(), temp);
        final ArrayParseNode blockArgsPre = new ArrayParseNode(node.getPosition(), blockVar);
        final ArgsParseNode blockArgs = new ArgsParseNode(
                node.getPosition(),
                blockArgsPre,
                null,
                null,
                null,
                null,
                null,
                null);
        final IterParseNode block = new IterParseNode(
                node.getPosition(),
                blockArgs,
                node.getScope(),
                bodyWithTempAssign);

        final CallParseNode callNode = new CallParseNode(node.getPosition(), receiver, "each", null, block);
        copyNewline(node, callNode);

        final RubyNode translated;
        final boolean translatingForStatement = this.translatingForStatement;
        this.translatingForStatement = true;
        try {
            translated = callNode.accept(this);
        } finally {
            this.translatingForStatement = translatingForStatement;
        }

        // TODO CS 23-Apr-19 I've tried to design logic so we never try to assign source sections twice
        //  but can't figure it out here
        if (!translated.hasSource()) {
            translated.unsafeSetSourceSection(node.getPosition());
        }

        return addNewlineIfNeeded(node, translated);
    }

    protected AssignableNode[] toAssignableNodes(ListParseNode nodes) {
        if (nodes == null) {
            return AssignableNode.EMPTY_ARRAY;
        }

        AssignableNode[] assignableNodes = new AssignableNode[nodes.size()];
        for (int i = 0; i < assignableNodes.length; i++) {
            assignableNodes[i] = toAssignableNode(nodes.get(i));
        }
        return assignableNodes;
    }

    protected AssignableNode toAssignableNode(ParseNode node) {
        if (node instanceof StarParseNode) {
            // Nothing to assign to, just execute the RHS
            return new NoopAssignableNode();
        } else if (node instanceof AssignableParseNode) {
            final AssignableParseNode assignable = (AssignableParseNode) node;

            if (assignable instanceof MultipleAsgnParseNode && assignable.getValueNode() == null) {
                // nested MultipleAsgnParseNode
                assignable.setValueNode(NilImplicitParseNode.NIL);
            }

            final ParseNode valueNode = assignable.getValueNode();
            if (valueNode != NilImplicitParseNode.NIL) {
                throw CompilerDirectives.shouldNotReachHere(
                        "value of assignable node is not implicit nil: " + valueNode.getClass());
            }

            final RubyNode translated = node.accept(this);
            return ((AssignableNode) translated).toAssignableNode();
        } else if (node instanceof AttrAssignParseNode) {
            final AttrAssignParseNode attrAssignParseNode = (AttrAssignParseNode) node;
            // The AttrAssignParseNode does not have a value yet, add a sentinel nil,
            // so the RubyCallNode is aware of the actual number of runtime arguments.
            setRHS(attrAssignParseNode, NilImplicitParseNode.NIL);

            final RubyNode translated = attrAssignParseNode.accept(this);
            return ((AssignableNode) translated).toAssignableNode();
        } else {
            throw CompilerDirectives
                    .shouldNotReachHere("toAssignableNode() does not know how to convert " + node.getClass());
        }
    }

    /** Same as {@link ParserSupport#node_assign(ParseNode, ParseNode)} but without needing a ParserSupport instance.
     * {@link ParserSupport#value_expr} was already done during parsing, no need to re-check it. */
    private ParseNode setRHS(ParseNode lhs, ParseNode rhs) {
        if (lhs instanceof AssignableParseNode) {
            ((AssignableParseNode) lhs).setValueNode(rhs);
            return lhs;
        } else if (lhs instanceof IArgumentNode) {
            IArgumentNode invokableNode = (IArgumentNode) lhs;
            return invokableNode
                    .setArgsNode(ParserSupport.arg_add(lhs.getPosition(), invokableNode.getArgsNode(), rhs));
        } else {
            throw new UnsupportedOperationException("Don't know how to set the RHS of a " + lhs.getClass().getName());
        }
    }

    @Override
    public RubyNode visitGlobalAsgnNode(GlobalAsgnParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode translatedValue = node.getValueNode().accept(this);

        final RubyNode writeGlobalVariableNode = WriteGlobalVariableNodeGen.create(node.getName(), translatedValue);

        return addNewlineIfNeeded(node, withSourceSection(sourceSection, writeGlobalVariableNode));
    }

    private static boolean isCaptureVariable(String name) {
        // $0 is always the program name and never refers to a variable match.
        if (name.equals("$0")) {
            return false;
        }

        // Check that each character after the leading '$' is numeric.
        for (int i = 1; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }

        return true;
    }

    @Override
    public RubyNode visitGlobalVarNode(GlobalVarParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        assert !isCaptureVariable(node.getName());
        final RubyNode readGlobal = ReadGlobalVariableNodeGen.create(node.getName());

        readGlobal.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, readGlobal);
    }

    @Override
    public RubyNode visitHashNode(HashParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        if (node.isEmpty()) { // an empty Hash literal like h = {}
            final RubyNode ret = HashLiteralNode.create(RubyNode.EMPTY_ARRAY);
            ret.unsafeSetSourceSection(sourceSection);
            return addNewlineIfNeeded(node, ret);
        }

        final List<RubyNode> hashConcats = new ArrayList<>();
        final List<RubyNode> keyValues = new ArrayList<>();

        for (ParseNodeTuple pair : node.getPairs()) {
            if (pair.getKey() == null) {
                // This null case is for splats {a: 1, **{b: 2}, c: 3}
                if (!keyValues.isEmpty()) {
                    final RubyNode hashLiteralSoFar = HashLiteralNode
                            .create(keyValues.toArray(RubyNode.EMPTY_ARRAY));
                    hashConcats.add(hashLiteralSoFar);
                }
                hashConcats.add(HashCastNodeGen.create(pair.getValue().accept(this)));
                keyValues.clear();
            } else {
                keyValues.add(pair.getKey().accept(this));

                if (pair.getValue() == null) {
                    keyValues.add(nilNode(sourceSection));
                } else {
                    keyValues.add(pair.getValue().accept(this));
                }
            }
        }

        if (!keyValues.isEmpty()) {
            final RubyNode hashLiteralSoFar = HashLiteralNode.create(keyValues.toArray(RubyNode.EMPTY_ARRAY));
            hashConcats.add(hashLiteralSoFar);
        }

        if (hashConcats.size() == 1) {
            final RubyNode ret = hashConcats.get(0);
            return addNewlineIfNeeded(node, ret);
        }

        final RubyNode ret = new ConcatHashLiteralNode(hashConcats.toArray(RubyNode.EMPTY_ARRAY));
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitIfNode(IfParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode condition = translateNodeOrNil(sourceSection, node.getCondition());

        ParseNode thenBody = node.getThenBody();
        ParseNode elseBody = node.getElseBody();

        final RubyNode ret;

        if (thenBody != null && elseBody != null) {
            final RubyNode thenBodyTranslated = thenBody.accept(this);
            final RubyNode elseBodyTranslated = elseBody.accept(this);
            ret = new IfElseNode(condition, thenBodyTranslated, elseBodyTranslated);
            ret.unsafeSetSourceSection(sourceSection);
        } else if (thenBody != null) {
            final RubyNode thenBodyTranslated = thenBody.accept(this);
            ret = new IfNode(condition, thenBodyTranslated);
            ret.unsafeSetSourceSection(sourceSection);
        } else if (elseBody != null) {
            final RubyNode elseBodyTranslated = elseBody.accept(this);
            ret = new UnlessNode(condition, elseBodyTranslated);
            ret.unsafeSetSourceSection(sourceSection);
        } else {
            ret = sequence(sourceSection, Arrays.asList(condition, new NilLiteralNode(true)));
        }

        return ret; // no addNewlineIfNeeded(node, ret) as the condition will already have a newline
    }

    @Override
    public RubyNode visitInstAsgnNode(InstAsgnParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final String name = node.getName();

        final RubyNode rhs;
        if (node.getValueNode() == null) {
            rhs = new DeadNode("null RHS of instance variable assignment");
            rhs.unsafeSetSourceSection(sourceSection);
        } else {
            rhs = node.getValueNode().accept(this);
        }

        final RubyNode self = new SelfNode(environment.getFrameDescriptor());
        final RubyNode ret = new WriteInstanceVariableNode(name, self, rhs);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitInstVarNode(InstVarParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final String name = node.getName();

        // About every case will use a SelfParseNode, just don't it use more than once.
        final SelfNode self = new SelfNode(environment.getFrameDescriptor());

        final RubyNode ret = new ReadInstanceVariableNode(name, self);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitIterNode(IterParseNode node) {
        return translateBlockLikeNode(node, false);
    }

    @Override
    public RubyNode visitLambdaNode(LambdaParseNode node) {
        return translateBlockLikeNode(node, true);
    }

    private RubyNode translateBlockLikeNode(IterParseNode node, boolean isStabbyLambda) {
        final SourceIndexLength sourceSection = node.getPosition();
        final ArgsParseNode argsNode = node.getArgsNode();

        // Unset this flag for any for any blocks within the for statement's body
        final boolean hasOwnScope = isStabbyLambda || !translatingForStatement;

        final boolean isProc = !isStabbyLambda;

        TranslatorEnvironment methodParent = environment.getSurroundingMethodEnvironment();
        final String methodName = methodParent.getMethodName();

        final int blockDepth = environment.getBlockDepth() + 1;

        // "block in foo"
        String backtraceName = SharedMethodInfo.getBlockName(blockDepth, methodName);
        // "block (2 levels) in M::C.foo"
        String parseName = SharedMethodInfo.getBlockName(blockDepth, methodParent.getSharedMethodInfo().getParseName());
        final SharedMethodInfo sharedMethodInfo = new SharedMethodInfo(
                sourceSection.toSourceSection(source),
                environment.getStaticLexicalScopeOrNull(),
                argsNode.getArity(),
                backtraceName,
                blockDepth,
                parseName,
                methodName,
                Helpers.argsNodeToArgumentDescriptors(argsNode));

        final ParseEnvironment parseEnvironment = environment.getParseEnvironment();
        final ReturnID returnID = isStabbyLambda ? parseEnvironment.allocateReturnID() : environment.getReturnID();

        final TranslatorEnvironment newEnvironment = new TranslatorEnvironment(
                environment,
                parseEnvironment,
                returnID,
                hasOwnScope,
                false,
                false,
                sharedMethodInfo,
                environment.getMethodName(),
                blockDepth,
                parseEnvironment.allocateBreakID(),
                TranslatorEnvironment.newFrameDescriptor(),
                environment.modulePath);
        final MethodTranslator methodCompiler = new MethodTranslator(
                language,
                this,
                newEnvironment,
                true,
                source,
                parserContext,
                currentNode,
                argsNode,
                currentCallMethodName,
                rubyWarnings);

        if (isProc) {
            methodCompiler.translatingForStatement = translatingForStatement;
        }

        methodCompiler.frameOnStackMarkerSlotStack = frameOnStackMarkerSlotStack;

        final RubyNode definitionNode = methodCompiler.compileBlockNode(
                sourceSection,
                node.getBodyNode(),
                isStabbyLambda,
                node.getScope().getVariables());

        return addNewlineIfNeeded(node, definitionNode);
    }

    @Override
    public WriteLocalNode visitLocalAsgnNode(LocalAsgnParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final String name = node.getName();

        if (environment.getNeverAssignInParentScope()) {
            environment.declareVar(name);
        }

        ReadLocalNode lhs = environment.findLocalVarNode(name, sourceSection);

        if (lhs == null) {
            TranslatorEnvironment environmentToDeclareIn = environment;
            while (!environmentToDeclareIn.hasOwnScopeForAssignments()) {
                environmentToDeclareIn = environmentToDeclareIn.getParent();
            }
            environmentToDeclareIn.declareVar(name);

            lhs = environment.findLocalVarNode(name, sourceSection);

            if (lhs == null) {
                throw new RuntimeException("shouldn't be here");
            }
        }

        RubyNode rhs;

        if (node.getValueNode() == null) {
            rhs = new DeadNode("BodyTranslator#visitLocalAsgnNode");
            rhs.unsafeSetSourceSection(sourceSection);
        } else {
            rhs = node.getValueNode().accept(this);
        }

        final WriteLocalNode ret = lhs.makeWriteNode(rhs);
        ret.unsafeSetSourceSection(sourceSection);
        addNewlineIfNeeded(node, ret);
        return ret;
    }

    @Override
    public RubyNode visitLocalVarNode(LocalVarParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final String name = node.getName();

        RubyNode readNode = environment.findLocalVarNode(name, sourceSection);

        if (readNode == null) {
            /* This happens for code such as:
             *
             * def destructure4r((*c,d)) [c,d] end
             *
             * We're going to just assume that it should be there and add it... */

            environment.declareVar(name);
            readNode = environment.findLocalVarNode(name, sourceSection);
        }

        return addNewlineIfNeeded(node, readNode);
    }

    @Override
    public RubyNode visitMatchNode(MatchParseNode node) {
        // Triggered when a Regexp literal is used as a conditional's value.

        final ParseNode argsNode = buildArrayNode(node.getPosition(), new GlobalVarParseNode(node.getPosition(), "$_"));
        final ParseNode callNode = new CallParseNode(node.getPosition(), node.getRegexpNode(), "=~", argsNode, null);
        copyNewline(node, callNode);
        final RubyNode ret = callNode.accept(this);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitMatch2Node(Match2ParseNode node) {
        // Triggered when a Regexp literal is the LHS of an expression.

        final ParseNode argsNode = buildArrayNode(node.getPosition(), node.getValueNode());
        final ParseNode callNode = new CallParseNode(node.getPosition(), node.getReceiverNode(), "=~", argsNode, null);
        copyNewline(node, callNode);
        RubyNode ret = callNode.accept(this);

        if (node.getReceiverNode() instanceof RegexpParseNode) {
            final RegexpParseNode regexpNode = (RegexpParseNode) node.getReceiverNode();
            final byte[] bytes = regexpNode.getValue().getBytes();
            final Regex regex = new Regex(
                    bytes,
                    0,
                    bytes.length,
                    regexpNode.getOptions().toOptions(),
                    regexpNode.getEncoding(),
                    Syntax.RUBY,
                    new RegexWarnDeferredCallback(rubyWarnings));
            final int numberOfNames = regex.numberOfNames();

            if (numberOfNames > 0) {
                final RubyNode[] setters = new RubyNode[numberOfNames];
                final RubyNode[] nilSetters = new RubyNode[numberOfNames];
                final String tempVar = environment.allocateLocalTemp("match_data");
                int n = 0;

                for (Iterator<NameEntry> i = regex.namedBackrefIterator(); i.hasNext(); n++) {
                    final NameEntry e = i.next();
                    //intern() to improve footprint
                    final String name = new String(e.name, e.nameP, e.nameEnd - e.nameP, StandardCharsets.UTF_8)
                            .intern();

                    TranslatorEnvironment environmentToDeclareIn = environment;
                    while (!environmentToDeclareIn.hasOwnScopeForAssignments()) {
                        environmentToDeclareIn = environmentToDeclareIn.getParent();
                    }
                    environmentToDeclareIn.declareVar(name);
                    nilSetters[n] = match2NilSetter(node, name);
                    setters[n] = match2NonNilSetter(node, name, tempVar);
                }
                final RubyNode readNode = ReadGlobalVariableNodeGen.create("$~");
                ReadLocalNode tempVarReadNode = environment.findLocalVarNode(tempVar, node.getPosition());
                RubyNode readMatchNode = tempVarReadNode.makeWriteNode(readNode);
                ret = new ReadMatchReferenceNodes.SetNamedVariablesMatchNode(ret, readMatchNode, setters, nilSetters);
            }
        }

        return addNewlineIfNeeded(node, ret);
    }

    private RubyNode match2NilSetter(ParseNode node, String name) {
        return environment.findLocalVarNode(name, node.getPosition()).makeWriteNode(new NilLiteralNode(true));
    }

    private RubyNode match2NonNilSetter(ParseNode node, String name, String tempVar) {
        ReadLocalNode varNode = environment.findLocalVarNode(name, node.getPosition());
        ReadLocalNode tempVarNode = environment.findLocalVarNode(tempVar, node.getPosition());
        ObjectLiteralNode symbolNode = new ObjectLiteralNode(language.getSymbol(name));
        GetIndexNode getIndexNode = GetIndexNode
                .create(tempVarNode, symbolNode, new ObjectLiteralNode(NotProvided.INSTANCE));
        return varNode.makeWriteNode(getIndexNode);
    }

    @Override
    public RubyNode visitMatch3Node(Match3ParseNode node) {
        // Triggered when a Regexp literal is the RHS of an expression.

        final ParseNode argsNode = buildArrayNode(node.getPosition(), node.getValueNode());
        final ParseNode callNode = new CallParseNode(node.getPosition(), node.getReceiverNode(), "=~", argsNode, null);
        copyNewline(node, callNode);
        final RubyNode ret = callNode.accept(this);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitModuleNode(ModuleParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final String name = node.getCPath().getName();

        RubyNode lexicalParent = translateCPath(sourceSection, node.getCPath());

        final DefineModuleNode defineModuleNode = DefineModuleNodeGen.create(name, lexicalParent);

        final RubyNode ret = openModule(
                sourceSection,
                defineModuleNode,
                name,
                node.getBodyNode(),
                OpenModule.MODULE,
                environment.isDynamicConstantLookup());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitMultipleAsgnNode(MultipleAsgnParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final ParseNode rhs = node.getValueNode();
        final RubyNode rhsTranslated;
        if (rhs == null) {
            throw CompilerDirectives.shouldNotReachHere("null rhs");
        } else {
            rhsTranslated = rhs.accept(this);
        }

        final AssignableNode[] preNodes = toAssignableNodes(node.getPre());
        final AssignableNode restNode = node.getRest() == null ? null : toAssignableNode(node.getRest());
        final AssignableNode[] postNodes = toAssignableNodes(node.getPost());

        final SplatCastNode splatCastNode = SplatCastNodeGen.create(
                language,
                translatingNextExpression
                        ? SplatCastNode.NilBehavior.EMPTY_ARRAY
                        : SplatCastNode.NilBehavior.ARRAY_WITH_NIL,
                true,
                null);

        final MultipleAssignmentNode ret = new MultipleAssignmentNode(
                preNodes,
                restNode,
                postNodes,
                splatCastNode,
                rhsTranslated);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitNextNode(NextParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        if (!environment.isBlock() && !translatingWhile) {
            final RubyContext context = RubyLanguage.getCurrentContext();
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().syntaxError(
                            "Invalid next",
                            currentNode,
                            sourceSection.toSourceSection(source)));
        }

        final RubyNode resultNode;

        final boolean t = translatingNextExpression;
        translatingNextExpression = true;
        try {
            resultNode = translateNodeOrNil(sourceSection, node.getValueNode());
        } finally {
            translatingNextExpression = t;
        }

        final RubyNode ret = new NextNode(resultNode);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitNilNode(NilParseNode node) {
        if (node instanceof NilImplicitParseNode) {
            final RubyNode ret = new NilLiteralNode(true);
            ret.unsafeSetSourceSection(node.getPosition());
            return addNewlineIfNeeded(node, ret);
        }

        SourceIndexLength sourceSection = node.getPosition();
        final RubyNode ret = nilNode(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitNthRefNode(NthRefParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode readMatchNode = ReadGlobalVariableNodeGen.create("$~");
        final RubyNode readGlobal = new ReadMatchReferenceNodes.ReadNthMatchNode(readMatchNode, node.getMatchNumber());

        readGlobal.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, readGlobal);
    }

    @Override
    public RubyNode visitOpAsgnAndNode(OpAsgnAndParseNode node) {
        return translateOpAsgnAndNode(node, node.getFirstNode().accept(this), node.getSecondNode().accept(this));
    }

    private RubyNode translateOpAsgnAndNode(ParseNode node, RubyNode lhs, RubyNode rhs) {
        /* This doesn't translate as you might expect!
         *
         * http://www.rubyinside.com/what-rubys-double-pipe-or-equals-really-does-5488.html */

        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode andNode = new AndNode(lhs, rhs);
        andNode.unsafeSetSourceSection(sourceSection);

        final RubyNode ret = new DefinedWrapperNode(language.coreStrings.ASSIGNMENT, andNode);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitOpAsgnConstDeclNode(OpAsgnConstDeclParseNode node) {
        final ReadConstantNode lhs = (ReadConstantNode) node.getFirstNode().accept(this);
        RubyNode rhs = node.getSecondNode().accept(this);

        if (!(rhs instanceof WriteConstantNode)) {
            rhs = lhs.makeWriteNode(rhs);
        }

        switch (node.getOperator()) {
            case "&&": {
                return translateOpAsgnAndNode(node, lhs, rhs);
            }

            case "||": {
                return new OrAssignConstantNode(lhs, (WriteConstantNode) rhs);
            }

            default: {
                final SourceIndexLength sourceSection = node.getPosition();
                final RubyCallNodeParameters callParameters = new RubyCallNodeParameters(
                        lhs,
                        node.getOperator(),
                        null,
                        new RubyNode[]{ rhs },
                        false,
                        true);
                final RubyNode opNode = language.coreMethodAssumptions.createCallNode(callParameters, environment);
                final RubyNode ret = lhs.makeWriteNode(opNode);
                ret.unsafeSetSourceSection(sourceSection);
                return addNewlineIfNeeded(node, ret);
            }
        }
    }

    @Override
    public RubyNode visitOpAsgnNode(OpAsgnParseNode node) {
        final SourceIndexLength pos = node.getPosition();

        final ValueFromNode receiverValue = ValueFromNode.valueFromNode(this, node.getReceiverNode());

        final boolean isOrOperator = node.getOperatorName().equals("||");
        if (isOrOperator || node.getOperatorName().equals("&&")) {
            // Why does this ||= or &&= come through as a visitOpAsgnNode and not a visitOpAsgnOrNode?

            final ParseNode readMethod = new CallParseNode(
                    pos,
                    receiverValue.get(pos),
                    node.getVariableName(),
                    null,
                    null);

            final ParseNode writeMethod = new AttrAssignParseNode(
                    pos,
                    receiverValue.get(pos),
                    node.getVariableName() + "=",
                    buildArrayNode(pos, node.getValueNode()),
                    false,
                    node.getReceiverNode() instanceof SelfParseNode);

            final SourceIndexLength sourceSection = pos;

            RubyNode lhs = readMethod.accept(this);
            RubyNode rhs = writeMethod.accept(this);

            final RubyNode controlNode = isOrOperator ? new OrNode(lhs, rhs) : new AndNode(lhs, rhs);

            final RubyNode ret = new DefinedWrapperNode(
                    language.coreStrings.ASSIGNMENT,
                    receiverValue.prepareAndThen(sourceSection, controlNode));
            ret.unsafeSetSourceSection(sourceSection);

            return addNewlineIfNeeded(node, ret);
        }

        /* We're going to de-sugar a.foo += c into a.foo = a.foo + c. Note that we can't evaluate a more than once, so
         * we put it into a temporary, and we're doing something more like:
         *
         * temp = a; temp.foo = temp.foo + c */

        final ParseNode readMethod = new CallParseNode(pos, receiverValue.get(pos), node.getVariableName(), null, null);
        final ParseNode operation = new CallParseNode(
                pos,
                readMethod,
                node.getOperatorName(),
                buildArrayNode(pos, node.getValueNode()),
                null);
        final ParseNode writeMethod = new CallParseNode(
                pos,
                receiverValue.get(pos),
                node.getVariableName() + "=",
                buildArrayNode(pos, operation),
                null);

        RubyNode body = writeMethod.accept(this);

        final SourceIndexLength sourceSection = pos;

        if (node.isLazy()) {
            body = new IfNode(
                    new NotNode(new IsNilNode(receiverValue.get(sourceSection).accept(this))),
                    body);
            body.unsafeSetSourceSection(sourceSection);
        }
        final RubyNode ret = receiverValue.prepareAndThen(sourceSection, body);

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitOpAsgnOrNode(OpAsgnOrParseNode node) {
        RubyNode lhs = node.getFirstNode().accept(this);
        RubyNode rhs = node.getSecondNode().accept(this);

        // This is needed for class variables. Constants are handled separately in visitOpAsgnConstDeclNode.
        // It is also needed for instance variables to prevent attempted read which may trigger "not initialized"
        // warnings unintentionally.
        if (node.getFirstNode().needsDefinitionCheck()) {
            RubyNode defined = new DefinedNode(lhs);
            lhs = new AndNode(defined, lhs);
        }

        return translateOpAsgOrNode(node, lhs, rhs);
    }

    private RubyNode translateOpAsgOrNode(ParseNode node, RubyNode lhs, RubyNode rhs) {
        /* This doesn't translate as you might expect!
         *
         * http://www.rubyinside.com/what-rubys-double-pipe-or-equals-really-does-5488.html */

        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode ret = new OrLazyValueDefinedNode(lhs, rhs);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitOpElementAsgnNode(OpElementAsgnParseNode node) {
        /* We're going to de-sugar a[b] += c into a[b] = a[b] + c. See discussion in visitOpAsgnNode. */

        final String tempName = environment.allocateLocalTemp("opelementassign");

        final ParseNode value = node.getValueNode();
        final ParseNode readReceiverFromTemp = new LocalVarParseNode(node.getPosition(), 0, tempName);
        final ParseNode writeReceiverToTemp = new LocalAsgnParseNode(
                node.getPosition(),
                tempName,
                0,
                node.getReceiverNode());

        final String op = node.getOperatorName();
        final boolean logicalOperation = op.equals("&&") || op.equals("||");

        if (logicalOperation) {
            final ParseNode write = write(node, readReceiverFromTemp, value);
            final ParseNode operation = operation(node, readReceiverFromTemp, op, write);

            return block(node, writeReceiverToTemp, operation);
        } else {
            final ParseNode operation = operation(node, readReceiverFromTemp, op, value);
            final ParseNode write = write(node, readReceiverFromTemp, operation);

            return block(node, writeReceiverToTemp, write);
        }
    }

    private RubyNode block(OpElementAsgnParseNode node, ParseNode writeReceiverToTemp, ParseNode main) {
        final BlockParseNode block = new BlockParseNode(node.getPosition());
        block.add(writeReceiverToTemp);
        block.add(main);

        final RubyNode ret = block.accept(this);
        return addNewlineIfNeeded(node, ret);
    }

    private ParseNode write(OpElementAsgnParseNode node, ParseNode readReceiverFromTemp, ParseNode value) {
        final ParseNode readArguments = node.getArgsNode();
        final ParseNode writeArguments;
        // Like ParserSupport#arg_add, but copy the first node
        if (readArguments instanceof ArrayParseNode) {
            final ArrayParseNode readArgsCopy = new ArrayParseNode(node.getPosition());
            readArgsCopy.addAll((ArrayParseNode) readArguments).add(value);
            writeArguments = readArgsCopy;
        } else {
            writeArguments = new ArgsPushParseNode(node.getPosition(), readArguments, value);
        }

        return new AttrAssignParseNode(node.getPosition(), readReceiverFromTemp, "[]=", writeArguments, false);
    }

    private ParseNode operation(
            OpElementAsgnParseNode node,
            ParseNode readReceiverFromTemp,
            String op,
            ParseNode right) {

        final ParseNode read = new CallParseNode(
                node.getPosition(),
                readReceiverFromTemp,
                "[]",
                node.getArgsNode(),
                null);
        ParseNode operation;
        switch (op) {
            case "||":
                operation = new OrParseNode(node.getPosition(), read, right);
                break;
            case "&&":
                operation = new AndParseNode(node.getPosition(), read, right);
                break;
            default:
                operation = new CallParseNode(
                        node.getPosition(),
                        read,
                        node.getOperatorName(),
                        buildArrayNode(node.getPosition(), right),
                        null);
                break;
        }

        copyNewline(node, operation);
        return operation;
    }

    private static ArrayParseNode buildArrayNode(SourceIndexLength sourcePosition, ParseNode first, ParseNode... rest) {
        if (first == null) {
            return new ArrayParseNode(sourcePosition);
        }

        final ArrayParseNode array = new ArrayParseNode(sourcePosition, first);

        for (ParseNode node : rest) {
            array.add(node);
        }

        return array;
    }

    @Override
    public RubyNode visitOrNode(OrParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode x = translateNodeOrNil(sourceSection, node.getFirstNode());
        final RubyNode y = translateNodeOrNil(sourceSection, node.getSecondNode());

        final RubyNode ret = new OrNode(x, y);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitPreExeNode(PreExeParseNode node) {
        // The parser seems to visit BEGIN blocks for us first, so we just need to translate them in place
        final RubyNode ret = node.getBodyNode().accept(this);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitPostExeNode(PostExeParseNode node) {
        // END blocks run after any other code - not just code in the same file

        // Turn into a call to Truffle::KernelOperations.at_exit

        // The scope is empty - we won't be able to access local variables
        // TODO fix this
        // https://github.com/jruby/jruby/issues/4257
        final StaticScope scope = new StaticScope(StaticScope.Type.BLOCK, null);

        final SourceIndexLength position = node.getPosition();

        return new OnceNode(
                translateCallNode(
                        new CallParseNode(
                                position,
                                new TruffleFragmentParseNode(
                                        position,
                                        new TruffleKernelOperationsModuleLiteralNode()),
                                "at_exit",
                                new ArrayParseNode(position, new TrueParseNode(position)),
                                new IterParseNode(position, node.getArgsNode(), scope, node.getBodyNode())),
                        false,
                        false,
                        false));
    }

    @Override
    public RubyNode visitRationalNode(RationalParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        // TODO(CS): use IntFixnumLiteralNode where possible

        final RubyNode ret = translateRationalComplex(
                sourceSection,
                "Rational",
                new LongFixnumLiteralNode(node.getNumerator()),
                new LongFixnumLiteralNode(node.getDenominator()));

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitBigRationalNode(BigRationalParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode ret = translateRationalComplex(
                sourceSection,
                "Rational",
                bignumOrFixnumNode(node.getNumerator()),
                bignumOrFixnumNode(node.getDenominator()));

        return addNewlineIfNeeded(node, ret);
    }

    private RubyNode translateRationalComplex(SourceIndexLength sourceSection, String name, RubyNode a, RubyNode b) {
        // Translate as Rational.convert(a, b) # ignoring visibility

        final RubyNode moduleNode = new ObjectClassLiteralNode();
        ReadConstantNode receiver = new ReadConstantNode(moduleNode, name);
        RubyNode[] arguments = new RubyNode[]{ a, b };
        RubyCallNodeParameters parameters = new RubyCallNodeParameters(
                receiver,
                "convert",
                null,
                arguments,
                false,
                true);
        return withSourceSection(sourceSection, new RubyCallNode(parameters));
    }

    @Override
    public RubyNode visitRedoNode(RedoParseNode node) {
        if (!environment.isBlock() && !translatingWhile) {
            final RubyContext context = RubyLanguage.getCurrentContext();
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().syntaxError(
                            "Invalid redo",
                            currentNode,
                            node.getPosition().toSourceSection(source)));
        }

        final RubyNode ret = new RedoNode();
        ret.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitRegexpNode(RegexpParseNode node) {
        final Rope rope = node.getValue();
        final RegexpOptions options = (RegexpOptions) node.getOptions().clone();
        options.setLiteral(true);
        Regex regex = null;
        try {
            regex = TruffleRegexpNodes.compile(language, rubyWarnings, rope, options, currentNode);
        } catch (DeferredRaiseException dre) {
            throw dre.getException(RubyLanguage.getCurrentContext());
        }

        // The RegexpNodes.compile operation may modify the encoding of the source rope. This modified copy is stored
        // in the Regex object as the "user object". Since ropes are immutable, we need to take this updated copy when
        // constructing the final regexp.
        final Rope updatedRope = (Rope) regex.getUserObject();
        final RubyRegexp regexp = RegexpNodes.createRubyRegexp(
                regex,
                updatedRope,
                options,
                new EncodingCache(),
                new TRegexCache());

        final ObjectLiteralNode literalNode = new ObjectLiteralNode(regexp);
        literalNode.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, literalNode);
    }

    @Override
    public RubyNode visitRescueNode(RescueParseNode node) {
        final RubyNode tryPart = translateNodeOrNil(node.getPosition(), node.getBodyNode());
        final List<RescueNode> rescueNodes = new ArrayList<>();

        RescueBodyParseNode rescueClause = node.getRescueNode();

        boolean canOmitBacktrace = false;

        if (language.options.BACKTRACES_OMIT_UNUSED && rescueClause != null &&
                rescueClause.getBodyNode() instanceof SideEffectFree && rescueClause.getOptRescueNode() == null) {
            canOmitBacktrace = true;
        }

        while (rescueClause != null) { // each rescue clause
            if (rescueClause.getExceptionNodes() != null) {
                final Deque<ParseNode> exceptionNodes = new ArrayDeque<>();
                exceptionNodes.push(rescueClause.getExceptionNodes());

                while (!exceptionNodes.isEmpty()) { // each "exception matcher" in that rescue clause: rescue A => a, B => b
                    final ParseNode exceptionNode = exceptionNodes.pop();

                    if (exceptionNode instanceof ArrayParseNode) {
                        final RescueNode rescueNode = translateRescueArrayParseNode(
                                (ArrayParseNode) exceptionNode,
                                rescueClause);
                        rescueNodes.add(rescueNode);
                    } else if (exceptionNode instanceof SplatParseNode) {
                        final RescueNode rescueNode = translateRescueSplatParseNode(
                                (SplatParseNode) exceptionNode,
                                rescueClause);
                        rescueNodes.add(rescueNode);
                    } else if (exceptionNode instanceof ArgsCatParseNode) {
                        final ArgsCatParseNode argsCat = (ArgsCatParseNode) exceptionNode;
                        exceptionNodes.push(
                                new SplatParseNode(argsCat.getSecondNode().getPosition(), argsCat.getSecondNode()));
                        exceptionNodes.push(argsCat.getFirstNode());
                    } else if (exceptionNode instanceof ArgsPushParseNode) {
                        final ArgsPushParseNode argsPush = (ArgsPushParseNode) exceptionNode;
                        exceptionNodes.push(
                                new ArrayParseNode(argsPush.getSecondNode().getPosition(), argsPush.getSecondNode()));
                        exceptionNodes.push(argsPush.getFirstNode());
                    } else {
                        throw CompilerDirectives.shouldNotReachHere();
                    }
                }
            } else {
                final RubyNode bodyNode = translateNodeOrNil(rescueClause.getPosition(), rescueClause.getBodyNode());
                final RescueStandardErrorNode rescueNode = withSourceSection(
                        rescueClause.getPosition(),
                        new RescueStandardErrorNode(bodyNode));
                rescueNodes.add(rescueNode);
            }

            rescueClause = rescueClause.getOptRescueNode();
        }

        RubyNode elsePart;

        if (node.getElseNode() == null) {
            elsePart = null;
        } else {
            elsePart = node.getElseNode().accept(this);
        }

        final RubyNode ret = new TryNode(
                new ExceptionTranslatingNode(tryPart),
                rescueNodes.toArray(EMPTY_RESCUE_NODE_ARRAY),
                elsePart,
                canOmitBacktrace);
        ret.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, ret);
    }

    private RescueNode translateRescueArrayParseNode(ArrayParseNode arrayParse, RescueBodyParseNode rescueClause) {
        final ParseNode[] exceptionNodes = arrayParse.children();
        final RubyNode[] handlingClasses = createArray(exceptionNodes.length);
        for (int n = 0; n < handlingClasses.length; n++) {
            handlingClasses[n] = exceptionNodes[n].accept(this);
        }

        final RubyNode translatedBody = translateNodeOrNil(rescueClause.getPosition(), rescueClause.getBodyNode());
        return withSourceSection(arrayParse.getPosition(), new RescueClassesNode(handlingClasses, translatedBody));
    }

    private RescueNode translateRescueSplatParseNode(SplatParseNode splat, RescueBodyParseNode rescueClause) {
        final RubyNode splatTranslated = translateNodeOrNil(rescueClause.getPosition(), splat.getValue());
        final RubyNode translatedBody = translateNodeOrNil(rescueClause.getPosition(), rescueClause.getBodyNode());
        return withSourceSection(splat.getPosition(), new RescueSplatNode(language, splatTranslated, translatedBody));
    }

    @Override
    public RubyNode visitRetryNode(RetryParseNode node) {
        final RubyNode ret = new RetryNode();
        ret.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitReturnNode(ReturnParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        RubyNode translatedChild = translateNodeOrNil(sourceSection, node.getValueNode());

        final RubyNode ret;

        if (environment.isBlock()) {
            final ReturnID returnID = environment.getReturnID();
            if (returnID == ReturnID.MODULE_BODY) {
                ret = new InvalidReturnNode(translatedChild);
            } else {
                ret = new DynamicReturnNode(returnID, translatedChild);
            }
        } else {
            ret = new LocalReturnNode(translatedChild);
        }

        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitSClassNode(SClassParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode receiverNode = node.getReceiverNode().accept(this);
        final SingletonClassNode singletonClassNode = SingletonClassNodeGen.create(receiverNode);

        boolean dynamicConstantLookup = environment.isDynamicConstantLookup();

        String modulePath = "<singleton class>";
        if (!dynamicConstantLookup) {
            if (environment.isModuleBody() && node.getReceiverNode() instanceof SelfParseNode) {
                // Common case of class << self in a module body, the constant lookup scope is still static.
                // Special pattern recognized by #modulePathAndMethodName:
                if (environment.isTopLevelObjectScope()) {
                    modulePath = "main::<singleton class>";
                } else {
                    modulePath = TranslatorEnvironment.composeModulePath(environment.modulePath, "<singleton class>");
                }
            } else if (environment.isTopLevelScope()) {
                // At the top-level of a file, opening the singleton class of an expression executed only once
            } else {
                // Switch to dynamic constant lookup
                dynamicConstantLookup = true;
                if (language.options.LOG_DYNAMIC_CONSTANT_LOOKUP) {
                    RubyLanguage.LOGGER.info(
                            () -> "start dynamic constant lookup at " +
                                    RubyLanguage.fileLine(sourceSection.toSourceSection(source)));
                }
            }
        }

        final RubyNode ret = openModule(
                sourceSection,
                singletonClassNode,
                modulePath,
                node.getBodyNode(),
                OpenModule.SINGLETON_CLASS,
                dynamicConstantLookup);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitSValueNode(SValueParseNode node) {
        final RubyNode ret = node.getValue().accept(this);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitSelfNode(SelfParseNode node) {
        final RubyNode ret = new SelfNode(environment.getFrameDescriptor());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitSplatNode(SplatParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode value = translateNodeOrNil(sourceSection, node.getValue());
        final RubyNode ret = SplatCastNodeGen.create(language, SplatCastNode.NilBehavior.CONVERT, false, value);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitStrNode(StrParseNode node) {
        final Rope nodeRope = node.getValue();
        final RubyNode ret;

        if (node.isFrozen()) {
            final ImmutableRubyString frozenString = language
                    .getFrozenStringLiteral(nodeRope.getBytes(), nodeRope.getEncoding(), node.getCodeRange());
            ret = new FrozenStringLiteralNode(frozenString, language.coreStrings.EXPRESSION);
        } else {
            final LeafRope cachedRope = language.ropeCache
                    .getRope(nodeRope.getBytes(), nodeRope.getEncoding(), node.getCodeRange());
            ret = new StringLiteralNode(cachedRope);
        }
        ret.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitSymbolNode(SymbolParseNode node) {
        final RubyNode ret = new ObjectLiteralNode(language.getSymbol(
                node.getRope(),
                Encodings.getBuiltInEncoding(node.getRope().getEncoding().getIndex())));
        ret.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitTrueNode(TrueParseNode node) {
        final RubyNode ret = new BooleanLiteralNode(true);
        ret.unsafeSetSourceSection(node.getPosition());

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitUndefNode(UndefParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode ret = ModuleNodesFactory.UndefMethodNodeFactory.create(new RubyNode[]{
                TypeNodes.CheckFrozenNode.create(new GetDefaultDefineeNode()),
                translateNameNodeToSymbol(node.getName())
        });

        ret.unsafeSetSourceSection(sourceSection);

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitUntilNode(UntilParseNode node) {
        WhileParseNode whileNode = new WhileParseNode(
                node.getPosition(),
                node.getConditionNode(),
                node.getBodyNode(),
                node.evaluateAtStart());
        copyNewline(node, whileNode);
        final RubyNode ret = translateWhileNode(whileNode, true);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitVCallNode(VCallParseNode node) {
        // TODO (pitr-ch 02-Dec-2019): replace with a primitive
        if (environment.getParseEnvironment().inCore() && node.getName().equals("undefined")) { // translate undefined
            final RubyNode ret = new ObjectLiteralNode(NotProvided.INSTANCE);
            ret.unsafeSetSourceSection(node.getPosition());
            return addNewlineIfNeeded(node, ret);
        }

        final ParseNode receiver = new SelfParseNode(node.getPosition());
        final CallParseNode callNode = new CallParseNode(node.getPosition(), receiver, node.getName(), null, null);
        copyNewline(node, callNode);
        final RubyNode ret = translateCallNode(callNode, true, true, false);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitWhileNode(WhileParseNode node) {
        final RubyNode ret = translateWhileNode(node, false);
        return addNewlineIfNeeded(node, ret);
    }

    private RubyNode translateWhileNode(WhileParseNode node, boolean conditionInversed) {
        final SourceIndexLength sourceSection = node.getPosition();

        RubyNode condition = node.getConditionNode().accept(this);
        if (conditionInversed) {
            condition = new NotNode(condition);
        }

        RubyNode body;
        final BreakID whileBreakID = environment.getParseEnvironment().allocateBreakID();

        final boolean oldTranslatingWhile = translatingWhile;
        translatingWhile = true;
        BreakID oldBreakID = environment.getBreakID();
        environment.setBreakIDForWhile(whileBreakID);
        frameOnStackMarkerSlotStack.push(BAD_FRAME_SLOT);
        try {
            body = translateNodeOrNil(sourceSection, node.getBodyNode());
        } finally {
            frameOnStackMarkerSlotStack.pop();
            environment.setBreakIDForWhile(oldBreakID);
            translatingWhile = oldTranslatingWhile;
        }

        final RubyNode loop;

        if (node.evaluateAtStart()) {
            loop = new WhileNode(new WhileNode.WhileRepeatingNode(condition, body));
        } else {
            loop = new WhileNode(new WhileNode.DoWhileRepeatingNode(condition, body));
        }

        final RubyNode ret = new CatchBreakNode(whileBreakID, loop, true);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitXStrNode(XStrParseNode node) {
        final ParseNode argsNode = buildArrayNode(
                node.getPosition(),
                new StrParseNode(node.getPosition(), node.getValue()));
        final ParseNode callNode = new FCallParseNode(node.getPosition(), "`", argsNode, null);
        final RubyNode ret = callNode.accept(this);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitYieldNode(YieldParseNode node) {
        final ParseNode argsNode = node.getArgsNode();
        boolean unsplat = false;

        final ParseNode[] arguments;
        if (argsNode == null) {
            // No arguments
            arguments = EMPTY_ARGUMENTS;
        } else if (argsNode instanceof ArrayParseNode) {
            // Multiple arguments
            arguments = ((ArrayParseNode) argsNode).children();
        } else if (argsNode instanceof SplatParseNode || argsNode instanceof ArgsCatParseNode ||
                argsNode instanceof ArgsPushParseNode) {
            unsplat = true;
            arguments = new ParseNode[]{ argsNode };
        } else {
            arguments = new ParseNode[]{ node.getArgsNode() };
        }

        final RubyNode[] argumentsTranslated = createArray(arguments.length);

        for (int i = 0; i < arguments.length; i++) {
            argumentsTranslated[i] = arguments[i].accept(this);
        }

        RubyNode readBlock = environment
                .findLocalVarOrNilNode(TranslatorEnvironment.METHOD_BLOCK_NAME, node.getPosition());

        final RubyNode ret = new YieldExpressionNode(
                unsplat,
                argumentsTranslated,
                readBlock,
                environment.shouldWarnYieldInModuleBody());

        ret.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitZArrayNode(ZArrayParseNode node) {
        final RubyNode ret = ArrayLiteralNode.create(language, RubyNode.EMPTY_ARRAY);
        ret.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitBackRefNode(BackRefParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode readGlobal = ReadGlobalVariableNodeGen.create("$" + node.getType());

        readGlobal.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, readGlobal);
    }

    @Override
    public RubyNode visitStarNode(StarParseNode star) {
        return nilNode(star.getPosition());
    }

    protected static RubyNode initFlipFlopStates(TranslatorEnvironment environment, SourceIndexLength sourceSection) {
        final RubyNode[] initNodes = createArray(environment.getFlipFlopStates().size());

        for (int n = 0; n < initNodes.length; n++) {
            initNodes[n] = new InitFlipFlopSlotNode(environment.getFlipFlopStates().get(n));
        }

        return sequence(sourceSection, Arrays.asList(initNodes));
    }

    @Override
    protected RubyNode defaultVisit(ParseNode node) {
        throw new UnsupportedOperationException(node.toString() + " " + node.getPosition());
    }

    public TranslatorEnvironment getEnvironment() {
        return environment;
    }

    @Override
    public RubyNode visitTruffleFragmentNode(TruffleFragmentParseNode node) {
        return addNewlineIfNeeded(node, node.getFragment());
    }

    @Override
    public RubyNode visitOther(ParseNode node) {
        throw new UnsupportedOperationException();
    }

    private void copyNewline(ParseNode from, ParseNode to) {
        if (from.isNewline()) {
            to.setNewline();
        }
    }

    private RubyNode addNewlineIfNeeded(ParseNode jrubyNode, RubyNode node) {
        if (jrubyNode.isNewline()) {
            final SourceIndexLength current = node.getEncapsulatingSourceIndexLength();

            if (current == null) {
                return node;
            }

            if (environment.getParseEnvironment().isCoverageEnabled()) {
                node.unsafeSetIsCoverageLine();
                language.coverageManager.setLineHasCode(source, current.toSourceSection(source).getStartLine());
            }
            node.unsafeSetIsNewLine();
        }

        return node;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + environment.getSharedMethodInfo();
    }

}
