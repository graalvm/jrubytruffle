# frozen_string_literal: true

# Copyright (c) 2016, 2020 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

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

module Process
  module Constants
    EXIT_SUCCESS = Truffle::Config['platform.process.EXIT_SUCCESS'] || 0
    EXIT_FAILURE = Truffle::Config['platform.process.EXIT_FAILURE'] || 1

    PRIO_PGRP    = Truffle::Config['platform.process.PRIO_PGRP']
    PRIO_PROCESS = Truffle::Config['platform.process.PRIO_PROCESS']
    PRIO_USER    = Truffle::Config['platform.process.PRIO_USER']

    RLIM_INFINITY  = Truffle::Config['platform.process.RLIM_INFINITY']
    RLIM_SAVED_MAX = Truffle::Config['platform.process.RLIM_SAVED_MAX']
    RLIM_SAVED_CUR = Truffle::Config['platform.process.RLIM_SAVED_CUR']

    RLIMIT_AS      = Truffle::Config['platform.process.RLIMIT_AS']
    RLIMIT_CORE    = Truffle::Config['platform.process.RLIMIT_CORE']
    RLIMIT_CPU     = Truffle::Config['platform.process.RLIMIT_CPU']
    RLIMIT_DATA    = Truffle::Config['platform.process.RLIMIT_DATA']
    RLIMIT_FSIZE   = Truffle::Config['platform.process.RLIMIT_FSIZE']
    RLIMIT_NOFILE  = Truffle::Config['platform.process.RLIMIT_NOFILE']
    RLIMIT_STACK   = Truffle::Config['platform.process.RLIMIT_STACK']

    %i[RLIMIT_MEMLOCK RLIMIT_NPROC RLIMIT_RSS RLIMIT_SBSIZE RLIMIT_RTPRIO
       RLIMIT_RTTIME RLIMIT_SIGPENDING RLIMIT_MSGQUEUE RLIMIT_NICE].each do |limit|
      if value = Truffle::Config.lookup("platform.process.#{limit}")
        const_set limit, value
      end
    end

    WNOHANG =   Truffle::Config['platform.process.WNOHANG']
    WUNTRACED = Truffle::Config['platform.process.WUNTRACED']
  end
  include Constants

  FFI = Truffle::FFI

  # Terminate with given status code.
  #
  def self.exit(code=0)
    case code
    when true
      code = 0
    when false
      code = 1
    else
      code = Truffle::Type.coerce_to code, Integer, :to_int
    end

    raise SystemExit, code
  end

  def self.exit!(code=1)
    case code
    when true
      code = 0
    when false
      code = 1
    else
      code = Truffle::Type.coerce_to code, Integer, :to_int
    end

    Primitive.vm_exit code
  end

  section = 'platform.clocks.'
  Truffle::Config.section(section) do |key, value|
    const_set(Primitive.string_substring(key, section.size, key.length), value)
  end

  def self.clock_getres(id, unit=:float_second)
    res = case id
          when :MACH_ABSOLUTE_TIME_BASED_CLOCK_MONOTONIC,
               CLOCK_MONOTONIC
            1
          when :GETTIMEOFDAY_BASED_CLOCK_REALTIME,
               :GETRUSAGE_BASED_CLOCK_PROCESS_CPUTIME_ID,
               :CLOCK_BASED_CLOCK_PROCESS_CPUTIME_ID
            1_000
          when CLOCK_REALTIME
            # https://bugs.openjdk.java.net/browse/JDK-8068730
            Primitive.vm_java_version >= 9 ? 1000 : 1_000_000
          when :TIMES_BASED_CLOCK_MONOTONIC,
               :TIMES_BASED_CLOCK_PROCESS_CPUTIME_ID
            10_000_000
          when :TIME_BASED_CLOCK_REALTIME
            1_000_000_000
          when Symbol
            raise Errno::EINVAL
          else
            res_for_id = Truffle::POSIX.truffleposix_clock_getres(id)
            if res_for_id == 0
              Errno.handle
            else
              res_for_id
            end
          end

    if :hertz == unit
      1.0 / nanoseconds_to_unit(res,:float_second)
    else
      nanoseconds_to_unit(res,unit)
    end
  end

  def self.clock_gettime(id, unit=:float_second)
    if Primitive.object_kind_of?(id, Symbol)
      id = case id
           when :GETTIMEOFDAY_BASED_CLOCK_REALTIME,
                :TIME_BASED_CLOCK_REALTIME
             CLOCK_REALTIME
           when :MACH_ABSOLUTE_TIME_BASED_CLOCK_MONOTONIC,
                :TIMES_BASED_CLOCK_MONOTONIC
             CLOCK_MONOTONIC
           when :GETRUSAGE_BASED_CLOCK_PROCESS_CPUTIME_ID,
                :TIMES_BASED_CLOCK_PROCESS_CPUTIME_ID,
                :CLOCK_BASED_CLOCK_PROCESS_CPUTIME_ID
             CLOCK_THREAD_CPUTIME_ID
           else
             raise Errno::EINVAL
           end
    end

    case id
    when CLOCK_MONOTONIC
      time = Primitive.process_time_nanotime
    when CLOCK_REALTIME
      time = Primitive.process_time_instant
    else
      time = Truffle::POSIX.truffleposix_clock_gettime(id)
      Errno.handle if time == 0
    end

    nanoseconds_to_unit(time, unit)
  end

  def self.nanoseconds_to_unit(nanoseconds, unit)
    case unit
    when :float_second, nil
      nanoseconds / 1e9
    when :float_microsecond
      nanoseconds / 1e3
    when :float_millisecond
      nanoseconds / 1e6
    when :second
      nanoseconds / 1_000_000_000
    when :millisecond
      nanoseconds / 1_000_000
    when :microsecond
      nanoseconds / 1_000
    when :nanosecond
      nanoseconds
    else
      raise ArgumentError, "unexpected unit: #{unit}"
    end
  end
  private_class_method :nanoseconds_to_unit

  ##
  # Sets the process title. Calling this method does not affect the value of
  # `$0` as per MRI behaviour. This method returns the title set.
  #
  # @param [String] title
  # @return [Title]
  #
  def self.setproctitle(title)
    title = Truffle::Type.coerce_to(title, String, :to_str)
    if TruffleRuby.native?
      Truffle::System.native_set_process_title(title)
    elsif Truffle::Platform.linux? && File.readable?('/proc/self/maps')
      setproctitle_linux_from_proc_maps(title)
    elsif Truffle::Platform.darwin?
      setproctitle_darwin(title)
    else
      # Silently don't set the process title if we can't do it
    end

    title
  end

  def self.setproctitle_darwin(title)
    argv0_address = Truffle::POSIX._NSGetArgv.read_pointer.read_pointer

    @_argv0_max_length ||= argv0_address.read_string.bytesize
    argv0_address = argv0_address.slice(0, @_argv0_max_length)

    new_title = setproctitle_truncate_title(title, @_argv0_max_length)
    argv0_address.write_bytes new_title
  end
  private_class_method :setproctitle_darwin

  # Very hacky implementation to pass the specs, since the JVM doesn't give us argv[0]
  # Overwrite *argv inplace because finding the argv pointer itself is harder.
  # Truncates title if title is longer than the orginal command line.
  def self.setproctitle_linux_from_proc_maps(title)
    @_argv0_address ||= begin
      command = File.binread('/proc/self/cmdline')

      stack = File.readlines('/proc/self/maps').grep(/\[stack\]/)
      raise stack.to_s unless stack.size == 1

      from, to = stack[0].split[0].split('-').map { |addr| Integer(addr, 16) }
      raise unless from < to

      args_length = 0
      Truffle::Boot.original_argv.each do |arg|
        args_length += 1 + arg.bytesize
      end

      env_length = 0
      ENV.each_pair do |key, val|
        env_length += key.bytesize + 1 + val.bytesize + 1
      end

      size = 2 * 4096 + args_length + env_length
      base = to - size
      base_ptr = FFI::Pointer.new(:char, base)
      haystack = base_ptr.read_string(size)

      i = haystack.index("\x00#{command}")
      raise 'argv[0] not found' unless i
      i += 1

      @_argv0_max_length = command.bytesize
      base + i
    end

    new_title = setproctitle_truncate_title(title, @_argv0_max_length)

    argv0_ptr = FFI::Pointer.new(:char, @_argv0_address).slice(0, @_argv0_max_length)
    argv0_ptr.write_bytes(new_title)

    new_command = File.binread('/proc/self/cmdline')
    raise 'failed' unless new_command.start_with?(new_title)
  end
  private_class_method :setproctitle_linux_from_proc_maps

  def self.setproctitle_truncate_title(title, size)
    if title.bytesize > size
      title.byteslice(0, size)
    else
      title + "\x00" * (size - title.bytesize)
    end
  end
  private_class_method :setproctitle_truncate_title

  def self.setrlimit(resource, cur_limit, max_limit=undefined)
    resource =  coerce_rlimit_resource(resource)
    cur_limit = Truffle::Type.coerce_to cur_limit, Integer, :to_int

    if Primitive.undefined? max_limit
      max_limit = cur_limit
    else
      max_limit = Truffle::Type.coerce_to max_limit, Integer, :to_int
    end

    rlim_t = Truffle::Config['platform.typedef.rlim_t']
    raise rlim_t unless rlim_t == 'ulong' or rlim_t == 'ulong_long'

    Truffle::FFI::MemoryPointer.new(:rlim_t, 2) do |ptr|
      ptr[0].write_ulong cur_limit
      ptr[1].write_ulong max_limit
      ret = Truffle::POSIX.setrlimit(resource, ptr)
      Errno.handle if ret == -1
    end
    nil
  end

  def self.getrlimit(resource)
    resource = coerce_rlimit_resource(resource)

    rlim_t = Truffle::Config['platform.typedef.rlim_t']
    raise rlim_t unless rlim_t == 'ulong' or rlim_t == 'ulong_long'

    Truffle::FFI::MemoryPointer.new(:rlim_t, 2) do |ptr|
      ret = Truffle::POSIX.getrlimit(resource, ptr)
      Errno.handle if ret == -1
      cur = ptr[0].read_ulong
      max = ptr[1].read_ulong
      [cur, max]
    end
  end

  def self.setsid
    pgid = Truffle::POSIX.setsid
    Errno.handle if pgid == -1
    pgid
  end

  def self.fork
    raise NotImplementedError, 'fork is not available'
  end
  Primitive.method_unimplement method(:fork)

  def self.times
    Truffle::FFI::MemoryPointer.new(:double, 4) do |ptr|
      ret = Truffle::POSIX.truffleposix_getrusage(ptr)
      Errno.handle if ret == -1
      Process::Tms.new(*ptr.read_array_of_double(4))
    end
  end

  def self.kill(signal, *pids)
    raise ArgumentError, 'PID argument required' if pids.length == 0

    use_process_group = false
    signal = signal.to_s if Primitive.object_kind_of?(signal, Symbol)

    if Primitive.object_kind_of?(signal, String)
      if signal.start_with? '-'
        signal = signal[1..-1]
        use_process_group = true
      end

      if signal.start_with? 'SIG'
        signal = signal[3..-1]
      end

      signal = Signal::Names[signal]
    end

    raise ArgumentError unless Primitive.object_kind_of?(signal, Integer)

    if signal < 0
      signal = -signal
      use_process_group = true
    end

    pids.each do |pid|
      pid = Truffle::Type.coerce_to pid, Integer, :to_int

      if pid == Process.pid && signal != 0
        signal_name = Signal::Numbers[signal].to_sym
        result = Primitive.process_kill_raise signal_name
        if result == -1 # Try kill() if the java Signal.raise() failed
          result = Truffle::POSIX.kill(pid, signal)
          Errno.handle if result == -1
        end
      else
        pid = -pid if use_process_group
        result = Truffle::POSIX.kill(pid, signal)
        Errno.handle if result == -1
      end
    end

    pids.length
  end

  def self.abort(msg=nil)
    if msg
      msg = StringValue(msg)
      $stderr.puts(msg)
    end
    raise SystemExit.new(1, msg)
  end

  def self.getpgid(pid)
    pid = Truffle::Type.coerce_to pid, Integer, :to_int

    ret = Truffle::POSIX.getpgid(pid)
    Errno.handle if ret == -1
    ret
  end

  def self.setpgid(pid, int)
    pid = Truffle::Type.coerce_to pid, Integer, :to_int
    int = Truffle::Type.coerce_to int, Integer, :to_int

    ret = Truffle::POSIX.setpgid(pid, int)
    Errno.handle if ret == -1
    ret
  end

  @maxgroups = 32
  class << self
    attr_accessor :maxgroups
  end

  def self.setpgrp
    setpgid(0, 0)
  end
  def self.getpgrp
    ret = Truffle::POSIX.getpgrp
    Errno.handle if ret == -1
    ret
  end

  def self.pid
    Truffle::POSIX.getpid
  end

  def self.ppid
    Truffle::POSIX.getppid
  end

  def self.uid=(uid)
    # the 4 rescue clauses below are needed
    # until respond_to? can be used to query the implementation of methods attached via FFI
    # atm respond_to returns true if a method is attached but not implemented on the platform
    uid = Truffle::Type.coerce_to uid, Integer, :to_int
    begin
      ret = Truffle::POSIX.setresuid(uid, -1, -1)
    rescue NotImplementedError
      begin
        ret = Truffle::POSIX.setreuid(uid, -1)
      rescue NotImplementedError
        begin
          ret = Truffle::POSIX.setruid(uid)
        rescue NotImplementedError
          if Process.euid == uid
            ret = Truffle::POSIX.setuid(uid)
          else
            raise NotImplementedError
          end
        end
      end
    end

    Errno.handle if ret == -1

    uid
  end

  def self.gid=(gid)
    gid = Truffle::Type.coerce_to gid, Integer, :to_int
    Process::Sys.setgid gid
  end

  def self.euid=(uid)
    # the 4 rescue clauses below are needed
    # until respond_to? can be used to query the implementation of methods attached via FFI
    # atm respond_to returns true if a method is attached but not implemented on the platform
    uid = Truffle::Type.coerce_to uid, Integer, :to_int
    begin
      ret = Truffle::POSIX.setresuid(-1, uid, -1)
    rescue NotImplementedError
      begin
        ret = Truffle::POSIX.setreuid(-1, uid)
      rescue NotImplementedError
        begin
          ret = Truffle::POSIX.seteuid(uid)
        rescue NotImplementedError
          if Process.uid == uid
            ret = Truffle::POSIX.setuid(uid)
          else
            raise NotImplementedError
          end
        end
      end
    end

    Errno.handle if ret == -1

    uid
  end

  def self.egid=(gid)
    gid = Truffle::Type.coerce_to gid, Integer, :to_int
    Process::Sys.setegid gid
  end

  def self.uid
    Truffle::POSIX.getuid
  end

  def self.gid
    Truffle::POSIX.getgid
  end

  def self.euid
    Truffle::POSIX.geteuid
  end

  def self.egid
    Truffle::POSIX.getegid
  end

  def self.getpriority(kind, id)
    kind = Truffle::Type.coerce_to kind, Integer, :to_int
    id =   Truffle::Type.coerce_to id, Integer, :to_int

    ret = Truffle::POSIX.truffleposix_getpriority(kind, id)
    if ret <= -100
      raise SystemCallError.new(nil, -(ret + 100))
    end
    ret
  end

  def self.setpriority(kind, id, priority)
    kind = Truffle::Type.coerce_to kind, Integer, :to_int
    id =   Truffle::Type.coerce_to id, Integer, :to_int
    priority = Truffle::Type.coerce_to priority, Integer, :to_int

    ret = Truffle::POSIX.setpriority(kind, id, priority)
    Errno.handle if ret == -1
    ret
  end

  def self.groups
    ngroups = Truffle::POSIX.getgroups(0, FFI::Pointer::NULL)
    Errno.handle if ngroups == -1

    gid_t = Truffle::Config['platform.typedef.gid_t']
    raise gid_t unless gid_t == 'uint'

    FFI::MemoryPointer.new(:gid_t, ngroups) do |ptr|
      ret = Truffle::POSIX.getgroups(ngroups, ptr)
      Errno.handle if ret == -1
      ptr.read_array_of_uint(ngroups)
    end
  end

  def self.groups=(groups)
    gid_t = Truffle::Config['platform.typedef.gid_t']
    raise gid_t unless gid_t == 'uint'

    @maxgroups = groups.size if groups.size > @maxgroups

    FFI::MemoryPointer.new(:gid_t, groups.size) do |ptr|
      ptr.write_array_of_uint(groups)
      r = Truffle::POSIX.setgroups(groups.size, ptr)
      Errno.handle if r == -1
    end
    groups
  end

  def self.initgroups(username, gid)
    username = StringValue(username)
    gid = Truffle::Type.coerce_to gid, Integer, :to_int

    if Truffle::POSIX.initgroups(username, gid) == -1
      Errno.handle
    end

    Process.groups
  end

  def self.last_status
    $?
  end

  #
  # Wait for the given process to exit.
  #
  # The pid may be the specific pid of some previously forked
  # process, or -1 to indicate to watch for *any* child process
  # exiting. Other options, such as process groups, may be available
  # depending on the system.
  #
  # With no arguments the default is to block waiting for any
  # child processes (pid -1.)
  #
  # The flag may be Process::WNOHANG, which indicates that
  # the child should only be quickly checked. If it has not
  # exited yet, nil is returned immediately instead.
  #
  # The return value is the exited pid or nil if Process::WNOHANG
  # was used and the child had not yet exited.
  #
  # If the pid has exited, the global $? is set to a Process::Status
  # object representing the exit status (and possibly other info) of
  # the child.
  #
  # If there exists no such pid (e.g. never forked or already
  # waited for), or no children at all, Errno::ECHILD is raised.
  #
  # TODO: Support other options such as WUNTRACED? --rue
  #
  def self.wait2(input_pid=-1, flags=nil)
    status = Truffle::ProcessOperations.wait(input_pid, flags, true, true)
    [status.pid, status]
  end

  #
  # Wait for all child processes.
  #
  # Blocks until all child processes have exited, and returns
  # an Array of [pid, Process::Status] results, one for each
  # child.
  #
  # Be mindful of the effects of creating new processes while
  # .waitall has been called (usually in a different thread.)
  # The .waitall call does not in any way check that it is only
  # waiting for children that existed at the time it was called.
  #
  def self.waitall
    statuses = []

    begin
      while status = Process.wait2
        statuses << status
      end
    rescue Errno::ECHILD
      nil # No child processes
    end

    statuses
  end

  def self.wait(pid=-1, flags=nil)
    Truffle::ProcessOperations.wait(pid, flags, true, true)&.pid
  end

  class << self
    alias_method :waitpid, :wait
    alias_method :waitpid2, :wait2
  end

  def self.daemon(*args)
    raise NotImplementedError, 'Process.daemon is not available'
  end
  Primitive.method_unimplement method(:daemon)

  def self.exec(*args)
    Truffle::ProcessOperations.exec(*args)
  end

  def self.spawn(*args)
    Truffle::ProcessOperations.spawn(*args)
  end

  # TODO: Should an error be raised on ECHILD? --rue
  #
  # TODO: This operates on the assumption that waiting on
  #       the event consumes very little resources. If this
  #       is not the case, the check should be made WNOHANG
  #       and called periodically.
  #
  def self.detach(pid)
    raise ArgumentError, 'Only positive pids may be detached' unless pid > 0

    thread = Thread.new { Process.wait pid; $? }
    thread[:pid] = pid
    def thread.pid; self[:pid] end

    thread
  end

  def self.coerce_rlimit_resource(resource)
    case resource
    when Integer
      return resource
    when Symbol, String
      # do nothing
    else
      unless r = Truffle::Type.rb_check_convert_type(resource, String, :to_str)
        return Truffle::Type.coerce_to resource, Integer, :to_int
      end

      resource = r
    end

    constant = "RLIMIT_#{resource}"
    unless const_defined? constant
      raise ArgumentError, "invalid resource name: #{constant}"
    end
    const_get constant
  end
  private_class_method :coerce_rlimit_resource

  #--
  # TODO: Most of the fields aren't implemented yet.
  # TODO: Also, these objects should only need to be constructed by
  # Process.wait and family.
  #++

  class Status

    attr_reader :exitstatus, :termsig, :stopsig

    def initialize(pid=nil, exitstatus=nil, termsig=nil, stopsig=nil, raw_status=nil)
      @pid = pid
      @exitstatus = exitstatus
      @termsig = termsig
      @stopsig = stopsig
      @raw_status = raw_status
    end

    def to_i
      @raw_status
    end

    def &(num)
      @raw_status & num
    end

    def >>(num)
      @raw_status >> num
    end

    def ==(other)
      other = other.to_i if Primitive.object_kind_of?(other, Process::Status)
      @raw_status == other
    end

    def coredump?
      false
    end

    def exited?
      @exitstatus != nil
    end

    def pid
      @pid
    end

    def signaled?
      @termsig != nil
    end

    def stopped?
      @stopsig != nil
    end

    def success?
      if exited?
        @exitstatus == 0
      else
        nil
      end
    end

    def to_s
      "pid #{@pid.inspect} exit #{@exitstatus.inspect}"
    end

    def inspect
      "#<Process::Status: #{self}>"
    end

    def self.wait(pid=-1, flags=nil)
      Truffle::ProcessOperations.wait(pid, flags, false, false)
    end
  end

  module Sys
    class << self
      def getegid
        Truffle::POSIX.getegid
      end

      def geteuid
        Truffle::POSIX.geteuid
      end

      def getgid
        Truffle::POSIX.getgid
      end

      def getuid
        Truffle::POSIX.getuid
      end

      def issetugid
        raise 'not implemented'
      end

      def setgid(gid)
        gid = Truffle::Type.coerce_to gid, Integer, :to_int

        ret = Truffle::POSIX.setgid gid
        Errno.handle if ret == -1
        nil
      end

      def setuid(uid)
        uid = Truffle::Type.coerce_to uid, Integer, :to_int

        ret = Truffle::POSIX.setuid uid
        Errno.handle if ret == -1
        nil
      end

      def setegid(egid)
        egid = Truffle::Type.coerce_to egid, Integer, :to_int

        ret = Truffle::POSIX.setegid egid
        Errno.handle if ret == -1
        nil
      end

      def seteuid(euid)
        euid = Truffle::Type.coerce_to euid, Integer, :to_int

        ret = Truffle::POSIX.seteuid euid
        Errno.handle if ret == -1
        nil
      end

      def setrgid(rgid)
        setregid(rgid, -1)
      end

      def setruid(ruid)
        setreuid(ruid, -1)
      end

      def setregid(rid, eid)
        rid = Truffle::Type.coerce_to rid, Integer, :to_int
        eid = Truffle::Type.coerce_to eid, Integer, :to_int

        ret = Truffle::POSIX.setregid rid, eid
        Errno.handle if ret == -1
        nil
      end

      def setreuid(rid, eid)
        rid = Truffle::Type.coerce_to rid, Integer, :to_int
        eid = Truffle::Type.coerce_to eid, Integer, :to_int

        ret = Truffle::POSIX.setreuid rid, eid
        Errno.handle if ret == -1
        nil
      end

      def setresgid(rid, eid, sid)
        rid = Truffle::Type.coerce_to rid, Integer, :to_int
        eid = Truffle::Type.coerce_to eid, Integer, :to_int
        sid = Truffle::Type.coerce_to sid, Integer, :to_int

        ret = Truffle::POSIX.setresgid rid, eid, sid
        Errno.handle if ret == -1
        nil
      end

      def setresuid(rid, eid, sid)
        rid = Truffle::Type.coerce_to rid, Integer, :to_int
        eid = Truffle::Type.coerce_to eid, Integer, :to_int
        sid = Truffle::Type.coerce_to sid, Integer, :to_int

        ret = Truffle::POSIX.setresuid rid, eid, sid
        Errno.handle if ret == -1
        nil
      end
    end
  end

  module UID
    class << self
      def change_privilege(uid)
        uid = Truffle::Type.coerce_to uid, Integer, :to_int

        ret = Truffle::POSIX.setreuid(uid, uid)
        Errno.handle if ret == -1
        uid
      end

      def eid
        Truffle::POSIX.geteuid
      end

      def eid=(uid)
        uid = Truffle::Type.coerce_to uid, Integer, :to_int

        ret = Truffle::POSIX.seteuid(uid)
        Errno.handle if ret == -1
        uid
      end
      alias_method :grant_privilege, :eid=

      def re_exchange
        real = Truffle::POSIX.getuid
        eff = Truffle::POSIX.geteuid
        ret = Truffle::POSIX.setreuid(eff, real)
        Errno.handle if ret == -1
        eff
      end

      def rid
        Truffle::POSIX.getuid
      end
    end
  end

  module GID
    class << self
      def change_privilege(gid)
        gid = Truffle::Type.coerce_to gid, Integer, :to_int

        ret = Truffle::POSIX.setregid(gid, gid)
        Errno.handle if ret == -1
        gid
      end

      def eid
        Truffle::POSIX.getegid
      end

      def eid=(gid)
        gid = Truffle::Type.coerce_to gid, Integer, :to_int

        ret = Truffle::POSIX.setegid(gid)
        Errno.handle if ret == -1
        gid
      end
      alias_method :grant_privilege, :eid=

      def re_exchange
        real = Truffle::POSIX.getgid
        eff = Truffle::POSIX.getegid
        ret = Truffle::POSIX.setregid(eff, real)
        Errno.handle if ret == -1
        eff
      end

      def rid
        Truffle::POSIX.getgid
      end
    end
  end

  xid = Module.new do
    def re_exchangeable?
      true
    end

    def sid_available?
      true
    end

    def switch
      eff = re_exchange
      if block_given?
        ret = yield
        re_exchange
        ret
      else
        eff
      end
    end
  end

  UID.extend xid
  GID.extend xid
end

Truffle::KernelOperations.define_hooked_variable(
  :'$0',
  -> { Primitive.global_variable_get :'$0' },
  -> v {
    v = StringValue(v)
    Process.setproctitle(v)
    Primitive.global_variable_set :'$0', v
  })

alias $PROGRAM_NAME $0
