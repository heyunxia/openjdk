#! /bin/sh

# Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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

# @test
# @summary Security

exec /bin/sh ${TESTSRC:-.}/tester.sh $0

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
            ModuleClassLoader mcl = new ModuleClassLoader(ModuleSystem.base()) { };
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
