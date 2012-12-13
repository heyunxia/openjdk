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
VMOPTS="${TESTVMOPTS} -esa -ea"

mk() {
  d=`dirname $1`
  if [ ! -d $d ]; then mkdir -p $d; fi
  cat - >$1
}

rm -rf z.*

mk z.src/com.greetings/module-info.java <<EOF
module com.greetings @ 0.1 {
    requires org.astro @ 1.2;
    requires test @ 1.0;
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
module org.astro @ 1.2 {
    exports org.astro;
}
EOF

mk z.src/org.astro/org/astro/World.java <<EOF
package org.astro;
public class World {
    public static String name() {
	return "world";
    }
}
EOF

mk z.src/test/module-info.java <<EOF
module test @ 1.0 {
    class test.Test;
}
EOF

mk z.src/test/test/Test.java <<EOF
package test;
import java.lang.module.*;
import java.util.jar.*;
public class Test {
    public static void main(String[] argv) throws Exception {
        Test t = new Test(argv[0]);
        t.run();
    }
    String jfname;
    public Test(String name) {
        this.jfname = name;
    } 
    public void run() throws Exception {
        JarFile jf = new JarFile(jfname);
        ModuleInfo mi = jf.getModuleInfo();
        if (mi == null)
            throw new RuntimeException("null ModuleInfo in " + jfname);
        if (!mi.defaultView().mainClass().equals("com.greetings.Hello")) {
            throw new RuntimeException("Unexpected main class " + mi);
        }
        if (mi.requiresModules().size() != 3)
            throw new RuntimeException("requires.length != 3");
        for (ViewDependence d : mi.requiresModules()) {
            String n = d.query().name();
            if (n.startsWith("jdk") || n.startsWith("java.")) continue;
            if (!n.equals("org.astro") && !n.equals("test"))
                throw new RuntimeException("Unexpected dependence:" + d);
        }
    }
}
EOF

mk z.src/manifest <<EOF
Class-Path: world.jar test.jar
Main-Class: com.greetings.Hello
EOF
mkdir z.modules z.jarfiles

$BIN/javac -d z.modules -modulepath z.modules `find z.src -name '*.java'`

run() {
   rm -rf z.lib
   DIR=$1
   # launch in legacy mode
   $BIN/java ${VMOPTS} -jar $DIR/hello.jar

   # launch in module mode
   $BIN/jmod ${TESTTOOLVMOPTS} -L z.lib create
   $BIN/jmod ${TESTTOOLVMOPTS} -L z.lib install $DIR/test.jar $DIR/world.jar $DIR/hello.jar
   $BIN/java ${VMOPTS} -L z.lib -m com.greetings
   # validate module-info.class in hello.jar
   $BIN/java ${VMOPTS} -L z.lib -m test $DIR/hello.jar
}

# Test jar file in both store-only mode and compressed mode
JAR="$BIN/jar ${TESTTOOLVMOPTS}" 
$JAR c0fe z.modules/test.jar test.Test -C z.modules/test .
$JAR c0f z.modules/world.jar -C z.modules/org.astro .
$JAR cfm z.modules/hello.jar z.src/manifest -C z.modules/com.greetings .

# modular jars with module-info.class entry
run z.modules


# modular jars without module-info.class entry but use -I option
$JAR c0fIe z.jarfiles/test.jar test@1.0 test.Test \
         -C z.modules/test test 
$JAR cfI z.jarfiles/world.jar org.astro@1.2 \
         -C z.modules/org.astro org
$JAR c0fmI z.jarfiles/hello.jar z.src/manifest com.greetings@0.1 \
         -C z.modules/com.greetings com

# Run in legacy mode and module mode
run z.jarfiles

# Update z.jarfiles 
$JAR ufI z.jarfiles/test.jar test@1.0
$JAR u0f z.jarfiles/world.jar \
         -C z.modules/org.astro module-info.class
$JAR u0fm z.jarfiles/hello.jar z.src/manifest \
         -C z.modules/com.greetings com

# Run in legacy mode and module mode
run z.jarfiles
