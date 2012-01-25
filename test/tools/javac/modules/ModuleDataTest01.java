
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
 * @summary Tests for module data
 * @build DirectiveTest
 * @run main ModuleDataTest01
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaFileObject;

import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.ModuleData_attribute;

public class ModuleDataTest01 extends DirectiveTest {
    public static void main(String... args) throws Exception {
        new ModuleDataTest01().run();
    }

    @Override
    void run() throws Exception {
        String[] tests = { /*"", */"  ", "\n", "\nthis is module data\n" };
        for (String s: tests) {
            test(s);
        }

        if (errorCount > 0)
            throw new Exception(errorCount + " errors found");
    }

    void test(String s) throws Exception {
        String testName = "test_" +
                s.toLowerCase()
                .replace("\n", "N")
                .replace(" ",  "S")
                .replace("\t", "T");
        init(testName);

        List<JavaFileObject> files = new ArrayList<JavaFileObject>();
        files.add(createFile("M1/module-info.java",
                "module M1 { }" + s));
        compile(files);

        String expect = s.replaceAll("^[ \n\t]*", "");
        if (expect.isEmpty()) expect = null;
        String found = getModuleData("M1/module-info.class");
        checkEqual("module data", expect, found);
    }

    String getModuleData(String path) throws IOException, ConstantPoolException {
        javap(path);
        ClassFile cf = ClassFile.read(new File(classesDir, path));
        ConstantPool cp = cf.constant_pool;
        ModuleData_attribute attr = (ModuleData_attribute) cf.getAttribute(Attribute.ModuleData);
        return (attr == null) ? null : attr.getData(cp);
    }
}
