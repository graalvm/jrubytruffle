/*
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.platform.java;

import jnr.ffi.Pointer;
import jnr.posix.Timeval;
import org.truffleruby.platform.posix.Sockets;

import java.nio.ByteBuffer;

public class JavaSockets implements Sockets {

    @Override
    public int getaddrinfo(CharSequence hostname, CharSequence servname, Pointer hints, Pointer res) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void freeaddrinfo(Pointer ai) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String gai_strerror(int ecode) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getnameinfo(Pointer sa, int salen, Pointer host, int hostlen, Pointer serv, int servlen, int flags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int socket(int domain, int type, int protocol) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int sendto(int socket, Pointer message, int length, int flags, Pointer dest_addr, int dest_len) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int setsockopt(int socket, int level, int option_name, Pointer option_value, int option_len) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int bind(int socket, Pointer address, int address_len) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int listen(int socket, int backlog) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int accept(int socket, Pointer address, int[] addressLength) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int accept(int socket, Pointer sockaddr, Pointer address_len) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int gethostname(Pointer name, int namelen) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int inet_network(String cp) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int inet_pton(int af, String src, Pointer dst) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Pointer gethostbyname(String name) {
        return null;
    }

    @Override
    public int select(int nfds, Pointer readfds, Pointer writefds, Pointer errorfds, Timeval timeout) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int socketpair(int domain, int type, int protocolint, Pointer ptr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getpeername(int socket, Pointer address, Pointer address_len) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getsockname(int socket, Pointer address, Pointer address_len) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getsockopt(int sockfd, int level, int optname, Pointer optval, Pointer optlen) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int connect(int socket, Pointer address, int address_len) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int recvfrom(int sockfd, ByteBuffer buf, int len, int flags, Pointer src_addr, Pointer addrlen) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int send(int sockfd, Pointer buf, int len, int flags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int shutdown(int socket, int how) {
        throw new UnsupportedOperationException();
    }

}
