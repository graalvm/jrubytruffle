# 1.0

New features

* `Queue` and `SizedQueue`, `#close` and `#closed?`, have been implemented.

# 1.0 RC 9

Security:

* CVE-2018-16396, *tainted flags are not propagated in Array#pack and
  String#unpack with some directives* has been mitigated by adding additional
  taint operations.

New features:

* LLVM for Oracle Linux 7 can now be installed without building from source.

Bug fixes

* Times can now be created with UTC offsets in `+/-HH:MM:SS` format.
* `Proc#to_s` now has `ASCII-8BIT` as its encoding instead of the
  incorrect `UTF-8`.
* `String#%` now has the correct encoding for `UTF-8` and `US-ASCII`
  format strings, instead of the incorrect `ASCII-8BIT`.
* Updated `BigDecimal#to_s` to use `e` instead of `E` for exponent
  notation.
* Fixed `BigDecimal#to_s` to allow `f` as a format flag to indicate
  conventional floating point notation. Previously only `F` was allowed.

Changes:

* The supported version of LLVM for Oracle Linux has been updated from 3.8
  to 4.0.
* `mysql2` is now patched to avoid a bug in passing `NULL` to
  `rb_scan_args`, and now passes the majority of its test suite.
* The post-install script now automatically detects if recompiling the OpenSSL
  C extension is needed. The post-install script should always be run in
  TravisCI as well, see `doc/user/standalone-distribution.md`.
* Detect when the system libssl is incompatible more accurately and
  add instructions on how to recompile the extension.

# 1.0 RC 8, October 2018

New features:

* `Java.synchronized(object) { }` and `TruffleRuby.synchronized(object) { }`
  methods have been added.
* Added a `TruffleRuby::AtomicReference` class.
* Ubuntu 18.04 LTS is now supported.
* macOS 10.14 (Mojave) is now supported.

Changes:

* Random seeds now use Java's `NativePRNGNonBlocking`.
* The supported version of Fedora is now 28, upgraded from 25.
* The FFI gem has been updated from 1.9.18 to 1.9.25.
* JCodings has been updated from 1.0.30 to 1.0.40.
* Joni has been updated from 2.1.16 to 2.1.25.

Performance

* Performance of setting the last exception on a thread has now been improved.

# 1.0 RC 7, October 2018

New features:

* Useful `inspect` strings have been added for more foreign objects.
* The C extension API now defines a preprocessor macro `TRUFFLERUBY`.
* Added the rbconfig/sizeof native extension for better MRI compatibility.
* Support for `pg` 1.1. The extension now compiles successfully, but
  may still have issues with some datatypes.

Bug fixes:

* `readline` can now be interrupted by the interrupt signal (Ctrl+C). This fixes
  Ctrl+C to work in IRB.
* Better compatibility with C extensions due to a new "managed struct" type.
* Fixed compilation warnings which produced confusing messages for end users (#1422).
* Improved compatibility with Truffle polyglot STDIO.
* Fixed version check preventing TruffleRuby from working with Bundler 2.0 and
  later (#1413).
* Fixed problem with `Kernel.public_send` not tracking its caller properly (#1425).
* `rb_thread_call_without_gvl()` no longer holds the C-extensions lock.
* Fixed `caller_locations` when called inside `method_added`.
* Fixed `mon_initialize` when called inside `initialize_copy` (#1428).
* `Mutex` correctly raises a `TypeError` when trying to serialize with `Marshal.dump`.

Performance:

* Reduced memory footprint for private/internal AST nodes.
* Increased the number of cases in which string equality checks will become
  compile-time constants.
* Major performance improvement for exceptional paths where the rescue body
  does not access the exception object (e.g., `x.size rescue 0`).

Changes:

* Many clean-ups to our internal patching mechanism used to make some native
  extensions run on TruffleRuby.
* Removed obsoleted patches for Bundler compatibility now that Bundler 1.16.5
  has built-in support for TruffleRuby.
* Reimplemented exceptions and other APIs that can return a backtrace to use
  Truffle's lazy stacktraces API.

# 1.0 RC 6, September 2018

New features:

* `Polyglot.export` can now be used with primitives, and will now convert
  strings to Java, and `.import` will convert them from Java.
* Implemented `--encoding`, `--external-encoding`, `--internal-encoding`.
* `rb_object_tainted` and similar C functions have been implemented.
* `rb_struct_define_under` has been implemented.
* `RbConfig::CONFIG['sysconfdir']` has been implemented.
* `Etc` has been implemented (#1403).
* The `-Xcexts=false` option disables C extensions.
* Instrumentation such as the CPUSampler reports methods in a clearer way like
  `Foo#bar`, `Gem::Specification.each_spec`, `block in Foo#bar` instead of just
  `bar`, `each_spec`, `block in bar` (which is what MRI displays in backtraces).
* TruffleRuby is now usable as a JSR 223 (`javax.script`) language.
* A migration guide from JRuby (`doc/user/jruby-migration.md`) is now included.
* `kind_of?` works as an alias for `is_a?` on foreign objects.
* Boxed foreign strings unbox on `to_s`, `to_str`, and `inspect`.

Bug fixes:

* Fix false-positive circular warning during autoload.
* Fix Truffle::AtomicReference for `concurrent-ruby`.
* Correctly look up `llvm-link` along `clang` and `opt` so it is no longer
  needed to add LLVM to `PATH` on macOS for Homebrew and MacPorts.
* Fix `alias` to work when in a refinement module (#1394).
* `Array#reject!` no longer truncates the array if the block raises an
  exception for an element.
* WeakRef now has the same inheritance and methods as MRI's version.
* Support `-Wl` linker argument for C extensions. Fixes compilation of`mysql2`
  and `pg`.
* Using `Module#const_get` with a scoped argument will now correctly
  autoload the constant if needed.
* Loaded files are read as raw bytes, rather than as a UTF-8 string and then
  converted back into bytes.
* Return 'DEFAULT' for `Signal.trap(:INT) {}`. Avoids a backtrace when quitting
  a Sinatra server with Ctrl+C.
* Support `Signal.trap('PIPE', 'SYSTEM_DEFAULT')`, used by the gem `rouge`
  (#1411).
* Fix arity checks and handling of arity `-2` for `rb_define_method()`.
* Setting `$SAFE` to a negative value now raises a `SecurityError`.
* The offset of `DATA` is now correct in the presence of heredocs.
* Fix double-loading of the `json` gem, which led to duplicate constant
  definition warnings.
* Fix definition of `RB_NIL_P` to be early enough. Fixes compilation of
  `msgpack`.
* Fix compilation of megamorphic interop calls.
* `Kernel#singleton_methods` now correctly ignores prepended modules of
  non-singleton classes. Fixes loading `sass` when `activesupport` is loaded.
* Object identity numbers should never be negative.

Performance:

* Optimize keyword rest arguments (`def foo(**kwrest)`).
* Optimize rejected (non-Symbol keys) keyword arguments.
* Source `SecureRandom.random_bytes` from `/dev/urandom` rather than OpenSSL.
* C extension bitcode is no longer encoded as Base64 to pass it to Sulong.
* Faster `String#==` using vectorization.

Changes:

* Clarified that all sources that come in from the Polyglot API `eval` method
  will be treated as UTF-8, and cannot be re-interpreted as another encoding
  using a magic comment.
* The `-Xembedded` option can now be set set on the launcher command line.
* The `-Xplatform.native=false` option can now load the core library, by
  enabling `-Xpolyglot.stdio`.
* `$SAFE` and `Thread#safe_level` now cannot be set to `1` - raising an error
  rather than warning as before. `-Xsafe` allows it to be set, but there are
  still no checks.
* Foreign objects are now printed as `#<Foreign:system-identity-hash-code>`,
  except for foreign arrays which are now printed as `#<Foreign [elements...]>`.
* Foreign objects `to_s` now calls `inspect` rather than Java's `toString`.
* The embedded configuration (`-Xembedded`) now warns about features which may
  not work well embedded, such as signals.
* The `-Xsync.stdio` option has been removed - use standard Ruby
  `STDOUT.sync = true` in your program instead.

# 1.0 RC 5, August 2018

New features:

* It is no longer needed to add LLVM (`/usr/local/opt/llvm@4/bin`) to `PATH` on
  macOS.
* Improve error message when LLVM, `clang` or `opt` is missing.
* Automatically find LLVM and libssl with MacPorts on macOS (#1386).
* `--log.ruby.level=` can be used to set the log level from any launcher.
* Add documentation about installing with Ruby managers/installers and how to
  run TruffleRuby in CI such as TravisCI (#1062, #1070).
* `String#unpack1` has been implemented.

Bug fixes:

* Allow any name for constants with `rb_const_get()`/`rb_const_set()` (#1380).
* Fix `defined?` with an autoload constant to not raise but return `nil` if the
  autoload fails (#1377).
* Binary Ruby Strings can now only be converted to Java Strings if they only
  contain US-ASCII characters. Otherwise, they would produce garbled Java
  Strings (#1376).
* `#autoload` now correctly calls `main.require(path)` dynamically.
* Hide internal file from user-level backtraces (#1375).
* Show caller information in warnings from the core library (#1375).
* `#require` and `#require_relative` should keep symlinks in `$"` and
  `__FILE__` (#1383).
* Random seeds now always come directly from `/dev/urandom` for MRI
  compatibility.
* SIGINFO, SIGEMT and SIGPWR are now defined (#1382).
* Optional and operator assignment expressions now return the value
  assigned, not the value returned by an assignment method (#1391).
* `WeakRef.new` will now return the correct type of object, even if `WeakRef` is
  subclassed (#1391).
* Resolving constants in prepended modules failed, this has now been
  fixed (#1391).
* Send and `Symbol#to_proc` now take account of refinements at their call
  sites (#1391).
* Better warning when the timezone cannot be found on WSL (#1393).
* Allow special encoding names in `String#force_encoding` and raise an exception
  on bad encoding names (#1397).
* Fix `Socket.getifaddrs` which would wrongly return an empty array (#1375).
* `Binding` now remembers the file and line at which it was created for `#eval`.
  This is notably used by `pry`'s `binding.pry`.
* Resolve symlinks in `GEM_HOME` and `GEM_PATH` to avoid related problems (#1383).
* Refactor and fix `#autoload` so other threads see the constant defined while
  the autoload is in progress (#1332).
* Strings backed by `NativeRope`s now make a copy of the rope when `dup`ed.
* `String#unpack` now taints return strings if the format was tainted, and
  now does not taint the return array if the format was tainted.
* Lots of fixes to `Array#pack` and `String#unpack` tainting, and a better
  implementation of `P` and `p`.
* Array literals could evaluate an element twice under some
  circumstances. This has now been fixed.

Performance:

* Optimize required and optional keyword arguments.
* `rb_enc_to_index` is now faster by eliminating an expensive look-up.

Changes:

* `-Xlog=` now needs log level names to be upper case.
* `-Dtruffleruby.log` and `TRUFFLERUBY_LOG` have been removed - use
  `-Dpolyglot.log.ruby.level`.
* The log format, handlers, etc are now managed by the Truffle logging system.
* The custom log levels `PERFORMANCE` and `PATCH` have been removed.

# 1.0 RC 4, 18 July 2018

*TruffleRuby was not updated in RC 4*

# 1.0 RC 3, 2 July 2018

New features:

* `is_a?` can be called on foreign objects.

Bug fixes:

* It is no longer needed to have `ruby` in `$PATH` to run the post-install hook.
* `Qnil`/`Qtrue`/`Qfalse`/`Qundef` can now be used as initial value for global
  variables in C extensions.
* Fixed error message when the runtime libssl has no SSLv2 support (on Ubuntu
  16.04 for instance).
* `RbConfig::CONFIG['extra_bindirs']` is now a String as other RbConfig values.
* `SIGPIPE` is correctly caught on SubstrateVM, and the corresponding write()
  raises `Errno::EPIPE` when the read end of a pipe or socket is closed.
* Use the magic encoding comment for determining the source encoding when using
  eval().
* Fixed a couple bugs where the encoding was not preserved correctly.

Performance:

* Faster stat()-related calls, by returning the relevant field directly and
  avoiding extra allocations.
* `rb_str_new()`/`rb_str_new_cstr()` are much faster by avoiding extra copying
  and allocations.
* `String#{sub,sub!}` are faster in the common case of an empty replacement
  string.
* Eliminated many unnecessary memory copy operations when reading from `IO` with
  a delimiter (e.g., `IO#each`), leading to overall improved `IO` reading for
  common use cases such as iterating through lines in a `File`.
* Use the byte[] of the given Ruby String when calling eval() directly for
  parsing.

# 1.0 RC 2, 6 June 2018

New features:

* We are now compatible with Ruby 2.4.4.
* `object.class` on a Java `Class` object will give you an object on which you
  can call instance methods, rather than static methods which is what you get by
  default.
* The log level can now also be set with `-Dtruffleruby.log=info` or
  `TRUFFLERUBY_LOG=info`.
* `-Xbacktraces.raise` will print Ruby backtraces whenever an exception is
  raised.
* `Java.import name` imports Java classes as top-level constants.
* Coercion of foreign numbers to Ruby numbers now works.
* `to_s` works on all foreign objects and calls the Java `toString`.
* `to_str` will try to `UNBOX` and then re-try `to_str`, in order to provoke
  the unboxing of foreign strings.

Changes:

* The version string now mentions if you're running GraalVM Community Edition
  (`GraalVM CE`) or GraalVM Enterprise Edition (`GraalVM EE`).
* The inline JavaScript functionality `-Xinline_js` has been removed.
* Line numbers `< 0`, in the various eval methods, are now warned about, because
  we don't support these at all. Line numbers `> 1` are warned about (at the
  fine level) but the are shimmed by adding blank lines in front to get to the
  correct offset. Line numbers starting at `0` are also warned about at the fine
  level and set to `1` instead.
* The `erb` standard library has been patched to stop using a -1 line number.
* `-Xbacktraces.interleave_java` now includes all the trailing Java frames.
* Objects with a `[]` method, except for `Hash`, now do not return anything
  for `KEYS`, to avoid the impression that you could `READ` them. `KEYINFO`
  also returns nothing for these objects, except for `Array` where it returns
  information on indices.
* `String` now returns `false` for `HAS_KEYS`.
* The supported additional functionality module has been renamed from `Truffle`
  to `TruffleRuby`. Anything not documented in
  `doc/user/truffleruby-additions.md` should not be used.
* Imprecise wrong gem directory detection was replaced. TruffleRuby newly marks
  its gem directories with a marker file, and warns if you try to use 
  TruffleRuby with a gem directory which is lacking the marker. 

Bug fixes:

* TruffleRuby on SubstrateVM now correctly determines the system timezone.
* `Kernel#require_relative` now coerces the feature argument to a path and
  canonicalizes it before requiring, and it now uses the current directory as
  the directory for a synthetic file name from `#instance_eval`.

# 1.0 RC 1, 17 April 2018

New features:

* The Ruby version has been updated to version 2.3.7.

Security:

* CVE-2018-6914, CVE-2018-8779, CVE-2018-8780, CVE-2018-8777, CVE-2017-17742
  and CVE-2018-8778 have been mitigated.

Changes:

* `RubyTruffleError` has been removed and uses replaced with standard
  exceptions.
* C++ libraries like `libc++` are now not needed if you don't run C++
  extensions. `libc++abi` is now never needed. Documentation updated to make it
  more clear what the minimum requirements for pure Ruby, C extensions, and C++
  extensions separately.
* C extensions are now built by default - `TRUFFLERUBY_CEXT_ENABLED` is assumed
  `true` unless set to `false`.
* The `KEYS` interop message now returns an array of Java strings, rather than
  Ruby strings. `KEYS` on an array no longer returns indices.
* `HAS_SIZE` now only returns `true` for `Array`.
* A method call on a foreign object that looks like an operator (the method name
  does not begin with a letter) will call `IS_BOXED` on the object and based on
  that will possibly `UNBOX` and convert to Ruby.
* Now using the native version of Psych.
* The supported version of LLVM on Oracle Linux has been dropped to 3.8.
* The supported version of Fedora has been dropped to 25, and the supported
  version of LLVM to 3.8, due to LLVM incompatibilities. The instructions for
  installing `libssl` have changed to match.

# 0.33, April 2018

New features:

* The Ruby version has been updated to version 2.3.6.
* Context pre-initialization with TruffleRuby `--native`, which significantly
  improves startup time and loads the `did_you_mean` gem ahead of time.
* The default VM is changed to SubstrateVM, where the startup is significantly 
  better. Use `--jvm` option for full JVM VM.
* The `Truffle::Interop` module has been replaced with a new `Polyglot` module
  which is designed to use more idiomatic Ruby syntax rather than explicit
  methods. A [new document](doc/user/polyglot.md) describes polyglot programming
  at a higher level.
* The `REMOVABLE`, `MODIFIABLE` and `INSERTABLE` Truffle interop key info flags
  have been implemented.
* `equal?` on foreign objects will check if the underlying objects are equal
  if both are Java interop objects.
* `delete` on foreign objects will send `REMOVE`, `size` will send `GET_SIZE`,
  and `keys` will send `KEYS`. `respond_to?(:size)` will send `HAS_SIZE`,
  `respond_to?(:keys)` will send `HAS_KEYS`.
* Added a new Java-interop API similar to the one in the Nashorn JavaScript
  implementation, as also implemented by Graal.js. The `Java.type` method
  returns a Java class object on which you can use normal interop methods. Needs
  the `--jvm` flag to be used.
* Supported and tested versions of LLVM for different platforms have been more
  precisely [documented](doc/user/installing-llvm.md).

Changes:

* Interop semantics of `INVOKE`, `READ`, `WRITE`, `KEYS` and `KEY_INFO` have
  changed significantly, so that `INVOKE` maps to Ruby method calls, `READ`
  calls `[]` or returns (bound) `Method` objects, and `WRITE` calls `[]=`.

Performance:

* `Dir.glob` is much faster and more memory efficient in cases that can reduce
  to direct filename lookups.
* `SecureRandom` now defers loading OpenSSL until it's needed, reducing time to
  load `SecureRandom`.
* `Array#dup` and `Array#shift` have been made constant-time operations by
  sharing the array storage and keeping a starting index.

Bug fixes:

* Interop key-info works with non-string-like names.

Internal changes:

* Changes to the lexer and translator to reduce regular expression calls.
* Some JRuby sources have been updated to 9.1.13.0.

# 0.32, March 2018

New features:

* A new embedded configuration is used when TruffleRuby is used from another
  language or application. This disables features like signals which may
  conflict with the embedding application, and threads which may conflict with
  other languages, and enables features such as the use of polyglot IO streams.

Performance:

* Conversion of ASCII-only Ruby strings to Java strings is now faster.
* Several operations on multi-byte character strings are now faster.
* Native I/O reads are ~22% faster.

Bug fixes:

* The launcher accepts `--native` and similar options in  the `TRUFFLERUBYOPT`
environment variable.

Internal changes:

* The launcher is now part of the TruffleRuby repository, rather than part of
the GraalVM repository.
* `ArrayBuilderNode` now uses `ArrayStrategies` and `ArrayMirrors` to remove
direct knowledge of array storage.
* `RStringPtr` and `RStringPtrEnd` now report as pointers for interop purposes,
fixing several issues with `char *` usage in C extensions.
