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

import java.io.*;
import java.lang.module.*;
import java.lang.reflect.*;

import static org.openjdk.jigsaw.Trace.*;


public final class Launcher {

    private static JigsawModuleSystem jms = JigsawModuleSystem.instance();

    // ## Should throw (mostly) ModuleNotFoundErrors if we fail here

    // Find a root module loader
    //
    private static Loader loadModule(File libPath, ModuleIdQuery midq)
        throws IOException
    {

        Library lb = SimpleLibrary.open(libPath, false);
        ModuleId mid = lb.findLatestModuleId(midq);
        if (mid == null)
            throw new Error(midq + ": No installed module"
                            + " satisfies this query");
        ModuleInfo mi = lb.readModuleInfo(mid);
        if (mi == null)
            throw new InternalError(midq + ": Can't read module-info");
        String cn = mi.mainClass();
        if (cn == null)
            throw new Error(mid + ": Module does not specify"
                            + " a main class");
        Configuration<Context> cf = lb.readConfiguration(mid);
        if (cf == null)
            throw new Error(mid + ": Module not configured");
        Context cx = cf.getContextForModuleName(mid.name());
        if (cx == null)
            throw new InternalError(mid + ": Cannot find context");
        LoaderPool lp = new LoaderPool(lb, cf, cn);

        return lp.findLoader(cx);

    }

    public static ClassLoader launch(String midqs) {
        // ## What about the extension class loader?
        // ## Delete these and other sjlm properties when done with them
        String lmlp = System.getProperty("sun.java.launcher.module.library");
        File mlp = ((lmlp != null)
                    ? new File(lmlp)
                    : Library.systemLibraryPath());
        Loader ld = null;
        try {
            ld = loadModule(mlp, jms.parseModuleIdQuery(midqs));
        } catch (FileNotFoundException x) {
            throw new Error(mlp + ": No such library", x);
        } catch (IOException x) {
            Error y = new InternalError("Cannot create root module loader");
            y.initCause(x);
            throw y;
        }
        Thread.currentThread().setContextClassLoader(ld);
        // ## Install optional security manager here? (cf. sun.misc.Launcher)
        return ld;
    }

    public static String mainClass(ClassLoader cl) {
        return ((Loader)cl).pool.mainClass();
    }

}
