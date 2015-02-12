/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Method;
import java.lang.reflect.Module;
import java.io.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;

import static org.openjdk.jigsaw.Trace.*;


/**
 * <p> A pool of {@linkplain Loader module class loaders} relative to a
 * specific {@linkplain Library module library} </p>
 */

public final class LoaderPool {

    private Library lib;
    Library library() { return lib; }

    private LibraryPool libPool;

    Library library(Context cx, ModuleId mid)
        throws IOException
    {
        return libPool.get(cx, mid);
    }

    private Configuration<Context> config;
    Configuration<Context> config() { return config; }

    // In the Jigsaw launcher we save the main class here for
    // later retrieval by the sun.launcher.LauncherHelper class
    //
    private String mainClass;

    /**
     * <p> Return the name of the main class of the application for which this
     * pool was created. </p>
     */
    public String mainClass() { return mainClass; }

    LoaderPool(Library lb, Configuration<Context> cf, String cn) {
        if (lb == null || cf == null)
            throw new IllegalArgumentException();
        lib = lb;
        libPool = new LibraryPool(lib);
        config = cf;
        mainClass = cn;
    }

    LoaderPool(Library lb, Configuration<Context> cf) {
        this(lb, cf, null);
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
    Loader findLoader(final Context cx) {
        if (cx == null)
            throw new AssertionError();
        Loader ld = loaderForContext.get(cx);
        if (ld == null) {
            ld = AccessController.doPrivileged(new PrivilegedAction<Loader>() {
                public Loader run() {
                    if (Platform.isBootContext(cx)) {
                        return new BootLoader(LoaderPool.this, cx);
                    } else {
                        return new Loader(LoaderPool.this, cx);
                    }
                }
            });
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

    Map<ClassLoader, Set<String>> findServices(Class<?> serviceInterface) {
        // ## finding services will become more efficient when service
        // provider information is moved from the context to the configuration
        String serviceInterfaceName = serviceInterface.getName();
        Map<ClassLoader, Set<String>> loaderToProviderClasses = new HashMap<>();

        for (Context cx: config.contexts()) {
            Set<String> providerClasses = cx.services().get(serviceInterfaceName);
            if (providerClasses != null) {
                // ## make call to findLoader lazy to avoid creation
                // until iterated over?
                loaderToProviderClasses.put(findLoader(cx), providerClasses);
            }
        }

        return loaderToProviderClasses;
    }

}
