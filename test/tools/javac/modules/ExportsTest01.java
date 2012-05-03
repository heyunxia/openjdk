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
 * @summary Tests for "exports package-name;"
 * @build DirectiveTest
 * @run main ExportsTest01
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.tools.JavaFileObject;

import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPool.CONSTANT_Utf8_info;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.ModuleProvides_attribute.View;

public class ExportsTest01 extends DirectiveTest {
    public static void main(String... args) throws Exception {
        new ExportsTest01().run();
    }

    @Test
    void basicTest() throws Exception {
        List<JavaFileObject> files = new ArrayList<JavaFileObject>();
        files.add(createFile("M1/module-info.java",
                "module M1 { exports p; }"));
        files.add(createFile("M1/p/C.java",
                "package p; public class C { }"));
        compile(files);

        Set<String> expect = createSet("p");
        Set<String> found = getExports("M1/module-info.class", null);
        checkEqual("exports", expect, found);
    }

    @Test
    void viewTest() throws Exception {
        List<JavaFileObject> files = new ArrayList<JavaFileObject>();
        files.add(createFile("M1/module-info.java",
                "module M1 { exports p; view V { exports q; } }"));
        files.add(createFile("M1/p/C.java",
                "package p; public class C { }"));
        files.add(createFile("M1/q/C.java",
                "package q; public class C { }"));
        compile(files);

        Set<String> expectDefault = createSet("p");
        Set<String> foundDefault = getExports("M1/module-info.class", null);
        checkEqual("exports", expectDefault, foundDefault);

        Set<String> expectV = createSet("p", "q");
        Set<String> foundV = getExports("M1/module-info.class", "V");
        checkEqual("exports", expectV, foundV);
    }

    @Test
    void duplTest() throws Exception {
        List<JavaFileObject> files = new ArrayList<JavaFileObject>();
        files.add(createFile("M1/module-info.java",
                "module M1 { exports p; exports p; }"));
        files.add(createFile("M1/p/C.java",
                "package p; public class C { }"));

        List<String> expectDiags = Arrays.asList("ERROR: compiler.err.dupl.exports [p]");
        compile(files, expectDiags);
    }

    Set<String> getExports(String path, String viewName) throws IOException, ConstantPoolException {
        javap(path);
        Set<String> found = new HashSet<String>();
        ClassFile cf = ClassFile.read(new File(classesDir, path));
        ConstantPool cp = cf.constant_pool;
        View v = getView(cf, viewName);
        for (int e: v.export_table) {
            CONSTANT_Utf8_info info = cp.getUTF8Info(e);
            found.add(info.value);
        }
        return found;
    }
}
