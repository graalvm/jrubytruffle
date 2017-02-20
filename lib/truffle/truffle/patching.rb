module Truffle::Patching
  TRUFFLE_PATCHES_DIRECTORY = "#{Truffle::Boot.ruby_home}/lib/patches"
  TRUFFLE_BEFORE_PATCHES           = Dir.glob("#{TRUFFLE_PATCHES_DIRECTORY}/before/**/*.rb").
      select { |path| File.file? path }.
      map { |path| [path[(TRUFFLE_PATCHES_DIRECTORY.size + 7 + 1)..-4], true] }.
      to_h
  TRUFFLE_AFTER_PATCHES           = Dir.glob("#{TRUFFLE_PATCHES_DIRECTORY}/after/**/*.rb").
      select { |path| File.file? path }.
      map { |path| [path[(TRUFFLE_PATCHES_DIRECTORY.size + 6 + 1)..-4], true] }.
      to_h
end

module Kernel

  private

  alias_method :require_without_truffle_patching, :require

  def require(path)
    if Truffle::Patching::TRUFFLE_BEFORE_PATCHES[path]
      patch_applied = require_without_truffle_patching "#{Truffle::Patching::TRUFFLE_PATCHES_DIRECTORY}/before/#{path}.rb"
      puts "[ruby] PATCH applied (before) #{path}" if patch_applied
    end

    required = require_without_truffle_patching path

    if required && Truffle::Patching::TRUFFLE_AFTER_PATCHES[path]
      puts "[ruby] PATCH applying (after) #{path}"
      load "#{Truffle::Patching::TRUFFLE_PATCHES_DIRECTORY}/after/#{path}.rb"
    end
    required
  end
end

class Module

  alias_method :autoload_without_truffle_patching, :autoload
  private :autoload_without_truffle_patching

  def autoload(const, path)
    if Truffle::Patching::TRUFFLE_AFTER_PATCHES[path] || Truffle::Patching::TRUFFLE_BEFORE_PATCHES[path]
      require path
    else
      autoload_without_truffle_patching const, path
    end
  end
end
