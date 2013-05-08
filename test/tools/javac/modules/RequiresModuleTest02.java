/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @summary Tests for requires finding modules in different locations
 * @run main RequiresModuleTest02
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RequiresModuleTest02 {
    static PrintStream out = System.out;

    public static void main(String... args) throws Exception {
        new RequiresModuleTest02().run();
    }

    enum PathKind {
        SOURCEPATH,
        MODULEPATH,
        MODULELIB
    }

    enum RequireKind {
        MODULE,
        ALIAS,
        VIEW
    }

    void run() throws Exception {
        setup();
        for (PathKind pk: PathKind.values()) {
            for (RequireKind rk: RequireKind.values()) {
                test(pk, rk);
            }
        }

        out.println(testCount + " tests" + ((errorCount == 0) ? "" : ", " + errorCount + " errors"));
        if (errorCount > 0)
            throw new Exception(errorCount + " errors found");
    }

    void setup() throws Exception {
        srcDir = new File("src");
        classesDir = new File("classes");
        classesDir.mkdirs();
        moduleLib = new File("mlib");

        List<File> files = Arrays.asList(
            // module M1
            createFile(srcDir, "M1/module-info.java",
                "module M1@1.0 {\n" +
                "    provides A1;\n" +
                "    view V1 { }\n" +
                "}")
        );

        List<String> args = new ArrayList<String>();
        args.add("-d");
        args.add(classesDir.getPath());
        args.add("-modulepath");
        args.add(classesDir.getPath());
        for (File f: files)
            args.add(f.getPath());
        compile(args);

        jmod(Arrays.asList("create", "-L", moduleLib.getPath()));
        jmod(Arrays.asList("install", "-L", moduleLib.getPath(),
                classesDir.getPath(), "M1"));
    }

    void test(PathKind pk, RequireKind rk) throws Exception {
        out.println("Test pk:" + pk + " rk:" + rk);
        if (rk == RequireKind.ALIAS) {
            out.println(".... skipped until Jigsaw supports aliases");
            return;
        }
        testCount++;

        File testDir = new File(pk + "-" + rk );
        File testSrcDir = new File(testDir, "src");
        File testClassesDir = new File(testDir, "classes");
        testClassesDir.mkdirs();

        String requires;
        switch (rk) {
            case MODULE:    requires = "M1"; break;
            case ALIAS:     requires = "A1"; break;
            case VIEW:      requires = "V1"; break;
            default: throw new IllegalArgumentException();
        }
        List<File> files = Arrays.asList(
            createFile(testSrcDir, "MT/module-info.java",
                "module MT@1.0 {\n" +
                "    requires " + requires + ";\n" +
                "}")
        );

        List<String> args = new ArrayList<String>();
        args.add("-d");
        args.add(testClassesDir.getPath());
        switch (pk) {
            case SOURCEPATH:
                args.add("-modulepath");
                args.add(testClassesDir.getPath());
                args.add("-sourcepath");
                args.add(srcDir.getPath());
                break;
            case MODULEPATH:
                args.add("-modulepath");
                args.add(classesDir.getPath()
                        + File.pathSeparator
                        + testClassesDir.getPath());
                break;
            case MODULELIB:
                args.add("-modulepath");
                args.add(testClassesDir.getPath());
                args.add("-L");
                args.add(moduleLib.getPath());
                break;
            default: throw new IllegalArgumentException();
        }
        for (File f: files)
            args.add(f.getPath());

        compile(args, 0);
    }

    File createFile(File srcDir, String path, final String body) throws IOException {
        File f = new File(srcDir, path);
        f.getParentFile().mkdirs();
        try (FileWriter out = new FileWriter(f)) {
            out.write(body);
        }
        return f;
    }

    void compile(List<String> args) throws Exception {
        int rc = com.sun.tools.javac.Main.compile(args.toArray(new String[args.size()]));
        if (rc != 0)
            throw new Exception("compilation failed");
    }

    void compile(List<String> args, int expect_rc) throws Exception {
        int rc = com.sun.tools.javac.Main.compile(args.toArray(new String[args.size()]));
        if (rc == expect_rc) {
            out.println("compilation " + (rc == 0 ? "succeeded" : "failed") + " as expected");
        } else {
            error("compilation " + (rc == 0 ? "succeeded" : "failed") + " unexpectedly");
        }
    }

    void jmod(List<String> args) throws Exception {
	// for now, use jmod via CLI; eventually should use API
        File javaHome = new File(System.getProperty("java.home"));
        File jmod = new File(new File(javaHome, "bin"), "jmod");
        List<String> cmdArgs = new ArrayList<String>();
        cmdArgs.add(jmod.getPath());
        cmdArgs.addAll(args);
        Process p = new ProcessBuilder()
                .command(cmdArgs)
                .redirectErrorStream(true)
                .start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null)
                out.println(line);
            int rc = p.waitFor();
            if (rc != 0)
                throw new Exception("jmod failed: rc=" + rc);
        }
    }

    void error(String msg) {
        out.println("ERROR: " + msg);
        errorCount++;
    }

    File srcDir;
    File classesDir;
    File moduleLib;

    int testCount;
    int errorCount;
}
