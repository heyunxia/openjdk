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
 * @summary single module
 * @build TestRunner
 * @run main DerbyTest
 */

import java.io.*;

public class DerbyTest extends TestRunner {
    public static void main(String... args) throws Exception {
	new DerbyTest().run();
    }

    void run() throws Exception {
	File mif = createFile("module-info.java", mi);
	File testf = createFile("test/Test.java", test);

	setModuleCompilationMode(ModuleCompilationMode.SINGLE_MODULE);

	setCommandLineFiles(testf);
	setExpectedClasses("test.Test");
	test();

	setCommandLineFiles(mif, testf);
	setExpectedClasses("module-info", "test.Test");
	test();

	setCommandLineFiles(testf);
	setSourcePath(srcDir);
	setExpectedClasses("module-info", "test.Test");
	test();

	File classesDir = new File("classes");
	classesDir.mkdirs();
	compile(classesDir, mif);
	setCommandLineFiles(testf);
	setClassPath(classesDir);
	setExpectedClasses("test.Test");
	test();

	summary();
    }

    String[] mi = {
	"module derby @ 7-ea {",
	"    // class test.Test;",
	"}"
    };

    String[] test = {
	"package test;",
	"import com.sun.rowset.JdbcRowSetImpl;",
	"public class Test {",
	"    public static void main(String[] args) throws Exception {",
	"	JdbcRowSetImpl rs = new JdbcRowSetImpl(\"jdbc:derby:/tmp/derbyDB;create=true\", \"a\", \"a\");",
	"    }",
	"}"
    };
}
