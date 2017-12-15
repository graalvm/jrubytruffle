# Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1

def glob(pattern)
  files = Dir.glob(pattern)
  raise "no libraries found with #{pattern}" if files.empty?
  files
end

stdlibs = glob('lib/mri/*.{rb,su}').map { |file|
  File.basename(file, '.*')
}

glob('lib/truffle/*.rb').map { |file|
  lib = File.basename(file, '.*')
  stdlibs << lib unless lib.end_with? '-stubs'
}

glob('lib/mri/net/*.rb').map { |file| File.basename(file, '.*') }.each { |file|
  stdlibs << "net/#{file}"
}

glob('lib/rubysl/*.rb') { |file|
  lib = File.basename(file, '.*')
  stdlibs << lib
}

stdlibs += %w[json]

ignore = %w[continuation debug mathn profile psych_jars shell]

stdlibs -= ignore

stdlibs.uniq!

stdlibs.each { |lib| require lib }

puts 3 * 4
