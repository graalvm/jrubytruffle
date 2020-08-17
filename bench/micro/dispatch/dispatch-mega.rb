# Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

classes = []
(2 + Truffle::Boot.get_option('dispatch-cache')).times do |i|
  classes[i] = Class.new do
    class_eval "def call; #{i}; end"
  end
end

callees = classes.cycle.lazy.map(&:new).take(1000).to_a

benchmark 'dispatch-mega' do
  i = 0
  while i < 1000
    callees[i].call
    i += 1
  end
end
