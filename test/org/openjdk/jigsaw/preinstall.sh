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

# @test Pre-install

set -e
SRC=${TESTSRC:-.}
BIN=${TESTJAVA:-../../../../build}/bin
VMOPTS="${TESTVMOPTS} -esa -ea"

alias jmod=$BIN/jmod

sh ${TESTSRC:-.}/tester.sh $0

mkdir -p z.test/modules/x/foo
echo '<hello/>' >z.test/modules/x/foo/x.xml

$BIN/jmod ${TESTTOOLVMOPTS} create -L z.lib
$BIN/jmod ${TESTTOOLVMOPTS} preinstall -L z.lib z.test/modules z.pre x y

# copy the preinstalled module content requiring a refresh
# to update the module directory
cp -r z.pre/* z.lib
$BIN/jmod refresh -L z.lib

# Need to truncate \r and \n on windows
ms=`$BIN/jmod list -L z.lib | grep -v jdk@ | sort | tr -s '\r' '\n' | tr -s '\n' ' '`
if [ "$ms" != "x@1 y@1 " ]; then
  echo Wrong modules: "$ms"
  exit 1
fi
$BIN/jmod ${TESTTOOLVMOPTS} config -L z.lib
$BIN/java ${VMOPTS} -L z.lib -m x
exit 0


# -- Setup

: setup pass compile

module x @ 1 {
  requires y @ 1;
  class x.X;
}

package x;
import java.io.*;
import java.net.*;
public class X {
    public static void main(String[] args) throws Exception {
        ClassLoader cl = X.class.getClassLoader();
        URL u = cl.getResource("foo/x.xml");
        if (u == null)
            throw new Error("No resource foo/x.xml");
        System.out.format("%s%n", u);
        InputStream in = cl.getResourceAsStream("/foo/x.xml");
        byte[] buf = new byte[1024];
        int n = in.read(buf);
        if (n <= 0)
            throw new Error();
        System.out.write(buf, 0, n);
        System.exit(y.Y.zero());
    }
}

module y @ 1 {
    exports y;
}

package y;
public class Y {
    public static int zero() { return 0; }
}
