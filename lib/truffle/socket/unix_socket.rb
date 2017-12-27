# Copyright (c) 2013, Brian Shirai
# All rights reserved.
# 
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
# 
# 1. Redistributions of source code must retain the above copyright notice, this
#    list of conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright notice,
#    this list of conditions and the following disclaimer in the documentation
#    and/or other materials provided with the distribution.
# 3. Neither the name of the library nor the names of its contributors may be
#    used to endorse or promote products derived from this software without
#    specific prior written permission.
# 
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY DIRECT,
# INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
# BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
# DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
# OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
# EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

class UNIXSocket < BasicSocket
  include IO::TransferIO

  def self.socketpair(type = Socket::SOCK_STREAM, protocol = 0)
    family = Socket::AF_UNIX
    type   = Truffle::Socket.socket_type(type)

    fd0, fd1 = Truffle::Socket::Foreign.socketpair(family, type, protocol)

    [for_fd(fd0), for_fd(fd1)]
  end

  class << self
    alias_method :pair, :socketpair
  end

  def initialize(path)
    @no_reverse_lookup = self.class.do_not_reverse_lookup
    @path              = '' # empty for client sockets

    fd = Truffle::Socket::Foreign.socket(Socket::AF_UNIX, Socket::SOCK_STREAM, 0)

    Errno.handle('socket(2)') if fd < 0

    IO.setup(self, fd, 'r+', true)
    binmode

    sockaddr = Socket.sockaddr_un(path)
    status   = Truffle::Socket::Foreign.connect(descriptor, sockaddr)

    Errno.handle('connect(2)') if status < 0
  end

  def recvfrom(bytes_read, flags = 0)
    Truffle::Socket::Foreign.memory_pointer(bytes_read) do |buf|
      n_bytes = Truffle::Socket::Foreign.recvfrom(@descriptor, buf, bytes_read, flags, nil, nil)
      Errno.handle('recvfrom(2)') if n_bytes == -1
      return [buf.read_string(n_bytes), ['AF_UNIX', '']]
    end
  end

  def path
    @path ||= Truffle::Socket::Foreign.getsockname(descriptor).unpack('SZ*')[1]
  end

  def addr
    ['AF_UNIX', path]
  end

  def peeraddr
    path = Truffle::Socket::Foreign.getpeername(descriptor).unpack('SZ*')[1]

    ['AF_UNIX', path]
  end

  def recv_io(klass = IO, mode = nil)
    begin
      fd = recv_fd
    rescue PrimitiveFailure
      raise SocketError, "file descriptor was not passed"
    end

    return fd unless klass

    if klass.is_a?(BasicSocket)
      klass.for_fd(fd)
    else
      klass.for_fd(fd, mode)
    end
  end

  def local_address
    address = addr

    Addrinfo.new(Socket.pack_sockaddr_un(address[1]), :UNIX, :STREAM)
  end

  def remote_address
    address = peeraddr

    Addrinfo.new(Socket.pack_sockaddr_un(address[1]), :UNIX, :STREAM)
  end
end
