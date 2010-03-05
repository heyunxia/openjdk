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
 * @summary add support for modules: test basic use of Module attribute
 */

import java.io.*;
import java.util.*;
import com.sun.tools.classfile.*;

public class ModuleAttributeTest01 {
    /** Simple combinations of compilation units to test. */
    enum Kind {
        NONE("package P; class C { }", null),
        NAME("package P; class C { }", "module M { }"),
        NAME_AND_VERSION("package P; class C { }", "module M@1.0 { }");
        Kind(String classBody, String moduleInfoBody) {
            this.classBody = classBody;
            this.moduleInfoBody = moduleInfoBody;
        }
        final String classBody;
        final String moduleInfoBody;
    };


    public static void main(String[] args) throws Exception {
        new ModuleAttributeTest01().run();
    }

    void run() throws Exception {
        for (Kind k: Kind.values()) {
            try {
                test(k);
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

    void test(Kind k) throws Exception {
        System.out.println("Test " + (++count) + ": " + k);

        File testDir = new File("test" + count);
        srcDir = new File(testDir, "src");
        classesDir = new File(testDir, "classes");
        resetDirs(srcDir, classesDir);

        String classBody = k.classBody;
        String moduleInfoBody = k.moduleInfoBody;

        List<File> files = new ArrayList<File>();
        addFile(files, createFile("C.java", classBody));
        addFile(files, createFile("module-info.java", moduleInfoBody));
        compile(files);
        String moduleId = getModuleId(moduleInfoBody);
        checkModuleAttribute("P/C.class", null); // Module attribute no longer written in class file
        if (moduleInfoBody != null)
            checkModuleAttribute("module-info.class", moduleId);
    }

    String getModuleId(String moduleBody) {
        if (moduleBody == null)
            return null;
        String[] tokens = moduleBody.split(" +");
        return tokens[1];
    }

    void checkModuleAttribute(String file, String moduleId) throws IOException {
        System.err.println("Checking " + file);
        try {
            ClassFile cf = ClassFile.read(new File(classesDir, file));
            Module_attribute attr = (Module_attribute) cf.getAttribute(Attribute.Module);
            if (attr == null) {
                if (moduleId != null)
                    error("Module attribute not found; expected " + moduleId);
            } else {
                if (moduleId == null) {
                    error("Unexpected module attribute found: " + attr);
                } else {
                    String name, version;
                    int sep = moduleId.indexOf("@");
                    if (sep == -1) {
                        name = moduleId;
                        version = null;
                    } else {
                        name = moduleId.substring(0, sep);
                        version = moduleId.substring(sep + 1);
                    }
                    ConstantPool cp = cf.constant_pool;
                    checkEqual("module name", name, attr.getModuleName(cp));
                    checkEqual("module version", version, attr.getModuleVersion(cp));
                }
            }
        } catch (ConstantPoolException e) {
            error("Error accessing constant pool " + file + ": " + e);
        } catch (IOException e) {
            error("Error reading " + file + ": " + e);
        }
    }

    <T> void checkEqual(String tag, T expect, T found) {
        if (expect == null ? found == null : expect.equals(found))
            return;
        error(tag + " mismatch", "expected " + expect, "found: " + found);
    }

    /**
     * Compile a list of files.
     */
    void compile(List<File> files) {
        List<String> options = new ArrayList<String>();
        options.addAll(Arrays.asList("-source", "7", "-d", classesDir.getPath()));
        for (File f: files)
            options.add(f.getPath());

        String[] opts = options.toArray(new String[options.size()]);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        int rc = com.sun.tools.javac.Main.compile(opts, pw);
        pw.close();

        String out = sw.toString();
        if (out.trim().length() > 0)
            System.err.println(out);
        if (rc != 0)
            throw new Error("compilation failed: rc=" + rc);
    }

    /**
     * Add a file to a list if the file is not null.
     */
    void addFile(List<File> files, File file) {
        if (file != null)
            files.add(file);
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
    File srcDir = new File("tmp", "src"); // use "tmp" to help avoid accidents
    File classesDir = new File("tmp", "classes");
}


