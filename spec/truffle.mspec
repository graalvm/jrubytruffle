require 'rbconfig'

class MSpecScript

  def self.child_process?
    ENV.key? "MSPEC_RUNNER"
  end

  def self.windows?
    ENV.key?('WINDIR') || ENV.key?('windir')
  end

  def self.linux?
    RbConfig::CONFIG['host_os'] == 'linux'
  end

  def self.solaris?
    RbConfig::CONFIG['host_os'] == 'solaris'
  end

  if child_process?
    system_ruby = `which ruby`.chomp
    if !system_ruby.empty? and `#{system_ruby} --version`.start_with?('ruby ')
      SYSTEM_RUBY = system_ruby
    end
  end

  TRUFFLERUBY_DIR = File.expand_path('../..', __FILE__)

  set :target, "#{TRUFFLERUBY_DIR}/bin/truffleruby"

  # No flags set if a Ruby binary is specified via -t
  if !child_process? and !ARGV.include?('-t')
    flags = %w[
      -J-ea
      -J-esa
      -J-Xmx2G
      -Xgraal.warn_unless=false
      --disable-gems
    ]
    core_path = "#{TRUFFLERUBY_DIR}/src/main/ruby"
    if File.directory?(core_path)
      flags << "-Xcore.load_path=#{core_path}"
      flags << "-Xbacktraces.hide_core_files=false"
    end
    set :flags, flags
  end

  set :command_line, [
    "spec/ruby/command_line"
  ]

  set :security, [
    "spec/ruby/security"
  ]

  set :language, [
    "spec/ruby/language"
  ]

  set :core, [
    "spec/ruby/core",
  ]

  set :library, [
    "spec/ruby/library",

    # Not yet explored
    "^spec/ruby/library/mathn",
    "^spec/ruby/library/readline",
    "^spec/ruby/library/syslog",

    # Doesn't exist as Ruby code - basically need to write from scratch
    "^spec/ruby/library/win32ole",

    # Uses the Rubinius FFI generator
    "^spec/ruby/library/etc",

    # Hangs in CI
    "^spec/ruby/library/net",

    # openssl and zlib are tested separately as they need Sulong
    "^spec/ruby/library/openssl",
    "^spec/ruby/library/zlib"
  ]

  set :capi, [
    "spec/ruby/optional/capi"
  ]

  set :library_cext, [
    "spec/ruby/library/openssl",
    "spec/ruby/library/zlib"
  ]

  set :truffle, [
    "spec/truffle"
  ]

  set :ruby24, [
    "spec/ruby/core/file/empty_spec.rb",
    "spec/ruby/core/hash/compact_spec.rb",
    "spec/ruby/core/integer/digits_spec.rb",
    "spec/ruby/core/regexp/match_spec.rb",
    "spec/ruby/core/string/match_spec.rb",
    "spec/ruby/core/hash/transform_values_spec.rb",
  ]

  set :ruby25, [
    "spec/ruby/core/kernel/yield_self_spec.rb",
    "spec/ruby/core/method/case_compare_spec.rb",
    "spec/ruby/core/enumerable/all_spec.rb",
    "spec/ruby/core/enumerable/any_spec.rb",
    "spec/ruby/core/enumerable/none_spec.rb",
    "spec/ruby/core/enumerable/one_spec.rb",
    "spec/ruby/core/hash/transform_keys_spec.rb",
  ]

  set :backtrace_filter, /mspec\//

  set :tags_patterns, [
    [%r(^.*/command_line/),             'spec/tags/command_line/'],
    [%r(^.*/security/),                 'spec/tags/security/'],
    [%r(^.*/language/),                 'spec/tags/language/'],
    [%r(^.*/core/),                     'spec/tags/core/'],
    [%r(^.*/library/),                  'spec/tags/library/'],
    [%r(^.*/optional/capi/),            'spec/tags/optional/capi/'],
    [%r(^.*/truffle),                   'spec/tags/truffle/'],
    [/_spec.rb$/,                       '_tags.txt']
  ]

  if windows?
    # exclude specs tagged with 'windows'
    set :xtags, (get(:xtags) || []) + ['windows']
  end

  if linux?
    # exclude specs tagged with 'linux'
    set :xtags, (get(:xtags) || []) + ['linux']
  end

  if solaris?
    # exclude specs tagged with 'solaris'
    set :xtags, (get(:xtags) || []) + ['solaris']
  end

  # Enable features
  MSpec.enable_feature :fiber
  MSpec.enable_feature :fiber_library
  MSpec.disable_feature :fork
  MSpec.enable_feature :encoding
  MSpec.enable_feature :readline

  set :files, get(:command_line) + get(:language) + get(:core) + get(:library) + get(:truffle) + get(:security)

  # All specs, including specs needing C-extensions support.
  # 2.4/2.5 specs are not included as they need to run in a separate process.
  set :all, get(:files) + get(:capi) + get(:library_cext)
end

if MSpecScript.child_process?
  if version = ENV["PRETEND_RUBY_VERSION"]
    ::VersionGuard::FULL_RUBY_VERSION = SpecVersion.new(version)
  elsif ARGV.include? ":ruby24"
    ::VersionGuard::FULL_RUBY_VERSION = SpecVersion.new("2.4.3")
  elsif ARGV.include? ":ruby25"
    ::VersionGuard::FULL_RUBY_VERSION = SpecVersion.new("2.5.0")
  end

  # We do not use Ruby 2.5's FrozenError yet
  def frozen_error_class
    RuntimeError
  end
end

if i = ARGV.index('slow') and ARGV[i-1] == '--excl-tag' and MSpecScript.child_process?
  require 'mspec'

  class SlowSpecsTagger
    def initialize
      MSpec.register :exception, self
    end

    def exception(state)
      if state.exception.is_a? SlowSpecException
        tag = SpecTag.new
        tag.tag = 'slow'
        tag.description = "#{state.describe} #{state.it}"
        MSpec.write_tag(tag)
      end
    end
  end

  class SlowSpecException < Exception
  end

  require 'timeout'

  slow_methods = [
    [Object, [:ruby_exe, :ruby_cmd]],
    [ObjectSpace.singleton_class, [:each_object]],
    [GC.singleton_class, [:start]],
    [Kernel, [:system, :`]],
    [Kernel.singleton_class, [:system, :`]],
    [Timeout.singleton_class, [:timeout]],
  ]

  module Kernel
    alias_method :"mspec_old_`", :`
    private :"mspec_old_`"
  end

  slow_methods.each do |klass, meths|
    klass.class_exec do
      meths.each do |meth|
        define_method(meth) do |*args, &block|
          if MSpec.current && MSpec.current.state # an example is running
            raise SlowSpecException, "Was tagged as slow as it uses #{meth}(). Rerun specs."
          else
            send("mspec_old_#{meth}", *args, &block)
          end
        end
        # Keep visibility for Kernel instance methods
        private meth if klass == Kernel
      end
    end
  end

  SlowSpecsTagger.new
end
