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
 * @summary add support for modules: test basic use of ModulePermits attribute
 */

import java.io.*;
import java.util.*;
import com.sun.tools.classfile.*;

public class ModulePermitsAttributeTest01 {
    enum Kind { LIST, DISTINCT };

    public static void main(String[] args) throws Exception {
        new ModulePermitsAttributeTest01().run();
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

    void test(Kind kind) throws Exception {
        System.err.println("Test " + kind);
        reset();
        try {
            String[] modules = { "M1", "M2.N2", "M3.N3.O3@1.0", "M4@4.0" };

            List<String> permitsList = new ArrayList<String>();
            for (String m: modules) {
                test(kind, m, permitsList);
                permitsList.add(getModuleName(m));
            }
        } catch (Throwable t) {
            t.printStackTrace();
            errors++;
        }

    }

    void test(Kind kind, String moduleId, List<String> permitsList) throws Exception {
        // do not reset on each test case so that we can reuse the previously
        // generated module classes
        count++;
        File f = createFile(kind, moduleId, permitsList);
        compile(Arrays.asList(f));
        checkPermitsAttribute(getModuleName(moduleId), permitsList);
    }

    File createFile(Kind kind, String moduleId, List<String> permitsList) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("module " + moduleId + " {");
        if (permitsList.size() > 0) {
            switch (kind) {
                case DISTINCT:
                    for (String p: permitsList) {
                        sb.append(" permits " + p + ";");
                    }
                    break;
                case LIST:
                    String sep = " permits ";
                    for (String p: permitsList) {
                        sb.append(sep);
                        sb.append(p);
                        sep = ", ";
                    }
                    sb.append("; ");
            }
        }
        sb.append(" }");
        return createFile("module-info.java", sb.toString());
    }

    private static final char FS = File.separatorChar;

    void checkPermitsAttribute(String moduleName, List<String> permitsList) {
        String file = moduleName.replace('.', FS) + FS + "module-info.class";
        System.err.println("Checking " + file);
        try {
            ClassFile cf = ClassFile.read(new File(classesDir, file));
            ModulePermits_attribute attr =
                    (ModulePermits_attribute) cf.getAttribute(Attribute.ModulePermits);
            if (attr == null) {
                if (permitsList.size() > 0)
                    error("ModulePermits attribute not found; expected " + permitsList);
            } else {
                if (permitsList.size() == 0) {
                    error("Unexpected module attribute found: " + attr);
                } else {
                    ConstantPool cp = cf.constant_pool;
                    List<String> attrList = new ArrayList<String>();
                    for (int i = 0; i < attr.permits_length; i++) {
                        attrList.add(cp.getUTF8Value(attr.permits_table[i]));
                    }
                    checkEqual("permits", permitsList, attrList);
                }
            }
        } catch (ConstantPoolException e) {
            error("Error accessing constant pool " + file + ": " + e);
        } catch (IOException e) {
            error("Error reading " + file + ": " + e);
        }
    }

    static String getModuleId(String name, String version) {
        return (version == null ? name : name + "@" + version);
    }

    static String getModuleName(String moduleId) {
        int sep = moduleId.indexOf("@");
        return (sep == -1 ? moduleId : moduleId.substring(0, sep).trim());
    }

    static String getModuleVersion(String moduleId) {
        int sep = moduleId.indexOf("@");
        return (sep == -1 ? moduleId : moduleId.substring(sep+1).trim());
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

    <T> void checkEqual(String tag, T expect, T found) {
        if (expect == null ? found == null : expect.equals(found))
            return;
        error(tag + " mismatch", "expected " + expect, "found: " + found);
    }

    /**
     * Set up empty src and classes directories for a test.
     */
    void reset() {
        resetDir(srcDir);
        resetDir(classesDir);
    }

    /**
     * Set up an empty directory.
     */
    void resetDir(File dir) {
        if (dir.exists())
            deleteAll(dir);
        dir.mkdirs();
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
