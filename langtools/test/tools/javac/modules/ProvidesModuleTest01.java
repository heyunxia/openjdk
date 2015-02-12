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
 * @summary Tests for "provides module-name;"
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

import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPool.CONSTANT_ModuleId_info;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.ModuleProvides_attribute.View;

public class ProvidesModuleTest01 extends DirectiveTest {
    public static void main(String... args) throws Exception {
        new ProvidesModuleTest01().run();
    }

    @Test
    void basicTest() throws Exception {
        List<JavaFileObject> files = new ArrayList<JavaFileObject>();
        files.add(createFile("M1/module-info.java",
                "module M1 { provides M1a; }"));
        compile(files);

        Set<String> expect = createSet("M1a");
        Set<String> found = getAliases("M1/module-info.class", null);
        checkEqual("aliases", expect, found);
    }

    @Test
    void duplTest() throws Exception {
        List<JavaFileObject> files = new ArrayList<JavaFileObject>();
        files.add(createFile("M1/module-info.java",
                "module M1 { provides M1a; provides M1a; }"));

        List<String> expectDiags = Arrays.asList("ERROR: compiler.err.dupl.provides [M1a]");
        compile(files, expectDiags);
    }

    Set<String> getAliases(String path, String viewName) throws IOException, ConstantPoolException {
        javap(path);
        Set<String> found = new HashSet<String>();
        ClassFile cf = ClassFile.read(new File(classesDir, path));
        ConstantPool cp = cf.constant_pool;
        View v = getView(cf, viewName);
        for (int i: v.alias_table) {
            CONSTANT_ModuleId_info info = cp.getModuleIdInfo(i);
            String s = info.getName();
            if (info.version_index != 0)
                s += "@" + info.getVersion();
            found.add(s);
        }
        return found;
    }
}
