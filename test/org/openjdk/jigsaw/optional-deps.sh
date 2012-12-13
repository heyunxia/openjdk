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
# @summary Unit test for optional circular dependencies
# @run shell optional-deps.sh

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

mk z.src/org.foo/module-info.java <<EOF
module org.foo @ 1 {
    requires optional net.bar;
    exports org.foo;
    exports org.foo.spi;
    class org.foo.Main;
}
EOF

mk z.src/org.foo/org/foo/Factory.java <<EOF
package org.foo;
import org.foo.spi.Service;

public class Factory {
    public static Service loadService(String cn, ClassLoader cl)
        throws ClassNotFoundException 
    {
        Class<?> c = Class.forName(cn, true, cl);
        Class<? extends Service> sc = c.asSubclass(Service.class);
        try {
            return sc.newInstance();
        } catch (InstantiationException x) {
            throw new RuntimeException(x);
        } catch (IllegalAccessException x) {
            throw new RuntimeException(x);
        }
    }
    public static Service loadService(String cn)
        throws ClassNotFoundException 
    {
        return loadService(cn, Service.class.getClassLoader());
    }
}
EOF

mk z.src/org.foo/org/foo/Main.java <<EOF
package org.foo;

public class Main {
    public static void main(String[] args) throws Exception {
        String cn = args.length == 0 ?
                        "org.foo.DefaultImpl" : args[0];
        Factory.loadService(cn);
    }
}
EOF

mk z.src/org.foo/org/foo/spi/Service.java <<EOF
package org.foo.spi;
public interface Service {
}
EOF

mk z.src/org.foo/org/foo/DefaultImpl.java <<EOF
package org.foo;
import org.foo.spi.Service;

class DefaultImpl implements Service {
    public DefaultImpl() {};
}
EOF

mk z.src/net.bar/module-info.java <<EOF
module net.bar @ 2 {
    requires org.foo;
    exports net.bar;
    class net.bar.Ness;
}
EOF

mk z.src/net.bar/net/bar/Provider.java <<EOF
package net.bar;
public class Provider implements org.foo.spi.Service {
    public Provider() {};
}
EOF

mk z.src/net.bar/net/bar/Ness.java <<EOF
package net.bar;
public class Ness {
    public static void main(String[] args) throws Exception {
        loadProvider();
    }
    public static void loadProvider()
        throws ClassNotFoundException 
    {
        org.foo.Factory.loadService("net.bar.Provider");
    }
}
EOF


mk z.src/com.foo.bar/module-info.java <<EOF
module com.foo.bar @ 1.2.3
{
    requires org.foo;
    requires net.bar;
    class com.foo.bar.Main;
}
EOF

mk z.src/com.foo.bar/com/foo/bar/FooBar.java <<EOF
package com.foo.bar;
public class FooBar implements org.foo.spi.Service {
    public FooBar() {};
}
EOF
mk z.src/com.foo.bar/com/foo/bar/Main.java <<EOF
package com.foo.bar;
public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("Hello, world!");
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        org.foo.Factory.loadService("net.bar.Provider", cl);
        org.foo.Factory.loadService("com.foo.bar.FooBar", cl);
    }
}
EOF

mkdir z.modules z.classes

$BIN/javac -source 8 -d z.modules -modulepath z.modules \
   `find z.src -name '*.java'`

# optional module is not installed
$BIN/jmod ${TESTTOOLVMOPTS} -L z.lib create
$BIN/jmod ${TESTTOOLVMOPTS} -L z.lib install z.modules org.foo
$BIN/java ${VMOPTS} -L z.lib -m org.foo


# install the optional module 
$BIN/jmod ${TESTTOOLVMOPTS} -L z.lib install z.modules net.bar
$BIN/java ${VMOPTS} -L z.lib -m net.bar
$BIN/java ${VMOPTS} -L z.lib -m org.foo net.bar.Provider

# find class from the system class loader
$BIN/jmod ${TESTTOOLVMOPTS} -L z.lib install z.modules com.foo.bar
$BIN/java ${VMOPTS} -L z.lib -m com.foo.bar

