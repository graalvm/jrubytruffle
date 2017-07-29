#!/usr/bin/env ruby
# Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved.
# This code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1

require 'json'

result = JSON.parse(File.read(ARGV[0]))
wait = ARGV[1] == '--wait'

failures = []

known_failures = [
  # OOM in flattenBytes
  ["server", "graal-core", "jruby", "truffle", "micro", "micro/core/file.rb:core-read-gigabyte"],
  ["server", "graal-enterprise", "jruby", "truffle", "micro", "micro/core/file.rb:core-read-gigabyte"],

  # C-exts
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-color-r"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-color-g"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-color-b"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-color-a"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-color-compose-quick"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-canvas-resampling-bilinear"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-canvas-resampling-nearest-neighbor"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-canvas-resampling-steps-residues"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-canvas-resampling-steps"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-decode-png-image-pass"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-encode-png-image-pass-to-stream"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-operations-compose"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-operations-replace"],

  # SVM (GR-5089)
  ["svm", "default", "jruby", "truffle", "classic", "deltablue"],
  ["svm", "default", "jruby", "truffle", "classic", "red-black"],
  ["svm", "default", "jruby", "truffle", "chunky", "chunky-decode-png-image-pass"],
  # SVM
  ["svm", "default", "jruby", "truffle", "asciidoctor", "asciidoctor:file-lines"],
  ["svm", "default", "jruby", "truffle", "asciidoctor", "asciidoctor:load-string"],
  ["svm", "default", "jruby", "truffle", "asciidoctor", "asciidoctor:load-file"],
  ["svm", "default", "jruby", "truffle", "asciidoctor", "asciidoctor:string-lines"],
  ["svm", "default", "jruby", "truffle", "classic", "binary-trees"],
  ["svm", "default", "jruby", "truffle", "psd", "psd-compose-color-burn"],
  ["svm", "default", "jruby", "truffle", "psd", "psd-compose-color-dodge"],
  ["svm", "default", "jruby", "truffle", "psd", "psd-compose-exclusion"],
  ["svm", "default", "jruby", "truffle", "psd", "psd-compose-hard-light"],
  ["svm", "default", "jruby", "truffle", "psd", "psd-compose-lighten"],
  ["svm", "default", "jruby", "truffle", "psd", "psd-compose-linear-burn"],
  ["svm", "default", "jruby", "truffle", "psd", "psd-compose-multiply"],
  ["svm", "default", "jruby", "truffle", "psd", "psd-compose-normal"],
  ["svm", "default", "jruby", "truffle", "psd", "psd-compose-overlay"],
  ["svm", "default", "jruby", "truffle", "psd", "psd-compose-screen"],
  ["svm", "default", "jruby", "truffle", "psd", "psd-compose-vivid-light"],
  ["svm", "default", "jruby", "truffle", "psd", "psd-imagemode-cmyk-combine-cmyk-channel"],
  ["svm", "default", "jruby", "truffle", "psd", "psd-renderer-blender-compose"],
  ["svm", "default", "jruby", "truffle", "savina", "savina-radix-sort"],
  ["svm", "default", "jruby", "truffle", "server", "tcp-server"],
  ["svm", "default", "jruby", "truffle", "server", "webrick"],
]

if File.exist?('failures')
  failures = File.read('failures').split("\n").map { |failure| eval(failure) }
else
  failures = []
end

(result['queries'] || []).any? do |q|
  if q['error'] == 'failed'
    failures.push [q['host-vm'], q['host-vm-config'], q['guest-vm'], q['guest-vm-config'], q['bench-suite'], q['benchmark']]
  end
end

known_failures.each do |known_failure|
  if failures.delete(known_failure)
    STDERR.puts "#{known_failure.inspect} failed, but we know about that"
  end
end

if wait
  if !failures.empty?
    STDERR.puts 'waiting to return failure...'
    File.write('failures', failures.map(&:inspect).join("\n"))
  end
else
  if !failures.empty? || File.exist?('failures')
    STDERR.puts 'these failed:'
    failures.each do |failure|
      STDERR.puts failure.inspect
    end
    exit 1
  end
end
