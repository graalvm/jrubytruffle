# Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1

x = Object.new

ObjectSpace.define_finalizer x, -> { exit! 0 }

x = nil

GC.start

sleep 1

y = Object.new

# Defining a new finalizer should cause the old finalizer to run in this thread

ObjectSpace.define_finalizer y, -> { exit! 1 }

exit! 1
