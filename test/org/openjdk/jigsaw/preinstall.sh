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

# @test Pre-install

set -e
SRC=${TESTSRC:-.}
BIN=${TESTJAVA:-../../../../build}/bin
alias jmod=$BIN/jmod

/bin/sh ${TESTSRC:-.}/tester.sh $0

mkdir -p z.res/foo
echo '<hello/>' >z.res/foo/x.xml

export JAVA_MODULES=z.lib
jmod create
jmod preinstall z.test/modules -r z.res z.pre x y
cp -r z.pre/* z.lib
ms="$(echo $(jmod list | grep -v jdk@ | sort))"
if [ "$ms" != "x@1 y@1" ]; then
  echo Wrong modules: "$ms"
  exit 1
fi
JIGSAW_TRACE=1 jmod config
jmod show x@1
$BIN/java -ea org.openjdk.jigsaw.Launcher z.lib x x.X
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

module y @ 1 { }

package y;
public class Y {
    public static int zero() { return 0; }
}
