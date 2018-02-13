# Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1

# Copyright (c) 2007-2015, Evan Phoenix and contributors
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# * Redistributions of source code must retain the above copyright notice, this
#   list of conditions and the following disclaimer.
# * Redistributions in binary form must reproduce the above copyright notice
#   this list of conditions and the following disclaimer in the documentation
#   and/or other materials provided with the distribution.
# * Neither the name of Rubinius nor the names of its contributors
#   may be used to endorse or promote products derived from this software
#   without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

class Encoding
  class << self
    def build_encoding_map
      map = {}
      Encoding.list.each_with_index do |encoding, index|
        key = encoding.name.upcase.to_sym
        map[key] = [nil, index]
      end

      Truffle::EncodingOperations.each_alias do |alias_name, index|
        key = alias_name.upcase.to_sym
        map[key] = [alias_name, index]
      end

      # Note the locale Encoding is patched below,
      # so modify that code too if you change the code here.
      %w[internal external locale filesystem].each do |name|
        key = name.upcase.to_sym
        enc = Truffle::EncodingOperations.get_default_encoding(name)
        index = enc ? map[enc.name.upcase.to_sym].last : nil
        map[key] = [name, index]
      end
      map
    end
    private :build_encoding_map
  end

  EncodingMap = build_encoding_map

  if Truffle::Boot.preinitializing?
    # Update for the new locale Encoding
    Truffle::Boot.delay do
      enc = Truffle::EncodingOperations.get_default_encoding('locale')
      enc_name = enc == Encoding::UTF_8 ? :'UTF-8' : enc.name.upcase.to_sym
      index = EncodingMap[enc_name].last
      EncodingMap[:LOCALE] = ['locale', index]
    end
  end

  @default_external = Truffle::EncodingOperations.get_default_encoding('external')
  @default_internal = Truffle::EncodingOperations.get_default_encoding('internal')

  Truffle::Boot.delay do
    FILESYSTEM = Truffle::EncodingOperations.get_default_encoding('filesystem')
  end

  def self.aliases
    aliases = {}
    EncodingMap.each do |_n, r|
      index = r.last
      next unless index

      aname = r.first
      aliases[aname] = Truffle.invoke_primitive(:encoding_get_encoding_by_index, index).name if aname
    end

    aliases
  end

  def self.set_alias_index(name, obj)
    key = name.upcase.to_sym

    case obj
    when Encoding
      source_name = obj.name
    when nil
      EncodingMap[key][1] = nil
      return
    else
      source_name = StringValue(obj)
    end

    entry = EncodingMap[source_name.upcase.to_sym]
    raise ArgumentError, "unknown encoding name - #{source_name}" unless entry
    index = entry.last

    EncodingMap[key][1] = index
  end
  private_class_method :set_alias_index

  def self.default_external
    @default_external
  end

  def self.default_external=(enc)
    raise ArgumentError, 'default external encoding cannot be nil' if enc.nil?

    enc = find(enc)
    set_alias_index 'external', enc
    set_alias_index 'filesystem', enc
    @default_external = enc
    Truffle::EncodingOperations.default_external = enc
  end

  def self.default_internal
    @default_internal
  end

  def self.default_internal=(enc)
    enc = find(enc) unless enc.nil?
    set_alias_index 'internal', enc
    @default_internal = enc
    Truffle::EncodingOperations.default_internal = enc
  end

  def self.try_convert(obj)
    case obj
    when Encoding
      return obj
    when String
      str = obj
    else
      str = StringValue obj
    end

    key = str.upcase.to_sym

    pair = EncodingMap[key]
    if pair
      index = pair.last
      return index && Truffle.invoke_primitive(:encoding_get_encoding_by_index, index)
    end

    false
  end

  def self.find(name)
    enc = try_convert(name)
    return enc unless false == enc

    raise ArgumentError, "unknown encoding name - #{name}"
  end

  def self.name_list
    EncodingMap.map do |_n, r|
      index = r.last
      r.first or (index and Truffle.invoke_primitive(:encoding_get_encoding_by_index, index).name)
    end
  end

  def inspect
    "#<Encoding:#{name}#{" (dummy)" if dummy?}>"
  end

  def names
    entry = EncodingMap[name.upcase.to_sym]
    names = [name]
    EncodingMap.each do |_k, r|
      aname = r.first
      names << aname if aname and r.last == entry.last
    end
    names
  end

  def replicate(name)
    name = StringValue(name)
    new_encoding, index = Truffle.invoke_primitive :encoding_replicate, self, name
    EncodingMap[name.upcase.to_sym] = [nil, index]
    new_encoding
  end

  def _dump(depth)
    name
  end

  def self._load(name)
    find name
  end
end
