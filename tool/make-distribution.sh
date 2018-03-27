#!/bin/bash

# This script creates a distribution of TruffleRuby
# with or without Graal and a JVM.

set -e

PREFIX="$1"
KIND="$2"
PLATFORM=linux-amd64

if [ -z "$KIND" ]; then
  echo "usage: $0 PREFIX KIND"
  echo "KIND is 'minimal' or 'full'"
  exit 1
fi

graal=false

case "$KIND" in
  minimal)
    ;;
  full)
    graal=true
    ;;
  *)
    echo "KIND is 'minimal' or 'full'"
    exit 1
    ;;
esac

rm -rf "${PREFIX:?}"/*
mkdir -p "$PREFIX"

# Expand $PREFIX
PREFIX=$(cd "$PREFIX" && pwd -P)

DEST="$PREFIX/truffleruby"

function copy {
  local dir
  for file in "$@"; do
    dir=$(dirname "$file")
    mkdir -p "$DEST/$dir"
    install "$file" "$DEST/$dir"
  done
}

revision=$(git rev-parse --short HEAD)

# Make sure Truffle is used as binary distribution
grep MX_BINARY_SUITES mx.truffleruby/env 2>/dev/null || echo MX_BINARY_SUITES=tools,truffle,sdk >> mx.truffleruby/env

# Setup binary suites
if [ -z "${SULONG_REVISION+x}" ]; then
  echo "You need to set SULONG_REVISION (can be '' for latest uploaded)"
  exit 1
fi
mx ruby_download_binary_suite sulong "$SULONG_REVISION"
export TRUFFLERUBY_CEXT_ENABLED=true
export TRUFFLERUBYOPT="-Xgraal.warn_unless=$graal"

if [ "$graal" = true ]; then
  mx ruby_download_binary_suite compiler truffle

  ruby tool/jt.rb install jvmci
  jvmci=$(ruby tool/jt.rb install jvmci 2>/dev/null)
  jvmci_basename=$(basename "$jvmci")
fi

# Build
tool/jt.rb build

# Copy distributions
# Truffle
copy mx.imports/binary/truffle/mxbuild/dists/truffle-api.jar
copy mx.imports/binary/truffle/mxbuild/dists/truffle-nfi.jar
copy mx.imports/binary/truffle/mxbuild/$PLATFORM/truffle-nfi-native/bin/libtrufflenfi.so
copy mx.imports/binary/truffle/mx.imports/binary/sdk/mxbuild/dists/graal-sdk.jar

# Sulong
copy mx.imports/binary/sulong/build/sulong.jar
copy mx.imports/binary/sulong/mxbuild/sulong-libs/libsulong.bc
copy mx.imports/binary/sulong/mxbuild/sulong-libs/libsulong.so

# Graal
if [ "$graal" = true ]; then
  graal_dist=mx.imports/binary/compiler/mxbuild/dists/graal.jar
  copy "$graal_dist"

  cp -r "$jvmci" "$DEST"
  # Remove JavaDoc as it's >250MB
  rm -r "$DEST/$jvmci_basename/docs/api"
  rm -r "$DEST/$jvmci_basename/docs/jdk"
  rm -r "$DEST/$jvmci_basename/docs/jre"
  # Removes sources (~50MB)
  rm "$DEST/$jvmci_basename/src.zip"
fi

# TruffleRuby
copy mxbuild/dists/truffleruby.jar
copy mxbuild/dists/truffleruby-launcher.jar

copy mxbuild/$PLATFORM/dists/truffleruby-zip.tar
cd "$DEST"
tar xf mxbuild/$PLATFORM/dists/truffleruby-zip.tar
rm mxbuild/$PLATFORM/dists/truffleruby-zip.tar

# Environment sourced by the launcher, so adding to $PATH is enough
cat > truffleruby_env <<EOS
export TRUFFLERUBY_RESILIENT_GEM_HOME=true
export TRUFFLERUBY_CEXT_ENABLED=$TRUFFLERUBY_CEXT_ENABLED
export TRUFFLERUBYOPT="$TRUFFLERUBYOPT \$TRUFFLERUBYOPT"
EOS
if [ "$graal" = true ]; then
  cat >> truffleruby_env <<EOS
export JAVACMD="\$root/$jvmci_basename/bin/java"
export JAVA_OPTS="-XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -Djvmci.class.path.append=\$root/$graal_dist \$JAVA_OPTS"
EOS
fi

export PATH="$DEST/bin:$PATH"

# Install bundler as we require a specific version and it's convenient
echo Installing Bundler
gem install -E bundler -v 1.16.1

cd "$PREFIX"
tar czf "truffleruby-$revision.tar.gz" truffleruby
