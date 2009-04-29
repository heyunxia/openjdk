#! /bin/sh

# Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Sun designates this
# particular file as subject to the "Classpath" exception as provided
# by Sun in the LICENSE file that accompanied this code.
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

# @test
# @summary Unit test for basic library methods

set -e

BIN=${TESTJAVA:-../../../../build}/bin
SRC=${TESTSRC:-.}

mk() {
  d=$(dirname $1)
  if ! [ -d $(dirname $1) ]; then mkdir -p $d; fi
  cat - >$1
}

rm -rf z.*

mk z.src/com.foo.bar/module-info.java <<EOF
module com.foo.bar @ 1.2.3_04-5a
    provides com.foo.baz @ 2.0, com.foo.bez @ 3.4a-9
{
    permits com.foo.buz, com.oof.byz;
    class com.foo.bar.Main;
}
EOF

mk z.src/com.foo.bar/Main.java <<EOF
module com.foo.bar;
package com.foo.bar;
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, world!");
    }
}
EOF

mk z.src/com.foo.bar/Internal.java <<EOF
module com.foo.bar;
package com.foo.bar;
class Internal {
    private static class Secret { }
}
EOF

mkdir z.classes

$BIN/javac -source 7 -d z.classes \
  $SRC/_Library.java $(find z.src -name '*.java')

for v in 1 1.2 2 3; do
  m=org.multi@$v
  mk z.src/$m/module-info.java <<EOF
module org.multi @ $v { }
EOF
mk z.src/$m/Tudinous.java <<EOF
module org.multi;
package org.multi;
public class Tudinous { }
EOF
  cl=z.classes/module-classes/$m
  mkdir -p $cl
  $BIN/javac -source 7 -d $cl z.src/$m/*.java
done

mk z.src/net.baz.aar/module-info.java <<EOF
module net.baz.aar @ 9 {
    requires org.multi @ 1;
    class net.baz.aar.Ness;
}
EOF

mk z.src/net.baz.aar/Ness.java <<EOF
module net.baz.aar;
package net.baz.aar;
public class Ness { }
EOF

$BIN/javac -source 7 -d z.classes \
  $(find z.src/net.baz.aar -name '*.java')

$BIN/java -cp z.classes _Library
