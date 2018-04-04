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
  
  it "returns an array" do
    keys = Truffle::Interop.keys({'a' => 1, 'b' => 2, 'c' => 3})
    keys.should be_an_instance_of(Array)
  end
  
  it "returns an array of Ruby strings by default" do
    keys = Truffle::Interop.keys({'a' => 1, 'b' => 2, 'c' => 3})
    keys[0].should be_an_instance_of(String)
  end
  
  it "returns an array of Java strings if you don't use conversion" do
    keys = Truffle::Interop.keys_without_conversion({'a' => 1, 'b' => 2, 'c' => 3})
    key = keys[0]
    Truffle::Interop.java_string?(key).should be_true
  end
  
  it "returns the keys of a hash" do
    Truffle::Interop.keys({'a' => 1, 'b' => 2, 'c' => 3}).should == ['a', 'b', 'c']
  end
  
  it "returns the keys of a hash converted to strings" do
    Truffle::Interop.keys({a: 1, b: 2, c: 3}).should == ['a', 'b', 'c']
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

    it "returns instance variables of an object" do
      Truffle::Interop.keys(TruffleInteropSpecs::InteropKeysClass.new, true).should include('@a', '@b', '@c')
    end
    
  end

end
