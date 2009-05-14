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
import java.lang.reflect.Method;
import java.lang.reflect.Module;
import java.io.*;
import java.util.*;

import static org.openjdk.jigsaw.Trace.*;


public final class LoaderPool {

    private Library lib;
    Library library() { return lib; }

    private Configuration config;
    Configuration config() { return config; }

    LoaderPool(Library lb, Configuration cf) {
        if (lb == null || cf == null)
            throw new IllegalArgumentException();
        lib = lb;
        config = cf;
    }

    // Our pool of module class loaders.  We use a weak set, so that
    // loaders can be garbage-collected when no longer in use.
    //
    private Set<Loader> loaders = new HashSet<Loader>();
        // ## = new WeakSet<Loader>();

    // Map from contexts to module class loaders.  References to loaders
    // are weak, as above.
    //
    private Map<Context,Loader> loaderForContext
        = new HashMap<Context,Loader>();
        // ## = new WeakValueHashMap<Context,Loader>();

    // Find a loader for the given context, or else create one
    //
    Loader findLoader(Context cx) {
        if (cx == null)
            throw new AssertionError();
        Loader ld = loaderForContext.get(cx);
        if (ld == null) {
            if (Platform.isPlatformContext(cx)) {
                ld = new BootLoader(this, cx);
            } else
                ld = new Loader(this, cx);
            loaders.add(ld);
            loaderForContext.put(cx, ld);
        }
        return ld;
    }

    Loader findLoader(String cxn) {
        Context cx = config.getContext(cxn);
        if (cx == null)
            throw new AssertionError();
        return findLoader(cx);
    }

    // Invoked by the launcher to load the main class
    //
    public static Class<?> loadClass(File libPath,
                                     ModuleIdQuery midq, String className)
        throws IOException, ClassNotFoundException
    {
        try {
            Library lb = SimpleLibrary.open(libPath, false);
            ModuleId mid = lb.findLatestModuleId(midq);
            String cn = className;
            if (cn == null) {
                // Use the module's declared main class, if any
                ModuleInfo mi = lb.readModuleInfo(mid);
                if (mi != null)
                    cn = mi.mainClass();
                else
                    throw new Error(mid + ": No main class specified");
            }
            Configuration cf = lb.readConfiguration(mid);
            if (cf == null)
                throw new Error(mid + ": Module not configured");
            Context cx = cf.findContextForModuleName(mid.name());
            if (cx == null)
                throw new ClassNotFoundException(mid.name() + ":" + cn);
            LoaderPool lp = new LoaderPool(lb, cf);
            Loader ld = lp.findLoader(cx);
            if (ld == null)
                throw new ClassNotFoundException(cn);
            return ld.findClass(mid, cn);
        } catch (IOException x) {
            // ## refactor; see Loader.cnf
            ClassNotFoundException cnfx
                = new ClassNotFoundException(midq.name() + ":" + className);
            cnfx.initCause(x);
            throw cnfx;
        }
    }

}
