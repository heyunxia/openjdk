#! /bin/sh

# Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
#

# @test
# @summary Unit test for circular dependencies
# @run shell circular-deps.sh

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

mk z.src/org.gee/module-info.java <<EOF
module org.gee @ 1 {
    requires net.baz.aar;
    exports org.gee.spi;
}
EOF

mk z.src/org.gee/org/gee/spi/Service.java <<EOF
package org.gee.spi;
public interface Service {
}
EOF

mk z.src/org.gee/org/gee/Main.java <<EOF
package org.gee;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println(net.baz.aar.Ness.getName());
    }
}
EOF

mk z.src/net.baz.aar/module-info.java <<EOF
module net.baz.aar @ 2 {
    requires org.gee;
    exports net.baz.aar;
    class net.baz.aar.Ness;
}
EOF

mk z.src/net.baz.aar/net/baz/aar/Provider.java <<EOF
package net.baz.aar;
public class Provider implements org.gee.spi.Service {
    public Provider() {};
}
EOF

mk z.src/net.baz.aar/net/baz/aar/Ness.java <<EOF
package net.baz.aar;
public class Ness {
    public static void main(String[] args) throws Exception {
        System.out.println("Hello, world!");
    }
    public static String getName() {
        return Ness.class.getName();
    }
}
EOF

mkdir z.modules z.classes

$BIN/javac -source 8 -d z.modules -modulepath z.modules \
    `find z.src -name '*.java'`
$BIN/jmod ${TESTTOOLVMOPTS} -L z.lib create
$BIN/jmod ${TESTTOOLVMOPTS} -L z.lib install z.modules `ls z.src`
$BIN/java ${VMOPTS} -L z.lib -m net.baz.aar

