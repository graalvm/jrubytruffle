# Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1

require_relative 'backtraces'

def meth_with_inlined_plus(n)
  noop = 42
  n + 2
end

check('inlined_method.backtrace') do
  meth_with_inlined_plus(nil)
end
