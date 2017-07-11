# Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1
# OTHER DEALINGS IN THE SOFTWARE.

require_relative '../ruby/spec_helper'

describe "The launcher" do

  before :each do
    @java_opts = ENV["JAVA_OPTS"]
  end

  after :each do
    ENV["JAVA_OPTS"] = @java_opts
  end

  it "runs when symlinked" do
    require "tmpdir"
    Dir.mktmpdir do |path|
      Dir.chdir(path) do
        `ln -s #{RbConfig.ruby} linktoruby`
        `./linktoruby --version 2>&1`.should include("truffleruby")
        $?.success?.should == true
      end
    end
  end

  def should_print_full_java_command(cmd)
    out   = `#{cmd}`
    parts = out.split(' ')
    parts[0].should == "$"
    parts[1].should =~ /(java|graalvm)$/
    $?.success?.should == true
  end

  it "prints the full java command with -J-cmd" do
    should_print_full_java_command "#{RbConfig.ruby} -J-cmd --version"
  end

  it "prints the full java command with -cmd in JAVA_OPTS" do
    should_print_full_java_command "JAVA_OPTS=-cmd #{RbConfig.ruby} --version"
  end

  it "adds options from $JAVA_OPTS to the command" do
    option = '-Dfoo.bar=baz'
    ENV["JAVA_OPTS"] = option
    out = `#{RbConfig.ruby} -J-cmd --version`
    parts = out.lines[0].split(' ')
    parts.find { |part| part =~ /^(-J:)?#{option}$/ }.should_not be_nil
    $?.success?.should == true
  end

  it "preserve spaces in options" do
    out = `#{RbConfig.ruby} -Xgraal.warn_unless=false -J-Dfoo="value with spaces" -e "print Truffle::System.get_java_property('foo')"`
    $?.success?.should == true
    out.should == "value with spaces"
  end

  it "warns when not using Graal" do
    out                 = `#{RbConfig.ruby} -e 'p graal: Truffle.graal?' 2>&1`
    on_graal            = out.lines.include? "{:graal=>true}\n"
    performance_warning = "[ruby] PERFORMANCE this JVM does not have the Graal compiler - performance will be limited - see doc/user/using-graalvm.md\n"
    if on_graal
      out.lines.should_not include performance_warning
    else
      out.lines.should include performance_warning
    end
  end

  it "takes options from TRUFFLERUBYOPT" do
    out = `TRUFFLERUBYOPT=-W2 #{RbConfig.ruby} -e 'puts $VERBOSE'`
    $?.success?.should == true
    out.should == "true\n"
  end

  it "takes options from RUBYOPT" do
    out = `RUBYOPT=-W2 #{RbConfig.ruby} -e 'puts $VERBOSE'`
    $?.success?.should == true
    out.should == "true\n"
  end

  it "takes options from system properties set in JAVA_OPTS" do
    out = `JAVA_OPTS=-Dpolyglot.ruby.verbosity=2 #{RbConfig.ruby} -e 'puts $VERBOSE'`
    $?.success?.should == true
    out.should == "true\n"
  end

  it "takes options from system properties set on the command line using -J" do
    out = `#{RbConfig.ruby} -J-Dpolyglot.ruby.verbosity=2 -e 'puts $VERBOSE'`
    $?.success?.should == true
    out.should == "true\n"
  end

  it "takes options from system properties set on the command line using -X" do
    out = `#{RbConfig.ruby} -Xverbosity=2 -e 'puts $VERBOSE'`
    $?.success?.should == true
    out.should == "true\n"
  end

  it "prioritises options on the command line over system properties" do
    out = `JAVA_OPTS=-Dpolyglot.ruby.verbosity=0 #{RbConfig.ruby} -W2 -e 'puts $VERBOSE'`
    $?.success?.should == true
    out.should == "true\n"
  end

  it "prioritises options on the command line using -X over system properties" do
    out = `JAVA_OPTS=-Dpolyglot.ruby.verbosity=0 #{RbConfig.ruby} -Xverbosity=2 -e 'puts $VERBOSE'`
    $?.success?.should == true
    out.should == "true\n"
  end

  it "allows -cp in JAVA_OPTS" do
    out = `JAVA_OPTS='-cp does-not-exist.jar' #{RbConfig.ruby} -J-cmd -e 'puts 14'`
    $?.success?.should == true
    out.lines[0].should include(":does-not-exist.jar")
    out.lines[1].should == "14\n"
  end

  it "allows -classpath in JAVA_OPTS" do
    out = `JAVA_OPTS='-classpath does-not-exist.jar' #{RbConfig.ruby} -J-cmd -e 'puts 14'`
    $?.success?.should == true
    out.lines[0].should include(":does-not-exist.jar")
    out.lines[1].should == "14\n"
  end

  it "doesn't include the polyglot.ruby prefix in the list of options" do
    out = `#{RbConfig.ruby} -Xoptions`
    $?.success?.should == true
    out.should_not include("polyglot.ruby")
  end

  it "logs options if -Xoptions.log=true is set" do
    out = `#{RbConfig.ruby} -Xoptions.log=true -Xlog=config -e 14 2>&1`
    $?.success?.should == true
    out.should include("CONFIG option home=")
  end

end
