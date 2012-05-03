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
 * @summary Tests for "requires module-name;"
 * @build DirectiveTest
 * @run main ProvidesModuleTest01
 */

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.tools.JavaFileObject;

import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPool.CONSTANT_ModuleQuery_info;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.ModuleRequires_attribute;

import com.sun.tools.javac.jvm.Target;

public class RequiresModuleTest01 extends DirectiveTest {
    public static void main(String... args) throws Exception {
        new RequiresModuleTest01().run();
    }

    @Test
    void basicTest() throws Exception {
        List<JavaFileObject> files = new ArrayList<JavaFileObject>();
        files.add(createFile("M1/module-info.java",
                "module M1 { requires M2; }"));
        files.add(createFile("M2/module-info.java",
                "module M2 { }"));
        compile(files);

        Set<String> expect = createSet("M2", "java/base@>=" + version);
        Set<String> found = getRequires("M1/module-info.class");
        checkEqual("required modules", expect, found);
    }

    @Test
    void duplTest() throws Exception {
        List<JavaFileObject> files = new ArrayList<JavaFileObject>();
        files.add(createFile("M1/module-info.java",
                "module M1 { requires M2; requires M2; }"));
        files.add(createFile("M2/module-info.java",
                "module M2 { }"));

        List<String> expectDiags = Arrays.asList("ERROR: compiler.err.dupl.requires [M2]");
        compile(files, expectDiags);
    }

    @Test
    void viewTest() throws Exception {
        List<JavaFileObject> files = new ArrayList<JavaFileObject>();
        files.add(createFile("M1/module-info.java",
                "module M1 { view V { requires M1; } }"));

        List<String> expectDiags = Arrays.asList("ERROR: compiler.err.requires.not.allowed.in.view []");
        compile(files, expectDiags);
    }

    Set<String> getRequires(String path) throws IOException, ConstantPoolException {
        javap(path);
        ClassFile cf = ClassFile.read(new File(classesDir, path));
        ConstantPool cp = cf.constant_pool;
        ModuleRequires_attribute attr = (ModuleRequires_attribute) cf.getAttribute(Attribute.ModuleRequires);
        Set<String> found = new HashSet<String>();
        for (ModuleRequires_attribute.Entry e: attr.module_table) {
            CONSTANT_ModuleQuery_info info = cp.getModuleQueryInfo(e.index);
            String s = info.getName();
            if (info.version_index != 0)
                s += "@" + info.getVersion();
            found.add(s);
        }
        return found;
    }

    Target target = Target.DEFAULT;
    String version = target.name.replace("1.", "");
}
