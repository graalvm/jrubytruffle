#!/usr/bin/env ruby

# Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved.
# This code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1

constants = [
    ['Truffle::UNDEFINED', 'undef'],
    true,
    false,
    nil,
    Array,
    Bignum,
    Class,
    Comparable,
    Data,
    Encoding,
    Enumerable,
    FalseClass,
    File,
    Fixnum,
    Float,
    Hash,
    Integer,
    IO,
    Kernel,
    [MatchData, 'Match'],
    Module,
    NilClass,
    Numeric,
    Object,
    Range,
    Regexp,
    String,
    Struct,
    Symbol,
    Time,
    Thread,
    TrueClass,
    Proc,
    Method,
    Dir,
    [ArgumentError, 'ArgError'],
    EOFError,
    Errno,
    Exception,
    FloatDomainError,
    IndexError,
    Interrupt,
    IOError,
    LoadError,
    LocalJumpError,
    [Math::DomainError, 'MathDomainError'],
    [Encoding::CompatibilityError, 'EncCompatError'],
    NameError,
    [NoMemoryError, 'NoMemError'],
    NoMethodError,
    [NotImplementedError, 'NotImpError'],
    RangeError,
    RegexpError,
    RuntimeError,
    ScriptError,
    SecurityError,
    [SignalException, 'Signal'],
    StandardError,
    SyntaxError,
    SystemCallError,
    SystemExit,
    [SystemStackError, 'SysStackError'],
    TypeError,
    ThreadError,
    IO::WaitReadable,
    IO::WaitWritable,
    [ZeroDivisionError, 'ZeroDivError'],
    ['STDIN', 'stdin'],
    ['STDOUT', 'stdout'],
    ['STDERR', 'stderr'],
    ['$,', 'output_fs'],
    ['$/', 'rs'],
    ['$\\', 'output_rs'],
    ['$;', 'default_rs']
].map do |const|
  if const.is_a?(Array)
    value, name = const
  else
    value = const
    
    if value.nil?
      name = 'nil'
    elsif value.is_a?(Module)
      name = value.name.split('::').last
    else
      name = value.to_s
    end
  end
  
  if value.nil?
    expr = 'nil'
  else
    expr = value.to_s
  end
  
  if [true, false, nil].include?(value) or name == 'undef'
    tag = 'Q'
  elsif value.is_a?(Class) && (value < Exception || value == Exception)
    tag = 'rb_e'
  elsif value.is_a?(Class)
    tag = 'rb_c'
  elsif value.is_a?(Module)
    tag = 'rb_m'
  else
    tag = 'rb_'
  end
  
  macro_name = "#{tag}#{name}"
  
  [macro_name, name, expr]
end

File.open("lib/cext/truffle/constants.h", "w") do |f|
  f.puts "// From #{__FILE__}"
  f.puts

  constants.each do |macro_name, name, _|
    f.puts "VALUE rb_tr_get_#{name}(void);" unless macro_name[0] == 'Q'
  end

  f.puts

  constants.each do |macro_name, name, _|
    f.puts "#define #{macro_name} rb_tr_get_#{name}()" unless macro_name[0] == 'Q'
  end
end

constants.each do |macro_name, name, _|
  puts "VALUE rb_tr_get_#{name}(void) {"
  puts "  return (VALUE) polyglot_invoke(RUBY_CEXT, \"#{macro_name}\");"
  puts "}"
  puts
end

puts

constants.each do |macro_name, _, expr|
  puts "  def #{macro_name}"
  puts "    #{expr}"
  puts "  end"
  puts
end
