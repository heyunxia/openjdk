/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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

public class ModuleResolverTest05 {
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
            "no version available",
            new Unit(CMDLINE, "m1/p/A.java",         "package p; class A { }"),
            new Unit(SRCPATH, "m1/module-info.java", "module m1 { requires m2; }")
        ),

        new Test(
            "no version available",
            new Unit(CMDLINE, "m1/p/A.java",         "package p; class A { q.B b; }"),
            new Unit(SRCPATH, "m1/module-info.java", "module m1 { requires m2; }"),
            new Unit(SRCPATH, "m1/q/B.java",         "package q; public class B { }")
        ),
    };

    enum ModuleResolutionMode { 
	ZEROMOD,
	JIGSAW
    };

    public static void main(String[] args) throws Exception {
        new ModuleResolverTest05().run();
    }

    void run() throws Exception {
	File javaHome = new File(System.getProperty("java.home"));
	if (javaHome.getName().equals("jre")) javaHome = javaHome.getParentFile();
	boolean jigsaw = file(javaHome, "lib", "modules", "%jigsaw-library").exists();
	Set<ModuleResolutionMode> modes = EnumSet.of(ModuleResolutionMode.ZEROMOD);
	if (jigsaw) modes.add(ModuleResolutionMode.JIGSAW);

        for (Test test: tests) {
	    for (ModuleResolutionMode mode: modes) {
                try {
                    test(test, mode);
                } catch (Throwable t) {
                    t.printStackTrace();
                    errors++;
                }
	    }
        }

        if (errors == 0)
            System.out.println(count + " tests passed");
        else
            throw new Exception(errors + "/" + count + " tests failed");
    }

    void test(Test t, ModuleResolutionMode mrm) throws IOException {
        System.err.println("Test " + (++count) + " " + t.desc + " " + mrm);

        File testDir = new File("test" + count);
        srcDir = new File(testDir, "src");
        modulesDir = new File(testDir, "modules");
        resetDirs(srcDir, modulesDir);

        List<String> opts = new ArrayList<String>();
        add(opts, "-source", "7");
        add(opts, "-modulepath", modulesDir.getPath());
        add(opts, "-d", modulesDir.getPath());

	if (mrm == ModuleResolutionMode.ZEROMOD)
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

        compile(opts, files);

        for (File f: modulesDir.listFiles()) {
            error("unexpected file found: " + f);
        }
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

        if (rc == 0)
            throw new Error("compilation succeeded unexpectedly");
        else 
            System.err.println("compilation failed as expected, rc=" + rc);
    }

    <T> void add(List<T> list, T... items) {
        for (T t: items)
            list.add(t);
    }

    File file(File dir, String... path) {
	File f = dir;
	for (String p: path) 
	    f = new File(f, p);
	return f;
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
