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
import com.sun.tools.classfile.*;

/*
 * Test compilation of module-info.java on the command line
 * and interaction with path options.
 */
public class ModuleResolverTest02
{
    enum Kind {
        CMD_LINE,
        SOURCE_PATH,
        CLASS_PATH,
        MODULE_PATH
    };

    static final String moduleId = "M@1.0";

    public static void main(String... args) throws Exception {
        new ModuleResolverTest02().run();
    }

    public void run() throws Exception {
        setup();

        for (Kind mk: Kind.values()) {
            for (Kind ak: Kind.values()) {
                if (mk == Kind.CMD_LINE || ak == Kind.CMD_LINE)
                    test(mk, ak);
            }
        }
    }

    // Initialize copies of src files and class files to be used in individual tests
    void setup() throws IOException {
        File srcDir = new File("ref", "src/m");
        File classesDir = new File("ref", "modules/m");
        resetDirs(srcDir, classesDir);

        File miFile = createFile(srcDir, "module-info.java", "module " + moduleId + " { }");
        File aFile  = createFile(srcDir, "p/A.java",         "package p; class A { }");

        List<String> args = new ArrayList<String>();
        append(args, "-d", classesDir.getPath());
        append(args, "-source", "7");
        compile(args, miFile, aFile);
    }

    void test(Kind mk, Kind ak) throws IOException {
        System.err.println("Test " + (++count) + ": module_info " + mk + "  class A " + ak);
        File testDir = new File("test" + count);
        File testOutDir = new File(testDir, "out");
        testOutDir.mkdirs();

        List<String> args = new ArrayList<String>();
        List<File> files = new ArrayList<File>();

        boolean multiModuleMode = (mk == Kind.MODULE_PATH || ak == Kind.MODULE_PATH);

        setup(testDir, mk, "module-info", args, files, multiModuleMode);
        setup(testDir, ak, "p/A", args, files, multiModuleMode);
        append(args, "-source", "7");
        append(args, "-d", testOutDir.getPath());

        compile(args, files.toArray(new File[files.size()]));

        List<File> expect = new ArrayList<File>();
        if (ak == Kind.CMD_LINE)
            expect.add(new File(testOutDir, (multiModuleMode ? "m/" : "") + "p/A.class"));
        if (mk == Kind.CMD_LINE || mk == Kind.SOURCE_PATH)
            expect.add(new File(testOutDir, (multiModuleMode ? "m/" : "") + "module-info.class"));

        for (File f: expect) {
            if (!f.exists())
                error("File not found: " + f);
        }
    }

    void setup(File testDir, Kind kind, String base,
            List<String> compileArgs, List<File> compileFiles,
            boolean multiModuleMode)
            throws IOException {

        switch (kind) {
            case CMD_LINE:
                compileFiles.add(new File("ref/src/m/" + base + ".java"));
                break;
            case SOURCE_PATH:
                copy("src/m/" + base + ".java", testDir);
                compileArgs.add("-sourcepath");
                compileArgs.add(testDir + "/src" + (multiModuleMode ? "" : "/m"));
                break;
            case CLASS_PATH:
                copy("modules/m/" + base + ".class", testDir);
                compileArgs.add("-classpath");
                compileArgs.add(testDir + "/modules/m");
                break;
            case MODULE_PATH:
                copy("modules/m/" + base + ".class", testDir);
                compileArgs.add("-modulepath");
                compileArgs.add(testDir + "/modules");
                break;
        }
    }

    void copy(String path, File dest) throws IOException {
        File from = new File("ref", path);
        byte[] data = new byte[(int) from.length()];
        DataInputStream in = new DataInputStream(new FileInputStream(from));
        in.readFully(data);
        in.close();

        File to = new File(dest, path);
        to.getParentFile().mkdirs();
        OutputStream out = new FileOutputStream(to);
        out.write(data);
        out.close();
    }

    void append(List<String> list, String... args) {
        list.addAll(Arrays.asList(args));
    }

    <T> void checkEqual(String tag, T expect, T found) {
        if (expect == null ? found == null : expect.equals(found))
            return;
        error(tag + " mismatch", "expected " + expect, "found: " + found);
    }

    /**
     * Compile a list of files.
     */
    void compile(List<String> opts, File... files) {
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
        if (rc != 0)
            throw new Error("compilation failed: rc=" + rc);
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
        System.out.println("error: " + msg);
        for (String s: more)
            System.out.println(s);
        errors++;
        throw new Error(msg);
    }

    int count;
    int errors;
}

