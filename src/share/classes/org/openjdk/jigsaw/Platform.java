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

package org.openjdk.jigsaw;

import java.lang.module.*;
import java.io.*;
import java.util.*;

import static java.lang.module.Dependence.Modifier;
import static org.openjdk.jigsaw.Trace.*;


public final class Platform {

    private Platform() { }

    private static final JigsawModuleSystem jms
        = JigsawModuleSystem.instance();

    private static final ModuleId DEFAULT_PLATFORM_MID
        = jms.parseModuleId("jdk@7-ea");

    private static final ModuleIdQuery DEFAULT_PLATFORM_MIDQ
        = DEFAULT_PLATFORM_MID.toQuery();

    private static final ModuleId BOOT_MID
        = jms.parseModuleId("jdk.base@7-ea");

    public static ModuleId bootModule() { return BOOT_MID; }

    public static boolean isPlatformModuleName(String mn) {
        return (mn.equals("jdk") || mn.startsWith("jdk."));
    }

    // ## We really must do something more secure and robust here!
    static boolean isPlatformContext(org.openjdk.jigsaw.Context cx) {
        for (ModuleId mid : cx.modules()) {
            if (!isPlatformModuleName(mid.name()))
                return false;
        }
        return true;
    }

    // ## Workaround: Compiler should not add synthetic dependences
    // ## to platform modules themselves
    //
    public static void adjustPlatformDependences(ModuleInfo mi) {
        if (!isPlatformModuleName(mi.id().name()))
            return;
        for (Iterator<Dependence> i = mi.requires().iterator();
             i.hasNext();)
        {
            Dependence d = i.next();
            if (d.modifiers().contains(Modifier.SYNTHETIC)) {
                if (tracing)
                    trace(1, "removing %s -> %s", mi.id(), d);
                i.remove();
            }
        }
    }

}
