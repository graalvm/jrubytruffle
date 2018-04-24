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

class File
  class Stat
    include Comparable

    S_IRUSR  = Truffle::Config['platform.file.S_IRUSR']
    S_IWUSR  = Truffle::Config['platform.file.S_IWUSR']
    S_IXUSR  = Truffle::Config['platform.file.S_IXUSR']
    S_IRGRP  = Truffle::Config['platform.file.S_IRGRP']
    S_IWGRP  = Truffle::Config['platform.file.S_IWGRP']
    S_IXGRP  = Truffle::Config['platform.file.S_IXGRP']
    S_IROTH  = Truffle::Config['platform.file.S_IROTH']
    S_IWOTH  = Truffle::Config['platform.file.S_IWOTH']
    S_IXOTH  = Truffle::Config['platform.file.S_IXOTH']

    S_IRUGO  = S_IRUSR | S_IRGRP | S_IROTH
    S_IWUGO  = S_IWUSR | S_IWGRP | S_IWOTH
    S_IXUGO  = S_IXUSR | S_IXGRP | S_IXOTH

    S_IFMT   = Truffle::Config['platform.file.S_IFMT']
    S_IFIFO  = Truffle::Config['platform.file.S_IFIFO']
    S_IFCHR  = Truffle::Config['platform.file.S_IFCHR']
    S_IFDIR  = Truffle::Config['platform.file.S_IFDIR']
    S_IFBLK  = Truffle::Config['platform.file.S_IFBLK']
    S_IFREG  = Truffle::Config['platform.file.S_IFREG']
    S_IFLNK  = Truffle::Config['platform.file.S_IFLNK']
    S_IFSOCK = Truffle::Config['platform.file.S_IFSOCK']
    S_ISUID  = Truffle::Config['platform.file.S_ISUID']
    S_ISGID  = Truffle::Config['platform.file.S_ISGID']
    S_ISVTX  = Truffle::Config['platform.file.S_ISVTX']

    attr_reader :path

    def initialize(path_or_buffer)
      if path_or_buffer.is_a?(Truffle::FFI::MemoryPointer)
        @buffer = path_or_buffer.read_array_of_uint64(BUFFER_ELEMENTS)
      else
        path = Truffle::Type.coerce_to_path(path_or_buffer)
        Truffle::FFI::MemoryPointer.new(:uint64, BUFFER_ELEMENTS) do |ptr|
          result = Truffle::POSIX.truffleposix_stat(path, ptr)
          Errno.handle path unless result == 0
          @buffer = ptr.read_array_of_uint64(BUFFER_ELEMENTS)
        end
      end
    end

    def self.stat(path)
      path = Truffle::Type.coerce_to_path(path)
      Truffle::FFI::MemoryPointer.new(:uint64, BUFFER_ELEMENTS) do |ptr|
        if Truffle::POSIX.truffleposix_stat(path, ptr) == 0
          Stat.new ptr
        else
          nil
        end
      end
    end

    def self.lstat(path)
      stat = lstat?(path)
      Errno.handle path unless stat
      stat
    end

    def self.lstat?(path)
      path = Truffle::Type.coerce_to_path(path)
      Truffle::FFI::MemoryPointer.new(:uint64, BUFFER_ELEMENTS) do |ptr|
        if Truffle::POSIX.truffleposix_lstat(path, ptr) == 0
          Stat.new ptr
        else
          nil
        end
      end
    end

    def self.fstat(fd)
      fd = Truffle::Type.coerce_to fd, Integer, :to_int
      Truffle::FFI::MemoryPointer.new(:uint64, BUFFER_ELEMENTS) do |ptr|
        result = Truffle::POSIX.truffleposix_fstat(fd, ptr)
        Errno.handle "file descriptor #{descriptor}" unless result == 0
        Stat.new ptr
      end
    end

    def blockdev?
      mode & S_IFMT == S_IFBLK
    end

    def chardev?
      mode & S_IFMT == S_IFCHR
    end

    def dev_major
      Truffle::POSIX.truffleposix_major(dev)
    end

    def dev_minor
      Truffle::POSIX.truffleposix_minor(dev)
    end

    def directory?
      mode & S_IFMT == S_IFDIR
    end

    def executable?
      return true if superuser?
      return mode & S_IXUSR != 0 if owned?
      return mode & S_IXGRP != 0 if grpowned?
      mode & S_IXOTH != 0
    end

    def executable_real?
      return true if rsuperuser?
      return mode & S_IXUSR != 0 if rowned?
      return mode & S_IXGRP != 0 if rgrpowned?
      mode & S_IXOTH != 0
    end

    def file?
      mode & S_IFMT == S_IFREG
    end

    def ftype
      if file?
        'file'
      elsif directory?
        'directory'
      elsif chardev?
        'characterSpecial'
      elsif blockdev?
        'blockSpecial'
      elsif pipe?
        'fifo'
      elsif socket?
        'socket'
      elsif symlink?
        'link'
      else
        'unknown'
      end
    end

    def owned?
      uid == Truffle::POSIX.geteuid
    end

    def pipe?
      mode & S_IFMT == S_IFIFO
    end

    def rdev_major
      Truffle::POSIX.truffleposix_major(rdev)
    end

    def rdev_minor
      Truffle::POSIX.truffleposix_minor(rdev)
    end

    def readable?
      return true if superuser?
      return mode & S_IRUSR != 0 if owned?
      return mode & S_IRGRP != 0 if grpowned?
      mode & S_IROTH != 0
    end

    def readable_real?
      return true if rsuperuser?
      return mode & S_IRUSR != 0 if rowned?
      return mode & S_IRGRP != 0 if rgrpowned?
      mode & S_IROTH != 0
    end

    def setgid?
      mode & S_ISGID != 0
    end

    def setuid?
      mode & S_ISUID != 0
    end

    def sticky?
      mode & S_ISVTX != 0
    end

    def size?
      size == 0 ? nil : size
    end

    def socket?
      mode & S_IFMT == S_IFSOCK
    end

    def symlink?
      mode & S_IFMT == S_IFLNK
    end

    def world_readable?
      if mode & S_IROTH == S_IROTH
        tmp = mode & (S_IRUGO | S_IWUGO | S_IXUGO)
        Truffle::Type.coerce_to_int tmp
      end
    end

    def world_writable?
      if mode & S_IWOTH == S_IWOTH
        tmp = mode & (S_IRUGO | S_IWUGO | S_IXUGO)
        Truffle::Type.coerce_to_int tmp
      end
    end

    def writable?
      return true if superuser?
      return mode & S_IWUSR != 0 if owned?
      return mode & S_IWGRP != 0 if grpowned?
      mode & S_IWOTH != 0
    end

    def writable_real?
      return true if rsuperuser?
      return mode & S_IWUSR != 0 if rowned?
      return mode & S_IWGRP != 0 if rgrpowned?
      mode & S_IWOTH != 0
    end

    def zero?
      size == 0
    end

    def <=>(other)
      return nil unless other.is_a?(File::Stat)
      self.mtime <=> other.mtime
    end

    def rgrpowned?
      gid == Truffle::POSIX.getgid
    end
    private :rgrpowned?

    def rowned?
      uid == Truffle::POSIX.getuid
    end
    private :rowned?

    def rsuperuser?
      Truffle::POSIX.getuid == 0
    end
    private :rsuperuser?

    def superuser?
      Truffle::POSIX.geteuid == 0
    end
    private :superuser?

    # Process.groups only return supplemental groups, so we need to check if gid/egid match too.
    def grpowned?
      gid = gid()
      return true if gid == Process.gid || gid == Process.egid
      Process.groups.include?(gid)
    end

    # These indices are from truffleposix.c

    BUFFER_ELEMENTS = 13

    def atime
      Time.at @buffer[0]
    end

    def mtime
      Time.at @buffer[1]
    end

    def ctime
      Time.at @buffer[2]
    end
    
    def nlink
      @buffer[3]
    end
    
    def rdev
      @buffer[4]
    end
    
    def blksize
      @buffer[5]
    end
    
    def blocks
      @buffer[6]
    end
    
    def dev
      @buffer[7]
    end
    
    def ino
      @buffer[8]
    end
    
    def size
      @buffer[9]
    end
    
    def mode
      @buffer[10]
    end
    
    def gid
      @buffer[11]
    end
    
    def uid
      @buffer[12]
    end

    def inspect
      "#<#{self.class.name} dev=0x#{self.dev.to_s(16)}, ino=#{self.ino}, " \
      "mode=#{sprintf("%07d", self.mode.to_s(8).to_i)}, nlink=#{self.nlink}, " \
      "uid=#{self.uid}, gid=#{self.gid}, rdev=0x#{self.rdev.to_s(16)}, " \
      "size=#{self.size}, blksize=#{self.blksize}, blocks=#{self.blocks}, " \
      "atime=#{self.atime}, mtime=#{self.mtime}, ctime=#{self.ctime}>"
    end
  end
end
