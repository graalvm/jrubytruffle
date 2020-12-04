#!/usr/bin/env bash

# This file should pass `shellcheck tool/import-mri-files.sh`.

set -x
set -e

topdir=$(cd ../ruby && pwd -P)

tag=$(cd "$topdir" && git describe --tags)
version=$(echo "$tag" | tr -d v | tr '_' '.')

RUBY_BUILD_DIR=$HOME/src/ruby-$version
if [ ! -d "$RUBY_BUILD_DIR" ]; then
    echo "$RUBY_BUILD_DIR does not exist!"
    exit 1
fi

# Generate ext/rbconfig/sizeof/sizes.c and limits.c
(
  cd ../ruby/ext/rbconfig/sizeof
  cp depend Makefile
  make sizes.c limits.c RUBY=ruby top_srcdir="$topdir"
  rm Makefile
)

# lib/
rm -r lib/mri
cp -r ../ruby/lib lib/mri
# Documentation, not code
rm lib/mri/racc/rdoc/grammar.en.rdoc
# We have our own version under lib/truffle
rm lib/mri/securerandom.rb
rm lib/mri/timeout.rb
rm lib/mri/weakref.rb
# Uses RubyVM
rm lib/mri/debug.rb
# Files not actually installed in MRI
find lib/mri -name '*.gemspec' -delete
find lib/mri -name '.document' -delete

# *.c
cp ../ruby/st.c src/main/c/cext/st.c

# Copy Ruby files in ext/, sorted alphabetically
cp -r ../ruby/ext/bigdecimal/lib/bigdecimal lib/mri
mkdir lib/mri/digest
cp -r ../ruby/ext/digest/sha2/lib/* lib/mri/digest
cp -r ../ruby/ext/fiddle/lib/fiddle lib/mri
cp -r ../ruby/ext/fiddle/lib/fiddle.rb lib/mri
cp ../ruby/ext/nkf/lib/*.rb lib/mri
cp -r ../ruby/ext/openssl/lib/* lib/mri
cp ../ruby/ext/pty/lib/*.rb lib/mri
cp ../ruby/ext/psych/lib/psych.rb lib/mri
cp -r ../ruby/ext/psych/lib/psych lib/mri
cp ../ruby/ext/ripper/lib/ripper.rb lib/mri
cp -r ../ruby/ext/ripper/lib/ripper lib/mri
cp -r ../ruby/ext/syslog/lib/syslog lib/mri

# Copy C extensions in ext/, sorted alphabetically
rm -r src/main/c/{bigdecimal,etc,nkf,openssl,psych,rbconfig-sizeof,syslog,ripper,zlib}
mkdir src/main/c/{bigdecimal,etc,nkf,openssl,psych,rbconfig-sizeof,syslog,ripper,zlib}
cp ../ruby/ext/bigdecimal/*.{c,gemspec,h,rb} src/main/c/bigdecimal
cp ../ruby/ext/etc/*.{c,rb} src/main/c/etc
cp ../ruby/ext/nkf/*.{c,rb} src/main/c/nkf
cp -r ../ruby/ext/nkf/nkf-utf8 src/main/c/nkf
cp ../ruby/ext/openssl/*.{c,h,rb} src/main/c/openssl
cp ../ruby/ext/psych/*.{c,h,rb} src/main/c/psych
cp -r ../ruby/ext/psych/yaml src/main/c/psych
cp ../ruby/ext/rbconfig/sizeof/*.{c,rb} src/main/c/rbconfig-sizeof
cp ../ruby/ext/syslog/*.{c,rb} src/main/c/syslog
cp ../ruby/ext/zlib/*.{c,rb} src/main/c/zlib

# Ripper
cp "$RUBY_BUILD_DIR"/{id.h,symbol.h} lib/cext/include/truffleruby/internal
cp "$RUBY_BUILD_DIR"/{node.c,parse.c,lex.c} src/main/c/ripper
cp "$RUBY_BUILD_DIR"/ext/ripper/*.{c,rb} src/main/c/ripper
cp "$RUBY_BUILD_DIR"/{node.h,parse.h,probes.h,probes.dmyh,regenc.h} src/main/c/ripper

# test/
rm -rf test/mri/tests
cp -r ../ruby/test test/mri/tests
rm -rf test/mri/tests/excludes
cp -r ../ruby/ext/-test- test/mri/tests
mkdir test/mri/tests/cext
mv test/mri/tests/-ext- test/mri/tests/cext-ruby
mv test/mri/tests/-test- test/mri/tests/cext-c
find test/mri/tests/cext-ruby -name '*.rb' -print0 | xargs -0 -n 1 sed -i.backup 's/-test-/c/g'
find test/mri/tests/cext-ruby -name '*.backup' -delete
rm -rf test/mri/excludes
git checkout -- test/mri/excludes

# Copy from tool/lib to test/lib
cp -r ../ruby/tool/lib/* test/mri/tests/lib
rm -f test/mri/tests/lib/leakchecker.rb

# basictest/ and bootstraptest/
rm -rf test/basictest
cp -r ../ruby/basictest test/basictest
rm -rf test/bootstraptest
cp -r ../ruby/bootstraptest test/bootstraptest

# Licences
cp ../ruby/BSDL doc/legal/ruby-bsdl.txt
cp ../ruby/COPYING doc/legal/ruby-licence.txt
cp lib/cext/include/ccan/licenses/BSD-MIT doc/legal/ccan-bsd-mit.txt
cp lib/cext/include/ccan/licenses/CC0 doc/legal/ccan-cc0.txt

# include/
rm -rf lib/cext/include/ruby lib/cext/include/ccan
git checkout lib/cext/include/ruby/config.h
cp -r ../ruby/include/. lib/cext/include
cp -r ../ruby/ccan/. lib/cext/include/ccan

# defs/
cp ../ruby/defs/known_errors.def tool
cp ../ruby/defs/id.def tool
