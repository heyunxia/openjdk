#! /bin/sh

# Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.

OS=`uname -s`
DASH_P="-p"
# gawk is not available on Solaris and Windows
# set to the proper nawk/awk/gawk 
case "$OS" in
  SunOS )
    PS=":"
    FS="/"
    AWK=nawk
    ;;
  Linux )
    AWK=gawk
    PS=":"
    FS="/"
    ;;
  Darwin )
    AWK=awk
    PS=":"
    FS="/"
    ;;
  Windows* )
    AWK=awk
    DASH_P=""
    PS=";"
    FS="\\"
    ;;
  CYGWIN* )
    AWK=awk
    PS=";"
    FS="/"
    isCygwin=true
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

BIN=${TESTJAVA:-../../../../../build}/bin
SRC=${TESTSRC:-.}

tsrc=$1
dir=`dirname $1`
if [ $tsrc = "-" ] ; then
  tsrc=""
elif [ $dir = "." ] ; then
  tsrc="../$tsrc"
fi

# clean up the working directory
rm -rf z.*
mkdir z.test
cd z.test

# parse the input $tsrc file and write source files in
# an appropriate directory (modulepath or package name hierarchy) 
#
# MKS awk system("mkdir") command looks like invoking the 
# DOS mkdir command rather than the MKS mkdir when running from
# jtreg harness (although system("which mkdir") shows it's MKS
# mkdir.exe.  It doesn't accept "-p" option nor forward slashes.
# It will create a directory named "-p" instead but it does create
# all non-existing subdirectories.  So the file separator and
# mkdir -p option are passed as variables to the awk script
# to handle MKS specially.
#
# MKS awk does not accept plan `{` and so the escape sequence
# `\{' is used that causes the warning on other platforms.
#
$AWK -v sep="$FS" -v dash_p="$DASH_P" '
  function toPath(dir, name) {
    return sprintf("%s%s%s", dir, sep, name)
  }
  function mkdir(dir) {
    system("mkdir " dash_p " " dir);
  }

  /^:/ {
    test = $2; status = $3; how = $4;
    tdir = sprintf("%03d%s", tcount++, $2);
    mkdir(tdir);
    if (NF == 3) e = $3; else e = $3 " " $4;
    efile = toPath(tdir, "expected");
    print e >efile;
    close(efile);
    msdir = toPath(tdir, "src");
    mkdir(msdir);
    moduleclassFound = 0;
  }

  /^module / {
    module = $2;
    mname = module;
    mdir = toPath(msdir, mname);
    mkdir(mdir);
    if (mfile != "") close(mfile);
    mfile = toPath(mdir, "module-info.java");
    print $0 >mfile;
    next;
  }

  /^package / {
    pkgpre = $0;
    pname = $2;
    gsub("\\.", sep, pname);
    gsub("(;| )", "", pname);
    pdir = toPath(mdir, pname);
    mkdir(pdir);
    module = 0;
    next;
  }

  /^import / {
    pkgpre = pkgpre "\n" $0;
    next;
  }

  /.* (class|interface) .* *\{ *\}? *$/ {
    class = $0;
    sub(" +\{ *\}? *", "", class);
    sub(".*class +", "", class);
    if (cfile != "") close(cfile);
    cfile = toPath(pdir, class ".java");
    print pkgpre >>cfile;
    print $0 >>cfile;
    pkgpre = "";
    next;
  }

  /^./ {
    if (module) {
      print $0 >>mfile;
      if (match($0, /^ +class +([a-zA-Z.]+) *;/)) {
          if (moduleclassFound == 0) {
              print module >toPath(tdir, "main");
              moduleclassFound = 1;
         }
      }
    }
    if (class) print $0 >>cfile;
    next;
  }

  /^#?$/ { module = 0; class = 0; }

  /.+/ { if (module || class) print $0; }

' $tsrc

tests=`echo * | wc -w`
if [ $tests = 1 ]; then
  d=`echo *`
  mv $d/* .
  rmdir $d
fi

failures=0
fail() {
  echo "FAIL: $*"
  failures=`expr $failures + 1`
}

compile() {
  $BIN/javac -d modules -modulepath modules \
     `find src -name '*.java'`
}

install() {
  mlist=`cd modules; echo *`
  $BIN/jmod ${TESTTOOLVMOPTS} create -L z.mlib \
  && $BIN/jmod ${TESTTOOLVMOPTS} install modules $mlist -L z.mlib
##  && $BIN/jmod list -L z.mlib
}

catfile() {
  tr -d '\r' < $1
}

invoke() {
  if [ -f main ] ; then
    modulename=`catfile main`
    $BIN/java ${VMOPTS} \
              -Dtest.src=${TESTSRC} -Dtest.classes=${TESTCLASSES} \
              -L z.mlib -m $modulename
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
  e=`catfile expected`
  [ $tests = 1 ] || echo "-- $test $e"
  mkdir modules
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
  pdir=`echo $BIN | cut -c1-3`
  if [ -z "$TESTSRC" -a "$pdir" = "../" ]; then
    BIN=../$BIN;
  fi
  for t in *; do
    if [ -d $t ] ; then
      cd $t
      run `echo $t | cut -c4-` || /bin/true
      cd ..
    fi
  done
fi

if [ $tests -gt 1 ]; then
  echo
  echo "== $tests tests, $failures failure(s)"
fi

exit $failures
