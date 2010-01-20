#! /bin/sh

# Copyright 2009-2010 Sun Microsystems, Inc.  All Rights Reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.

set -e

tsrc=$1
case $tsrc in
  /*) ;;
  *)  tsrc=../$tsrc;;
esac

BIN=${TESTJAVA:-../../../../../build}/bin
SRC=${TESTSRC:-.}

rm -rf z.*
mkdir z.test
cd z.test

gawk <$tsrc '

  /^:/ {
    test = $2; status = $3; how = $4;
    tdir = sprintf("%03d%s", tcount++, $2);
    system("mkdir -p " tdir);
    if (NF == 3) e = $3; else e = $3 " " $4;
    print e >(tdir "/expected");
    msdir = tdir "/src";
  }

  /^module / {
    module = $2;
    mname = module;
    mdir = msdir "/" mname;
    system("mkdir -p " mdir);
    mfile = mdir "/module-info.java";
    print $0 >mfile;
    next;
  }

  /^package / {
    pkgpre = $0;
    pdir = mdir "/" gensub("\\.", "/", "g", gensub("(package|;| )", "", "g"));
    system("mkdir -p " pdir);
    module = 0;
    next;
  }

  /^import / {
    pkgpre = pkgpre "\n" $0;
    next;
  }

  /.* (class|interface) .* *{ *}? *$/ {
    class = gensub(".*class +([A-Za-z]+) +{ *}? *", "\\1", 1);
    cfile = pdir  "/" class ".java";
    print pkgpre >>cfile;
    print $0 >>cfile;
    pkgpre = "";
    next;
  }

  /^./ {
    if (module) {
      print $0 >>mfile;
      if (match($0, "^ +class +([a-zA-Z.]+) *;", g))
        print module >(tdir "/main");
    }
    if (class) print $0 >>cfile;
    next;
  }

  /^#?$/ { module = 0; class = 0; }

  /.+/ { if (module || class) print $0; }

'

tests=$(echo * | wc -w)
if [ $tests = 1 ]; then
  d=$(eval 'echo *')
  mv $d/* .
  rmdir $d
fi

failures=0
fail() {
  echo "FAIL: $*"
  failures=$(expr $failures + 1)
}

compile() {
  $BIN/javac -source 7 -d modules -modulepath modules \
    $(find src -name '*.java')
}

install() {
  $BIN/jmod create \
  && $BIN/jmod $VM_FLAGS_INSTALL install modules $(cd modules; echo *)
#  && $BIN/jmod list
}

catfile() {
  tr -d '\r' < $1
}

invoke() {
  if [ -e main ]; then
    $BIN/java $VM_FLAGS -ea -L module-lib -m $(catfile main)
  else
    true
  fi
}

step() {
  if $1; then
    if [ $2 = fail ]; then
      fail $1 did not fail as expected
      return 1
    fi
  else
    if [ $2 = pass ]; then
      fail $1 failed
      return 1
    fi
  fi
}

run() {
  test=$1
  e=$(catfile expected)
  [ $tests = 1 ] || echo "-- $test $e"
  mkdir modules
  export JAVA_MODULES=module-lib
  case "$e" in
    'fail compile')
      step compile fail;;
    'fail install')
      step compile pass && step install fail;;
    'fail invoke')
      step compile pass && step install pass && step invoke fail;;
    'pass setup')
      ;;
    'pass compile')
      step compile pass;;
    pass)
      step compile pass && step install pass && step invoke pass;;
    *)
      fail "Unhandled expected result: $e";;
  esac
}

if [ $tests = 1 ]; then
  run singleton || /bin/true
else
  if [ -z "$TESTSRC" -a "$(echo $BIN | cut -c1-3)" = ../ ]; then
    BIN=../$BIN;
  fi
  for t in *; do
    cd $t
    run $(echo $t | cut -c4-) || /bin/true
    cd ..
  done
fi

if [ $tests -gt 1 ]; then
  echo
  echo -n "== $tests test$([ $tests != 1 ] && echo s), "
  echo "$failures failure$([ $failures != 1 ] && echo s)"
fi

exit $failures
