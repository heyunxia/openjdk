#! /bin/sh

# Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
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
# @summary Unit test for jmod command

set -e

BIN=${TESTJAVA:-../../../../../build}/bin

mk() {
  d=`dirname $1`
  if [ ! -d $d ]; then mkdir -p $d; fi
  cat - >$1
}

rm -rf z.src

mk z.src/com.foo.bar/module-info.java <<EOF
module com.foo.bar @ 1.2.3_01-4a
    provides baz @ 2.0, biz @ 3.4a
{
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

rm -rf z.modules && mkdir z.modules
$BIN/javac -source 8 -d z.modules -modulepath z.modules `find z.src -name '*.java'`

JAVA_MODULES=z.lib
export JAVA_MODULES

testjmod() {
  createargs=$1
  installargs=$2
  rm -rf $JAVA_MODULES
  $BIN/jmod create $createargs
  $BIN/jmod id
  $BIN/jmod install $installargs z.modules com.foo.bar
  $BIN/jmod install $installargs z.modules com.foo.byz
  $BIN/jmod list
  $BIN/jmod list -v
  $BIN/jmod dump-class com.foo.bar@1.2.3_01-4a com.foo.bar.Main z
  if [ "$installargs" = "" ]; then
    cmp z z.modules/com.foo.bar/com/foo/bar/Main.class 
  fi
}

# Test combinations of compressed/uncompressed module library and
# debug attributes stripped/not stripped during installation
# debug attributes stripped.
testjmod
testjmod -z
testjmod --enable-compression
testjmod "" -G
testjmod "" --strip-debug
testjmod -z -G
