/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/* @test
 * @summary java.lang.module.ModuleId unit test
 * @run main _ModuleId
 */

import java.io.*;
import java.util.*;
import java.lang.module.*;


public class _ModuleId {

    private static ModuleSystem ms;

    static void bad(String id) {
        try {
            ModuleId mid = ms.parseModuleId(id);
            throw new AssertionError(mid);
        } catch (RuntimeException x) {
        }
    }

    static void ok(String id) {
        try {
            ms.parseModuleId(id);
        } catch (RuntimeException x) {
            x.printStackTrace(System.out);
            throw new AssertionError(id);
        }
    }

    static void ok(boolean b) {
        if (!b)
            throw new AssertionError();
    }

    public static void main(String[] args) {

        ms = ModuleSystem.base();

        if (args.length > 0) {
            for (String a : args)
                System.out.println(ms.parseModuleId(a));
            return;
        }

        ok("M@1.0");
        ok("M @1.0");
        ok("M@ 1.0");
        ok("M @ 1.0");
        ok("a.b.c@ 1.0");
        ok("a.b.C.d_foo");

        bad("M@");
        bad("M @");
        bad("M@ ");
        bad("M @ ");
        bad("M ");
        bad(" M");
        bad("@1.0");
        bad("@ 1.0");
        bad("@ ");
        bad("3@1");
        bad("M*@1");
        bad("M\t@2");

        ModuleId mid = ms.parseModuleId("M @ 0.1-2");
        ok(mid.equals(ms.parseModuleId("M@ 0.1-2")));
        ok(mid.equals(ms.parseModuleId("M @0.1-2")));
        ok(mid.equals(ms.parseModuleId("M @ 0.1-2")));
        ok(!mid.equals(ms.parseModuleId("M@0.1-3")));
        ok(!mid.equals(ms.parseModuleId("M")));
        ok(!ms.parseModuleId("M").equals(mid));

    }

}
