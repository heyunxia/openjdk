#! /bin/sh

# Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNES FOR A PARTICULAR PURPOSE.  See the GNU General Public License
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
# @summary Basic test for optional dependency.
#

exec sh ${TESTSRC:-.}/tester.sh $0

: hello pass

module com.greetings @ 1.0 {
    requires org.astro @ 2.0;
    requires optional com.foo @ 3.0;
    class com.greetings.Hello;
}

package com.greetings;
import java.lang.reflect.*;
import org.astro.World;
public class Hello {
    public static void main(String[] args) throws Throwable {
        boolean expected = args.length == 0 || Boolean.parseBoolean(args[0]);
        boolean present = Hello.class.isModulePresent("com.foo");
        if (present != expected)
            throw new RuntimeException("com.foo is expected to be " +
                (present ? "present" : " not present"));
        String s = null;
        if (present) {
            WorldWrapper ww = new WorldWrapper();
            s = ww.getName();
        } else {
            s = World.name();
        }
        System.out.println("Hello, " + s + "!");
    }
}

package com.greetings;
import com.foo.Foo;
import org.astro.World;
public class WorldWrapper {
    public String getName() {
        Foo f = new Foo();
        return f.name();
    }
}

module org.astro @ 2.0 { }

package org.astro;
import java.lang.reflect.Module;
public class World {
    public static String name() {
        if (World.class.isModulePresent("com.foo")) {
            throw new RuntimeException("com.foo is not expected to be present");
        }
        return "world";
    }
}

module com.foo @ 3.0 { }

package com.foo;
public class Foo {
    public String name() {
        return "world from Foo";
    }
}
