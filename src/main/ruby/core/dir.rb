# Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1

# Copyright (c) 2007-2015, Evan Phoenix and contributors
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# * Redistributions of source code must retain the above copyright notice, this
#   list of conditions and the following disclaimer.
# * Redistributions in binary form must reproduce the above copyright notice
#   this list of conditions and the following disclaimer in the documentation
#   and/or other materials provided with the distribution.
# * Neither the name of Rubinius nor the names of its contributors
#   may be used to endorse or promote products derived from this software
#   without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

class Dir
  include Enumerable

  attr_reader :path
  alias_method :to_path, :path

  def initialize(path, options=undefined)
    @path = Rubinius::Type.coerce_to_path path

    if undefined.equal? options
      enc = nil
    else
      options = Rubinius::Type.coerce_to options, Hash, :to_hash
      enc = options[:encoding]
      enc = Rubinius::Type.coerce_to_encoding enc if enc
    end

    @encoding = enc || Encoding::FILESYSTEM

    @ptr = Truffle::POSIX.opendir(@path)
    Errno.handle if @ptr.null?
  end

  private def ensure_open
    raise IOError, 'closed directory' if closed?
  end

  def fileno
    ensure_open
    Truffle::POSIX.dirfd(@ptr)
  end

  def pos
    ensure_open
    pos = Truffle::POSIX.telldir(@ptr)
    Errno.handle if pos == -1
    pos
  end
  alias_method :tell, :pos

  def seek(pos)
    ensure_open
    Truffle::POSIX.seekdir(@ptr, pos)
    self
  end

  def pos=(position)
    seek(position)
    position
  end

  def rewind
    ensure_open
    Truffle::POSIX.truffleposix_rewinddir(@ptr)
    self
  end

  def read
    ensure_open
    entry = Truffle::POSIX.truffleposix_readdir(@ptr)
    Errno.handle unless entry
    return if entry.empty?

    entry = entry.force_encoding(@encoding)

    if Encoding.default_external == Encoding::US_ASCII && !entry.valid_encoding?
      entry.force_encoding Encoding::ASCII_8BIT
      return entry
    end

    enc = Encoding.default_internal
    enc ? entry.encode(enc) : entry
  end

  def close
    unless closed?
      ret = Truffle::POSIX.closedir(@ptr)
      Errno.handle if ret == -1
      @ptr = nil
    end
  end

  def closed?
    @ptr == nil
  end

  def each
    return to_enum unless block_given?

    while s = read
      yield s
    end

    self
  end

  def inspect
    "#<#{self.class}:#{@path}>"
  end

  class << self

    # This seems silly, I know. But we do this to make Dir more resistent to people
    # screwing with ::File later (ie, fakefs)
    PrivateFile = ::File

    def open(path, options=undefined)
      dir = new path, options
      if block_given?
        begin
          yield dir
        ensure
          dir.close
        end
      else
        dir
      end
    end

    def entries(path, options=undefined)
      ret = []

      open(path, options) do |dir|
        while s = dir.read
          ret << s
        end
      end

      ret
    end

    def exist?(path)
      PrivateFile.directory?(path)
    end
    alias_method :exists?, :exist?

    def home(user=nil)
      PrivateFile.expand_path("~#{user}")
    end

    def [](*patterns)
      if patterns.size == 1
        pattern = Rubinius::Type.coerce_to_path(patterns[0], false)
        return [] if pattern.empty?
        patterns = glob_split pattern
      end

      glob patterns
    end

    def glob(pattern, flags=0, &block)
      if pattern.kind_of? Array
        patterns = pattern
      else
        pattern = Rubinius::Type.coerce_to_path(pattern, false)

        return [] if pattern.empty?

        patterns = glob_split pattern
      end

      matches = []
      index = 0

      patterns.each do |pat|
        pat = Rubinius::Type.coerce_to_path pat
        enc = Rubinius::Type.ascii_compatible_encoding pat
        Dir::Glob.glob pat, flags, matches

        total = matches.size
        while index < total
          Rubinius::Type.encode_string matches[index], enc
          index += 1
        end
      end

      if block
        matches.each(&block)
        nil
      else
        matches
      end
    end

    def glob_split(pattern)
      result = []
      start = 0
      while idx = pattern.find_string("\0", start)
        result << pattern.byteslice(start, idx)
        start = idx + 1
      end
      result << pattern.byteslice(start, pattern.bytesize)
    end

    def foreach(path)
      return to_enum(:foreach, path) unless block_given?

      open(path) do |dir|
        while s = dir.read
          yield s
        end
      end

      nil
    end

    def chdir(path = ENV['HOME'])
      path = Rubinius::Type.coerce_to_path path

      if block_given?
        original_path = self.getwd
        ret = Truffle::POSIX.chdir path
        Errno.handle_nfi(path) if ret != 0

        begin
          yield path
        ensure
          ret = Truffle::POSIX.chdir original_path
          Errno.handle_nfi(original_path) if ret != 0
        end
      else
        ret = Truffle::POSIX.chdir path
        Errno.handle_nfi path if ret != 0
        ret
      end
    end

    def mkdir(path, mode = 0777)
      ret = Truffle::POSIX.mkdir(Rubinius::Type.coerce_to_path(path), mode)
      Errno.handle_nfi path if ret != 0
      ret
    end

    def rmdir(path)
      ret = Truffle::POSIX.rmdir(Rubinius::Type.coerce_to_path(path))
      Errno.handle_nfi path if ret != 0
      ret
    end
    alias_method :delete, :rmdir
    alias_method :unlink, :rmdir

    def getwd
      Rubinius::FFI::MemoryPointer.new(Rubinius::PATH_MAX) do |ptr|
        wd = Truffle::POSIX.getcwd(ptr, Rubinius::PATH_MAX)
        Errno.handle_nfi unless wd

        Rubinius::Type.external_string wd
      end
    end
    alias_method :pwd, :getwd

    def chroot(path)
      ret = Truffle::POSIX.chroot Rubinius::Type.coerce_to_path(path)
      Errno.handle path if ret != 0
      ret
    end
  end
end
