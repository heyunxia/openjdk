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

# @test Resources

set -e
SRC=${TESTSRC:-.}
BIN=${TESTJAVA:-../../../../build}/bin

sh ${TESTSRC:-.}/tester.sh $0

mk() {
  mkdir -p `dirname $1`
  echo "$2" >$1
}

mk z.test/modules/x/foo/x 'Hello!'
mk z.test/modules/x/inf/a 'A one,'

mk z.test/modules/y/bar/y 'Bonjour!'
mk z.test/modules/y/inf/a 'and a two,'

mk z.test/modules/z/baz/z 'Hola!'
mk z.test/modules/z/inf/a 'and a three!'

echo; echo "Direct install"
$BIN/jmod create -L z.lib
$BIN/jmod install -L z.lib z.test/modules z
$BIN/jmod install -L z.lib z.test/modules y
$BIN/jmod install -L z.lib z.test/modules x
$BIN/java -ea -L z.lib -m x

echo; echo "Module-file install"
$BIN/jpkg -m z.test/modules/z jmod z
$BIN/jpkg -m z.test/modules/y jmod y
$BIN/jpkg -m z.test/modules/x jmod x
rm -rf z.lib
$BIN/jmod create -L z.lib
$BIN/jmod install -L z.lib x@1.jmod y@1.jmod z@1.jmod
$BIN/java -ea -L z.lib -m x

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
import java.util.*;
public class X {
    private static void show(URL u, String ev)
        throws IOException
    {
        InputStream in = u.openStream();
        byte[] buf = new byte[1024];
        int n = in.read(buf);
        if (n <= 0 || in.read(buf) != -1)
            throw new Error();
        System.out.write(buf, 0, n);
        String v = new String(buf, 0, n, "US-ASCII");
        if (!v.trim().equals(ev))
            throw new AssertionError("Wrong value, expected " + ev);
    }
    private static void load(String rn, String ev)
        throws IOException
    {
        ClassLoader cl = X.class.getClassLoader();
        URL u = cl.getResource(rn);
        if (u == null)
            throw new Error(rn + ": Not found");
        System.out.format("%s%n", u);
        show(u, ev);
    }
    private static void loadAll(String rn, String ... evs)
        throws IOException
    {
        ClassLoader cl = X.class.getClassLoader();
        List<URL> us = Collections.list(cl.getResources(rn));
        Collections.sort(us, new Comparator<URL>() {
            public int compare(URL u, URL v) {
                return u.toString().compareTo(v.toString());
            }});
        if (us.isEmpty())
            throw new Error(rn + ": Not found");
        System.out.format("%s%n", us);
        int i = 0;
        for (URL u : us)
            show(u, evs[i++]);
    }
    public static void main(String[] args) throws Exception {
        load("foo/x", "Hello!");
        load("/bar/y", "Bonjour!");
        load("/baz/z", "Hola!");
        loadAll("/inf/a",
                "A one,", "and a two,", "and a three!");
    }
}

module y @ 1 {
  requires z @ 1;
}

module z @ 1 { }
