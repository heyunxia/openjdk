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
# @summary this test runs modules with a SecurityManager in the default
#    sandbox and checks that a SecurityException is thrown for
#    various security-sensitive operations

exec sh ${TESTSRC:-.}/tester.sh $0

: getProperty pass

module test.security @ 0.1 {
    class test.security.GetProperty;
}

package test.security;
public class GetProperty {
    public static void main(String[] args) {
        System.setSecurityManager(new SecurityManager());
        boolean caught = false;
        try {
            System.getProperty("user.home");
        } catch (SecurityException se) {
            se.printStackTrace();
            caught = true;
        }
        if (!caught) throw new RuntimeException("GetProperty test failed");
    }
}

: accessClassInPackage pass

module test.security @ 0.1 {
    class test.security.AccessClassInPackage;
}

package test.security;
public class AccessClassInPackage {
    public static void main(String[] args) {
        System.setSecurityManager(new SecurityManager());
        boolean caught = false;
        try {
            sun.security.util.Debug debug =
                sun.security.util.Debug.getInstance("access");
        } catch (SecurityException se) {
            se.printStackTrace();
            caught = true;
        }
        if (!caught) 
            throw new RuntimeException("AccessClassInPackage test failed");
    }
}

: createClassLoader pass

module test.security @ 0.1 {
    class test.security.CreateClassLoader;
}

package test.security;
import java.lang.module.ModuleClassLoader;
import java.lang.module.ModuleSystem;
public class CreateClassLoader {
    public static void main(String[] args) {
        System.setSecurityManager(new SecurityManager());
        boolean caught = false;
        try {
            ModuleClassLoader mcl = new ModuleClassLoader(ModuleSystem.base()) {
                public boolean isModulePresent(String mn) {
                    throw new AssertionError("should not reach here");
                }
            };
        } catch (SecurityException se) {
            se.printStackTrace();
            caught = true;
        }
        if (!caught) throw new RuntimeException("CreateClassLoader test failed");
    }
}

: checkAccess pass

module test.security @ 0.1 {
    class test.security.CheckAccess;
}

package test.security;
public class CheckAccess {
    public static void main(String arg[]) {
        System.setSecurityManager(new SecurityManager());
        boolean caught = false;
        try {
            ThreadGroup root =  Thread.currentThread().getThreadGroup();
            while (root.getParent() != null)
                root = root.getParent();
        } catch (SecurityException se) {
            caught = true;
        }
        if (!caught) throw new RuntimeException("CheckAccess test failed");
    }
}

: checkMemberAccess pass

module test.security @ 0.1 {
    class test.security.CheckMemberAccess;
}

package test.security;
import java.lang.reflect.Method;
public class CheckMemberAccess {
    public static void main(String arg[]) {
        System.setSecurityManager(new SecurityManager());
        Class c = CheckMemberAccess.class;
        // this call should work
        Method[] methods = c.getDeclaredMethods();
        boolean caught = false;
        try {
            // this call should throw a security exception
            c = String.class;
            methods = c.getDeclaredMethods();
        } catch (SecurityException se) {
            caught = true;
        }
        if (!caught) throw new RuntimeException("CheckMemberAccess test failed");
    }
}

: getClassLoader pass

module test.security @ 0.1 {
    requires jdk.base;
    requires jdk.management;
    requires foo;
    class test.security.GetClassLoader;
}

package test.security;
public class Other {
}

package test.security;
public class GetClassLoader {
    public static void main(String[] args) {
        System.setSecurityManager(new SecurityManager());
        getClassLoader(test.security.Other.class, true);
        // ## It's an open issue to evaluate the compatibility concern
        // ## if it only allows getClassLoader of its own module loader
        // ## and will address the inconsistency that currently allows
        // ## to get null class loader.
        getClassLoader(java.lang.ProcessBuilder.class, true);
        getClassLoader(java.lang.management.ManagementFactory.class, false);
        getClassLoader(foo.Foo.class, false);
        getClassLoader(foo.Foo.getBar(), false);
    }
    private static void getClassLoader(Class<?> c, boolean allow) {
        try {
            ClassLoader cl = c.getClassLoader();
            if (cl != null) cl.getParent();
            if (!allow)
                throw new RuntimeException("Should not permit " +
                    "getting class loader of " + c.getName());
        } catch (SecurityException se) {
            se.printStackTrace();
            if (allow)
                throw new RuntimeException("Failed to get class loader of " +
                    c.getName());
        }
    }
}

module foo @ 1 {
    requires bar;
    exports foo;
}

package foo;
public class Foo {
    public static Class<?> getBar() {
        return bar.Bar.class;
    }
}

module bar @ 2 {
    exports bar;
}

package bar;
public class Bar {
    public static void run() {
        System.out.println("hello");
    }
}
