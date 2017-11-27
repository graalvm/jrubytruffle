#!/usr/bin/env bash

source test/truffle/common.sh.inc

# Disable fast-fail to get more useful output
set +e
# Disable debug output by Bash as it creates extra output in the output file
set +x

function check() {
  if [ "$1" -ne 0 ]; then
    echo Command failed with $1
    echo Output:
    cat temp.txt
    exit 1
  fi

  if ! cmp --silent temp.txt test/truffle/integration/no_extra_output/twelve.txt
  then
    echo Extra output
    cat temp.txt
    exit 1
  else
    rm -f temp.txt
  fi
}

echo "Basic test of the output"

jt ruby --no-print-cmd -e 'puts 3*4' 1>temp.txt 2>&1
check $?

echo "Basic test of the output with lazy options disabled"

jt ruby --no-print-cmd -Xlazy.default=false -e 'puts 3*4' 1>temp.txt 2>&1
check $?

echo "Test loading many standard libraries"

jt ruby --no-print-cmd -Xlazy.default=false test/truffle/integration/no_extra_output/all_stdlibs.rb 1>temp.txt 2>&1
check $?
