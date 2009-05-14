#! /bin/sh

# Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

BIN=${TESTJAVA:-../../../../../../build}/bin
SRC=${TESTSRC:-.}

rm -rf z.*
mkdir z.tests
cd z.tests

awk <$tsrc '

  /^:/ {
    test = $2; status = $3; how = $4;
    tdir = sprintf("%03d%s", tcount++, $2);
    system("mkdir -p " tdir);
    if (NF == 3) e = $3; else e = $3 " " $4;
    print e >(tdir "/expected");
    msdir = tdir "/modules";
  }

  /^module / {
    module = $2;
    mname = module;
    mdir = msdir "/" module;
    system("mkdir -p " mdir);
    mfile = mdir "/module-info.java";
    print $0 >mfile;
    next;
  }

  /^package / {
    pkgpre = $0;
    module = 0;
    next;
  }

  /^import / {
    pkgpre = pkgpre "\n" $0;
    next;
  }

  /.* (class|interface) .* *{ *}? *$/ {
    class = gensub(".*class +([A-Za-z]+) +{ *}? *", "\\1", 1);
    cfile = mdir "/" class ".java";
    print "module " mname ";" >cfile
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

failures=0
fail() {
  echo "FAIL: $*"
  failures=$(expr $failures + 1)
}

compile() {
  $BIN/javac -source 7 -d classes $(find modules -name '*.java')
}

install() {
  $BIN/jmod create \
  && $BIN/jmod $VM_FLAGS_INSTALL install classes $(cd modules; echo *)
#  && $BIN/jmod list
}

invoke() {
  if [ -e main ]; then
    $BIN/java $VM_FLAGS -ea org.openjdk.jigsaw.Launcher z.lib $(cat main)
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
  e=$(cat expected)
  [ $tests = 1 ] || echo "-- $test $e"
  mkdir classes
  export JAVA_MODULES=z.lib
  case "$e" in
    'fail compile')
      step compile fail;;
    'fail install')
      step compile pass && step install fail;;
    'fail invoke')
      step compile pass && step install pass && step invoke fail;;
    'pass compile')
      step compile pass;;
    pass)
      step compile pass && step install pass && step invoke pass;;
    *)
      fail "Unhandled expected result: $e";;
  esac
}

for t in *; do
  cd $t
  run $(echo $t | cut -c4-) || /bin/true
  cd ..
done

if [ $tests -gt 1 ]; then
  echo
  echo -n "== $tests test$([ $tests != 1 ] && echo s), "
  echo "$failures failure$([ $failures != 1 ] && echo s)"
fi

exit $failures
