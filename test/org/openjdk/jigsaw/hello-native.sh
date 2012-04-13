#! /bin/sh

# Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
# @summary Hello

set -e

sh ${TESTSRC:-.}/tester.sh $0

BIN=${TESTJAVA:-../../../../build}/bin
VMOPTS="${TESTVMOPTS} -esa -ea"

mkdir -p z.test/native/src z.test/native/lib
$BIN/javah ${TESTTOOLVMOPTS} -classpath z.test/modules/org.astro \
    -d z.test/native/src org.astro.World

cat >z.test/native/src/org_astro_World.c <<___

#include <string.h>
#include "org_astro_World.h"

JNIEXPORT jbyteArray JNICALL
Java_org_astro_World_getName(JNIEnv *env, jclass cl)
{
    const char *s = "native World";
    int n = strlen(s);
    jbyteArray jb;
    jb = (*env)->NewByteArray(env, n);
    (*env)->SetByteArrayRegion(env, jb, 0, n, (jbyte *)s);
    return jb;
}

___


# Build native library in a regression test.
# Temporary leave with the dependency on the C compiler
# until other test harness covers the functionality for
# native libraries support.
OS=`uname -s`
case "$OS" in
  SunOS )
    (cd z.test/native/src;
     cc -G -o ../lib/libworld.so -I$TESTJAVA/include -I$TESTJAVA/include/solaris org_astro_World.c -lc)
    ;;
  Linux )
    (cd z.test/native/src;
     gcc -o ../lib/libworld.so -I$TESTJAVA/include -I$TESTJAVA/include/linux -shared org_astro_World.c -static -lc)
    ;;
  Darwin )
    (cd z.test/native/src;
     gcc -o ../lib/libworld.so -I$TESTJAVA/include -I$TESTJAVA/include/darwin -shared org_astro_World.c -static -lc)
    ;;
  Windows* )
    (cd z.test/native/src;
     cl /LD /Fe../lib/world.dll /I$TESTJAVA/include /I$TESTJAVA/include/win32 org_astro_World.c)
    ;;
  CYGWIN* )
    (cd z.test/native/src;
     cl /LD /Fe../lib/world.dll /I$TESTJAVA/include /I$TESTJAVA/include/win32 org_astro_World.c)
    ;;
  * )
    echo "Unrecognized system!"
    exit 1
esac


mkdir -p z.test/module-files
$BIN/jpkg ${TESTTOOLVMOPTS} -d z.test/module-files \
          -m z.test/modules/com.greetings jmod com.greetings
$BIN/jpkg ${TESTTOOLVMOPTS} -d z.test/module-files \
          -m z.test/modules/org.astro \
          --natlib z.test/native/lib jmod org.astro
$BIN/jmod ${TESTTOOLVMOPTS} -L z.lib create
$BIN/jmod ${TESTTOOLVMOPTS} -L z.lib install z.test/module-files/*
$BIN/java ${VMOPTS} -L z.lib -m com.greetings
$BIN/jmod ${TESTTOOLVMOPTS} -L z.libImageLib create \
          --natlib z.libSpecifyLib_libs
$BIN/jmod ${TESTTOOLVMOPTS} -L z.libImageLib install z.test/module-files/*
$BIN/java ${VMOPTS} -L z.libImageLib -m com.greetings

exit 0

: hello pass compile

module com.greetings @ 0.1 {
    requires org.astro @ 1.2;
    class com.greetings.Hello;
}

package com.greetings;
import org.astro.World;
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello, " + World.name() + "!");
    }
}

module org.astro @ 1.2 {
    exports org.astro;
}

package org.astro;
public class World {
    private static native byte[] getName();
    static {
        System.loadLibrary("world");
    }
    public static String name() {
	return new String(getName());
    }
}
