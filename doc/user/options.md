# TruffleRuby Options and Command Line

TruffleRuby has the same command line interface as MRI 2.3.6.

```
Usage: truffleruby [switches] [--] [programfile] [arguments]
  -0[octal]       specify record separator (\0, if no argument)
  -a              autosplit mode with -n or -p (splits $_ into $F)
  -c              check syntax only
  -Cdirectory     cd to directory before executing your script
  -d, --debug     set debugging flags (set $DEBUG to true)
  -e 'command'    one line of script. Several -e's allowed. Omit [programfile]
  -Eex[:in], --encoding=ex[:in]
                  specify the default external and internal character encodings
  -Fpattern       split() pattern for autosplit (-a)
  -i[extension]   edit ARGV files in place (make backup if extension supplied)
  -Idirectory     specify $LOAD_PATH directory (may be used more than once)
  -l              enable line ending processing
  -n              assume 'while gets(); ... end' loop around your script
  -p              assume loop like -n but print line also like sed
  -rlibrary       require the library before executing your script
  -s              enable some switch parsing for switches after script name
  -S              look for the script using PATH environment variable
  -T[level=1]     turn on tainting checks
  -v, --verbose   print version number, then turn on verbose mode
  -w              turn warnings on for your script
  -W[level=2]     set warning level; 0=silence, 1=medium, 2=verbose
  -x[directory]   strip off text before #!ruby line and perhaps cd to directory
  --copyright     print the copyright
  --enable=feature[,...], --disable=feature[,...]
                  enable or disable features
  --external-encoding=encoding, --internal-encoding=encoding
                  specify the default external or internal character encoding
  --version       print the version
  --help          show this message, -h for short message
Features:
  gems            rubygems (default: enabled)
  did_you_mean    did_you_mean (default: enabled)
  rubyopt         RUBYOPT environment variable (default: enabled)
  frozen-string-literal
                  freeze all string literals (default: disabled)
```

TruffleRuby also reads the `RUBYOPT` environment variable.

## Unlisted Ruby switches

MRI has some extra Ruby switches which are aren't normally listed.

```
  -U              set the internal encoding to UTF-8
  -KEeSsUuNnAa    sets the source and external encoding
  -y, --ydebug    debug the parser
  -Xdirectory     the same as -Cdirectory
  --dump=insns    print disassembled instructions
```

## TruffleRuby-specific switches

Beyond the standard Ruby command line switches we support some additional
switches specific to TruffleRuby.

```
TruffleRuby switches:
  -Xlog=severe,warning,performance,info,config,fine,finer,finest
                  set the TruffleRuby logging level
  -Xsingle_threaded
                  run in single-threaded configuration
  -Xoptions       print available TruffleRuby options
  -Xname=value    set a TruffleRuby option (omit value to set to true)
```

As well as being set at the command line, options, except for `log` and
`single_threaded`, can be set using `--ruby.option=` in any GraalVM launcher.
For example `--ruby.inline_js=true`. They can also be set as JVM system
properties, where they have a prefix `polyglot.ruby.`. For example
`-J-Dpolyglot.ruby.inline_js=true`, or via any other way of setting JVM system
properties. Finally, options can be set as `PolyglotEngine` or SDK configuration
options.

The priority for options is the command line first, then the `PolyglotEngine`
configuration, then the SDK configuration, then system properties last.

The logging level is not a TruffleRuby option like the others and so cannot be
set with a JVM system property. This is because the logger is once per VM,
rather than once per TruffleRuby instance, and is used to report problems
loading the TruffleRuby instance before options are loaded.

TruffleRuby-specific options, as well as conventional Ruby options, can also
bet set in the `TRUFFLERUBYOPT` environment variable.

`--` or the first non-option argument both stop processing of Truffle-specific
arguments in the same way it stops processing of Ruby arguments.

The `-Xsingle_threaded` option can also be set as a system property
`-J-Dtruffleruby.single_threaded=true`.

## JVM- and SVM-specific switches

If you are running TruffleRuby on a JVM or the GraalVM, we additionally support
passing options to the JVM using either a `-J-`, `-J:`, or `--jvm.` prefix.
For example `-J-ea` or `-J:ea`. `-J-classpath` and `-J-cp` (or the `-J:`
variants) also implicitly take the following argument to be passed to the JVM.
`-J-cmd` or `-J:cmd` print the Java command that will be executed, for
debugging.

```
JVM switches:
  -J-arg, -J:arg, --jvm.arg      pass arg to the JVM
```

`--` or the first non-option argument both stop processing of JVM-specific
arguments in the same way it stops processing of Ruby arguments.

TruffleRuby also supports the `JAVA_HOME`, `JAVACMD` and `JAVA_OPTS` environment
variables when running on a JVM (except for `JAVACMD` on the GraalVM).

## SVM-specific switches

The SVM supports `-D` for setting system properties and `-XX:arg` for SVM
options. Unlike with the standard Ruby command-line, these options are always
taken by the SVM, wherever they appear in the arguments (such as after a `--`).

```
SVM switches:
  -Dname=value     set a system property
  -XX:arg          pass arg to the SVM
```

## Determining the TruffleRuby home

TruffleRuby needs to know where to locate files such as the standard library.
These are stored in the TruffleRuby home directory. The TruffleRuby option
`home` has priority for setting the home directory. Otherwise it is set
automatically to the directory containing the TruffleRuby JAR file, if
TruffleRuby is running on a JVM.
