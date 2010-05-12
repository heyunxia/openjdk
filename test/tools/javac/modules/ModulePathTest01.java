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
 * @summary add support for modules: test basic use of module resolution
 */

import java.io.*;
import java.util.*;

public class ModulePathTest01
{
    enum ModuleKind { SINGLE, MULTIPLE };

    enum FileKind {
        CLASS("p", "A.java", "package p; class A { }"),
        MODULE_INFO("", "module-info.java", "module M { }");
        FileKind(String pkgName, String path, String body) {
            this.pkgName = pkgName;
            this.path = path;
            this.body = body;
        }
        final String pkgName;
        final String path;
        final String body;
    }

    enum TestKind { GOOD, BAD };

    public static void main(String... args) throws Exception {
        new ModulePathTest01().run();
    }

    public void run() throws Exception {
        for (ModuleKind mk: ModuleKind.values()) {
            for (FileKind fk: FileKind.values()) {
                for (TestKind tk: TestKind.values()) {
                    test(mk, fk, tk);
                }
            }
        }

        if (errors == 0)
            System.out.println(count + " tests passed");
        else
            throw new Exception(errors + "/" + count + " tests failed");
    }

    void test(ModuleKind mk, FileKind fk, TestKind tk) throws IOException {
        System.err.println("Test " + (++count) + ": moduleKind " + mk + ": fileKind " + fk + ": testKind " + tk);

        File testDir = new File("test" + count);
        srcDir = new File(testDir, "src");
        modulesDir = new File(testDir, "modules");
        resetDirs(srcDir, modulesDir);

        String path = "m/" + (tk == TestKind.GOOD ? fk.pkgName : "BAD") + "/" + fk.path;
        File srcFile = createFile(srcDir, path, fk.body);

        List<String> args = new ArrayList<String>();
        if (mk == ModuleKind.MULTIPLE)
            append(args, "-modulepath", modulesDir.getPath());
        append(args, "-d", modulesDir.getPath());
        append(args, "-source", "7");
        append(args, "-XDrawDiagnostics");

        String expectPath = null; // path name of expected output file
        String expectDiag = null; //
        if (mk == ModuleKind.SINGLE)
            expectPath = (fk.pkgName.equals("") ? "" : fk.pkgName + "/") + replaceExtn(fk.path, ".class");
        else if (tk == TestKind.GOOD)
            expectPath = "m/" + (fk.pkgName.equals("") ? "" : fk.pkgName + "/") + replaceExtn(fk.path, ".class");
        else if (fk == FileKind.MODULE_INFO)
            expectPath = "BAD/module-info.class"; // cannot tell bad package dir from module tag dir
        else
            expectDiag = "file.in.wrong.directory";

        Set<File> expectFiles = (expectPath == null) ?
                Collections.<File>emptySet() :
                Collections.singleton(new File(modulesDir, expectPath));

        compile(args, Arrays.asList(srcFile), expectDiag);

        Set<File> files = listClasses(modulesDir);

        checkEqual("output files", expectFiles, files);
    }

    <T> void append(List<T> list, T... items) {
        list.addAll(Arrays.asList(items));
    }

    <T> void checkEqual(String tag, T expect, T found) {
        if (expect == null ? found == null : expect.equals(found))
            return;
        error(tag + " mismatch", "expected " + expect, "found: " + found);
    }

    String replaceExtn(String path, String extn) {
        int dot = path.lastIndexOf(".");
        return path.substring(0, dot) + extn;
    }

    /**
     * Compile a list of files.
     */
    boolean compile(List<String> opts, List<File> files, String diag) {
        List<String> argList = new ArrayList<String>(opts);
        for (File f: files)
            argList.add(f.getPath());
        System.err.println("Compile: " + argList);
        String[] args = argList.toArray(new String[argList.size()]);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        int rc = com.sun.tools.javac.Main.compile(args, pw);
        pw.close();

        String out = sw.toString();
        if (out.trim().length() > 0)
            System.err.println(out);

        if (diag == null) {
            if (rc == 0)
                return true;
            else {
                error("compilation failed unexpectedly: rc=" + rc);
                return false;
            }
        } else {
            if (rc == 0) {
                error("compilation succeeded unexpectedly");
                return false;
            } else if (!out.contains(diag)) {
                error("compilation failed unexpectedly: rc=" + rc);
                return false;
            } else {
                System.err.println("compilation failed as expected");
                return true;
            }
        }
    }

    Set<File> listClasses(File dir) {
        Set<File> files = new LinkedHashSet<File>();
        listClasses(dir, files);
        return files;
    }
    // where
    private void listClasses(File dir, Set<File> files) {
        for (File f: dir.listFiles()) {
            if (f.isDirectory())
                listClasses(f, files);
            else if (f.getName().endsWith(".class"))
                files.add(f);
        }
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
    void error(String msg, String... more) {
        System.err.println("error: " + msg);
        for (String s: more)
            System.err.println(s);
        errors++;
        //throw new Error(msg);
    }

    int count;
    int errors;
    File srcDir;
    File modulesDir;
}
