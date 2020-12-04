# Updating our version of Ruby

Before you do anything, check with Benoit Daloze for clearance to upgrade.

The workflow below will allow you to see and reapply the modifications that we
have to MRI source code while updating.

You can re-run these instructions at any time to compare against unmodified
MRI files.

## Setup

Set the environment variable `$VERSION` to the target version:
```
export VERSION=n.n.n
```

## Create reference branches

For both the current version of Ruby you're using, and the new version, create
reference branches that include unmodified MRI sources.

Check out the version of Ruby you want to create the branch for in `../ruby`.

Then create the reference branch in the TruffleRuby repository

```bash
git checkout -b vNN
tool/import-mri-files.sh
git commit -am 'vNN'
```

You can then compare between these two branches and yours. For example to see
what changes you made on top of the old version, what's changed between the
old version and the new version, and so on. Keep them around while you do the
update.

## Update MRI with modifications

Re-install the target MRI version using the commands, to have a clean set of gems:
```
rm -rf ~/.rubies/ruby-$VERSION
ruby-install ruby $VERSION
```

In your working branch you can import MRI files again, and you can re-apply
old patches using the old reference branch.

```bash
tool/import-mri-files.sh
git revert vNN
```

You'll usually get some conflicts to work out.

## Comment out `-test-` requires

Run

```bash
git grep -E -- "^\\s+require '-test-/"
git grep -E -- '^\s+require "-test-/'
```

And comment any `require` found in files under `test/mri/tests`
but not for files under `test/mri/tests/cext-ruby`.

## Update config_*.h files

Configuration files must be regenerated from ruby for Linux and macOS
and copied into `lib/cext/include/truffleruby`. In the MRI repository
do the following:

```
ruby-build truffleruby-dev ~/.rubies/truffleruby-dev
chruby truffleruby-dev

graalvm_clang=$(ruby -e 'puts RbConfig::CONFIG["CC"]')

autoconf
CC=$graalvm_clang ./configure
```

The output of configure should report that it has created or updated a
config.h file. For example

```
.ext/include/x86_64-linux/ruby/config.h updated
```

You will need to copy that file to
`lib/cext/include/truffleruby/config_linux.h` or
`lib/cext/include/truffleruby/config_darwin.h`.

After that you should clean your MRI source repository with:

```bash
git clean -Xdf
```

## Update libraries from third-party repos

Look in `../ruby/ext/json/lib/json/version.rb` to see the version of `flori/json` being used,
compare to `lib/json/lib/json/version.rb` and if different then
copy `flori/json`'s `lib` directory into `lib/json`:
```
rm -rf lib/json/lib
cp -R ../../json/lib lib/json
```

## Updating default and bundled gems

You need a clean install (e.g., no extra gems installed) of MRI for this
(see `ruby-install` above).

```
export TRUFFLERUBY=$(pwd)
rm -rf lib/gems/gems
rm -rf lib/gems/specifications

cd ~/.rubies/ruby-$VERSION
cp -R lib/ruby/gems/*.0/gems $TRUFFLERUBY/lib/gems
cp -R lib/ruby/gems/*.0/specifications $TRUFFLERUBY/lib/gems

cd $TRUFFLERUBY
ruby tool/patch-default-gemspecs.rb
```

## Updating bin/ executables

```
rm -rf bin
cp -R ~/.rubies/ruby-$VERSION/bin .
rm -f bin/ruby
ruby tool/patch_launchers.rb
```

## Make other changes

In a separate commit, update all of these:

* Update `.ruby-version`, `TruffleRuby.LANGUAGE_VERSION`
* Update `versions.json` (from `../ruby/gems/bundled_gems`)
* Copy and paste `-h` and `--help` output to `RubyLauncher`
* Copy and paste the TruffleRuby `--help` output to `doc/user/options.md`
* Update `doc/user/compatibility.md` and `README.md`
* Update `doc/legal/legal.md`
* Update method lists - see `spec/truffle/methods_spec.rb`
* Run `jt test gems default-bundled-gems`
* Grep for the old version with `git grep -F x.y.z`
* If `tool/id.def` or `lib/cext/include/truffleruby/internal/id.h` has changed, `jt build core-symbols` and check for correctness.
* Update the list of `:next` specs and change the "next version" in `spec/truffleruby.mspec`.

## Last step

* Request the new MRI version on Jira, then update `ci.jsonnet` to use the corresponding MRI version for benchmarking.
