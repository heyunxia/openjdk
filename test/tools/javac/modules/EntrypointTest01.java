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
 * @summary Tests for "class class-name;"
 * @build DirectiveTest
 * @run main EntrypointTest01
 */

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.tools.JavaFileObject;

import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPool.CONSTANT_Class_info;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.ModuleProvides_attribute.View;

public class EntrypointTest01 extends DirectiveTest {
    public static void main(String... args) throws Exception {
        new EntrypointTest01().run();
    }

    void run() throws Exception {
        basicTest();
        duplTest();

        if (errors > 0)
            throw new Exception(errors + " errors found");
    }

    void basicTest() throws Exception {
        init("basic");

        List<JavaFileObject> files = new ArrayList<JavaFileObject>();
        files.add(createFile("M1/module-info.java",
                "module M1 { class p.Main; }"));
        files.add(createFile("M1/p/Main.java",
                "package p; public class Main { public static void main(String... args) { } }"));
        compile(files);

        Set<String> expect = createSet("p/Main");
        Set<String> found = getEntrypoints("M1/module-info.class", null);
        checkEqual("entrypoint", expect, found);
    }

    void duplTest() throws Exception {
        init("dupl");

        List<JavaFileObject> files = new ArrayList<JavaFileObject>();
        files.add(createFile("M1/module-info.java",
                "module M1 { class p.Main; class p.Main2; }"));
        files.add(createFile("M1/p/Main.java",
                "package p; public class Main { public static void main(String... args) { } }"));
        files.add(createFile("M1/p/Main2.java",
                "package p; public class Main2 { public static void main(String... args) { } }"));

        List<String> expectDiags = Arrays.asList("ERROR: compiler.err.dupl.entrypoint []");
        compile(files, expectDiags);
    }

    Set<String> getEntrypoints(String path, String viewName) throws IOException, ConstantPoolException {
        javap(path);
        Set<String> found = new HashSet<String>();
        ClassFile cf = ClassFile.read(new File(classesDir, path));
        ConstantPool cp = cf.constant_pool;
        View v = getView(cf, viewName);
        CONSTANT_Class_info info = cp.getClassInfo(v.entrypoint_index);
        found.add(info.getName());
        return found;
    }
}
