#! /bin/sh

# Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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

/bin/sh ${TESTSRC:-.}/tester.sh $0

BIN=${TESTJAVA:-../../../../build}/bin

mkdir -p z.test/native/src z.test/native/lib
$BIN/javah -classpath z.test/modules/org.astro -d z.test/native/src \
  org.astro.World

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

(cd z.test/native/src;
 cc -o ../lib/libworld.so -shared org_astro_World.c -static -lc)

mkdir -p z.test/module-files
$BIN/jpkg -d z.test/module-files -m z.test/modules/com.greetings \
          jmod com.greetings
$BIN/jpkg -d z.test/module-files -m z.test/modules/org.astro \
          --natlib z.test/native/lib jmod org.astro
$BIN/jmod -L z.lib create
$BIN/jmod -L z.lib install z.test/module-files/*
$BIN/java -L z.lib -m com.greetings

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

module org.astro @ 1.2 { }

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
