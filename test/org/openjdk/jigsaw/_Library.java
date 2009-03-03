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

// Compiled and invoked by library.sh

import java.io.*;
import java.util.*;
import java.lang.module.*;
import org.openjdk.jigsaw.*;

import static java.lang.System.out;


public class _Library {

    private static File testClasses = new File("z.classes");

    private static JigsawModuleSystem ms
	= JigsawModuleSystem.instance();

    private static void eq(Object o1, Object o2) {
	if (!o1.equals(o2))
	    throw new AssertionError(o1.toString() + " : " + o2.toString());
    }

    private static void checkFooModuleInfo(ModuleInfo mi) {
	eq(mi.id().name(), "com.foo.bar");
	Version v = ms.parseVersion("1.2.3_04-5a");
	eq(mi.id().version(), v);
	eq(mi.id().version().toString(), v.toString());
    }

    public static void main(String[] args)
	throws IOException
    {

	File libPath = new File("z.lib");

	// Create
	Library lib = Library.open(libPath, true);
	out.format("%s%n", lib);

	// Check
	lib = Library.open(libPath, false);
	eq(lib.path(), libPath);
	eq(lib.majorVersion(), 0);
	eq(lib.minorVersion(), 1);

	// Install
	lib.install(testClasses, "com.foo.bar");

	// Enumerate
	lib = Library.open(libPath, false);
	final int[] n = new int[1];
	lib.visitModules(new Library.ModuleVisitor() {
		public void accept(ModuleInfo mi) {
		    checkFooModuleInfo(mi);
		    n[0]++;
		}
	    });
	if (n[0] != 1)
	    throw new RuntimeException("Wrong number of modules: " + n[0]);

	// Install multiple versions of a module
	String[] multiVersions = new String[] { "1", "1.2", "2", "3" };
	for (String v : multiVersions) {
	    lib.install(new File(testClasses,
				 "module-classes/org.multi@" + v),
			"org.multi");
	}

	// Find module ids by name
	Set<ModuleId> mids = lib.findModuleIds("org.multi");
	out.format("find: %s%n", mids);
	Set<ModuleId> emids = new HashSet<ModuleId>();
	for (String v : multiVersions)
	    emids.add(ms.parseModuleId("org.multi@" + v));
	eq(mids, emids);

	// Find module ids by query
	mids = lib.findModuleIds(new ModuleIdQuery("org.multi",
						   ms.parseVersionQuery(">1.1")));
	out.format("query: %s%n", mids);
	emids = new HashSet<ModuleId>();
	for (String v : multiVersions) {
	    if (v.equals("1"))
		continue;
	    emids.add(ms.parseModuleId("org.multi@" + v));
	}
	eq(mids, emids);

	// Find a ModuleInfo
	ModuleId foomid = ms.parseModuleId("com.foo.bar@1.2.3_04-5a");
	ModuleInfo mi = lib.findModuleInfo(foomid);
	checkFooModuleInfo(mi);
	mi = lib.findModuleInfo(ms.parseModuleId("net.none@0.99z"));
	if (mi != null)
	    throw new AssertionError();

	// Find a class
	File rcf = new File(testClasses, "com/foo/bar/Main.class");
	byte[] rbs = new byte[(int)rcf.length()];
	DataInputStream ds = new DataInputStream(new FileInputStream(rcf));
	try {
	    ds.readFully(rbs);
	} finally {
	    ds.close();
	}
	byte[] bs = lib.findClass(foomid, "com.foo.bar.Main");
	if (bs == null)
	    throw new AssertionError();
	if (!Arrays.equals(rbs, bs))
	    throw new AssertionError();
    }

}
