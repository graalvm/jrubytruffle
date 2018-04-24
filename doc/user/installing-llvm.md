# Installing LLVM

TruffleRuby needs LLVM to run and build C extensions. The versions that are
tested for each platform and how to install them are documented below. You may
have success with later versions, but we don't actively test these.

## Oracle Linux

The tested version of LLVM for Oracle Linux is 3.8.

Oracle Linux does not include recent-enough LLVM packages, so you will have to
[build LLVM from scratch](https://llvm.org/docs/CMake.html). You'll need to
include at least the `libcxx` packages for running, and `clang`
for building. One way to build it is documented in the
`tool/docker/oraclelinux-llvm` Dockerfile in the TruffleRuby source repository.
These instructions will also install tools that you need to build C extensions.

For using C++ extensions you will also need to install:

```
yum install libcxx
```

And for building C++ extensions:

```
yum install libcxx-devel
```

## Ubuntu

The tested version of LLVM for Ubuntu is 3.8.

For building C extensions you need to install:

```
apt-get install make clang llvm
```

For building and using C++ extensions you need to install:

```
apt-get install libc++-dev
```

Note that we install `libc++-dev` here even for just using C++ extensions, as
installing `libc++` seems to introduce some system conflicts.

## Fedora

The tested version of LLVM for Fedora is 3.8.

For building C extensions you need to install:

```
sudo dnf install clang llvm
```

For using C++ extensions you need to install:

```
sudo dnf install libcxx
```

And for building C++ extensions:

```
sudo dnf install libcxx-devel
```

## macOS

The tested version of LLVM for macOS is 4.0.1.

We need the `opt` command, so you can't just use what is installed by Xcode if
you are on macOS. We would recommend that you install LLVM 4 via
[Homebrew](https://brew.sh) and then manually set your path.

For building and using C and C++ extensions on macOS we recommend just
installing the full `llvm` package. Make sure you have also installed the
standard C headers from Xcode via `xcode-select --install`. We give more
specific advice above for Linux to support use in containers such as Docker.

```bash
xcode-select --install
brew install llvm@4
export PATH="/usr/local/opt/llvm@4/bin:$PATH"
```
