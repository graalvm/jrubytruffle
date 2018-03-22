# Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
# 
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1

require_relative '../../ruby/spec_helper'
require_relative 'fixtures/classes'

describe "Truffle::Interop.keys" do
  
  it "returns the keys of a hash" do
    Truffle::Interop.keys({'a' => 1, 'b' => 2, 'c' => 3}).should == ['a', 'b', 'c']
  end
  
  it "returns the keys of a hash converted to strings" do
    Truffle::Interop.keys({a: 1, b: 2, c: 3}).should == ['a', 'b', 'c']
  end

  it "returns the indices of an array" do
    array = [1, 2, 3]
    Truffle::Interop.keys([1, 2, 3]).should == [0, 1, 2]
  end
  
  it "returns the methods of an object" do
    object = Object.new
    Truffle::Interop.keys(object).should include(*object.methods.map(&:to_s))
  end
  
  it "returns the methods of a user-defined method" do
    object = TruffleInteropSpecs::InteropKeysClass.new
    Truffle::Interop.keys(object).should include(*object.methods.map(&:to_s))
  end
  
  it "returns a user-defined method" do
    object = TruffleInteropSpecs::InteropKeysClass.new
    Truffle::Interop.keys(object).should include('foo')
  end
  
  describe "without internal set" do
    
    it "does not return the instance variables of a hash" do
      hash = {a: 1, b: 2, c: 3}
      hash.instance_variable_set(:@foo, 14)
      Truffle::Interop.keys(hash, true).should_not include('@foo')
    end

    it "does not return the instance variables of an array" do
      array = [1, 2, 3]
      array.instance_variable_set(:@foo, 14)
      Truffle::Interop.keys(array, true).should_not include('@foo')
    end
    
    it "does not return instance variables of an object" do
      Truffle::Interop.keys(TruffleInteropSpecs::InteropKeysClass.new).should_not include('@a', '@b', '@c')
    end
    
  end
  
  describe "with internal set" do
    
    it "does not return the instance variables of a hash" do
      hash = {a: 1, b: 2, c: 3}
      hash.instance_variable_set(:@foo, 14)
      Truffle::Interop.keys(hash, true).should_not include('@foo')
    end

    it "does not return the instance variables of an array" do
      array = [1, 2, 3]
      array.instance_variable_set(:@foo, 14)
      Truffle::Interop.keys(array, true).should_not include('@foo')
    end

    it "returns instance variables of an object" do
      Truffle::Interop.keys(TruffleInteropSpecs::InteropKeysClass.new, true).should include('@a', '@b', '@c')
    end
    
  end

end
