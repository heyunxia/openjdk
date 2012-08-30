/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
        DataInputStream dis = new DataInputStream(new FileInputStream(f));
        dis.readFully(data);

        ModuleInfo mi = ModuleSystem.base().parseModuleInfo(data);
        out.println(mi);

        ModuleSystem ms = ModuleSystem.base();
        eq(mi.id(), ms.parseModuleId("M@1.0"));

        // provides
        Set<ModuleId> ps = new HashSet<>();
        ps.add(ms.parseModuleId("M1 @ 2.0"));
        ps.add(ms.parseModuleId("M2 @ 2.1"));
        eq(mi.defaultView().aliases(), ps);

        // requires
        Set<ViewDependence> ds = new HashSet<>();
        ds.add(new ViewDependence(EnumSet.of(OPTIONAL, LOCAL),
                                  new ModuleIdQuery("N",
                                                     ms.parseVersionQuery("9.0"))));
        ds.add(new ViewDependence(EnumSet.of(OPTIONAL, LOCAL),
                                  new ModuleIdQuery("P",
                                                      // ## Should be >=9.1, but
                                                      // ## javac can't compile
                                                      // ## that at the moment
                                                      ms.parseVersionQuery("9.1"))));
        ds.add(new ViewDependence(EnumSet.of(PUBLIC),
                                  new ModuleIdQuery("Q",
                                                    ms.parseVersionQuery("5.11"))));
        VersionQuery vq = null;
	for (ViewDependence d : mi.requiresModules()) {
            if (d.modifiers().equals(EnumSet.of(SYNTHESIZED))) {
                // use the version query generated by javac since
                // it varies depending on the target release
                vq = d.query().versionQuery();
                if (!d.query().name().equals("java.base") || vq == null) { 
                    throw new AssertionError("Synthesized dependence: " + 
                        d + " not the default platform module");
                }
            }
        }
        ds.add(new ViewDependence(EnumSet.of(SYNTHESIZED),
                                  new ModuleIdQuery("java.base", vq)));
        eq(mi.requiresModules(), ds);

        // permits
        eq(mi.defaultView().permits(),
           new HashSet<>(Arrays.asList("A", "B")));

        // main class
        ok(mi.defaultView().mainClass().equals("M.X.Y.Main"));

    }

}
