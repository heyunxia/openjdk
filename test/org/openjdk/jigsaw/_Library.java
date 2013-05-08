/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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

// Compiled and invoked by library.sh

import java.io.*;
import java.util.*;
import java.lang.module.*;
import org.openjdk.jigsaw.*;

import static java.lang.System.out;


public class _Library {

    private static File testModules = new File("z.modules");

    private static boolean verbose = System.getenv("JIGSAW_TRACE") != null;

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
        eq(mi.defaultView().permits(), Arrays.asList("com.foo.buz", "com.oof.byz"));
        eq(mi.defaultView().mainClass(), "com.foo.bar.Main");

        List<String> aliases = new ArrayList<>();
        List<String> aliasNames = new ArrayList<>();
        for (ModuleId mid : mi.defaultView().aliases()) {
            aliases.add(mid.toString());
            aliasNames.add(mid.name().toString());
        }
        eq(aliases, Arrays.asList("com.foo.baz@2.0", "com.foo.bez@3.4a-9"));
        eq(aliasNames, Arrays.asList("com.foo.baz", "com.foo.bez"));
    }

    public static void main(String[] args)
        throws Exception
    {

        File libPath = new File("z.lib");

        // Create
        File jhlib = new File(System.getProperty("java.home"),
                              "lib/modules");
        Library lib = SimpleLibrary.create(libPath, jhlib);
        out.format("%s%n", lib);

        // Check
        lib = SimpleLibrary.open(libPath);
        eq(lib.majorVersion(), 0);
        eq(lib.minorVersion(), 1);

        // Install
        lib.installFromManifests(Arrays.asList(Manifest.create("com.foo.bar",
                                                               testModules)));

        // Enumerate
        lib = SimpleLibrary.open(libPath);
        int n = 0;
        for (ModuleId mid : lib.listLocalModuleIds()) {
            ModuleInfo mi = lib.readModuleInfo(mid);
            checkFooModuleInfo(mi);
            if (mi.id().equals(mid))
                n++;
        }
        if (n != 1)
            throw new RuntimeException("Wrong number of modules: " + n);

        // Install multiple versions of a module
        String[] multiVersions = new String[] { "1", "1.2", "2", "3" };
        for (String v : multiVersions) {
            lib.installFromManifests(Arrays.asList(Manifest.create("org.multi")
                                                   .addClasses(new File("z.modules.org.multi@" + v))));
        }

        // Find module ids by name
        Set<ModuleId> mids = new HashSet<ModuleId>(lib.findModuleIds("org.multi"));
        out.format("find: %s%n", mids);
        Set<ModuleId> emids = new HashSet<ModuleId>();
        for (String v : multiVersions)
            emids.add(ms.parseModuleId("org.multi", v));
        eq(mids, emids);

        // Find module ids by query
        mids = new HashSet<ModuleId>(
                       lib.findModuleIds(new ModuleIdQuery("org.multi",
                                         ms.parseVersionQuery(">1.1"))));
        out.format("query: %s%n", mids);
        emids = new HashSet<ModuleId>();
        for (String v : multiVersions) {
            if (v.equals("1"))
                continue;
            emids.add(ms.parseModuleId("org.multi", v));
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
        File rcf = new File(testModules, "com.foo.bar/com/foo/bar/Main.class");
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
        List<String> pcns = lib.listLocalClasses(foomid, false);
        eq(pcns, Arrays.asList("com.foo.bar.Main"));
        List<String> acns = lib.listLocalClasses(foomid, true);
        List<String> comFooBarClasses =
            Arrays.asList("com.foo.bar.Main",
                          "com.foo.bar.Internal",
                          "com.foo.bar.Internal$Secret");
        eq(acns, comFooBarClasses);

        // Load configuration
        Configuration<Context> cf = lib.readConfiguration(foomid);
        if (verbose)
            cf.dump(System.out);
        eq(cf.roots().size(), 1);
        eq(foomid, cf.roots().iterator().next());
        // FIXME: need to filter the contexts due to jdk modules
        // eq(cf.contexts().size(), 2);
        Context cx = cf.getContext("+com.foo.bar");
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
        lib.installFromManifests(Arrays.asList(Manifest.create("net.baz.aar",
                                                               testModules)));
        ModuleId bazmid = ms.parseModuleId("net.baz.aar@9");

        // Then check its configuration
        cf = lib.readConfiguration(ms.parseModuleId("net.baz.aar@9"));
        if (verbose)
            cf.dump(System.out);
        eq(cf.roots().size(), 1);
        eq(bazmid, cf.roots().iterator().next());
        // FIXME: need to filter the contexts due to jdk modules
        // eq(cf.contexts().size(), 3);
        int cb = 0;

        // FIXME: should determine this list at runtime
        final String[] jdkModules = {
            "appletviewer", "apt", "attach", "corba.tools", "deprecated.tools",
            "debugging", "extcheck", "idlj", "jar", "jarsigner", "javac", "javadoc",
            "javah", "javap", "jaxws.tools", "jconsole", "jdb", "jhat",
            "jinfo", "jmap", "jpkg", "jrepo", "jps", "jrunscript", "jsadebugd",
            "jstack", "jstat", "jstatd", "jvmstat", "keytool", "native2ascii",
            "orbd", "pack200", "policytool", "rmi.tools", "rmic",
            "rmid", "rmiregistry", "sajdi", "schemagen", "serialver",
            "servertool", "tnameserv", "tools", "wsgen", "wsimport", "xjc",
            "kinit", "klist", "ktab"
        };
        for (Context dx : cf.contexts()) {
            if (dx.toString().startsWith("+jdk") ||
                dx.toString().startsWith("+sun"))
                continue;
            boolean skip = false;
            for (String s : jdkModules) {
                if (dx.toString().startsWith("+" + s)) {
                    skip = true;
                    break;
                }
            }
            if (skip)
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
        Library lib2 = SimpleLibrary.create(lib2path, libPath);
        lib2 = SimpleLibrary.open(lib2path);
        eq(lib2.findModuleIds("com.foo.bar"), Arrays.asList(foomid));
        eq(lib2.findModuleIds("net.baz.aar"), Arrays.asList(bazmid));

        // find classes in its parent library
        for (String cn : comFooBarClasses) {
            bs = lib2.readClass(foomid, cn);
            if (bs == null)
                throw new RuntimeException(cn + " not found through delegration");
        }
    }

}
