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

// ## TODO: Work through security and concurrency issues

package org.openjdk.jigsaw;

import java.lang.module.*;
import java.lang.reflect.Module;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.security.AccessController;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.util.*;
import sun.security.util.SecurityConstants;

import static org.openjdk.jigsaw.Trace.*;


/**
 * <p> A module class loader </p>
 */

public class Loader                     // ## Should be final?
    extends ModuleClassLoader
{

    /**
     * <p> The pool whence this loader came </p>
     */
    protected final LoaderPool pool;

    /**
     * <p> The stored context loaded by this loader </p>
     */
    protected final Context context;

    private final Map<String,Module> moduleForName = new HashMap<>();

    /**
     * <p> The ids of the modules loaded by this loader so far </p>
     */
    protected final Set<ModuleId> modules = new HashSet<>();

    /**
     * <p> Construct a new loader for the given pool and context. </p>
     */
    public Loader(LoaderPool p, Context cx) {
        super(JigsawModuleSystem.instance());
        if (cx == null)
            throw new IllegalArgumentException("Null context");
        pool = p;
        context = cx;
    }

    // Primary entry point from the VM
    //
    protected Class<?> loadClass(String cn, boolean resolve)
        throws ClassNotFoundException
    {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            int i = cn.lastIndexOf('.');
            if (i != -1) {
                sm.checkPackageAccess(cn.substring(0, i));
            }
        }

        // Check the loaded-class cache first.  The VM guarantees not to invoke
        // this method more than once for any given class name, but we still
        // need to check the cache manually in case this method is invoked by
        // user code.
        //
        Class<?> c = findLoadedClass(cn);
        if (c != null) {
            if (tracing) {
                trace(0, "%s: (cache) %s", this, cn);
            }
        } else {

            // Is the requested class local or remote?  It can be one or the other,
            // but not both.
            //
            String rcxn = context.findContextForRemoteClass(cn);
            ModuleId lmid = context.findModuleForLocalClass(cn);
            if (rcxn != null && lmid != null) {
                throw new AssertionError("Class " + cn
                                         + " defined both locally and remotely");
            }

            // Find a loader, and use that to load the class
            //
            Loader ld = null;
            if (lmid != null) {
                ld = this;
                if (tracing)
                    trace(0, "%s: load %s:%s", this, lmid, cn);
            } else if (rcxn != null) {
                ld = pool.findLoader(rcxn);
                if (tracing)
                    trace(0, "%s: load %s:%s", this, rcxn, cn);
            }
            if (ld == null) {
                throw new ClassNotFoundException(cn + " : requested by " + context);
            }
            c = ld.findClass(lmid, cn);
        }
        if (resolve)
            resolveClass(c);
        return c;
    }

    // Invoked by findClass, below, and (eventually) by LoaderPool.init()
    //
    Module defineModule(ModuleId mid, byte[] bs, CodeSource cs) {
        Module m = super.defineModule(mid, bs, 0, bs.length, cs);
        moduleForName.put(mid.name(), m);
        modules.add(mid);
        return m;
    }

    private ClassNotFoundException cnf(String mn, String cn, IOException x) {
        return new ClassNotFoundException(cn + " in module " + mn, x);
    }

    Class<?> findClass(ModuleId mid, String cn)
        throws ClassNotFoundException
    {

        if (mid == null) {
            mid = context.findModuleForLocalClass(cn);
            if (mid == null)
                throw new ClassNotFoundException(context + ":" + cn);
        }

        Class<?> c = findLoadedClass(cn);
        if (c != null) {
            ModuleId cmid = c.getModule().getModuleId();
            if (cmid == null || !cmid.equals(mid))
                throw new AssertionError(cn + " previously loaded from "
                                         + cmid + "; now trying to load from "
                                         + mid);
            return c;
        }

        Module m = null;
        Library lib = null;
        try {
            // Find the library from which we'll load the class
            //
            lib = pool.library(context, mid);

            // Find this class's module
            //
            m = findModule(lib, mid);
        } catch (IOException x) {
            throw cnf(mid.name(), cn, x);
        }

        // Define the package
        //
        int i = cn.lastIndexOf('.');
        if (i >= 0) {
            String pn = cn.substring(0, i);
            Package p = getPackage(pn);
            if (p == null) {
                // ## Should we pass in additional information here?
                definePackage(pn, null, null, null, null, null, null, null);
            }
        }

        // The last step, of actually locating the class, is in a
        // separate method so that the boot loader can override it
        //
        return finishFindingClass(lib, mid, m, cn);

    }

    Module findModule(Library lib, ModuleId mid) throws IOException {
        // Have we defined this module yet?
        //
        Module m = moduleForName.get(mid.name());
        if (m != null) {
            if (!m.getModuleId().equals(mid))
                throw new AssertionError("Duplicate module in loader");
            return m;
        }

        // Define the module
        //
        try {
            final ModuleId modid = mid;
            final Library l = lib;
            m = AccessController.doPrivileged(
                new PrivilegedExceptionAction<Module>() {
                    public Module run()
                        throws IOException
                    {
                        byte[] bs = l.readLocalModuleInfoBytes(modid);
                        if (bs == null)
                            throw new AssertionError();

                        URL url;
                        try {
                            // for now the module version is not included
                            url = new URL("module:///" + modid.name());
                        } catch (Exception x) {
                            throw new AssertionError(x);
                        }
                        CodeSigner[] cs = l.readLocalCodeSigners(modid);
                        return defineModule(modid, bs,
                                            new CodeSource(url, cs));
                    }
                }
            );
            if (tracing)
                trace(0, "%s: define %s [%s]", this, mid, lib.name());
        } catch (PrivilegedActionException x) {
            throw (IOException) x.getException();
        }
        return m;
    }

    Class<?> finishFindingClass(final Library lib, final ModuleId mid,
                                Module m, final String cn)
        throws ClassNotFoundException
    {

        try {
            byte[] bs = AccessController.doPrivileged(
                new PrivilegedExceptionAction<byte[]>() {
                    public byte[] run() throws IOException {
                        return lib.readLocalClass(mid, cn);
                    }
                }
            );
            if (bs == null)
                throw new ClassNotFoundException(mid + ":" + cn);
            Class<?> c = defineClass(m, cn, bs, 0, bs.length);
            if (tracing)
                trace(0, "%s: define %s:%s [%s]", this, mid, cn, lib.name());
            return c;
        } catch (PrivilegedActionException x) {
            throw cnf(m.getName(), cn, (IOException) x.getException());
        }

    }

    protected PermissionCollection getPermissions(CodeSource cs) {
        URL u;
        if (cs != null && ((u = cs.getLocation()) != null)) {
            String path = u.getPath();
            // For now we grant all permissions to jdk.* modules
            if (path.startsWith("/jdk.")) {
                Permissions perms = new Permissions();
                perms.add(SecurityConstants.ALL_PERMISSION);
                return perms;
            }
        }
        return super.getPermissions(cs);
    }

    public boolean isModulePresent(String mn) {
        Context cx = pool.config().findContextForModuleName(mn);
        if (cx == null) return false;

        return cx == context ||
            context.remoteContexts().contains(cx.name());
    }

    /**
     * <p> Find the a map of class loader to fully-qualified service-provider
     * class names available for a given service interface. </p>
     *
     * <p> The value of the map is the set of fully-qualified service-provider
     * class names which can be loaded by the corresponding key that is the
     * class loader. </p>
     *
     * <p> The returned map is invariant to the instance of {@link Loader} and
     * is scoped to the configuration. </p>
     */
    Map<ClassLoader,Set<String>> findServices(Class<?> serviceInterface) {
        return pool.findServices(serviceInterface);
    }

    public String toString() {
        return context.name();
    }


    // -- Native libraries --

    // Native libraries are, for now, discovered at run time.
    //
    // This could be made more efficient by instead identifying them
    // at module-link time and storing a map from library names to full
    // paths.

    @Override
    protected String findLibrary(String name) {
        String fn = System.mapLibraryName(name);
        IOException iox = null;
        try {
            for (ModuleId mid : context.modules()) {
                File nlf = (pool.library(context, mid)
                            .findLocalNativeLibrary(mid, fn));
                if (nlf != null) {
                    if (tracing)
                        trace(0, "%s: lib %s", this, nlf);
                    return nlf.getAbsolutePath();
                }
            }
        } catch (IOException x) {
            iox = x;
        }
        Error e = new UnsatisfiedLinkError("No library " + fn
                                           + " in module context "
                                           + context.name());
        if (iox != null)
            e.initCause(iox);
        throw e;
    }


    // -- Resources --

    // --
    //
    // The approach taken here is simply to discover resources at run time.
    // Given a resource name we first search this loader's modules for that
    // resource; we then search the loaders for every other context in this
    // configuration.  If we think of a Jigsaw configuration as a "better
    // class path" then this isn't completely unreasonable, but it is meant
    // to be temporary.
    //
    // An eventual fuller treatment of resources will treat them more like
    // classes, resolving them statically, allowing them to be declared
    // private to a module or public, and re-exporting them from one
    // context to another when "requires public" is used.
    //
    // --

    private static interface ResourceVisitor {
        // Return null to continue the search or a URI to terminate
        // the search, returning that URI
        public URI accept(URI u) throws IOException;
    }

    private URI visitLocalResources(String rn, ResourceVisitor rv)
        throws IOException
    {
        if (rn.startsWith("/"))
            rn = rn.substring(1);
        for (ModuleId mid : context.modules()) {
            URI u = pool.library(context, mid).findLocalResource(mid, rn);
            if (u != null) {
                u = rv.accept(u);
                if (u != null)
                    return u;
            }
        }
        return null;
    }

    private URI visitResources(String rn, ResourceVisitor rv)
        throws IOException
    {
        // ## Should look up "platform" resources first,
        // ## in order to mimic current behavior
        URI u = visitLocalResources(rn, rv);
        if (u != null)
            return u;
        for (Context cx : pool.config().contexts()) {
            if (context == cx)
                continue;
            u = pool.findLoader(cx).visitLocalResources(rn, rv);
            if (u != null)
                return u;
        }
        return null;
    }

    public URL getResource(String rn) {
        try {
            if (tracing)
                trace(0, "%s: get resource %s", this, rn);

            URI u = visitResources(rn, new ResourceVisitor() {
                    public URI accept(URI u) {
                        return u;
                    }
                });
            if (u != null)
                return u.toURL();
            return null;
        } catch (IOException x) {
            // ClassLoader.getResource doesn't throw IOException (!)
            return null;
        }
    }

    public Enumeration<URL> getResources(String rn)
        throws IOException
    {
        if (tracing)
            trace(0, "%s: get resources %s", this, rn);

        final List<URL> us = new ArrayList<>();
        visitResources(rn, new ResourceVisitor() {
                public URI accept(URI u) throws IOException {
                    us.add(u.toURL());
                    return null;
                }
            });
        return Collections.enumeration(us);
    }

    // -- Stubs for methods not yet re-implemented --

    /* ## Can't do this -- CL.getParent is final
    public ClassLoader getParent() {
        throw new UnsupportedOperationException();
    }
    */

}
