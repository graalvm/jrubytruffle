# Copyright (c) 2016, 2019 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

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

  it "returns an array of public methods for an array" do
    Truffle::Interop.keys([1, 2, 3]).should include(*([].public_methods.map(&:to_s)))
  end

  it "returns an empty array for a big integer" do
    Truffle::Interop.keys(bignum_value).should == []
  end

  it "returns an empty array for a proc" do
    Truffle::Interop.keys(proc {}).should == []
  end

  it "returns an empty array for a lambda" do
    Truffle::Interop.keys(-> {}).should == []
  end

  it "returns an empty array for a method" do
    object = TruffleInteropSpecs::ReadHasMethod.new
    method = object.method(:foo)
    Truffle::Interop.keys(method).should == []
  end

  it "returns an empty array for an object with a custom #[] method" do
    object = TruffleInteropSpecs::ReadHasIndex.new
    Truffle::Interop.keys(object).should == []
  end

  it "does not return the keys of a hash" do
    Truffle::Interop.keys({'a' => 1, 'b' => 2, 'c' => 3}).should_not include('a', 'b', 'c')
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

    it "does return the instance variables of a hash" do
      hash = {a: 1, b: 2, c: 3}
      hash.instance_variable_set(:@foo, 14)
      Truffle::Interop.keys(hash, true).should include('@foo')
    end

    it "does not return instance variables of an object" do
      Truffle::Interop.keys(TruffleInteropSpecs::InteropKeysClass.new).should_not include('@a', '@b', '@c')
    end

  end

  describe "with internal set" do

    it "does return the instance variables of a hash" do
      hash = {a: 1, b: 2, c: 3}
      hash.instance_variable_set(:@foo, 14)
      Truffle::Interop.keys(hash, true).should include('@foo')
    end

    it "returns instance variables of an object" do
      Truffle::Interop.keys(TruffleInteropSpecs::InteropKeysClass.new, true).should include('@a', '@b', '@c')
    end

  end

end
