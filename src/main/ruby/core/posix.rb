# Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1

module Truffle::POSIX
  class LazyLibrary
    def initialize(&block)
      @block = block
      @resolved = nil
    end

    def resolve
      if resolved = @resolved
        resolved
      else
        raise 'loading library while pre-initializing' if Truffle::Boot.preinitializing?
        Truffle.synchronize(self) do
          @resolved ||= @block.call
        end
      end
    end
  end

  LIBC = LazyLibrary.new do
    Truffle::Interop.eval('application/x-native', 'default')
  end

  LIBTRUFFLEPOSIX = LazyLibrary.new do
    if home = Truffle::Boot.ruby_home
      libtruffleposix = "#{home}/lib/cext/truffleposix.#{Truffle::Platform::NATIVE_DLEXT}"
      Truffle::Interop.eval('application/x-native', "load '#{libtruffleposix}'")
    else
      LIBC.resolve
    end
  end

  LIBCRYPT = LazyLibrary.new do
    if Truffle::Platform.linux?
      Truffle::Interop.eval('application/x-native', 'load libcrypt.so')
    else
      LIBC.resolve
    end
  end

  TYPES = {
    :short => :sint16,
    :ushort => :uint16,
    :int => :sint32,
    :uint => :uint32,
    :long => :sint64,
    :ulong => :uint64,
    :long_long => :sint64,
    :ulong_long => :uint64,
  }

  EINTR = Errno::EINTR::Errno

  def self.to_nfi_type(type)
    if found = TYPES[type]
      found
    elsif typedef = Truffle::Config.lookup("platform.typedef.#{type}")
      TYPES[type] = to_nfi_type(typedef.to_sym)
    else
      TYPES[type] = type
    end
  end

  # the actual function is looked up and attached on its fist call
  def self.attach_function(native_name, argument_types, return_type,
                           library = LIBC, blocking = false, method_name = native_name, on = self)

    on.define_singleton_method(method_name) do |*args|
      Truffle::POSIX.attach_function_eagerly native_name, argument_types, return_type,
                                             library, blocking, method_name, on
      __send__ method_name, *args
    end
  end

  def self.attach_function_eagerly(native_name, argument_types, return_type,
                                   library, blocking, method_name, on)

    library = library.resolve
    begin
      func = library[native_name]
    rescue NameError # Missing function
      func = nil
    end

    if func
      string_args = []
      nfi_args_types = []
      argument_types.each_with_index do |arg_type, i|
        if arg_type == :string
          string_args << i
          nfi_args_types << '[sint8]'
        else
          nfi_args_types << to_nfi_type(arg_type)
        end
      end
      string_args.freeze

      nfi_return_type = to_nfi_type(return_type)

      bound_func = func.bind("(#{nfi_args_types.join(',')}):#{nfi_return_type}")

      on.define_singleton_method(method_name) do |*args|
        string_args.each do |i|
          str = args.fetch(i)
          # TODO CS 14-Nov-17 this involves copying to a Java byte[], and then NFI will copy it again!
          args[i] = Truffle.invoke_primitive :string_to_null_terminated_byte_array, str
        end

        if blocking
          result = Truffle.invoke_primitive :thread_run_blocking_nfi_system_call, -> {
            r = bound_func.call(*args)
            if Integer === r and r == -1 and Errno.errno == EINTR
              undefined
            else
              r
            end
          }
        else
          result = bound_func.call(*args)
        end

        if return_type == :string
          if result.nil?
            result = nil
          else
            ptr = Truffle::FFI::Pointer.new(Truffle::Interop.as_pointer(result))
            result = ptr.read_string_to_null
          end
        elsif return_type == :pointer
          result = Truffle::FFI::Pointer.new(Truffle::Interop.as_pointer(result))
        elsif return_type == :ssize_t
          result = Truffle.invoke_primitive(:fixnum_lower, result)
        end

        result
      end
    else
      on.define_singleton_method(method_name) do |*|
        raise NotImplementedError, "#{native_name} is not available"
      end
      Truffle.invoke_primitive :method_unimplement, method(method_name)
    end
  end

  # Filesystem-related
  attach_function :access, [:string, :int], :int
  attach_function :chdir, [:string], :int
  attach_function :chmod, [:string, :mode_t], :int
  attach_function :chown, [:string, :uid_t, :gid_t], :int
  attach_function :chroot, [:string], :int
  attach_function :truffleposix_clock_gettime, [:int], :int64_t, LIBTRUFFLEPOSIX
  attach_function :close, [:int], :int
  attach_function :closedir, [:pointer], :int
  attach_function :dirfd, [:pointer], :int
  attach_function :dup, [:int], :int
  attach_function :dup2, [:int, :int], :int
  attach_function :fchmod, [:int, :mode_t], :int
  attach_function :fchown, [:int, :uid_t, :gid_t], :int
  attach_function :fcntl, [:int, :int, :int], :int
  attach_function :truffleposix_flock, [:int, :int], :int, LIBTRUFFLEPOSIX, true
  attach_function :truffleposix_fstat, [:int, :pointer], :int, LIBTRUFFLEPOSIX
  attach_function :fsync, [:int], :int
  attach_function :ftruncate, [:int, :off_t], :int
  attach_function :getcwd, [:pointer, :size_t], :string
  attach_function :isatty, [:int], :int
  attach_function :lchmod, [:string, :mode_t], :int
  attach_function :lchown, [:string, :uid_t, :gid_t], :int
  attach_function :link, [:string, :string], :int
  attach_function :lseek, [:int, :off_t, :int], :off_t
  attach_function :truffleposix_lstat, [:string, :pointer], :int, LIBTRUFFLEPOSIX
  attach_function :truffleposix_major, [:dev_t], :uint, LIBTRUFFLEPOSIX
  attach_function :truffleposix_minor, [:dev_t], :uint, LIBTRUFFLEPOSIX
  attach_function :mkdir, [:string, :mode_t], :int
  attach_function :mkfifo, [:string, :mode_t], :int
  attach_function :open, [:string, :int, :mode_t], :int
  attach_function :opendir, [:string], :pointer
  attach_function :pipe, [:pointer], :int
  attach_function :read, [:int, :pointer, :size_t], :ssize_t, LIBC, true
  attach_function :readlink, [:string, :pointer, :size_t], :ssize_t
  attach_function :truffleposix_readdir, [:pointer], :string, LIBTRUFFLEPOSIX
  attach_function :rename, [:string, :string], :int
  attach_function :truffleposix_rewinddir, [:pointer], :void, LIBTRUFFLEPOSIX
  attach_function :rmdir, [:string], :int
  attach_function :seekdir, [:pointer, :long], :void
  attach_function :truffleposix_select, [:int, :pointer, :int, :pointer, :int, :pointer, :long], :int, LIBTRUFFLEPOSIX
  attach_function :truffleposix_stat, [:string, :pointer], :int, LIBTRUFFLEPOSIX
  attach_function :symlink, [:string, :string], :int
  attach_function :telldir, [:pointer], :long
  attach_function :truncate, [:string, :off_t], :int
  attach_function :umask, [:mode_t], :mode_t
  attach_function :unlink, [:string], :int
  attach_function :truffleposix_utimes, [:string, :long, :int, :long, :int], :int, LIBTRUFFLEPOSIX
  attach_function :write, [:int, :pointer, :size_t], :ssize_t, LIBC, true

  # Process-related
  attach_function :getegid, [], :gid_t
  attach_function :getgid, [], :gid_t
  attach_function :setresgid, [:gid_t, :gid_t, :gid_t], :int
  attach_function :setregid, [:gid_t, :gid_t], :int
  attach_function :setegid, [:uid_t], :int
  attach_function :setgid, [:gid_t], :int

  attach_function :geteuid, [], :uid_t
  attach_function :getuid, [], :uid_t
  attach_function :setresuid, [:uid_t, :uid_t, :uid_t], :int
  attach_function :setreuid, [:uid_t, :uid_t], :int
  attach_function :setruid, [:uid_t], :int
  attach_function :seteuid, [:uid_t], :int
  attach_function :setuid, [:uid_t], :int

  attach_function :getpid, [], :pid_t
  attach_function :getppid, [], :pid_t
  attach_function :kill, [:pid_t, :int], :int
  attach_function :getpgrp, [], :pid_t
  attach_function :getpgid, [:pid_t], :pid_t
  attach_function :setpgid, [:pid_t, :pid_t], :int
  attach_function :setsid, [], :pid_t

  attach_function :getgroups, [:int, :pointer], :int
  attach_function :setgroups, [:size_t, :pointer], :int

  attach_function :getrlimit, [:int, :pointer], :int
  attach_function :setrlimit, [:int, :pointer], :int
  attach_function :truffleposix_getrusage, [:pointer], :int, LIBTRUFFLEPOSIX

  attach_function :truffleposix_getpriority, [:int, :id_t], :int, LIBTRUFFLEPOSIX
  attach_function :setpriority, [:int, :id_t, :int], :int

  attach_function :execve, [:string, :pointer, :pointer], :int
  attach_function :truffleposix_posix_spawnp, [:string, :pointer, :pointer, :int, :pointer, :int], :pid_t, LIBTRUFFLEPOSIX
  attach_function :truffleposix_waitpid, [:pid_t, :int, :pointer], :pid_t, LIBTRUFFLEPOSIX, true

  # ENV-related
  attach_function :getenv, [:string], :string

  attach_function :setenv, [:string, :string, :int], :int, LIBC, false, :setenv_native
  def self.setenv(name, value, overwrite)
    Truffle.invoke_primitive :posix_invalidate_env, name
    setenv_native(name, value, overwrite)
  end

  attach_function :unsetenv, [:string], :int, LIBC, false, :unsetenv_native
  def self.unsetenv(name)
    Truffle.invoke_primitive :posix_invalidate_env, name
    unsetenv_native(name)
  end

  # Other routines
  attach_function :crypt, [:string, :string], :string, LIBCRYPT
  attach_function :truffleposix_get_user_home, [:string], :pointer, LIBTRUFFLEPOSIX

  # Errno-related
  if Truffle::Platform.linux?
    attach_function :__errno_location, [], :pointer, LIBC, false, :errno_address
  elsif Truffle::Platform.darwin?
    attach_function :__error, [], :pointer, LIBC, false, :errno_address
  elsif Truffle::Platform.solaris?
    attach_function :___errno, [], :pointer, LIBC, false, :errno_address
  else
    raise 'Unsupported platform'
  end

  # Platform-specific
  if Truffle::Platform.darwin?
    attach_function :_NSGetArgv, [], :pointer
  end
end if Truffle::Boot.get_option 'platform.native'

module Truffle::POSIX
  NATIVE = Truffle::Boot.get_option 'platform.native'

  def self.with_array_of_ints(ints)
    if ints.empty?
      yield Truffle::FFI::Pointer::NULL
    else
      Truffle::FFI::MemoryPointer.new(:int, ints.size) do |ptr|
        ptr.write_array_of_int(ints)
        yield ptr
      end
    end
  end

  def self.with_array_of_strings_pointer(strings)
    Truffle::FFI::MemoryPointer.new(:pointer, strings.size + 1) do |ptr|
      pointers = strings.map do |str|
        Truffle::FFI::MemoryPointer.from_string(str)
      end
      pointers << Truffle::FFI::Pointer::NULL
      ptr.write_array_of_pointer pointers
      yield(ptr)
    end
  end

  TRY_AGAIN_ERRNOS = [Errno::EAGAIN::Errno, Errno::EWOULDBLOCK::Errno]

  def self.read_blocking(io, count)
    while true # rubocop:disable Lint/LiteralInCondition
      buffer, bytes_read, errno = read_bytes(io, count)
      return [buffer, bytes_read] if errno == 0
      if TRY_AGAIN_ERRNOS.include? errno
        IO.select([io])
      else
        Errno.handle
      end
    end
  end

  # This method must call `read_string' in order to properly support polyglot STDIO.
  def self.read_string_blocking(io, count)
    while true # rubocop:disable Lint/LiteralInCondition
      string, errno = read_string(io, count)
      return string if errno == 0
      if TRY_AGAIN_ERRNOS.include? errno
        IO.select([io])
      else
        Errno.handle
      end
    end
  end

  # This method must call `read_string' in order to properly support polyglot STDIO.
  def self.read_string_nonblock(io, count)
    string, errno = read_string(io, count)
    if errno == 0
      string
    elsif TRY_AGAIN_ERRNOS.include? errno
      raise IO::EAGAINWaitReadable
    else
      Errno.handle
    end
  end

  def self.read_bytes(io, length)
    fd = io.descriptor
    buffer = Truffle.invoke_primitive(:io_get_thread_buffer, length)
    bytes_read = Truffle::POSIX.read(fd, buffer, length)
    if bytes_read < 0
      [nil, bytes_read, Errno.errno]
    elsif bytes_read == 0 # EOF
      [nil, 0, 0]
    else
      [buffer, bytes_read, 0]
    end
  end

  def self.read_string(io, length)
    buffer, bytes_read, errno = read_bytes(io, length)

    if bytes_read < 0
      [nil, errno]
    elsif bytes_read == 0 # EOF
      [nil, 0]
    else
      [buffer.read_string(bytes_read), 0]
    end
  end

  def self.write_string(io, string, continue_on_eagain)
    fd = io.descriptor
    length = string.bytesize
    buffer = Truffle.invoke_primitive(:io_get_thread_buffer, length)
    buffer.write_string string

    written = 0
    while written < length
      ret = Truffle::POSIX.write(fd, buffer + written, length - written)
      if ret < 0
        errno = Errno.errno
        if TRY_AGAIN_ERRNOS.include? errno
          if continue_on_eagain
            IO.select([], [io])
          else
            return written
          end
        else
          Errno.handle
        end
      end
      written += ret
    end
    written
  end

  def self.write_string_nonblock(io, string)
    fd = io.descriptor
    length = string.bytesize
    buffer = Truffle.invoke_primitive(:io_get_thread_buffer, length)
    buffer.write_string string
    written = Truffle::POSIX.write(fd, buffer, length)

    if written < 0
      errno = Errno.errno
      if TRY_AGAIN_ERRNOS.include? errno
        raise IO::EAGAINWaitWritable
      else
        Errno.handle
      end
    end
    written
  end

  Truffle::Boot.delay do
    if Truffle::Boot.get_option('polyglot.stdio')
      class << self
        alias_method :read_string_native, :read_string
        alias_method :write_string_native, :write_string
        alias_method :write_string_nonblock_native, :write_string_nonblock
      end

      def self.read_string(io, length)
        fd = io.descriptor
        if fd == 0
          read = Truffle.invoke_primitive :io_read_polyglot, fd, length
          [read, 0]
        else
          read_string_native(io, length)
        end
      end

      def self.write_string(io, string, continue_on_eagain)
        fd = io.descriptor
        if fd == 1 || fd == 2
          # Ignore continue_on_eagain for polyglot writes
          Truffle.invoke_primitive :io_write_polyglot, fd, string
        else
          write_string_native(io, string, continue_on_eagain)
        end
      end

      def self.write_string_nonblock(io, string)
        fd = io.descriptor
        if fd == 1 || fd == 2
          # Ignore non-blocking for polyglot writes
          Truffle.invoke_primitive :io_write_polyglot, fd, string
        else
          write_string_nonblock_native(io, string)
        end
      end
    end
  end
end
