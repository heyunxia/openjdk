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
 * @summary Simple test of "requires module-name;"
 */

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import com.sun.source.util.JavacTask;
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPool.CONSTANT_ModuleQuery_info;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.ModuleRequires_attribute;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javap.JavapTask;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.tools.StandardJavaFileManager;

public class RequiresModuleTest01 {
    public static void main(String... args) throws Exception {
        new RequiresModuleTest01().run();
    }

    void run() throws Exception {
        srcDir.mkdirs();
        classesDir.mkdirs();
        javac = JavacTool.create();
        fm = javac.getStandardFileManager(null, null, null);
        fm.setLocation(StandardLocation.SOURCE_PATH, Arrays.asList(srcDir));
        fm.setLocation(StandardLocation.MODULE_PATH, Collections.<File>emptyList());
        fm.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(classesDir));

        List<JavaFileObject> files = new ArrayList<JavaFileObject>();
        files.add(createFile("M1/module-info.java",
                "module M1 { requires M2; }"));
        files.add(createFile("M2/module-info.java",
                "module M2 { }"));
        compile(fm, files);

        checkRequiresAttribute("M1/module-info.class");

        if (errors > 0)
            throw new Exception(errors + " errors found");
    }

    void checkRequiresAttribute(String path) throws IOException, ConstantPoolException {
        javap(path);
        ClassFile cf = ClassFile.read(new File(classesDir, path));
        ConstantPool cp = cf.constant_pool;
        ModuleRequires_attribute attr = (ModuleRequires_attribute) cf.getAttribute(Attribute.ModuleRequires);
        Set<String> expect = createSet("M2", "java/base@>=" + version);
        Set<String> found = new HashSet<String>();
        for (ModuleRequires_attribute.Entry e: attr.module_table) {
            CONSTANT_ModuleQuery_info info = cp.getModuleQueryInfo(e.index);
            String s = info.getName();
            if (info.version_index != 0)
                s += "@" + info.getVersion();
            found.add(s);
        }
        checkEqual("required modules", expect, found);
    }

    void compile(JavaFileManager fm, List<JavaFileObject> files) throws Exception {
        JavacTask task = javac.getTask(null, fm, null, null, null, files);
        if (!task.call())
            throw new Exception("compilation failed");
    }

    void javap(String path) {
        List<String> opts = Arrays.asList("-v");
        List<String> files = Arrays.asList(new File(classesDir, path).getPath());
        JavapTask t = new JavapTask(null, fm, null, opts, files);
        t.call();
    }

    JavaFileObject createFile(String path, final String body) throws IOException {
        File f = new File(srcDir, path);
        f.getParentFile().mkdirs();
        try (FileWriter out = new FileWriter(f)) {
            out.write(body);
        }
        return fm.getJavaFileObjects(f).iterator().next();
    }

    <T> Set<T> createSet(T... items) {
        return new HashSet<T>(Arrays.asList(items));
    }

    <T> void checkEqual(String label, Collection<T> expect, Collection<T> found) {
        if (found.equals(expect))
            return;
        System.err.println("Error: mismatch");
        System.err.println("  expected: " + expect);
        System.err.println("     found: " + found);
        errors++;
    }

    JavacTool javac;
    StandardJavaFileManager fm;
    File srcDir = new File("src");
    File classesDir = new File("classes");
    Target target = Target.DEFAULT;
    String version = target.name.replace("1.", "");
    int errors;
}
