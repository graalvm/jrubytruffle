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
    
    class Buffer < Rubinius::FFI::Struct
      layout  :atime,   :uint64,
              :mtime,   :uint64,
              :ctime,   :uint64,
              :nlink,   :uint64,
              :rdev,    :uint64,
              :blksize, :uint64,
              :blocks,  :uint64,
              :dev,     :uint64,
              :ino,     :uint64,
              :size,    :uint64,
              :mode,    :uint64,
              :gid,     :uint64,
              :uid,     :uint64
    end

    S_IRUSR  = Rubinius::Config['rbx.platform.file.S_IRUSR']
    S_IWUSR  = Rubinius::Config['rbx.platform.file.S_IWUSR']
    S_IXUSR  = Rubinius::Config['rbx.platform.file.S_IXUSR']
    S_IRGRP  = Rubinius::Config['rbx.platform.file.S_IRGRP']
    S_IWGRP  = Rubinius::Config['rbx.platform.file.S_IWGRP']
    S_IXGRP  = Rubinius::Config['rbx.platform.file.S_IXGRP']
    S_IROTH  = Rubinius::Config['rbx.platform.file.S_IROTH']
    S_IWOTH  = Rubinius::Config['rbx.platform.file.S_IWOTH']
    S_IXOTH  = Rubinius::Config['rbx.platform.file.S_IXOTH']

    S_IRUGO  = S_IRUSR | S_IRGRP | S_IROTH
    S_IWUGO  = S_IWUSR | S_IWGRP | S_IWOTH
    S_IXUGO  = S_IXUSR | S_IXGRP | S_IXOTH

    S_IFMT   = Rubinius::Config['rbx.platform.file.S_IFMT']
    S_IFIFO  = Rubinius::Config['rbx.platform.file.S_IFIFO']
    S_IFCHR  = Rubinius::Config['rbx.platform.file.S_IFCHR']
    S_IFDIR  = Rubinius::Config['rbx.platform.file.S_IFDIR']
    S_IFBLK  = Rubinius::Config['rbx.platform.file.S_IFBLK']
    S_IFREG  = Rubinius::Config['rbx.platform.file.S_IFREG']
    S_IFLNK  = Rubinius::Config['rbx.platform.file.S_IFLNK']
    S_IFSOCK = Rubinius::Config['rbx.platform.file.S_IFSOCK']
    S_IFWHT  = Rubinius::Config['rbx.platform.file.S_IFWHT']
    S_ISUID  = Rubinius::Config['rbx.platform.file.S_ISUID']
    S_ISGID  = Rubinius::Config['rbx.platform.file.S_ISGID']
    S_ISVTX  = Rubinius::Config['rbx.platform.file.S_ISVTX']

    attr_reader :path

    def initialize(path_or_buffer)
      if path_or_buffer.is_a?(Buffer)
        @buffer = path_or_buffer.to_h
      else
        path = Rubinius::Type.coerce_to_path(path_or_buffer)
        Buffer.new { |buffer|
          result = Truffle::POSIX.truffleposix_stat(path, buffer)
          Errno.handle_nfi path unless result == 0
          @buffer = buffer.to_h
        }
      end
    end

    def self.stat(path)
      path = Rubinius::Type.coerce_to_path(path)
      Buffer.new { |buffer|
        result = Truffle::POSIX.truffleposix_stat(path, buffer)
        if result == 0
          return Stat.new buffer
        else
          return nil
        end
      }
    end

    def self.lstat(path)
      stat = lstat?(path)
      Errno.handle_nfi path unless stat
      stat
    end

    def self.lstat?(path)
      path = Rubinius::Type.coerce_to_path(path)
      Buffer.new { |buffer|
        result = Truffle::POSIX.truffleposix_lstat(path, buffer)
        if result == 0
          return Stat.new buffer
        else
          return nil
        end
      }
    end

    def self.fstat(fd)
      fd = Rubinius::Type.coerce_to fd, Integer, :to_int
      Buffer.new { |buffer|
        result = Truffle::POSIX.truffleposix_fstat(fd, buffer)
        Errno.handle_nfi "file descriptor #{descriptor}" unless result == 0
        return Stat.new buffer
      }
    end

    def blockdev?
      mode & S_IFMT == S_IFBLK
    end

    def chardev?
      mode & S_IFMT == S_IFCHR
    end

    def dev_major
      major = Truffle::POSIX.major dev
      major < 0 ? nil : major
    end

    def dev_minor
      minor = Truffle::POSIX.minor dev
      minor < 0 ? nil : minor
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
      major = Truffle::POSIX.major rdev
      major < 0 ? nil : major
    end

    def rdev_minor
      minor = Truffle::POSIX.minor rdev
      minor < 0 ? nil : minor
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
        Rubinius::Type.coerce_to tmp, Fixnum, :to_int
      end
    end

    def world_writable?
      if mode & S_IWOTH == S_IWOTH
        tmp = mode & (S_IRUGO | S_IWUGO | S_IXUGO)
        Rubinius::Type.coerce_to tmp, Fixnum, :to_int
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

    def atime
      Time.at @buffer[:atime]
    end

    def mtime
      Time.at @buffer[:mtime]
    end

    def ctime
      Time.at @buffer[:ctime]
    end
    
    def nlink
      @buffer[:nlink]
    end
    
    def rdev
      @buffer[:rdev]
    end
    
    def blksize
      @buffer[:blksize]
    end
    
    def blocks
      @buffer[:blocks]
    end
    
    def dev
      @buffer[:dev]
    end
    
    def ino
      @buffer[:ino]
    end
    
    def size
      @buffer[:size]
    end
    
    def mode
      @buffer[:mode]
    end
    
    def gid
      @buffer[:gid]
    end
    
    def uid
      @buffer[:uid]
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
