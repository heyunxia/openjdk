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

import java.io.IOException;
import java.lang.module.*;
import java.lang.reflect.Module;

/**
 * <p> Properties of the running platform </p>
 */

public final class Platform {

    private Platform() { }

    static final String BASE_MODULE_NAME = "jdk.base";

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
        for (ModuleId mid : cx.modules()) {
            if (mid.name().equals(BASE_MODULE_NAME))
                return true;
        }
        return false;
    }

    public static ModuleClassLoader getBaseModuleLoader() {
        return BootLoader.getBaseModuleLoader();
    }

    public static boolean isPlatformLoader(ClassLoader cl) {
        if (cl == null) {
            return true;
        } else if (cl instanceof Loader) {
            return isPlatformContext(((Loader)cl).context);
        } else {
            return false;
        }
    }

    /**
     * Returns the Module for the given class loaded by the VM
     * bootstrap class loader.
     */
    public static Module getPlatformModule(Class<?> c) {
        try {

            BootLoader ld = BootLoader.getBaseModuleLoader();
            Context cx = ld.context;
            ModuleId mid = cx.findModuleForLocalClass(c.getName());
            if (mid == null) {
                return null;
            }

            // Find the library from which we'll load the class
            Library lib = ld.pool.library(cx, mid);
            return ld.findModule(lib, mid);

        } catch (java.io.IOException x) {
            // ## if Module has not been defined, possibly run into
            // ## I/O error when reading module-info.
            throw new InternalError(x);
        }
    }

    /**
     * Tests if the VM is running in module mode.
     */
    public static boolean isModuleMode() {
        assert sun.misc.VM.isBooted() == true;
        return ClassLoader.getSystemClassLoader() instanceof ModuleClassLoader;
    }

}
