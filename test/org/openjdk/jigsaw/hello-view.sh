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

# @test
# @summary Hello

exec sh ${TESTSRC:-.}/tester.sh $0

: hello-view pass

module com.greetings @ 0.1 {
    requires org.astro.internal @ 1.2;
    class com.greetings.Hello;
}

package com.greetings;
import org.astro.internal.World;
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello, " + World.name() + "!");
    }
}

module org.astro @ 1.2 {
    exports org.astro;
    view org.astro.internal {
        exports org.astro.internal;
        permits com.greetings;
    }
    view org.astro.main {
        class org.astro.World;
    }
}

package org.astro;
public class World {
    public static void main(String[] args) {
        System.out.println("Welcome to this " + name());
    }
    public static String name() {
	return "world";
    }
}

package org.astro.internal;
public class World {
    public static String name() {
	return "internal world";
    }
}

: reexports-view pass

module com.greetings @ 0.1 {
    requires foo.internal @ 1;
    class com.greetings.Hello;
}

package com.greetings;
import org.astro.internal.World;
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello, " + World.name() + "!");
    }
}

module foo @ 1 {
    requires public org.astro.internal @ 1.2;
    requires org.astro @ 1.2;
    view foo.internal {
        permits com.greetings;
    }
}

module org.astro @ 1.2 {
    exports org.astro;
    view org.astro.internal {
        exports org.astro.internal;
    }
}

package org.astro;
public class World {
    public static void main(String[] args) {
        System.out.println("Welcome to this " + name());
    }
    public static String name() {
	return "world";
    }
}

package org.astro.internal;
public class World {
    public static String name() {
	return "internal world";
    }
}

: multi-views pass

module foo @ 1 {
    requires bar @ 1.2;
    requires baz;
    class foo.Main;
}

package foo;
import bar.Bar;
import baz.Baz;
public class Main {
    public static void main(String[] args) {
        System.out.println("foo, " + 
             bar.Bar.name() + ", " +
             baz.Baz.name()); 
    }
}

module bar @ 1.2 {
    exports bar;
    view baz {
        exports baz;
    }
}

package bar;
public class Bar {
    public static String name() {
	return "bar";
    }
}

package baz;
public class Baz {
    public static String name() {
	return "baz";
    }
}

