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
 * @summary org.openjdk.jigsaw.JigsawVersionQuery unit test
 * @run main _JigsawVersionQuery
 */

import java.io.*;
import java.util.*;
import java.lang.module.*;
import org.openjdk.jigsaw.*;

import static java.lang.System.out;


public class _JigsawVersionQuery {

    private static ModuleSystem ms = JigsawModuleSystem.instance();

    static void bad(String s) {
        try {
            VersionQuery v = ms.parseVersionQuery(s);
            throw new AssertionError(v);
        } catch (RuntimeException x) {
            out.format("'%s': %s%n", s, x);
        }
    }

    static void ok(String s) {
        try {
            ms.parseVersionQuery(s);
            out.format("ok: %s%n", s);
        } catch (RuntimeException x) {
            x.printStackTrace(out);
            throw new AssertionError(s);
        }
    }

    static void match(String s1, String s2) {
        if (!ms.parseVersionQuery(s1).matches(ms.parseVersion(s2)))
            throw new AssertionError(s1 + "(" + s2 + ")");
        out.format("%s matches %s%n", s1, s2);
    }

    static void nomatch(String s1, String s2) {
        if (ms.parseVersionQuery(s1).matches(ms.parseVersion(s2)))
            throw new AssertionError(s1 + "(" + s2 + ")");
        out.format("%s !matches %s%n", s1, s2);
    }

    public static void main(String[] args) {

        if (args.length == 1) {
            out.println(ms.parseVersionQuery(args[0]));
            return;
        }

        if (args.length == 2) {
            VersionQuery q = ms.parseVersionQuery(args[0]);
            Version v = ms.parseVersion(args[1]);
            out.format("%s(%s) = %b%n", q, v, q.matches(v));
            return;
        }

        ok(">1.2");
        ok(">=1.2");
        ok("<1.2");
        ok("<=1.2");
        ok("=1.2");
        ok("1.2");
        ok(null);

        bad(">");
        bad("<");
        bad(">=");
        bad("<=");
        bad("=");
        bad("");

        match(">1.2", "1.3");
        match(">1.2", "2");
        nomatch(">1.2", "1.2");
        nomatch(">1.2", "1.1");

        match("<1.2", "1.0");
        match("<1.2", "1");
        nomatch("<1.2", "1.2");
        nomatch("<1.2", "1.4");

        match("<=1.2", "1.0");
        match("<=1.2", "1");
        match("<=1.2", "1.2");
        nomatch("<=1.2", "1.3");

        nomatch(">=1.2", "1.0");
        nomatch(">=1.2", "1");
        match(">=1.2", "1.2");
        match(">=1.2", "1.3");

        match("=1.2", "1.2");
        match("1.2", "1.2");
        match("1.2", "1.2.0.0.0.0");
        nomatch("1.2", "1.2.0.0.1");

    }

}
