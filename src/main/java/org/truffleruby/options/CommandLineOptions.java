/***** BEGIN LICENSE BLOCK *****
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
 * Copyright (C) 2007-2011 Nick Sieger <nicksieger@gmail.com>
 * Copyright (C) 2009 Joseph LaFata <joe@quibb.org>
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
package org.truffleruby.options;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class CommandLineOptions {

    private Map<String, String> options = new HashMap<>();

    private List<String> loadPaths = new ArrayList<>();
    private String[] arguments = new String[]{};
    private StringBuffer inlineScript = new StringBuffer();
    private boolean hasInlineScript;
    private boolean usePathScript;
    private String scriptFileName;
    private Collection<String> requiredLibraries = new LinkedHashSet<>();
    private boolean argvGlobalsOn;
    private Map<String, String> optionGlobals = new HashMap<>();
    private boolean split;
    private boolean showVersion;
    private boolean showCopyright;
    private boolean shouldRunInterpreter = true;
    private boolean shouldPrintUsage;
    private boolean shouldCheckSyntax;
    private String inPlaceBackupExtension;
    private boolean hasScriptArgv;
    private boolean forceStdin;
    private boolean shouldPrintShortUsage;

    public Map<String, String> getOptions() {
        return options;
    }

    public String[] getArguments() {
        return arguments;
    }

    public void setArguments(String[] arguments) {
        this.arguments = arguments;
    }

    public String inlineScript() {
        return inlineScript.toString();
    }

    public StringBuffer getInlineScript() {
        return inlineScript;
    }

    public void setHasInlineScript(boolean hasInlineScript) {
        this.hasScriptArgv = true;
        this.hasInlineScript = hasInlineScript;
    }

    public Collection<String> getRequiredLibraries() {
        return requiredLibraries;
    }

    public List<String> getLoadPaths() {
        return loadPaths;
    }

    public void setShouldPrintUsage(boolean shouldPrintUsage) {
        this.shouldPrintUsage = shouldPrintUsage;
    }

    public boolean getShouldPrintUsage() {
        return shouldPrintUsage;
    }

    public boolean isInlineScript() {
        return hasInlineScript;
    }

    public boolean isForceStdin() {
        return forceStdin;
    }

    public void setForceStdin(boolean forceStdin) {
        this.forceStdin = forceStdin;
    }

    public void setScriptFileName(String scriptFileName) {
        this.hasScriptArgv = true;
        this.scriptFileName = scriptFileName;
    }

    public String getScriptFileName() {
        return scriptFileName;
    }

    public void setSplit(boolean split) {
        this.split = split;
    }

    public boolean isSplit() {
        return split;
    }

    public void setShowVersion(boolean showVersion) {
        this.showVersion = showVersion;
    }

    public boolean isShowVersion() {
        return showVersion;
    }

    public void setShowCopyright(boolean showCopyright) {
        this.showCopyright = showCopyright;
    }

    public boolean isShowCopyright() {
        return showCopyright;
    }

    public void setShouldRunInterpreter(boolean shouldRunInterpreter) {
        this.shouldRunInterpreter = shouldRunInterpreter;
    }

    public boolean getShouldRunInterpreter() {
        return shouldRunInterpreter && (hasScriptArgv || !showVersion);
    }

    public void setShouldCheckSyntax(boolean shouldSetSyntax) {
        this.shouldCheckSyntax = shouldSetSyntax;
    }

    public boolean getShouldCheckSyntax() {
        return shouldCheckSyntax;
    }

    public void setInPlaceBackupExtension(String inPlaceBackupExtension) {
        this.inPlaceBackupExtension = inPlaceBackupExtension;
    }

    public String getInPlaceBackupExtension() {
        return inPlaceBackupExtension;
    }

    public Map<String, String> getOptionGlobals() {
        return optionGlobals;
    }

    public boolean isArgvGlobalsOn() {
        return argvGlobalsOn;
    }

    public void setArgvGlobalsOn(boolean argvGlobalsOn) {
        this.argvGlobalsOn = argvGlobalsOn;
    }

    public boolean doesHaveScriptArgv() {
        return hasScriptArgv;
    }

    public void setUsePathScript(String name) {
        scriptFileName = name;
        usePathScript = true;
    }

    public boolean shouldUsePathScript() {
        return usePathScript;
    }

    public void setShouldPrintShortUsage(boolean shouldPrintShortUsage) {
        this.shouldPrintShortUsage = shouldPrintShortUsage;
    }

    public boolean getShouldPrintShortUsage() {
        return shouldPrintShortUsage;
    }

}
