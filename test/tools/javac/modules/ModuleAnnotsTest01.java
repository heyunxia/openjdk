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
 * @summary add support for modules: test annotations in module-info.java
 */

import java.io.*;
import java.util.*;
import com.sun.tools.classfile.*;

public class ModuleAnnotsTest01 {
    public static void main(String[] args) throws Exception {
        new ModuleAnnotsTest01().run();
    }

    void run() throws Exception {
	count++;
        File testDir = new File("test" + count);
        srcDir = new File(testDir, "src");
        classesDir = new File(testDir, "classes");
        resetDirs(srcDir, classesDir);

	File f = createFile("module-info.java", "@Deprecated module x { }");
	compile(Arrays.asList(f));
	checkAnnotations("module-info.class", "java.lang.Deprecated");

        if (errors == 0)
            System.out.println(count + " tests passed");
        else
            throw new Exception(errors + "/" + count + " tests failed");
    }

    void checkAnnotations(String file, String... annots) {
        System.err.println("Checking " + file);
        try {
            ClassFile cf = ClassFile.read(new File(classesDir, file));
            RuntimeVisibleAnnotations_attribute attr = 
	 	(RuntimeVisibleAnnotations_attribute) cf.getAttribute(Attribute.RuntimeVisibleAnnotations);
            if (attr == null) {
                error("RuntimeVisibleAnnotations attribute not found; expected " + Arrays.asList(annots));
            } else {
		Set<String> expect = new TreeSet(Arrays.asList(annots));
		Set<String> found = new TreeSet();
		for (Annotation a: attr.annotations) {
		    found.add(getAnnotName(cf.constant_pool, a));
		}
		if (!found.equals(expect)) {
		    error("mismatch\nexpected: " + expect + "\nfound: " + found);
		}
            }
        } catch (Descriptor.InvalidDescriptor e) {
            error("Invalid descriptor " + e);
        } catch (ConstantPoolException e) {
            error("Error accessing constant pool " + file + ": " + e);
        } catch (IOException e) {
            error("Error reading " + file + ": " + e);
        }
    }

    String getAnnotName(ConstantPool cp, Annotation a) throws ConstantPoolException, Descriptor.InvalidDescriptor {
    	Descriptor d = new Descriptor(a.type_index);
        return d.getFieldType(cp);
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

    File srcDir;
    File classesDir;
    int count;
    int errors;
}
