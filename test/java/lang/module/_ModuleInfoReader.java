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

import java.io.*;
import java.util.*;
import java.lang.module.*;

import static java.lang.System.out;
import static java.lang.module.Dependence.Modifier.*;


public class _ModuleInfoReader {

    static void ok(boolean b) {
	if (!b)
	    throw new AssertionError();
    }

    static void eq(Object o1, Object o2) {
	if (!o1.equals(o2))
	    throw new AssertionError(o1 + " : " + o2);
    }

    public static void main(String[] args) throws Exception {

	File f = new File("z.test/modules/M/module-info.class");
        
        byte[] data = new byte[(int) f.length()];
        DataInputStream d = new DataInputStream(new FileInputStream(f));
        d.readFully(data);
        
        ModuleInfo mi = ModuleSystem.base().parseModuleInfo(data);
        out.println(mi);

	ModuleSystem ms = ModuleSystem.base();
	eq(mi.id(), ms.parseModuleId("M@1.0"));

	// provides
	Set<ModuleId> ps = new HashSet<ModuleId>();
	ps.add(ms.parseModuleId("M1 @ 2.0"));
	ps.add(ms.parseModuleId("M2 @ 2.1"));
	eq(mi.provides(), ps);

	// requires
	Set<Dependence> ds = new HashSet<Dependence>();
	ds.add(new Dependence(EnumSet.of(OPTIONAL, LOCAL),
			      new ModuleIdQuery("N",
						ms.parseVersionQuery("9.0"))));
	ds.add(new Dependence(EnumSet.of(OPTIONAL, LOCAL),
			      new ModuleIdQuery("P",
                                                // ## Should be >=9.1, but
                                                // ## javac can't compile
                                                // ## that at the moment
						ms.parseVersionQuery("9.1"))));
	ds.add(new Dependence(EnumSet.of(PUBLIC),
			      new ModuleIdQuery("Q",
						ms.parseVersionQuery("5.11"))));
        ds.add(new Dependence(EnumSet.of(SYNTHETIC),
                              new ModuleIdQuery("jdk",
                                                ms.parseVersionQuery("7-ea"))));
	eq(mi.requires(), ds);

	// permits
	eq(mi.permits(),
	   new HashSet<String>(Arrays.asList("A", "B")));

	// main class
        ok(mi.mainClass().equals("M.X.Y.Main"));

    }

}
