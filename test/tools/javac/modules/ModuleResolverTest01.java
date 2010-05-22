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
public class ModuleResolverTest01
{
    public static void main(String... args) throws Exception {
        new ModuleResolverTest01().run();
    }

    public void run() throws Exception {
        boolean[] values = { false, true };
        for (boolean modulepath : values) {
            for (boolean classpath : values) {
                for (boolean sourcepath : values) {
                    test(modulepath, classpath, sourcepath);
                }
            }
        }

        if (errors == 0)
            System.out.println(count + " tests passed");
        else
            throw new Exception(errors + "/" + count + " tests failed");
    }

    void test(boolean mp, boolean cp, boolean sp) throws IOException {
        System.err.println("Test " + (++count) + ": modulepath " + mp + "  classpath " + cp + " sourcepath " + sp);

        File srcDir = new File("test" + count, "src");
        File classesDir = new File("test" + count, "classes");
        resetDirs(srcDir, classesDir);

        boolean modular = (mp && !cp);

        String moduleId = "M@1.0";
        File srcFile = createFile(srcDir,
                    modular ? "m/module-info.java" : "module-info.java",
                    "module " + moduleId + " { }");

        List<String> args = new ArrayList<String>();
        if (cp) append(args, "-classpath", ".");
        if (sp) append(args, "-sourcepath", ".");
        if (mp) append(args, "-modulepath", ".");
        append(args, "-d", classesDir.getPath());
        append(args, "-source", "7");
        compile(args, srcFile);

        checkModuleAttribute(classesDir,
                        modular ? "m/module-info.class" : "module-info.class",
                        moduleId);
    }

    void checkModuleAttribute(File dir, String path, String moduleId) throws IOException {
        System.err.println("Checking " + path);
        try {
            ClassFile cf = ClassFile.read(new File(dir, path));
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
            error("Error accessing constant pool " + path + ": " + e);
        } catch (FileNotFoundException e) {
            error("File not found: " + path);
        } catch (IOException e) {
            error("Error reading " + path + ": " + e);
        }
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
            System.out.println(out);
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
        System.err.println("error: " + msg);
        for (String s: more)
            System.err.println(s);
        errors++;
        throw new Error(msg);
    }

    int count;
    int errors;
}



