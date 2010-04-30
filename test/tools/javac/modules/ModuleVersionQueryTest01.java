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
 * @summary add support for modules: test version strings
 */

import java.io.*;
import java.util.*;
import com.sun.tools.classfile.*;

public class ModuleVersionQueryTest01 {
    String[] values = {
        "0",
        "1",
        "2.3",
        "3.4alpha",
        "(2:3]",
        "[3:4]",
        "<1",
        "=2",
        ">3",
        "\"alpha\"",
        "\"alpha beta\"",
        "\"alpha,beta\""
    };


    public static void main(String[] args) throws Exception {
        new ModuleVersionQueryTest01().run();
    }

    void run() throws Exception {
        for (String v: values) {
            try {
                System.err.println("Test " + v);
                test(v);
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

    void test(String version) throws Exception {
        count++;
        reset();
        List<File> files = new ArrayList<File>();
        addFile(files, createFile("m/module-info.java", "module M { requires N @ " + version + "; }"));
        addFile(files, createFile("n/module-info.java", "module N @ " + version + " { }"));
        compile(files);
        String moduleQuery = "N@" + unquote(version);
        checkModuleRequiresAttribute("m/module-info.class", moduleQuery);
    }

    String unquote(String v) {
        if (v.startsWith("\"") && v.endsWith("\""))
            return v.substring(1, v.length() - 1);
        else
            return v;
    }

    void checkModuleRequiresAttribute(String file, String moduleQuery) throws IOException {
        System.err.println("Checking " + file);
        try {
            ClassFile cf = ClassFile.read(new File(classesDir, file));
            ModuleRequires_attribute attr = (ModuleRequires_attribute) cf.getAttribute(Attribute.ModuleRequires);
            if (attr == null) {
                if (moduleQuery != null)
                    error("ModuleRequires attribute not found; expected " + moduleQuery);
            } else {
                if (moduleQuery == null) {
                    error("Unexpected ModuleRequires attribute found: " + attr);
                } else {
                    String name, version;
                    int sep = moduleQuery.indexOf("@");
                    if (sep == -1) {
                        name = moduleQuery;
                        version = null;
                    } else {
                        name = moduleQuery.substring(0, sep);
                        version = moduleQuery.substring(sep + 1);
                    }
                    ConstantPool cp = cf.constant_pool;
                    for (int i = 0; i < attr.requires_length; i++) {
                        ConstantPool.CONSTANT_ModuleId_info r = cp.getModuleIdInfo(attr.requires_table[i].requires_index);
                        String rn = r.getName();
                        String rv = r.getVersion();
                        if (name.equals(rn) && (version == null ? rv == null : version.equals(rv)))
                            return;
                    }
                    error("module query not found");
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
        options.addAll(Arrays.asList("-source", "7", "-d", classesDir.getPath(), "-modulepath", classesDir.getPath(),
                                        "-XDzeroMod"));
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

