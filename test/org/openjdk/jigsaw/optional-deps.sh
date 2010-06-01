#! /bin/sh

# Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Sun designates this
# particular file as subject to the "Classpath" exception as provided
# by Sun in the LICENSE file that accompanied this code.
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
# @summary Unit test for optional circular dependencies
# @run shell optional-deps.sh

set -e

BIN=${TESTJAVA:-../../../../build}/bin
SRC=${TESTSRC:-.}

mk() {
  d=$(dirname $1)
  if ! [ -d $(dirname $1) ]; then mkdir -p $d; fi
  cat - >$1
}

rm -rf z.*

mk z.src/org.foo/module-info.java <<EOF
module org.foo @ 1 {
    requires jdk.base;
    requires optional net.bar;
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

public class DefaultImpl implements Service {
    public DefaultImpl() {};
}
EOF

mk z.src/net.bar/module-info.java <<EOF
module net.bar @ 2 {
    requires jdk.base;
    requires org.foo;
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
    requires jdk.base;
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

$BIN/javac -source 7 -d z.modules -modulepath z.modules \
  $(find z.src -name '*.java')         || exit 1

# optional module is not installed
$BIN/jmod -L z.lib create              || exit 2
$BIN/jmod -L z.lib install z.modules org.foo || exit 3
$BIN/java -ea -L z.lib -m org.foo      || exit 4


# install the optional module 
$BIN/jmod -L z.lib install z.modules net.bar || exit 5
$BIN/java -L z.lib -m net.bar          || exit 6
$BIN/java -L z.lib -m org.foo net.bar.Provider || exit 7

# find class from the system class loader
$BIN/jmod -L z.lib install z.modules com.foo.bar || exit 5
$BIN/java -L z.lib -m com.foo.bar || exit 7

