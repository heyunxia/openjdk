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
 * @summary Tests for "provides service service-name with impl-name;"
 * @build DirectiveTest
 * @run main ProvidesServiceTest01
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
import com.sun.tools.classfile.ConstantPool.CONSTANT_Class_info;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.ModuleProvides_attribute;
import com.sun.tools.classfile.ModuleProvides_attribute.View;

public class ProvidesServiceTest01 extends DirectiveTest {
    public static void main(String... args) throws Exception {
        new ProvidesServiceTest01().run();
    }

    @Test
    void basicTest() throws Exception {
        List<JavaFileObject> files = new ArrayList<JavaFileObject>();
        files.add(createFile("M1/module-info.java",
                "module M1 { provides service p.S1 with p.S2; }"));
        files.add(createFile("M1/p/S1.java",
                "package p; public class S1 { }"));
        files.add(createFile("M1/p/S2.java",
                "package p; public class S2 extends S1 { }"));
        compile(files);

        Set<String> expect = createSet("p/S1 p/S2");
        Set<String> found = getServices("M1/module-info.class", null);
        checkEqual("services", expect, found);
    }

    @Test
    void duplTest() throws Exception {
        List<JavaFileObject> files = new ArrayList<JavaFileObject>();
        files.add(createFile("M1/module-info.java",
                "module M1 { provides service p.S1 with p.S2; provides service p.S1 with p.S2; }"));
        files.add(createFile("M1/p/S1.java",
                "package p; public class S1 { }"));
        files.add(createFile("M1/p/S2.java",
                "package p; public class S2 extends S1 { }"));

        List<String> expectDiags = Arrays.asList("ERROR: compiler.err.dupl.provides.service [p.S1, p.S2]");
        compile(files, expectDiags);
    }

    @Test
    void implIsAbstractTest() throws Exception {
        List<JavaFileObject> files = new ArrayList<JavaFileObject>();
        files.add(createFile("M1/module-info.java",
                "module M1 { provides service p.S1 with p.S2; }"));
        files.add(createFile("M1/p/S1.java",
                "package p; public class S1 { }"));
        files.add(createFile("M1/p/S2.java",
                "package p; public abstract class S2 extends S1 { }"));

        List<String> expectDiags = Arrays.asList("ERROR: compiler.err.service.impl.is.abstract [p.S2]");
        compile(files, expectDiags);
    }

    @Test
    void implNotPublicTest() throws Exception {
        List<JavaFileObject> files = new ArrayList<JavaFileObject>();
        files.add(createFile("M1/module-info.java",
                "module M1 { provides service p.S1 with p.S2; }"));
        files.add(createFile("M1/p/S1.java",
                "package p; public class S1 { }"));
        files.add(createFile("M1/p/S2.java",
                "package p; class S2 extends S1 { }"));

        List<String> expectDiags = Arrays.asList("ERROR: compiler.err.not.def.public.cant.access [p.S2, p]");
        compile(files, expectDiags);
    }

    @Test
    void implConstrNotPublicTest() throws Exception {
        List<JavaFileObject> files = new ArrayList<JavaFileObject>();
        files.add(createFile("M1/module-info.java",
                "module M1 { provides service p.S1 with p.S2; }"));
        files.add(createFile("M1/p/S1.java",
                "package p; public class S1 { }"));
        files.add(createFile("M1/p/S2.java",
                "package p; public class S2 extends S1 { private S2() { } }"));

        List<String> expectDiags = Arrays.asList("ERROR: compiler.err.service.impl.no.default.constr [p.S2]");
        compile(files, expectDiags);
    }

    @Test
    void implConstrHasArgsTest() throws Exception {
        List<JavaFileObject> files = new ArrayList<JavaFileObject>();
        files.add(createFile("M1/module-info.java",
                "module M1 { provides service p.S1 with p.S2; }"));
        files.add(createFile("M1/p/S1.java",
                "package p; public class S1 { }"));
        files.add(createFile("M1/p/S2.java",
                "package p; public class S2 extends S1 { public S2(int i) { } }"));

        List<String> expectDiags = Arrays.asList("ERROR: compiler.err.service.impl.no.default.constr [p.S2]");
        compile(files, expectDiags);
    }

    @Test
    void implIsInnerTest() throws Exception {
        List<JavaFileObject> files = new ArrayList<JavaFileObject>();
        files.add(createFile("M1/module-info.java",
                "module M1 { provides service p.S1 with p.S2.Inner; }"));
        files.add(createFile("M1/p/S1.java",
                "package p; public class S1 { }"));
        files.add(createFile("M1/p/S2.java",
                "package p; public class S2 extends S1 { public class Inner { } }"));

        List<String> expectDiags = Arrays.asList("ERROR: compiler.err.service.impl.is.inner [p.S2.Inner]");
        compile(files, expectDiags);
    }

    Set<String> getServices(String path, String viewName) throws IOException, ConstantPoolException {
        javap(path);
        Set<String> found = new HashSet<String>();
        ClassFile cf = ClassFile.read(new File(classesDir, path));
        ConstantPool cp = cf.constant_pool;
        View v = getView(cf, viewName);
        for (ModuleProvides_attribute.Service e: v.service_table) {
            CONSTANT_Class_info serviceInfo = cp.getClassInfo(e.service_index);
            CONSTANT_Class_info implInfo = cp.getClassInfo(e.impl_index);
            found.add(serviceInfo.getName() + " " + implInfo.getName());
        }
        return found;
    }
}
