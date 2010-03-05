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

public class ModuleResolverTest04 {
    static class Test {
        Test(String desc, String diag, Unit... units) {
            this.desc = desc;
            this.diag = diag;
            this.units = units;
        }
        final String desc;
        final String diag;
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
            "no version available",
            "compiler.err.mdl.no.version.available",
            new Unit(CMDLINE, "m1/p/A.java",         "package p; class A { q.B b; }"),
            new Unit(SRCPATH, "m1/module-info.java", "module m1 { requires m2; }"),
            new Unit(SRCPATH, "m1/q/B.java",         "package q; public class B { }")
        ),

        new Test(
            "no unique version available",
            "compiler.err.mdl.no.unique.version.available",
            new Unit(CMDLINE, "m1/p/A.java",          "package p; class A { q.B b; }"),
            new Unit(SRCPATH, "m1/module-info.java",  "module m1 { requires m2; }"),
            new Unit(SRCPATH, "m21/module-info.java", "module m2@1.0 { }"),
            new Unit(SRCPATH, "m21/q/B.java",         "package q; public class B { }"),
            new Unit(SRCPATH, "m22/module-info.java", "module m2@2.0 { }"),
            new Unit(SRCPATH, "m22/q/B.java",         "package q; public class B { }")
        ),

        new Test(
            "required version not available",
            "compiler.err.mdl.required.version.not.available",
            new Unit(CMDLINE, "m1/p/A.java",          "package p; class A { q.B b; }"),
            new Unit(SRCPATH, "m1/module-info.java",  "module m1 { requires m2@3.0; }"),
            new Unit(SRCPATH, "m21/module-info.java", "module m2@1.0 { }"),
            new Unit(SRCPATH, "m22/module-info.java", "module m2@2.0 { }")
        ),

        new Test(
            "duplicate versions available",
            "compiler.err.mdl.duplicate.definition",
            new Unit(CMDLINE, "m1/p/A.java",           "package p; class A { q.B b; }"),
            new Unit(SRCPATH, "m1/module-info.java",   "module m1 { requires m2; }"),
            new Unit(SRCPATH, "m21a/module-info.java", "module m2@1.0 { }"),
            new Unit(SRCPATH, "m21a/q/B.java",         "package q; public class B { }"),
            new Unit(SRCPATH, "m21b/module-info.java", "module m2@1.0 { }"),
            new Unit(SRCPATH, "m22a/q/B.java",         "package q; public class B { }")
        ),

        new Test(
            "duplicate versions available (provides)",
            "compiler.err.mdl.duplicate.definition",
            new Unit(CMDLINE, "m1/p/A.java",           "package p; class A { q.B b; }"),
            new Unit(SRCPATH, "m1/module-info.java",   "module m1 { requires m2@1.0; }"),
            new Unit(SRCPATH, "m21/module-info.java",  "module m2@1.0 { }"),
            new Unit(SRCPATH, "m21/q/B.java",          "package q; public class B { }"),
            new Unit(SRCPATH, "m22/module-info.java",  "module m2@2.0 provides m2@1.0 { }"),
            new Unit(SRCPATH, "m22/q/B.java",          "package q; public class B { }")
        )
    };

    public static void main(String[] args) throws Exception {
        new ModuleResolverTest04().run();
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

        File testDir = new File("test" + count);
        srcDir = new File(testDir, "src");
        modulesDir = new File(testDir, "modules");
        resetDirs(srcDir, modulesDir);

        List<String> opts = new ArrayList<String>();
        add(opts, "-source", "7");
        add(opts, "-modulepath", modulesDir.getPath());
        add(opts, "-d", modulesDir.getPath());
        add(opts, "-XDrawDiagnostics");

	// *** NOTE ***
	// Jigsaw does not currently provide enough diagnostic info to support this test,
	// so for now, we retain it as an explicit test for the zeroMod module resolver.
	// Eventually, we will need a test of the diagnostics that are generated when
	// we fail to resolve modules, but for that to happen, Jigsaw itself must provide
	// more details.
	add(opts, "-XDzeroMod");

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

        compile(opts, files, t.diag);

        for (File f: modulesDir.listFiles()) {
            error("unexpected file found: " + f);
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
    void compile(List<String> opts, List<File> files, String diag) {
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

        if (rc == 0)
            throw new Error("compilation succeeded unexpectedly");
        else if (!out.contains(diag))
            throw new Error("compilation failed unexpectedly: rc=" + rc);
        else
            System.err.println("compilation failed as expected");
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
