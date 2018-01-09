# Legal Documentation

This document applies to TruffleRuby as built and distributed as part of
GraalVM, which is the only supported way to use TruffleRuby.

## TruffleRuby

TruffleRuby is copyright (c) 2013-2017 Oracle and/or its
affiliates, and is made available to you under the terms of three licenses:

* Eclipse Public License version 1.0
* GNU General Public License version 2
* GNU Lesser General Public License version 2.1

See `epl-1.0.txt`, `gpl-2.txt`, `lgpl-2.1.txt`.

## MRI

The standard implementation of Ruby is MRI. TruffleRuby contains code from MRI
version 2.3.6, including the standard library in `lib/mri`, Ruby C extension API
in `lib/cext` and `src/main/c/cext`, and OpenSSL in
`src/main/c/openssl`.

MRI is copyright Yukihiro Matsumoto. It is made available under the terms of the
2-clause BSD licence `ruby-bsdl.txt`, or a custom licence `ruby-licence.txt`.

## JRuby

TruffleRuby contains code from JRuby 9.1.7.0, including Java implementation
code, build system, shell script launchers, standard library modified from MRI,
and so on.

Where original files had JRuby licence headers we have copied them over. In
general this code is available under these licences:

* Eclipse Public License version 1.0, or
* GNU General Public License version 2, or
* GNU Lesser General Public License version 2.1, or
* Common Public License 1.0 (only for the parser)

See `epl-1.0.txt`, `gpl-2.txt`, `lgpl-2.1.txt`, `cpl-1.0.txt`.

Note that the JRuby project object model file declares the licenses as GPL 3 and
LGPL 3. This is an error, documented in
https://github.com/jruby/jruby/issues/4454 and fixed by the maintainers in
https://github.com/jruby/jruby/commit/cb165f2a903158a11e015ddaeb500fb95cb017b2
after the release of 9.1.7.0.

Some libraries that were spun out of JRuby, such as ByteList, have been
incorporated into our source code. These were under the same copyright and
licence as JRuby in the first place, so we have considered them part of JRuby.

For historical information from JRuby, see `jruby-copying.txt`, but this will
now be out of date.

# Rubinius

TruffleRuby contains code from Rubinius 2.11, including core library
implementation in `truffleruby/core/src/main/ruby/core`. This is in some cases
copyright 2011 Evan Phoenix, and in other cases copyright 2007-2015 Evan Phoenix
and contributors, and released under the 3-clause BSD license. We have included
licence headers in these files which weren't in the original ones.

Some parts of the Truffle Java code (such as the implementation of Rubinius
primitives) are derived from Rubinius C++ code which is copyright 2007-2015,
Evan Phoenix and contributors, and released under the 3-clause BSD license.

Some parts of the Ruby implementations of the standard library in `lib/truffle`
are copyright 2013 Brian Shirai and are licensed under the 3-clause BSD license.
In some cases this code is just code from MRI, and covered by their licence. In
some cases we have modified this code.

# Included gems

Versions as used in MRI unless otherwise specified.

#### did_you_mean

did_you_mean is copyright 2014 Yuki Nishijima and is available under an MIT
licence (see `mit.txt`).

#### minitest

minitest is copyright Ryan Davis and is available under an MIT licence (see
`mit.txt`).

#### net-telnet

net-telnet is under the same copyright and licence as MRI.

#### power_assert

power_assert copyright Kazuki Tsujimoto, but available under the same licence as
MRI.

#### Rake

Rake is copyright Jim Weirich and is available under an MIT licence (see
`mit.txt`).

#### test-unit

test-unit is copyright Kouhei Sutou, Ryan Davis, and Nathaniel Talbott and is
available under the terms of the GPL 2 (see `gpl-2.txt`), or the same custom
licence as MRI (see `ruby-licence.txt`).

#### JSON

The JSON gem is available under the same licence as MRI.

#### RDoc

It's part of the standard library, not an included gem, but RDoc is copyright
Dave Thomas and Eric Hodel and is available under the terms of the GPL 2 (see
`gpl-2.txt`), or the same custom licence as MRI (see `ruby-licence.txt`). Some
other files in RDoc have different, but compatible, licences detailed in the
files.

#### pr-zlib

TruffleRuby uses pr-zlib 1.0.3, with modifications. pr-zlib is copyright Park
Heesob and Daniel Berger and covered under the same license as zlib itself (see
`zlib.txt`).

#### FFI

TruffleRuby includes parts of the FFI gem 1.9.18. The FFI gem is copyright
2008-2016, Ruby FFI project contributors, and covered by the three-clause BSD
licence (see `ffi.txt`).

# Java dependencies

TruffleRuby has Java dependencies on these modules, which are then included in
the distribution:

#### SnakeYAML

TruffleRuby uses SnakeYaml (version as described in `mx.truffleruby/suite.py`).
SnakeYAML is copyright the SnakeYAML authors and is licensed under Apache 2.0
(see `apache-2.0.txt`).

#### JONI

TruffleRuby uses JONI (version as described in `mx.truffleruby/suite.py`). JONI is
copyright its authors and is released under an MIT licence (see `mit.txt`).

#### JCodings

TruffleRuby uses JCodings (version as described in `mx.truffleruby/suite.py`).
JCodings is copyright its authors and is released under an MIT licence (see
`mit.txt`).

# Ruby Specs

We do not distribute MSpec or the Ruby Specs, but they are both copyright 2008
Engine Yard and are released under an MIT licence (see `mit.txt`).
