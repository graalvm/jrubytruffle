# Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
# 
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1

require_relative '../../ruby/spec_helper'

describe "Truffle::Interop.invoke" do
  
  class InvokeTestClass
    
    def add(a, b)
      a + b
    end
    
  end
  
  it "invokes methods using symbols" do
    Truffle::Interop.invoke(InvokeTestClass.new, :add, 14, 2).should == 16
  end
  
  it "invokes methods using strings" do
    Truffle::Interop.invoke(InvokeTestClass.new, 'add', 14, 2).should == 16
  end

  it "raises a NoMethodError when the method is not found on a foreign object" do
    foreign = Truffle::Interop.java_array(1, 2, 3)
    lambda { foreign.foo(42) }.should raise_error(NoMethodError, /Unknown identifier: foo/) { |e|
      e.receiver.equal?(foreign).should == true
      e.name.should == :foo
      e.args.should == [42]
    }
  end

end
