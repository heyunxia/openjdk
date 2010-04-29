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
 * @summary org.openjdk.jigsaw.JigsawVersion unit test
 * @run main _JigsawVersion
 */

import java.io.*;
import java.util.*;
import java.lang.module.*;
import org.openjdk.jigsaw.*;

import static java.lang.System.out;


public class _JigsawVersion {

    private static ModuleSystem ms = JigsawModuleSystem.instance();

    static void bad(String s) {
        try {
            Version v = ms.parseVersion(s);
            throw new AssertionError(v);
        } catch (RuntimeException x) {
            out.format("\"%s\": %s%n", s, x);
        }
    }

    static void ok(String s) {
        try {
            ms.parseVersion(s);
            out.format("ok: %s%n", s);
        } catch (RuntimeException x) {
            x.printStackTrace(out);
            throw new AssertionError(s);
        }
    }

    static void gt(String s1, String s2) {
        boolean b = ms.parseVersion(s1).compareTo(ms.parseVersion(s2)) > 0;
        if (!b)
            throw new AssertionError(s1 + " : " + s2);
        out.format("%s gt %s%n", s1, s2);
    }

    static void eq(String s1, String s2) {
        if (!(ms.parseVersion(s1).compareTo(ms.parseVersion(s2)) == 0))
            throw new AssertionError(s1 + " ==c " + s2);
        if (!ms.parseVersion(s1).equals(ms.parseVersion(s2)))
            throw new AssertionError(s1 + " ==e " + s2);
        out.format("%s eq %s%n", s1, s2);
    }

    public static void main(String[] args) {

        if (args.length == 1) {
            out.println(ms.parseVersion(args[0]));
            return;
        }

        if (args.length == 2) {
            Version v1 = ms.parseVersion(args[0]);
            Version v2 = ms.parseVersion(args[1]);
            out.format("%s : %s = %d%n", v1, v2, v1.compareTo(v2));
            return;
        }

        ok("1.0");
        ok("1.0.1");
        ok("1");
        ok("1-1");
        ok("1.1-1");
        ok("1.0r3");
        ok(null);

        bad("a.b.c");
        bad("1.0-");
        bad("-1");
        bad("foo");
        bad("");

        gt("1.0", "0.9");
        gt("1.0.1", "1.0");
        gt("1b", "1a");
        eq("1.0", "1.0");
        eq("1.0.0", "1.0");
        eq("1.0.0.0", "1.0");
        eq("1.0.0.0", "1");
        eq("1.0", "1.0.0.0");
        eq("1", "1.0.0.0");
        gt("1.0.0.0.1", "1");
        gt("1.10", "1.1");

    }

}
