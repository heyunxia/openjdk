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

# @test Resources

set -e
SRC=${TESTSRC:-.}
BIN=${TESTJAVA:-../../../../build}/bin
alias jmod=$BIN/jmod

/bin/sh ${TESTSRC:-.}/tester.sh $0

mk() {
  mkdir -p $(dirname $1)
  echo "$2" >$1
}

mk z.res.x/foo/x 'Hello!'
mk z.res.x/inf/a 'A one,'

mk z.res.y/bar/y 'Bonjour!'
mk z.res.y/inf/a 'and a two,'

mk z.res.z/baz/z 'Hola!'
mk z.res.z/inf/a 'and a three!'

export JAVA_MODULES=z.lib
jmod create
jmod install z.test/modules -r z.res.z z
jmod install z.test/modules -r z.res.y y
jmod install z.test/modules -r z.res.x x
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
