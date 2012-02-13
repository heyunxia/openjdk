/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package org.openjdk.jigsaw;

import java.lang.module.*;


public final class Platform {

    private Platform() { }

    private static final JigsawModuleSystem jms
        = JigsawModuleSystem.instance();

    private static final ModuleId BASE_MID
        = jms.parseModuleId("jdk.base@8-ea");

    public static ModuleId baseModule() { return BASE_MID; }

    private static boolean isPlatformModuleName(String mn) {
        return (mn.equals("jdk") || mn.startsWith("jdk.") ||
            mn.startsWith("sun."));
    }

    // ## We really must do something more secure and robust here!
    static boolean isPlatformContext(BaseContext cx) {
        for (ModuleId mid : cx.modules()) {
            if (!isPlatformModuleName(mid.name()))
                return false;
        }
        return true;
    }

    static boolean isBootContext(BaseContext cx) {
        String boot = baseModule().name();
        for (ModuleId mid : cx.modules()) {
            if (mid.name().equals(boot))
                return true;
        }
        return false;
    }
}
