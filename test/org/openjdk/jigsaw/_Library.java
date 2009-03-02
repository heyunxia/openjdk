/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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

/* @test
 * @summary org.openjdk.jigsaw.Library unit test
 * @library eg
 * @compile -source 7 eg/com/foo/bar/module-info.java eg/com/foo/bar/Main.java
 * @run main _Library
 */

import java.io.*;
import java.lang.module.*;
import org.openjdk.jigsaw.*;

import static java.lang.System.out;


public class _Library {

    private static File testClasses
	= new File(System.getProperty("test.classes", "."));

    private static void eq(Object o1, Object o2) {
	if (!o1.equals(o2))
	    throw new AssertionError(o1.toString() + " : " + o2.toString());
    }

    public static void main(String[] args)
	throws IOException
    {

	File libPath = new File("lib");

	Library lib = Library.open(libPath, true);
	out.format("lib: %s%n", lib);

	lib = Library.open(libPath, false);
	eq(lib.path(), libPath);
	eq(lib.majorVersion(), 0);
	eq(lib.minorVersion(), 1);

	lib.install(testClasses, "com.foo.bar");

	lib = Library.open(libPath, false);
	final int[] n = new int[1];
	lib.visitModules(new Library.ModuleVisitor() {
		public void accept(ModuleInfo mi) {
		    n[0]++;
		    eq(mi.id().name(), "com.foo.bar");
		    Version v = JigsawModuleSystem.instance().parseVersion("1.2.3_04-5a");
		    eq(mi.id().version(), v);
		    eq(mi.id().version().toString(), v.toString());
		}
	    });
	if (n[0] != 1)
	    throw new RuntimeException("Wrong number of modules: " + n[0]);

    }

}
