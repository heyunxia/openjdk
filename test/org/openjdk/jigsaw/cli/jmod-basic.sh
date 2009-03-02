#! /bin/sh -e

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
# @summary Unit test for jmod command

BIN=${TESTJAVA:-../../../../../build/linux-i586}/bin

mk() {
  d=$(dirname $1)
  if ! [ -d $(dirname $1) ]; then mkdir -p $d; fi
  cat - >$1
}

rm -rf z.src

mk z.src/com.foo.bar/module-info.java <<EOF
module com.foo.bar @ 1.2.3_01-4a
    provides baz @ 2.0, biz @ 3.4a
{
    requires optional org.tim.buz @ ">=1.1";
    requires private local edu.mit.bez @ ">=2.2";
    permits com.foo.top, com.foo.bottom;
    class com.foo.bar.Main;
}
EOF

mk z.src/com.foo.byz/module-info.java <<EOF
module com.foo.byz @ 0.11-42 { }
EOF

mk z.src/com.foo.bar/com/foo/bar/Main.java <<EOF
package com.foo.bar;
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, world!");
    }
}
EOF

rm -rf z.classes && mkdir z.classes
$BIN/javac -source 7 -d z.classes $(find z.src -name '*.java')

rm -rf z.lib
export JAVA_MODULES=z.lib
$BIN/jmod create
$BIN/jmod id
$BIN/jmod install z.classes com.foo.bar
$BIN/jmod install z.classes com.foo.byz
$BIN/jmod list
$BIN/jmod list -v
