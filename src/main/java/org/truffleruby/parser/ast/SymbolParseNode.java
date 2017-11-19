/*
 ***** BEGIN LICENSE BLOCK *****
 * Version: EPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2002 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2002 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2006 Lukas Felber <lfelber@hsr.ch>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the EPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.truffleruby.parser.ast;

import org.jcodings.Encoding;
import org.jcodings.specific.USASCIIEncoding;
import org.truffleruby.core.rope.CodeRange;
import org.truffleruby.core.rope.Rope;
import org.truffleruby.core.rope.RopeOperations;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.language.SourceIndexLength;
import org.truffleruby.parser.ast.types.ILiteralNode;
import org.truffleruby.parser.ast.types.INameNode;
import org.truffleruby.parser.ast.visitor.NodeVisitor;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Represents a symbol (:symbol_name).
 */
public class SymbolParseNode extends ParseNode implements ILiteralNode, INameNode, SideEffectFree {
    private final String name;
    private final Rope rope;

    // Interned ident path (e.g. [':', ident]).
    public SymbolParseNode(SourceIndexLength position, String name, Encoding encoding, CodeRange cr) {
        // The cr argument is ignored since it is not always correct
        super(position);
        this.name = name;  // Assumed all names are already intern'd by lexer.
        if (encoding.isAsciiCompatible() && StringOperations.isASCIIOnly(name)) {
            this.rope = StringOperations.encodeRope(name, USASCIIEncoding.INSTANCE, CodeRange.CR_7BIT);
        } else {
            this.rope = StringOperations.encodeRope(name, encoding, CodeRange.CR_UNKNOWN);
        }
    }

    // String path (e.g. [':', str_beg, str_content, str_end])
    public SymbolParseNode(SourceIndexLength position, Rope value) {
        super(position);
        this.name = RopeOperations.decodeRope(StandardCharsets.ISO_8859_1, value).intern();

        if (value.getCodeRange() == CodeRange.CR_7BIT) {
            rope = RopeOperations.withEncodingVerySlow(value, USASCIIEncoding.INSTANCE);
        } else {
            rope = value;
        }
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.SYMBOLNODE;
    }

    @Override
    public <T> T accept(NodeVisitor<T> iVisitor) {
        return iVisitor.visitSymbolNode(this);
    }

    /**
     * Gets the name.
     * @return Returns a String
     */
    public String getName() {
        return name;
    }

    public Rope getRope() {
        return rope;
    }

    @Override
    public List<ParseNode> childNodes() {
        return EMPTY_LIST;
    }
}
