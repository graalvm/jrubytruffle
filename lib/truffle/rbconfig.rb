# Copyright (c) 2014, 2017 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1
#
# Copyright (C) 1993-2013 Yukihiro Matsumoto. All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions
# are met:
# 1. Redistributions of source code must retain the above copyright
# notice, this list of conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright
# notice, this list of conditions and the following disclaimer in the
# documentation and/or other materials provided with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
# ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
# ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
# OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
# HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
# LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
# OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
# SUCH DAMAGE.
#
#  RbConfig.expand method imported from MRI sources
#

module RbConfig

  host_os  = Truffle::System.host_os
  host_cpu = Truffle::System.host_cpu
  host_vendor = 'unknown'
  # Some config entries report linux-gnu rather than simply linux.
  if host_os == 'linux'
    host_os_full = 'linux-gnu'
  else
    host_os_full = host_os
  end
  # Host should match the target triplet for clang or GCC, otherwise some C extension builds will fail.
  host         = "#{host_cpu}-#{host_vendor}-#{host_os_full}"

  ruby_install_name = 'truffleruby'

  ruby_base_name = 'ruby'
  ruby_version   = Truffle::RUBY_BASE_VERSION

  arch     = "#{host_cpu}-#{host_os}"
  cppflags = ''
  libs     = ''

  # Sorted alphabetically using sort(1)
  CONFIG = {
    'arch'              => arch,
    'ARCH_FLAG'         => '',
    'build'             => host,
    'build_os'          => host_os_full,
    'configure_args'    => ' ',
    'CPPFLAGS'          => cppflags,
    'CPPOUTFILE'        => '-o conftest.i',
    'DLDFLAGS'          => '',
    'DLDLIBS'           => '',
    'DLEXT'             => 'su',
    'ENABLE_SHARED'     => 'yes',
    'EXECUTABLE_EXTS'   => '',
    'exeext'            => '',
    'EXEEXT'            => '',
    'host_alias'        => '',
    'host_cpu'          => host_cpu,
    'host'              => host,
    'host_os'           => host_os_full,
    'includedir'        => '',
    'LDFLAGS'           => '',
    'libdirname'        => 'libdir',
    'LIBEXT'            => 'a',
    'LIBRUBY'           => '',
    'LIBRUBY_A'         => '',
    'LIBRUBYARG'        => '',
    'LIBRUBYARG_SHARED' => '',
    'LIBRUBYARG_STATIC' => '',
    'LIBRUBY_SO'        => 'cext/ruby.su',
    'LIBS'              => libs,
    'NATIVE_DLEXT'      => Truffle::Platform::NATIVE_DLEXT,
    'NULLCMD'           => ':',
    'OBJEXT'            => 'bc',
    'optflags'          => '',
    'PATH_SEPARATOR'    => File::PATH_SEPARATOR,
    'prefix'            => '',
    'RM'                => 'rm -f',
    'RUBY_BASE_NAME'    => ruby_base_name,
    'ruby_install_name' => ruby_install_name,
    'RUBY_INSTALL_NAME' => ruby_install_name,
    'ruby_version'      => ruby_version,
    'target_cpu'        => host_cpu,
    'target_os'         => host_os,
  }

  MAKEFILE_CONFIG = CONFIG.dup

  expanded = CONFIG
  mkconfig = MAKEFILE_CONFIG

  expanded['RUBY_SO_NAME'] = ruby_base_name
  mkconfig['RUBY_SO_NAME'] = '$(RUBY_BASE_NAME)'

  ruby_home = Truffle::Boot.ruby_home

  if ruby_home
    prefix        = ruby_home
    bindir        = "#{prefix}/bin"
    graalvm_home = Truffle::System.get_java_property 'org.graalvm.home'
    extra_bindirs = if graalvm_home
                      graalvm_home = Truffle::System.get_java_property 'org.graalvm.home'
                      [File.expand_path('bin', graalvm_home),
                       File.expand_path('jre/bin', graalvm_home)]
                    else
                      []
                    end

    common = {
      'prefix' => prefix,
      'bindir' => bindir,
      'extra_bindirs' => extra_bindirs,
      'hdrdir' => "#{prefix}/lib/cext/include",
      'rubyhdrdir' => "#{prefix}/lib/cext/include",
      'rubyarchhdrdir' => "#{prefix}/lib/cext/include",
    }
    expanded.merge!(common)
    mkconfig.merge!(common)

    exec_prefix = \
    expanded['exec_prefix'] = prefix
    mkconfig['exec_prefix'] = '$(prefix)'
    libdir = \
    expanded['libdir'] = "#{exec_prefix}/lib"
    mkconfig['libdir'] = '$(exec_prefix)/lib'
    rubylibprefix = \
    expanded['rubylibprefix'] = "#{libdir}/#{ruby_base_name}"
    mkconfig['rubylibprefix'] = '$(libdir)/$(RUBY_BASE_NAME)'
    rubylibdir = \
    expanded['rubylibdir'] = "#{rubylibprefix}/#{ruby_version}"
    mkconfig['rubylibdir'] = '$(rubylibprefix)/$(ruby_version)'
    rubyarchdir = \
    expanded['rubyarchdir'] = "#{rubylibdir}/#{arch}"
    mkconfig['rubyarchdir'] = '$(rubylibdir)/$(arch)'
    archdir = \
    expanded['archdir'] = rubyarchdir
    mkconfig['archdir'] = '$(rubyarchdir)'
    sitearch = \
    expanded['sitearch'] = arch
    mkconfig['sitearch'] = '$(arch)'
    sitedir = \
    expanded['sitedir'] = "#{rubylibprefix}/site_ruby"
    mkconfig['sitedir'] = '$(rubylibprefix)/site_ruby'
    sitelibdir = \
    expanded['sitelibdir'] = "#{sitedir}/#{ruby_version}"
    mkconfig['sitelibdir'] = '$(sitedir)/$(ruby_version)'
    expanded['sitearchdir'] = "#{sitelibdir}/#{sitearch}"
    mkconfig['sitearchdir'] = '$(sitelibdir)/$(sitearch)'
    expanded['topdir'] = archdir
    mkconfig['topdir'] = '$(archdir)'
    datarootdir = \
    expanded['datarootdir'] = "#{prefix}/share"
    mkconfig['datarootdir'] = '$(prefix)/share'
    expanded['ridir'] = "#{datarootdir}/ri"
    mkconfig['ridir'] = '$(datarootdir)/ri'
  end

  launcher = Truffle::Boot.ruby_launcher
  unless launcher
    if ruby_home
      launcher = "#{bindir}/#{ruby_install_name}"
    else
      launcher = ruby_install_name
    end
  end

  if ruby_home
    expanded['LDSHARED'] = "#{launcher} #{libdir}/cext/ldshared.rb"
  end

  RUBY = launcher

  def self.ruby
    RUBY
  end

  def RbConfig.expand(val, config = CONFIG)
    newval = val.gsub(/\$\$|\$\(([^()]+)\)|\$\{([^{}]+)\}/) do
      var = $&
      if !(v = $1 || $2)
        '$'
      elsif key = config[v = v[/\A[^:]+(?=(?::(.*?)=(.*))?\z)/]]
        pat, sub  = $1, $2
        config[v] = false
        config[v] = RbConfig.expand(key, config)
        key       = key.gsub(/#{Regexp.quote(pat)}(?=\s|\z)/n) {sub} if pat
        key
      else
        var
      end
    end
    val.replace(newval) unless newval == val
    val
  end

end

CROSS_COMPILING = nil
