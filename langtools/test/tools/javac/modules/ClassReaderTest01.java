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
 * @summary Tests for reading module-info.class files
 * @build DirectiveTest
 * @run main ClassReaderTest01
 */

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

public class ClassReaderTest01 extends DirectiveTest {
    public static void main(String... args) throws Exception {
        new ClassReaderTest01().run();
    }

    @Test
    void basicTest() throws Exception {
        List<JavaFileObject> files = new ArrayList<JavaFileObject>();
        files.add(createFile("M1/module-info.java",
                "module M1 { exports p; }"));
        files.add(createFile("M1/p/C1.java",
                "package p; public class C1 { }"));
        files.add(createFile("M2/module-info.java",
                "module M2 { requires public M1; }"));
        fm.setLocation(StandardLocation.MODULE_PATH, Arrays.asList(classesDir));
        compile(files);

        srcDir = new File(srcDir.getParentFile(), "src2");
        srcDir.mkdirs();
        fm.setLocation(StandardLocation.SOURCE_PATH, Arrays.asList(srcDir));

        List<JavaFileObject> files2 = new ArrayList<JavaFileObject>();
        files2.add(createFile("M3/module-info.java",
                "module M3 { requires M2; }"));
        files2.add(createFile("M3/q/C2.java",
                "package q; public class C2 { void m() { new p.C1(); } }"));
        compile(files2);
    }
}
