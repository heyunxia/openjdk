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

    private static <T> boolean eq(Collection<T> c1, Collection<T> c2) {
	return c1.containsAll(c2) && c2.containsAll(c1);
    }

    private static void checkFooModuleInfo(ModuleInfo mi) {
	eq(mi.id().name(), "com.foo.bar");
	Version v = ms.parseVersion("1.2.3_04-5a");
	eq(mi.id().version(), v);
	eq(mi.id().version().toString(), v.toString());
    }

    public static void main(String[] args)
	throws Exception
    {

	File libPath = new File("z.lib");

	// Create
        File jhlib = new File(System.getProperty("java.home"),
                              "lib/modules");
	Library lib = SimpleLibrary.open(libPath, true, jhlib);
	out.format("%s%n", lib);

	// Check
	lib = SimpleLibrary.open(libPath);
	eq(lib.majorVersion(), 0);
	eq(lib.minorVersion(), 1);

	// Install
	lib.install(Arrays.asList(Manifest.create("com.foo.bar", testClasses)));

	// Enumerate
	lib = SimpleLibrary.open(libPath);
	int n = 0;
        for (ModuleId mid : lib.listModuleIds(false)) {
            checkFooModuleInfo(lib.readModuleInfo(mid));
            n++;
        }
	if (n != 1)
	    throw new RuntimeException("Wrong number of modules: " + n);

	// Install multiple versions of a module
	String[] multiVersions = new String[] { "1", "1.2", "2", "3" };
	for (String v : multiVersions) {
	    lib.install(Arrays.asList(Manifest.create("org.multi")
                                      .addClasses(new File(testClasses,
                                                           "module-classes/org.multi@" + v))));
	}

	// Find module ids by name
	Set<ModuleId> mids = new HashSet<ModuleId>(lib.findModuleIds("org.multi"));
	out.format("find: %s%n", mids);
	Set<ModuleId> emids = new HashSet<ModuleId>();
	for (String v : multiVersions)
	    emids.add(ms.parseModuleId("org.multi@" + v));
	eq(mids, emids);

	// Find module ids by query
	mids = new HashSet<ModuleId>(lib.findModuleIds(new ModuleIdQuery("org.multi",
									 ms.parseVersionQuery(">1.1"))));
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
	ModuleInfo mi = lib.readModuleInfo(foomid);
	checkFooModuleInfo(mi);
	mi = lib.readModuleInfo(ms.parseModuleId("net.none@0.99z"));
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
	byte[] bs = lib.readClass(foomid, "com.foo.bar.Main");
	if (bs == null)
	    throw new AssertionError();
	if (!Arrays.equals(rbs, bs))
	    throw new AssertionError();

	// List classes
	List<String> pcns = lib.listClasses(foomid, false);
	eq(pcns, Arrays.asList("com.foo.bar.Main"));
	List<String> acns = lib.listClasses(foomid, true);
	eq(acns, Arrays.asList("com.foo.bar.Main",
			       "com.foo.bar.Internal",
			       "com.foo.bar.Internal$Secret"));

	// Load configuration
	Configuration cf = lib.readConfiguration(foomid);
	cf.dump(System.out);
	eq(foomid, cf.root());
	eq(cf.contexts().size(), 2);
	Context cx = cf.getContext("+com.foo.bar");
        //ModuleId jdkmid = ms.parseModuleId("jdk@7-ea");
	eq(cx.modules(), Arrays.asList(foomid));
	eq(cx.localClasses(), Arrays.asList("com.foo.bar.Main",
					    "com.foo.bar.Internal",
					    "com.foo.bar.Internal$Secret"));
	for (String cn : cx.localClasses()) {
            ModuleId mid = cx.findModuleForLocalClass(cn);
            if (!mid.equals(foomid))
                throw new AssertionError(mid + " : " + foomid + "; class " + cn);
        }

	// Install a root module that has a supplier
	lib.install(Arrays.asList(Manifest.create("net.baz.aar", testClasses)));
	ModuleId bazmid = ms.parseModuleId("net.baz.aar@9");

	// Then check its configuration
	cf = lib.readConfiguration(ms.parseModuleId("net.baz.aar@9"));
	cf.dump(System.out);
	eq(bazmid, cf.root());
	eq(cf.contexts().size(), 3);
	int cb = 0;
	for (Context dx : cf.contexts()) {
            if (dx.toString().equals("+jdk"))
                continue;
	    if (dx.toString().equals("+org.multi")) {
		ModuleId mid = ms.parseModuleId("org.multi@1");
		eq(dx.modules(), Arrays.asList(mid));
		eq(dx.localClasses(), Arrays.asList("org.multi.Tudinous"));
		for (String cn : dx.localClasses())
		    eq(dx.findModuleForLocalClass(cn), mid);
		cb |= 1;
		continue;
	    }
	    if (dx.toString().equals("+net.baz.aar")) {
		eq(dx.modules(), Arrays.asList(bazmid));
		eq(dx.localClasses(), Arrays.asList("net.baz.aar.Ness"));
		for (String cn : dx.localClasses())
		    eq(dx.findModuleForLocalClass(cn), bazmid);
		eq(dx.remotePackages().contains("org.multi"), true);
		cb |= 2;
		continue;
	    }
	    throw new AssertionError(dx.toString());
	}
	if (cb != 3)
	    throw new AssertionError(cb);

        // Delegation
        File lib2path = new File("z.lib2");
        Library lib2 = SimpleLibrary.open(lib2path, true, libPath);
        lib2 = SimpleLibrary.open(lib2path);
        eq(lib2.findModuleIds("com.foo.bar"), Arrays.asList(foomid));
        eq(lib2.findModuleIds("net.baz.aar"), Arrays.asList(bazmid));

    }

}
