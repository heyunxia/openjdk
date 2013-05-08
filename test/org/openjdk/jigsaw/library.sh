#! /bin/sh

# Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
# @summary Unit test for basic library methods
# @run shell/timeout=300 library.sh

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

mk z.src/com.foo.bar/module-info.java <<EOF
module com.foo.bar @ 1.2.3_04-5a
{
    provides com.foo.baz @ 2.0;
    provides com.foo.bez @ 3.4a-9;
    permits com.foo.buz;
    permits com.oof.byz;
    class com.foo.bar.Main;
}
EOF

mk z.src/com.foo.bar/com/foo/bar/Main.java <<EOF
package com.foo.bar;
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, world!");
    }
}
EOF

mk z.src/com.foo.bar/com/foo/bar/Internal.java <<EOF
package com.foo.bar;
class Internal {
    private static class Secret { }
}
EOF

mkdir z.modules z.classes

$BIN/javac -source 8 -d z.classes $SRC/_Library.java

$BIN/javac -source 8 -d z.modules -modulepath z.modules \
    `find z.src -name '*.java'`

for v in 1 1.2 2 3; do
  m=org.multi@$v
  mk z.src.$m/org.multi/module-info.java <<EOF
module org.multi @ $v {
  exports org.multi;
}
EOF
mk z.src.$m/org.multi/org/multi/Tudinous.java <<EOF
package org.multi;
public class Tudinous { }
EOF
  md=z.modules.$m
  mkdir -p $md
  $BIN/javac -source 8 -d $md -modulepath $md `find z.src.$m -name '*.java'`
done

mk z.src/net.baz.aar/module-info.java <<EOF
module net.baz.aar @ 9 {
    requires org.multi @ 1;
    class net.baz.aar.Ness;
}
EOF

mk z.src/net.baz.aar/net/baz/aar/Ness.java <<EOF
package net.baz.aar;
public class Ness {
    public static void main(String[] argv) { }
}
EOF

OS=`uname -s`
case "$OS" in
  SunOS | Linux | Darwin )
    PS=":"
    FS="/"
    ;;
  Windows* )
    PS=";"
    FS="\\"
    ;;
  CYGWIN* )
    PS=";"
    FS="\\"
    isCygwin=true
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

$BIN/javac -source 8 -d z.modules -modulepath z.modules${PS}z.modules.org.multi@1 \
   `find z.src/net.baz.aar -name '*.java'`

$BIN/java ${VMOPTS} -cp z.classes _Library
