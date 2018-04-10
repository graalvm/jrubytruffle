
# Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1

require_relative '../../ruby/spec_helper'

guard -> { !Truffle.native? } do
  describe "Truffle::Interop.java_type" do

    it "returns a Java class for a known primitive name" do
      Truffle::Interop.java_type_name(Truffle::Interop.java_type("int")).should == "int"
    end

    it "returns a Java class for known primitive name as an array" do
      Truffle::Interop.java_type_name(Truffle::Interop.java_type("int[]")).should == "[I"
    end

    it "returns a Java class for a known class name " do
      Truffle::Interop.java_type_name(Truffle::Interop.java_type("java.math.BigInteger")).should == "java.math.BigInteger"
    end

    it "returns a Java class for known class name as an array" do
      Truffle::Interop.java_type_name(Truffle::Interop.java_type("java.math.BigInteger[]")).should == "[Ljava.math.BigInteger;"
    end

    it "throws RuntimeError for unknown class names" do
      lambda { Truffle::Interop.java_type("does.not.Exist") }.should raise_error(RuntimeError)
    end

    it "works with symbols" do
      Truffle::Interop.java_type_name(Truffle::Interop.java_type(:int)).should == "int"
    end

  end
end
