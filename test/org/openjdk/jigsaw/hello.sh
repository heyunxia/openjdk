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

# @test
# @summary Hello

set -e

BIN=${TESTJAVA:-../../../../build}/bin
SRC=${TESTSRC:-.}

mk() {
  d=$(dirname $1)
  if ! [ -d $(dirname $1) ]; then mkdir -p $d; fi
  cat - >$1
}

rm -rf z.*

mk z.src/com.greetings/module-info.java <<EOF
module com.greetings @ 0.1 {
    requires org.astro @ 1.2;
    class com.greetings.Hello;
}
EOF

mk z.src/com.greetings/Hello.java <<EOF
module com.greetings;
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

mk z.src/org.astro/World.java <<EOF
module org.astro;
package org.astro;
public class World {
    public static String name() {
	return "world";
    }
}
EOF

mkdir z.classes

$BIN/javac -source 7 -d z.classes $(find z.src -name '*.java')

if false; then
  # Hack
  $BIN/javac -d z.classes -cp asm/classes StripModuleAttributes.java
  $BIN/java -cp z.classes:asm/classes StripModuleAttributes \
    $(find z.classes -name '*.class' | grep -v module-info.class)
fi

#export JIGSAW_TRACE=${JIGSAW_TRACE:-1}

export JAVA_MODULES=z.lib
$BIN/jmod create
$BIN/jmod install z.classes org.astro
$BIN/jmod install z.classes com.greetings

$BIN/java org.openjdk.jigsaw.Launcher z.lib com.greetings
