/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @bug 6802521
 * @summary add support for modules: test basic use of Module Resolver
 */

import java.io.*;
import java.util.*;

public class ModuleResolverTest03 {
    static class Test {
        Test(String desc, Unit... units) {
            this.desc = desc;
            this.units = units;
        }
        final String desc;
        final Unit[] units;
    };

    // uugh can't static import these because this class is in unnamed package
    Unit.Kind CMDLINE = Unit.Kind.CMDLINE;
    Unit.Kind SRCPATH = Unit.Kind.SRCPATH;

    static class Unit {
        enum Kind { CMDLINE, SRCPATH };
        Unit(Kind kind, String path, String text) {
            this.kind = kind;
            this.path = path;
            this.text = text;
        }
        final Kind kind;
        final String path;
        final String text;
    }

    Test[] tests = {
        new Test(
            "no versions; expect m1, m3, m5",
            new Unit(CMDLINE, "m1/p/A.java",         "package p; class A { q.B b; }"),
            new Unit(SRCPATH, "m1/module-info.java", "module m1 { requires m3; }"),
            new Unit(SRCPATH, "m2/module-info.java", "module m2 { requires m4; }"),
            new Unit(SRCPATH, "m2/q/B.java",         "ERROR"),
            new Unit(SRCPATH, "m3/module-info.java", "module m3 { requires m5; }"),
            new Unit(SRCPATH, "m4/module-info.java", "module m4 { }"),
            new Unit(SRCPATH, "m4/q/B.java",         "ERROR"),
            new Unit(SRCPATH, "m5/module-info.java", "module m5 { }"),
            new Unit(SRCPATH, "m5/q/B.java",         "package q; public class B { }")
        ),

        new Test(
            "no versions; provides; expect m1, m3, m5",
            new Unit(CMDLINE, "m1/p/A.java",         "package p; class A { q.B b; }"),
            new Unit(SRCPATH, "m1/module-info.java", "module m1 { requires m3; }"),
            new Unit(SRCPATH, "m2/module-info.java", "module m2 { requires m4; }"),
            new Unit(SRCPATH, "m2/q/B.java",         "ERROR"),
            new Unit(SRCPATH, "m3/module-info.java", "module mX provides m3 { requires m5; }"),
            new Unit(SRCPATH, "m4/module-info.java", "module m4 { }"),
            new Unit(SRCPATH, "m4/q/B.java",         "ERROR"),
            new Unit(SRCPATH, "m5/module-info.java", "module m5 { }"),
            new Unit(SRCPATH, "m5/q/B.java",         "package q; public class B { }")
        ),

        new Test(
            "no version in requires; single versions available; expect m1@1.0, m3@1.0, m5@1.0",
            new Unit(CMDLINE, "m1/p/A.java",         "package p; class A { q.B b; }"),
            new Unit(SRCPATH, "m1/module-info.java", "module m1@1.0 { requires m3; }"),
            new Unit(SRCPATH, "m2/module-info.java", "module m2@1.0 { requires m4; }"),
            new Unit(SRCPATH, "m2/q/B.java",         "ERROR"),
            new Unit(SRCPATH, "m3/module-info.java", "module m3@1.0 { requires m5; }"),
            new Unit(SRCPATH, "m4/module-info.java", "module m4@1.0 { }"),
            new Unit(SRCPATH, "m4/q/B.java",         "ERROR"),
            new Unit(SRCPATH, "m5/module-info.java", "module m5@1.0 { }"),
            new Unit(SRCPATH, "m5/q/B.java",         "package q; public class B { }")
        ),

        new Test(
            "version in requires; multiple versions available; expect m1@1.0, m3@1.0, m5@1.0",
            new Unit(CMDLINE, "m11/p/A.java",         "package p; class A { q.B b; }"),
            new Unit(SRCPATH, "m11/module-info.java", "module m1@1.0 { requires m3@1.0; }"),
            new Unit(SRCPATH, "m21/module-info.java", "module m2@1.0 { requires m4@1.0; }"),
            new Unit(SRCPATH, "m21/q/B.java",         "ERROR"),
            new Unit(SRCPATH, "m22/module-info.java", "module m2@2.0 { requires m4@2.0; }"),
            new Unit(SRCPATH, "m22/q/B.java",         "ERROR"),
            new Unit(SRCPATH, "m31/module-info.java", "module m3@1.0 { requires m5@1.0; }"),
            new Unit(SRCPATH, "m32/module-info.java", "module m3@2.0 { requires m5@2.0; }"),
            new Unit(SRCPATH, "m41/module-info.java", "module m4@1.0 { }"),
            new Unit(SRCPATH, "m41/q/B.java",         "ERROR"),
            new Unit(SRCPATH, "m42/module-info.java", "module m4@2.0 { }"),
            new Unit(SRCPATH, "m42/q/B.java",         "ERROR"),
            new Unit(SRCPATH, "m51/module-info.java", "module m5@1.0 { }"),
            new Unit(SRCPATH, "m51/q/B.java",         "package q; public class B { }"),
            new Unit(SRCPATH, "m52/module-info.java", "module m5@2.0 { }"),
            new Unit(SRCPATH, "m52/q/B.java",         "ERROR")
        ),

        new Test(
            "no versions, simple cycle (m3,m5); expect m1, m3, m5",
            new Unit(CMDLINE, "m1/p/A.java",         "package p; class A { q.B b; }"),
            new Unit(SRCPATH, "m1/module-info.java", "module m1 { requires m3; }"),
            new Unit(SRCPATH, "m2/module-info.java", "module m2 { requires m4; }"),
            new Unit(SRCPATH, "m2/q/B.java",         "ERROR"),
            new Unit(SRCPATH, "m3/module-info.java", "module m3 { requires m5; }"),
            new Unit(SRCPATH, "m4/module-info.java", "module m4 { }"),
            new Unit(SRCPATH, "m4/q/B.java",         "ERROR"),
            new Unit(SRCPATH, "m5/module-info.java", "module m5 { requires m3;}"),
            new Unit(SRCPATH, "m5/q/B.java",         "package q; public class B { }")
        ),

        new Test(
            "no versions, bigger cycle (m3,m4,m5); expect m1, m3, m4, m5",
            new Unit(CMDLINE, "m1/p/A.java",         "package p; class A { q.B b; }"),
            new Unit(SRCPATH, "m1/module-info.java", "module m1 { requires m3; }"),
            new Unit(SRCPATH, "m2/module-info.java", "module m2 { requires m4; }"),
            new Unit(SRCPATH, "m2/q/B.java",         "ERROR"),
            new Unit(SRCPATH, "m3/module-info.java", "module m3 { requires m4; }"),
            new Unit(SRCPATH, "m4/module-info.java", "module m4 { requires m5; }"),
            new Unit(SRCPATH, "m5/module-info.java", "module m5 { requires m3;}"),
            new Unit(SRCPATH, "m5/q/B.java",         "package q; public class B { }")
        ),

        new Test(
            "no versions, two cycles (m3,m4),(m5,m6); expect m1, m3, m4, m5,m6",
            new Unit(CMDLINE, "m1/p/A.java",         "package p; class A { q.B b; }"),
            new Unit(SRCPATH, "m1/module-info.java", "module m1 { requires m3; }"),
            new Unit(SRCPATH, "m2/module-info.java", "module m2 { requires m4; }"),
            new Unit(SRCPATH, "m2/q/B.java",         "ERROR"),
            new Unit(SRCPATH, "m3/module-info.java", "module m3 { requires m4; requires m5; }"),
            new Unit(SRCPATH, "m4/module-info.java", "module m4 { requires m3; }"),
            new Unit(SRCPATH, "m5/module-info.java", "module m5 { requires m6;}"),
            new Unit(SRCPATH, "m6/module-info.java", "module m6 { requires m5;}"),
            new Unit(SRCPATH, "m6/q/B.java",         "package q; public class B { }")
        ),

        new Test(
            "no versions, complex cycle (m3,m4,m5,m6); expect m1, m3, m4, m5,m6",
            new Unit(CMDLINE, "m1/p/A.java",         "package p; class A { q.B b; }"),
            new Unit(SRCPATH, "m1/module-info.java", "module m1 { requires m3; }"),
            new Unit(SRCPATH, "m2/module-info.java", "module m2 { requires m4; }"),
            new Unit(SRCPATH, "m2/q/B.java",         "ERROR"),
            new Unit(SRCPATH, "m3/module-info.java", "module m3 { requires m4; requires m5; }"),
            new Unit(SRCPATH, "m4/module-info.java", "module m4 { requires m3; }"),
            new Unit(SRCPATH, "m5/module-info.java", "module m5 { requires m6;}"),
            new Unit(SRCPATH, "m6/module-info.java", "module m6 { requires m3; requires m5;}"),
            new Unit(SRCPATH, "m6/q/B.java",         "package q; public class B { }")
        ),
    };

    public static void main(String[] args) throws Exception {
        new ModuleResolverTest03().run();
    }

    void run() throws Exception {
        for (Test test: tests) {
            try {
                test(test);
            } catch (Throwable t) {
                t.printStackTrace();
                errors++;
            }
        }

        if (errors == 0)
            System.out.println(count + " tests passed");
        else
            throw new Exception(errors + "/" + count + " tests failed");
    }

    void test(Test t) throws IOException {
        System.err.println("Test " + (++count) + " " + t.desc);

	if (t.desc.contains("provides")) {
	    System.err.println("TEST SKIPPED: JIGSAW DOES NOT YET SUPPORT \"provides\".");
	    return;
        }

        File testDir = new File("test" + count);
        srcDir = new File(testDir, "src");
        modulesDir = new File(testDir, "modules");
        resetDirs(srcDir, modulesDir);

        List<String> opts = new ArrayList<String>();
        add(opts, "-source", "7");
        add(opts, "-modulepath", modulesDir.getPath());
        add(opts, "-d", modulesDir.getPath());

        boolean srcPath = false;
        List<File> files = new ArrayList<File>();

        for (Unit u: t.units) {
            File f = createFile(u.path, u.text);
            switch (u.kind) {
                case CMDLINE: files.add(f); break;
                case SRCPATH: srcPath = true; break;
            }
        }

        if (srcPath)
            add(opts, "-sourcepath", srcDir.getPath());

        compile(opts, files);

        for (Unit u: t.units) {
            File f = new File(modulesDir, replaceExtension(u.path, ".class"));
            if (u.text.equals("ERROR")) {
                if (f.exists())
                    error("unexpected output found: " + f);
            } else {
                if (!f.exists())
                    error("expected file not found: " + f);
            }
        }
    }

    <T> void add(List<T> list, T... items) {
        for (T t: items)
            list.add(t);
    }

    String replaceExtension(String path, String extn) {
        int dot = path.lastIndexOf(".");
        return path.substring(0, dot) + extn;
    }

    /**
     * Compile a list of files.
     */
    void compile(List<String> opts, List<File> files) {
        List<String> argList = new ArrayList<String>();
        argList.addAll(opts);
        for (File f: files)
            argList.add(f.getPath());

        System.err.println("compile: " + argList);

        String[] args = argList.toArray(new String[argList.size()]);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        int rc = com.sun.tools.javac.Main.compile(args, pw);
        pw.close();

        String out = sw.toString();
        if (out.trim().length() > 0)
            System.err.println(out);
        if (rc != 0)
            throw new Error("compilation failed: rc=" + rc);
    }

    /**
     * Create a test file with given content if the content is not null.
     */
    File createFile(String path, String body) throws IOException {
        if (body == null)
            return null;
        File file = new File(srcDir, path);
        file.getAbsoluteFile().getParentFile().mkdirs();
        FileWriter out = new FileWriter(file);
        out.write(body);
        out.close();
        return file;
    }

    /**
     * Set up empty directories.
     */
    void resetDirs(File... dirs) {
        for (File dir: dirs) {
            if (dir.exists())
                deleteAll(dir);
            dir.mkdirs();
        }
    }

    /**
     * Delete a file or a directory (including all its contents).
     */
    boolean deleteAll(File file) {
        if (file.isDirectory()) {
            for (File f: file.listFiles())
                deleteAll(f);
        }
        return file.delete();
    }

    /**
     * Report an error.
     */
    void error(String msg, String... more) {
        System.err.println("error: " + msg);
        for (String s: more)
            System.err.println(s);
        errors++;
    }

    int count;
    int errors;
    File srcDir;
    File modulesDir;
}
