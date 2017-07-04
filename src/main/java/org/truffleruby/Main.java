/*
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 *
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
 * Copyright (C) 2001 Alan Moore <alan_moore@gmx.net>
 * Copyright (C) 2001-2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2001-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004-2006 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2005 Kiel Hodges <jruby-devel@selfsosoft.com>
 * Copyright (C) 2005 Jason Voegele <jason@jvoegele.com>
 * Copyright (C) 2005 Tim Azzopardi <tim@tigerfive.com>
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
 */
package org.truffleruby;

import com.oracle.truffle.api.TruffleOptions;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotContext;
import org.graalvm.polyglot.Source;
import org.truffleruby.options.CommandLineOptions;
import org.truffleruby.options.CommandLineParser;
import org.truffleruby.options.MainExitException;
import org.truffleruby.options.OptionsBuilder;
import org.truffleruby.options.OptionsCatalog;

import java.lang.management.ManagementFactory;
import java.util.Map;

public class Main {

    public static final String BOOT_SOURCE_NAME = "main_boot_source";
    private static final boolean METRICS_TIME = Boolean.getBoolean(OptionsBuilder.PREFIX + "metrics.time");
    private static final boolean METRICS_MEMORY_USED_ON_EXIT = Boolean.getBoolean(OptionsBuilder.PREFIX + "metrics.memory_used_on_exit");

    public static void main(String[] args) {
        printTruffleTimeMetric("before-main");

        final CommandLineOptions config = new CommandLineOptions();

        try {
            processArguments(config, args);
        } catch (MainExitException mee) {
            if (!mee.isAborted()) {
                System.err.println(mee.getMessage());
                if (mee.isUsageError()) {
                    CommandLineParser.printHelp(System.err);
                }
            }
            System.exit(mee.getStatus());
        }

        if (config.isShowVersion()) {
            System.out.println(RubyLanguage.getVersionString());
        }

        if (config.isShowCopyright()) {
            System.out.println(RubyLanguage.getCopyrightString());
        }

        final int exitCode;

        if (config.getShouldRunInterpreter()) {
            final String filename = config.getDisplayedFileName();
            try (Engine engine = createEngine(config, filename);
                 final PolyglotContext polyglotContext = engine.newPolyglotContextBuilder().
                         setArguments(getLanguageId(), config.getArguments()).build()) {

                printTruffleTimeMetric("before-run");
                if (config.getShouldCheckSyntax()) {
                    // check syntax only and exit
                    final Source source = Source.newBuilder(
                            //language=ruby
                            "Truffle::Boot.check_syntax").name("check_syntax").build();
                    boolean status = polyglotContext.eval(getLanguageId(), source).asBoolean();
                    exitCode = status ? 0 : 1;
                } else {
                    final Boolean graalWarning = OptionsCatalog.GRAAL_WARNING_UNLESS.checkValue(
                            config.getOptions().getOrDefault(
                                    OptionsCatalog.GRAAL_WARNING_UNLESS.getName(),
                                    String.valueOf(OptionsCatalog.GRAAL_WARNING_UNLESS.getDefaultValue())));
                    if (!RubyLanguage.isGraal() && graalWarning) {
                        Log.performanceOnce("this JVM does not have the Graal compiler - performance will be limited" +
                                " - see doc/user/using-graalvm.md");
                    }

                    final Source source = Source.newBuilder(
                            //language=ruby
                            config.shouldUsePathScript() ? "Truffle::Boot.main_s" : "Truffle::Boot.main").name(
                            BOOT_SOURCE_NAME).build();
                    exitCode = polyglotContext.eval(getLanguageId(), source).asInt();
                }
                printTruffleTimeMetric("after-run");
            }
        } else {
            if (config.getShouldPrintShortUsage()) {
                CommandLineParser.printShortHelp(System.out);
            } else if (config.getShouldPrintUsage()) {
                CommandLineParser.printHelp(System.out);
            }
            exitCode = 0;
        }

        printTruffleTimeMetric("after-main");
        printTruffleMemoryMetric();
        System.exit(exitCode);
    }

    private static Engine createEngine(CommandLineOptions config, String filename) {
        final Engine.Builder builder = Engine.newBuilder();

        // TODO CS 2-Jul-17 some of these values are going back and forth from string and array representation
        builder.setOption(
                OptionsCatalog.LOAD_PATHS.getSDKName(),
                OptionsCatalog.LOAD_PATHS.toString(config.getLoadPaths().toArray(new String[]{})));
        builder.setOption(
                OptionsCatalog.REQUIRED_LIBRARIES.getSDKName(),
                OptionsCatalog.LOAD_PATHS.toString(config.getRequiredLibraries().toArray(new String[]{})));
        builder.setOption(OptionsCatalog.INLINE_SCRIPT.getSDKName(), config.inlineScript());
        builder.setOption(OptionsCatalog.DISPLAYED_FILE_NAME.getSDKName(), filename);

        /*
         * We turn off using the polyglot IO streams when running from our launcher, because they don't act like
         * normal file descriptors and this can cause problems in some advanced IO functionality, such as pipes and
         * blocking behaviour. We also turn off sync on stdio and so revert to Ruby's default logic for looking
         * at whether a file descriptor looks like a TTY for deciding whether to make it synchronous or not.
         */

        builder.setOption(OptionsCatalog.POLYGLOT_STDIO.getSDKName(), Boolean.FALSE.toString());
        builder.setOption(OptionsCatalog.SYNC_STDIO.getSDKName(), Boolean.FALSE.toString());

        for (Map.Entry<String, String> option : config.getOptions().entrySet()) {
            builder.setOption(getLanguageId() + "." + option.getKey(), option.getValue());
        }

        return builder.build();
    }

    private static String getLanguageId() {
        return "ruby";
    }

    public static void processArguments(CommandLineOptions config, String[] arguments) {
        new CommandLineParser(arguments, config).processArguments();

        if (config.getOptions().
                getOrDefault(
                        OptionsCatalog.READ_RUBYOPT.getSDKName(),
                        OptionsCatalog.READ_RUBYOPT.getDefaultValue().toString()).equals(Boolean.TRUE.toString())) {
            CommandLineParser.processEnvironmentVariable("RUBYOPT", config, true);
            CommandLineParser.processEnvironmentVariable("TRUFFLERUBYOPT", config, false);
        }

        if (!config.doesHaveScriptArgv() && !config.shouldUsePathScript() && System.console() != null) {
            config.setUsePathScript("irb");
        }

        config.setOption(
                OptionsCatalog.ORIGINAL_INPUT_FILE,
                config.shouldUsePathScript() ? config.getScriptFileName() : config.getDisplayedFileName());
    }

    public static void printTruffleTimeMetric(String id) {
        if (METRICS_TIME) {
            final long millis = System.currentTimeMillis();
            System.err.printf("%s %d.%03d%n", id, millis / 1000, millis % 1000);
        }
    }

    private static void printTruffleMemoryMetric() {
        // Memory stats aren't available on AOT.
        if (!TruffleOptions.AOT && METRICS_MEMORY_USED_ON_EXIT) {
            for (int n = 0; n < 10; n++) {
                System.gc();
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            System.err.printf("allocated %d%n", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        }
    }

}
