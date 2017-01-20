# The TruffleRuby Contributor Workflow

## Requirements

You will need:

* Java 8 (not 9 EA)
* Ruby 2

## Developer tool

We use a Ruby script to run most commands.

```
$ ruby tool/jt.rb --help
```

Most of us create a symlink to this executable somewhere on our `$PATH` so
that we can simply run.

```
$ jt --help
```

## Building

```
$ jt build
```

## Testing

We have 'specs' which come from the Ruby Spec Suite. These are usually high
quality, small tests, and are our priority at the moment. We also have MRI's
unit tests, which are often very complex and we aren't actively working on now.
Finally, we have tests of our own. The integration tests test more macro use of
Ruby. The ecosystem tests test commands related to Ruby. The gems tests test a
small number of key Ruby 3rd party modules.

The basic test to run every time you make changes is a subset of specs which
runs in reasonable time.

```
$ jt test fast
```

You may also want to regularly run the integration tests.

```
$ jt test integration
```

Other tests can be hard to set up and can require other repositories, so we
don't normally run them locally unless we're working on that functionality.

## Running

`jt ruby` runs TruffleRuby. You can use it exactly as you'd run the MRI `ruby`
command. Although it does set a couple of extra options to help you when
developing, such as loading the core lirbary from disk rather than the JAR. `jt
ruby` prints the real command it's running as it starts.

```
$ ruby ...
$ jt ruby ...
```

## Options

Specify JVM options with `-J-option`.

```
$ jt ruby -J-Xmx1G test.rb
```

TruffleRuby options are set with `-Xtruffle...=...`. For example
`-Xtruffle.exceptions.print_java=true` to print Java exceptions before
translating them to Ruby exceptions.

To see all options run `jt ruby -Xtruffle...` (literally, with the three dots).

You can also set JVM options in the `JAVA_OPTS` environment variable (don't
prefix with `-J`), or the `JRUBY_OPTS` variable (do prefix with `-J`). Ruby
command line options and arguments can also be set in `JRUBY_OPTS` or `RUBYOPT`
if they aren't TruffleRuby-specific.

## Running with Graal

To run with a GraalVM binary tarball, set the `GRAALVM_BIN` environment variable
and run with the `--graal` option.

```
$ export GRAALVM_BIN=.../graalvm-0.19-re/bin/java
$ jt ruby --graal ...
```

You can check this is working by printing the value of `Truffle::Graal.graal?`.

```
$ export GRAALVM_BIN=.../graalvm-0.19-re/bin/java
$ jt ruby --graal -e 'p Truffle::Graal.graal?'
```

To run with Graal built from source, set `GRAAL_HOME`.

```
$ export GRAAL_HOME=.../graal-core
$ jt ruby --graal ...
```

Set Graal options as any other JVM option.

```
$ jt ruby --graal -J-Dgraal.TraceTruffleCompilation=true ...
```

We have flags in `jt` to set some options, such as `--trace` for
`-J-Dgraal.TraceTruffleCompilation=true` and `--igv` for
`-J-Dgraal.Dump=Truffle`.

## Testing with Graal

The basic test for Graal is to run our compiler tests. This includes tests that
things partially evaluate as we expect, that things optimise as we'd expect,
that on-stack-replacement works and so on.

```
$ jt test compiler
```

## mx and integrating with other Graal projects

TruffleRuby can also be built and run using `mx`, like the other Graal projects.
This is intended for special cases such as integrating with other Graal
projects, and we wouldn't recommend using it for normal development. If you do
use it, you should clean before using `jt` again as having built it with `mx`
will change some behaviour.
