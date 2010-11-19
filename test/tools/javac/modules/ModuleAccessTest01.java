/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6802521
 * @summary add support for modules: test basic use of module access modifier
 */

import java.io.*;
import java.util.*;

/*
 * Test access to items in a module for combinations of:
 * -- referencing module
 * -- type of item being referenced
 * As a control, we verify that access to the items fails
 * when they have package access, but succeeds with module
 * access.
 *
 * Verify that compilation errors are generated or that
 * compilation is successful.
 */
public class ModuleAccessTest01
{
    enum AccessKind {
        PACKAGE(""),
        MODULE("module");
        AccessKind(String s) {
            text = s;
        }
        final String text;
    }

    enum ItemKind {
        CLASS("p.Ref.C r = null;"),
        CONSTR("new p.Ref();"),
        FIELD("int i = ref.field;"),
        METHOD("int i = ref.method();");
        ItemKind(String s) {
            text = s;
        }
        final String text;
    };

    enum ModuleKind {
        M1("m1", "p1"),
        M2("m2", "p2"),
        UNNAMED("", "");
        ModuleKind(String m, String p) {
            moduleName = m;
            packageName = p;
        }
        String moduleDir() { return (moduleName.equals("") ? "anon" : moduleName) + "/"; }
        String moduleDecl() { return "module " + moduleName + " { }"; }
        String packageDir() { return (packageName.equals("") ? "" : packageName + "/"); }
        String packageDecl() { return (packageName.equals("") ? "" : "package " + packageName + ";"); }
        final String moduleName;
        final String packageName;
    };

    public static void main(String... args) throws Exception {
        new ModuleAccessTest01().run();
    }

    public void run() throws Exception {
        for (ItemKind ik: ItemKind.values()) {
            for (ModuleKind mk: ModuleKind.values()) {
                for (AccessKind ak: AccessKind.values()) {
                    test(ik, mk, ak);
                }
            }
        }

        if (errors == 0)
            System.out.println(count + " tests passed");
        else
            throw new Exception(errors + "/" + count + " tests failed");
    }

    void test(ItemKind ik, ModuleKind mk, AccessKind ak) throws Exception {
        System.out.println("Test " + (++count) + ": " + ik + " " + mk + " " + ak);

        File testDir = new File("test" + count);
        srcDir = new File(testDir, "src");
        classesDir = new File(testDir, "classes");
        resetDirs(srcDir, classesDir);

        List<File> files = new ArrayList<File>();

        // create a reference class in module m1, package p
        String[] refBody = {
            "package p;",
            "public class Ref {",
            "    " + ak.text + " Ref() { }",
            "    " + ak.text + " int field;",
            "    " + ak.text + " int method() { return 0; }",
            "    " + ak.text + " class C { }",
            "}"
        };
        files.add(createFile(srcDir, "m1/p/Ref.java", join(refBody)));
        files.add(createFile(srcDir, "m1/module-info.java", "module m1 { }"));

        // create a test class in module mk.moduleName, package mk.packageName
        String[] testBody = {
            mk.packageDecl(),
            "class Test {",
            "    void m(p.Ref ref) {",
            "        " + ik.text,
            "    }",
            "}"
        };
        files.add(createFile(srcDir, mk.moduleDir() + mk.packageDir() + "Test.java", join(testBody)));
        switch (mk) {
        case UNNAMED:   // no module-info
        case M1:        // already generated for ref
            break;
        default:
            files.add(createFile(srcDir, mk.moduleDir() + "module-info.java", mk.moduleDecl()));
        }

        System.out.println("files: " + files);

        boolean expectError = (ak == AccessKind.PACKAGE || mk != ModuleKind.M1);

        compile(files, classesDir, Arrays.asList("-modulepath", classesDir.getPath()), expectError);
    }

    void compile(List<File> files, File classOutDir, List<String> extraOpts, boolean expectError) {
        List<String> options = new ArrayList<String>();
        options.add("-XDrawDiagnostics");
        options.addAll(Arrays.asList("-source", "7", "-d", classOutDir.getPath()));
        if (extraOpts != null)
            options.addAll(extraOpts);
        for (File f: files)
            options.add(f.getPath());

        String[] opts = options.toArray(new String[options.size()]);
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        int rc = com.sun.tools.javac.Main.compile(opts, out);
        out.close();

        if (expectError) {
            if (rc == 0)
                error(files, "compilation succeeded unexpectedly");
            else {
                //log(files, "compilation failed as expected", sw.toString());
                //log(files, "OK");
            }
        }
        else {
            if (rc != 0)
                error(files, "compilation failed unexpectedly", sw.toString());
            else {
                //log(files, "OK");
            }
        }
    }

    /**
     * Join lines with newline.
     */
    String join(String... lines) {
        StringBuilder sb = new StringBuilder();
        for (String s: lines) {
            sb.append(s);
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Create a test file with given content.
     */
    File createFile(File dir, String path, String body) throws IOException {
        File file = new File(dir, path);
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
    void error(List<File> files, String msg, String... more) {
        System.out.println("test " + files);
        System.out.println("error: " + msg);
        for (String s: more)
            System.out.println(s);
        errors++;
        //throw new Error(msg);
    }

    /**
     * Report an error.
     */
    void error(String msg, String... more) {
        System.out.println("error: " + msg);
        for (String s: more)
            System.out.println(s);
        errors++;
        throw new Error(msg);
    }

    /**
     * Log a message.
     */
    void log(List<File> files, String... msgs) {
        System.out.println("test " + files);
        for (String s: msgs)
            System.out.println(s);
    }

    int count;
    int errors;
    File srcDir;
    File classesDir;

}
