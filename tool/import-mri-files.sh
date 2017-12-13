#!/usr/bin/env bash

set -x
set -e

rm -r lib/mri
cp -r ../ruby/lib lib/mri
rm lib/mri/racc/rdoc/grammar.en.rdoc
rm lib/mri/securerandom.rb
rm lib/mri/timeout.rb
rm lib/mri/weakref.rb
rm src/main/c/openssl/*.{c,h}
cp ../ruby/ext/openssl/*.{c,h} src/main/c/openssl
cp ../ruby/ext/syslog/*.{c,rb} src/main/c/syslog
cp -r ../ruby/ext/openssl/lib/* lib/mri
cp -r ../ruby/ext/bigdecimal/lib/bigdecimal lib/mri
cp -r ../ruby/ext/psych/lib/psych lib/mri
cp -r ../ruby/ext/psych/lib/*.rb lib/mri
cp -r ../ruby/ext/pty/lib/*.rb lib/mri
cp -r ../ruby/ext/syslog/lib/syslog lib/mri/syslog

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
git checkout -- test/mri/tests/runner.rb

cp ../ruby/BSDL doc/legal/ruby-bsdl.txt
cp ../ruby/COPYING doc/legal/ruby-licence.txt

rm -rf lib/cext/ruby.h lib/cext/ruby
git checkout lib/cext/ruby/config.h
cp -r ../ruby/include/. lib/cext/
cp -r ../ruby/ccan/. lib/cext/ccan
