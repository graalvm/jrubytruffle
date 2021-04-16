# frozen_string_literal: true

# Copyright (c) 2017, 2020 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle
  module RegexpOperations

    def self.search_region(re, str, start_index, end_index, forward)
      raise TypeError, 'uninitialized regexp' unless Primitive.regexp_initialized?(re)
      raise ArgumentError, "invalid byte sequence in #{str.encoding}" unless str.valid_encoding?
      Primitive.encoding_ensure_compatible(re, str)

      if forward
        from = start_index
        to = end_index
      else
        from = end_index
        to = start_index
      end
      match_in_region(re, str, from, to, false, true, 0)
    end

    # This path is used by some string and scanner methods and allows
    # for at_start to be specified on the matcher.  FIXME it might be
    # possible to refactor search region to offer the ability to
    # specify at start, we should investigate this at some point.
    def self.match_onwards(re, str, from, at_start)
      md = match_in_region(re, str, from, str.bytesize, at_start, true, from)
      Primitive.matchdata_fixup_positions(md, from) if md
      md
    end

    def self.match(re, str, pos=0)
      return nil unless str

      str = str.to_s if str.is_a?(Symbol)
      str = StringValue(str)

      pos = pos < 0 ? pos + str.size : pos
      pos = Primitive.string_byte_index_from_char_index(str, pos)
      search_region(re, str, pos, str.bytesize, true)
    end

    def self.match_from(re, str, pos)
      return nil unless str

      search_region(re, str, pos, str.bytesize, true)
    end

    Truffle::Boot.delay do
      COMPARE_ENGINES = Truffle::Boot.get_option('compare-regex-engines')
      USE_TRUFFLE_REGEX = Truffle::Boot.get_option('use-truffle-regex')

      if Truffle::Boot.get_option('regexp-instrument-creation') || Truffle::Boot.get_option('regexp-instrument-match')
        at_exit do
          Truffle::RegexpOperations.print_stats
        end
      end
    end

    def self.match_in_region(re, str, from, to, at_start, encoding_conversion, start)
      if COMPARE_ENGINES
        match_in_region_compare_engines(re, str, from, to, at_start, encoding_conversion, start)
      elsif USE_TRUFFLE_REGEX
        match_in_region_tregex(re, str, from, to, at_start, encoding_conversion, start)
      else
        Primitive.regexp_match_in_region(re, str, from, to, at_start, encoding_conversion, start)
      end
    end

    def self.match_in_region_compare_engines(re, str, from, to, at_start, encoding_conversion, start)
      begin
        md1 = match_in_region_tregex(re, str, from, to, at_start, encoding_conversion, start)
      rescue => e
        md1 = e
      end
      begin
        md2 = Primitive.regexp_match_in_region(re, str, from, to, at_start, encoding_conversion, start)
      rescue => e
        md2 = e
      end
      if self.results_match?(md1, md2)
        return self.return_match_data(md1)
      else
        $stderr.puts "match_in_region(/#{re}/, \"#{str}\"@#{str.encoding}, #{from}, #{to}, #{at_start}, #{encoding_conversion}, #{start}) gate"
        self.print_match_data(md1)
        $stderr.puts 'but we expected'
        self.print_match_data(md2)
        return self.return_match_data(md2)
      end
    end

    def self.match_in_region_tregex(re, str, from, to, at_start, encoding_conversion, start)
      bail_out = to < from || to != str.bytesize || start != 0 || from < 0
      if !bail_out
        compiled_regex = tregex_compile(re, at_start, select_encoding(re, str, encoding_conversion))
        bail_out = compiled_regex.nil?
      end
      if !bail_out
        str_bytes = StringOperations.raw_bytes(str)
        regex_result = compiled_regex.execBytes(str_bytes, from)
      end
      if bail_out
        return Primitive.regexp_match_in_region(re, str, from, to, at_start, encoding_conversion, start)
      elsif regex_result.isMatch
        starts = []
        ends = []
        compiled_regex.groupCount.times do |pos|
          starts << regex_result.getStart(pos)
          ends << regex_result.getEnd(pos)
        end
        Primitive.matchdata_create(re, str.dup, starts, ends)
      else
        nil
      end
    end

    def self.results_match?(md1, md2)
      if md1 == nil
        md2 == nil
      elsif md2 == nil
        false
      elsif md1.kind_of?(Exception)
        md1.class == md2.class
      elsif md2.kind_of?(Exception)
        false
      else
        if md1.size != md2.size
          return false
        end
        md1.size.times do |x|
          if md1.begin(x) != md2.begin(x) || md1.end(x) != md2.end(x)
            return false
          end
        end
        true
      end
    end

    def self.print_match_data(md)
      if md == nil
        $stderr.puts '    NO MATCH'
      elsif md.kind_of?(Exception)
        $stderr.puts "    EXCEPTION - #{md}"
      else
        md.size.times do |x|
          $stderr.puts "    #{md.begin(x)} - #{md.end(x)}"
        end
        md.captures.each do |c|
          $stderr.puts "    #{c}"
        end
      end
    end

    def self.return_match_data(md)
      if md.kind_of?(Exception)
        raise md
      else
        md
      end
    end

    def self.compilation_stats
      Hash[*compilation_stats_array]
    end

    def self.match_stats
      Hash[*match_stats_array]
    end

    def self.print_stats
      puts '--------------------'
      puts 'Regular expression statistics'
      puts '--------------------'
      puts '  Compilation'
      print_stats_table compilation_stats
      puts '  --------------------'
      puts '  Matches'
      print_stats_table match_stats
      puts '--------------------'
    end

    def self.print_stats_table(table)
      return if table.empty?
      sorted = table.to_a.sort_by(&:last).reverse
      width = sorted.first.last.to_s.size
      sorted.each do |regexp, count|
        printf "    %#{width}d    %s\n", count, regexp
      end
    end

    def self.option_to_string(option)
      string = +''
      string << 'm' if (option & Regexp::MULTILINE) > 0
      string << 'i' if (option & Regexp::IGNORECASE) > 0
      string << 'x' if (option & Regexp::EXTENDED) > 0
      string
    end

    def self.collapsing?(match)
      Primitive.match_data_byte_begin(match, 0) == Primitive.match_data_byte_end(match, 0)
    end

    def self.pre_match_from(match, idx)
      source = Primitive.match_data_get_source(match)
      match_byte_begin = Primitive.match_data_byte_begin(match, 0)
      return source.byteslice(0, 0) if match_byte_begin == 0

      nd = match_byte_begin - 1
      source.byteslice(idx, nd-idx+1)
    end
  end
end
