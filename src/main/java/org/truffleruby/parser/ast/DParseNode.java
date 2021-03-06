/*
 ***** BEGIN LICENSE BLOCK *****
 * Version: EPL 2.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v20.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
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
import org.jcodings.specific.ASCIIEncoding;
import org.truffleruby.language.SourceIndexLength;

/** Base class for all D (e.g. Dynamic) node types like DStrParseNode, DSymbolParseNode, etc... */
public abstract class DParseNode extends ListParseNode {
    protected Encoding encoding;

    public DParseNode(SourceIndexLength position) {
        // FIXME: I believe this possibly should be default parsed encoding but this is
        // what we currently default to if we happen to receive a null encoding.  This is
        // an attempt to at least always have a valid encoding set to something.
        this(position, ASCIIEncoding.INSTANCE);
    }

    public DParseNode(SourceIndexLength position, Encoding encoding) {
        super(position);

        assert encoding != null : getClass().getName() + " passed in a null encoding";

        this.encoding = encoding;
    }

    public Encoding getEncoding() {
        return encoding;
    }
}
