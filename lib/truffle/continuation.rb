# Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1

warn "#{File.basename(__FILE__)}: warning: callcc is obsolete; use Fiber instead"

class Continuation
  def initialize
    @fiber = Fiber.current
  end

  def call
    if Fiber.current != @fiber
      raise 'continuation called across fiber'
    end
    raise 'Continuations are unsupported on TruffleRuby'
  end
end

module Kernel
  def callcc
    yield Continuation.new
  end
  module_function :callcc
end
