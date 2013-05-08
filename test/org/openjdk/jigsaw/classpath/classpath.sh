#! /bin/sh

# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

# @test
# @summary Add prepath or postpath to the search paths
# @compile ClassPathLoader.java Bar.java
# @run shell classpath.sh

set -e

BIN=${TESTJAVA:-../../../../build}/bin
SRC=${TESTSRC:-.}
VMOPTS="${TESTVMOPTS} -esa -ea"

mk() {
  d=`dirname $1`
  if [ ! -d $d ]; then mkdir -p $d; fi
  cat - >$1
}

rm -rf z.*

mk z.001/src/Bar.java <<EOF
public class Bar {
    public static int version() {
        return 1;
    }
}
EOF

mk z.002/src/Bar.java <<EOF
public class Bar {
    public static int version() {
        return 2;
    }
}
EOF
mk z.003/src/Bar.java <<EOF
public class Bar {
    public static int version() {
        return 3;
    }
}
EOF


mkdir z.classes z.jarfiles
mkJar() {
   ver=$1
   $BIN/javac -d z.classes `find z.$ver -name '*.java'`
   $BIN/jar ${TESTTOOLVMOPTS} cf z.jarfiles/bar$ver.jar -C z.classes .
}

mkJar 001
mkJar 002
mkJar 003

OS=`uname -s`
case "$OS" in
  SunOS )
    PS=":"
    ;;
  Linux )
    PS=":"
    ;;
  Darwin )
    PS=":"
    ;;
  Windows* )
    PS=";"
    ;;
  CYGWIN* )
    PS=";"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

JAVAHOME=${TESTJAVA:-../../../../build}

# Test classpath
echo "Testing -classpath"
$BIN/java ${VMOPTS} -cp z.jarfiles/bar001.jar${PS}${TESTCLASSES} ClassPathLoader 1
$BIN/java ${VMOPTS} -cp z.jarfiles/bar002.jar${PS}${TESTCLASSES}${PS}z.jarfiles/bar001.jar \
   ClassPathLoader 2
$BIN/java ${VMOPTS} -cp z.jarfiles/bar003.jar${PS}${TESTCLASSES} ClassPathLoader 3
$BIN/java ${VMOPTS} -cp ${TESTCLASSES}${PS}z.jarfiles/bar003.jar ClassPathLoader 0

# Test extension
mkdir z.ext
cp z.jarfiles/bar003.jar z.ext
echo "Testing -Djava.ext.dirs"
$BIN/java ${VMOPTS} -Djava.ext.dirs=${JAVAHOME}/lib/ext${PS}z.ext -cp ${TESTCLASSES} ClassPathLoader 3

# Test bootclasspath
echo "Testing -Xbootclasspath"
$BIN/java ${VMOPTS} -Xbootclasspath/p:z.jarfiles/bar001.jar -cp ${TESTCLASSES} ClassPathLoader 1
$BIN/java ${VMOPTS} -Xbootclasspath/p:z.jarfiles/bar003.jar${PS}z.jarfiles/bar001.jar \
   -cp ${TESTCLASSES} ClassPathLoader 3
$BIN/java ${VMOPTS} -Xbootclasspath/p:z.jarfiles/bar002.jar -Xbootclasspath/a:z.jarfiles/bar003.jar \
   -cp ${TESTCLASSES} ClassPathLoader 2
$BIN/java ${VMOPTS} -Xbootclasspath/a:z.jarfiles/bar003.jar \
   -cp ${TESTCLASSES} ClassPathLoader 3
