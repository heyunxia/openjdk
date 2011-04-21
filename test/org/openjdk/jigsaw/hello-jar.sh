#! /bin/sh

# Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
# @summary Test modular JAR file

set -e

BIN=${TESTJAVA:-../../../../build}/bin
SRC=${TESTSRC:-.}

mk() {
  d=`dirname $1`
  if [ ! -d $d ]; then mkdir -p $d; fi
  cat - >$1
}

rm -rf z.*

mk z.src/com.greetings/module-info.java <<EOF
module com.greetings @ 0.1 {
    requires org.astro @ 1.2;
    class com.greetings.Hello;
}
EOF

mk z.src/com.greetings/com/greetings/Hello.java <<EOF
package com.greetings;
import org.astro.World;
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello, " + World.name() + "!");
    }
}
EOF

mk z.src/org.astro/module-info.java <<EOF
module org.astro @ 1.2 { }
EOF

mk z.src/org.astro/org/astro/World.java <<EOF
package org.astro;
public class World {
    public static String name() {
	return "world";
    }
}
EOF

mk z.src/manifest <<EOF
Class-Path: world.jar
Main-Class: com.greetings/Hello
EOF
mkdir z.modules z.jarfiles

$BIN/javac -source 7 -d z.modules -modulepath z.modules \
   `find z.src -name '*.java'`

mkdir z.modules/com.greetings/META-INF
mkdir z.modules/org.astro/META-INF
mv z.modules/com.greetings/module-info.class \
       z.modules/com.greetings/META-INF
mv z.modules/org.astro/module-info.class \
       z.modules/org.astro/META-INF

# Test jar file in both store-only mode and compressed mode
$BIN/jar c0f z.jarfiles/world.jar -C z.modules/org.astro .
$BIN/jar cfm z.jarfiles/hello.jar z.src/manifest -C z.modules/com.greetings .

# launch in legacy mode
$BIN/java -jar z.jarfiles/hello.jar

$BIN/jmod -L z.lib create
$BIN/jmod -L z.lib install z.jarfiles/world.jar z.jarfiles/hello.jar
$BIN/java -L z.lib -m com.greetings
