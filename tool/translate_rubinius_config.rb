#!/usr/bin/env ruby

# Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
# 
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1

=begin
Instructions:
git clone https://github.com/rubinius/rubinius.git
cd rubinius
git checkout v2.71828182
bundle install
./configure --disable-llvm
# If running on Solaris, use the following instead. "-m64" forces a 64-bit binary. "-D_XOPEN_SOURCE=600" tells Solaris to use the SUSv3 feature set. "-std=gnu99" is required to build with SUSv3 enabled.
# CC="gcc -std=gnu99 -m64 -D_XOPEN_SOURCE=600 -D__EXTENSIONS__=1" ./configure --disable-llvm
bundle exec rake runtime/platform.conf
cd ../truffleruby
ruby tool/translate_rubinius_config.rb ../rubinius/runtime/platform.conf
=end

puts "        // Generated from tool/translate_rubinius_config.rb < ../rubinius/runtime/platform.conf"

ARGF.each do |line|
  next unless /^(?<var>rbx(\.\w+)*) = (?<value>.*)$/ =~ line
  code = case value
  when ""
    0
  when /^-?\d+$/
    case Integer(value)
    when (-2**31...2**31)
      value
    when (-2**63...2**63)
      "#{value}L"
    else
      "newBignum(context, \"#{value}\")"
    end
  when "true"
    value
  else
    "string(context, \"#{value}\")"
  end
  puts "        configuration.config(\"#{var}\", #{code});"
end
