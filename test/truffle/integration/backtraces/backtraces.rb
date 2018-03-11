# Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1

def check(file)
  dir = 'test/truffle/integration/backtraces'

  expected = File.open("#{dir}/#{file}") do |f|
    f.lines.map(&:chomp)
  end

  begin
    yield
  rescue Exception => exception
    actual = exception.full_message.lines.map(&:chomp)
  end

  while actual.size < expected.size
    actual.push '(missing)'
  end

  while expected.size < actual.size
    expected.push '(missing)'
  end

  success = true

  actual = actual.map { |line|
    line.sub(File.expand_path(dir), '')
        .sub(dir, '')
  }

  print = []
  expected.zip(actual).each do |e, a|
    unless a == e
      print << "Actual:   #{a}"
      print << "Expected: #{e}"
      success = false
    else
      print << ". #{a}"
    end
  end

  unless success
    puts 'Full actual backtrace:'
    puts actual
    puts

    puts 'Full expected backtrace:'
    puts expected
    puts

    puts print.join("\n")
    exit 1
  end
end
