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
# @summary Hello module with optional dependency

exec sh ${TESTSRC:-.}/tester.sh $0

: optional-resolved pass

module com.greetings @ 0.1 {
    requires org.astro @ 1.2;
    requires optional com.foo;
    class com.greetings.Hello;
}

package com.greetings;
import org.astro.World;
import java.lang.reflect.Module;
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello, " + World.name() + "!");
        testOptionalMethod();
    }
    public static void testOptionalMethod() {
        if (Hello.class.isModulePresent("com.foo")) {
            com.foo.Foo.findit();
        } else {
            throw new RuntimeException("com.foo module not present");
        }
    }
}

module org.astro @ 1.2 {
    exports org.astro;
}

package org.astro;
public class World {
    public static String name() {
	return "world";
    }
}

module com.foo @ 2.0 {
    exports com.foo;
}

package com.foo;
public class Foo {
    public static void findit() {
 	System.out.println("com.foo is present");
    }
    public static String name() {
        return "world from Foo";
    }
}

: foo-not-present pass

module com.greetings @ 0.2 {
    requires org.astro @ 1.2;
    requires optional com.foo;
    class com.greetings.Hello;
}

package com.greetings;
import org.astro.World;
import java.lang.reflect.Module;
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello, " + World.name() + "!");
        testOptionalMethod();
    }
    public static void testOptionalMethod() {
        if (Hello.class.isModulePresent("com.foo")) {
            throw new RuntimeException("com.foo is present");
        }
        boolean cnfe = false;
        try {
            Class.forName("com.foo.Foo");
        } catch (ClassNotFoundException e) {
            cnfe = true;
        }
        if (!cnfe) {
            throw new RuntimeException("com.foo.Foo found");
        }
    }
}

module org.astro @ 1.2 {
    exports org.astro;
}

package org.astro;
import java.lang.reflect.Module;
public class World {
    public static String name() {
	return "world";
    }
}

: hello-foo pass

module com.greetings @ 1.0 {
    requires org.astro @ 2.0;
    requires optional com.foo;
    class com.greetings.Hello;
}

package com.greetings;
import org.astro.World;
import java.lang.reflect.Module;
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello, " + World.name() + "!");
        com.foo.Foo.findit();
    }
}

module org.astro @ 2.0 {
    requires optional com.foo;
    exports org.astro;
}

package org.astro;
import java.lang.reflect.Module;
public class World {
    public static String name() {
        if (!World.class.isModulePresent("com.foo"))
            throw new RuntimeException("com.foo is present");
        return com.foo.Foo.name();
    }
}

module com.foo @ 2.0 {
    exports com.foo;
}

package com.foo;
public class Foo {
    public static void findit() {
 	System.out.println("com.foo is present");
    }
    public static String name() {
        return "world from Foo";
    }
}

