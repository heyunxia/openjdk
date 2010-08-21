/*
 * Copyright 2009-2010 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.io.File;
import java.util.*;


/**
 * <p> An install-time view of a module class loader at run time </p>
 *
 * @see Configuration
 * @see Resolver
 */

public class Context
    extends BaseContext
{

    /**
     * Construct a new, empty context.
     */
    public Context() { }

    // For each module in this context, the location of its catalog,
    // or null if the module is defined in the catalog from which the
    // containing configuration was read
    //
    private Map<ModuleId,File> libraryForModule = new HashMap<>();

    public final File findLibraryPathForModule(ModuleId mid) {
        return libraryForModule.get(mid);
    }

    protected void putLibraryPathForModule(ModuleId mid, File path) {
        libraryForModule.put(mid, path);
    }

    // For each type defined by this context,
    // the id of the module that defines it
    //
    private Map<String,ModuleId> moduleForLocalClass
        = new HashMap<String,ModuleId>();

    /**
     * Return this context's map from local class names to modules
     * (read-only).
     */
    public final Map<String,ModuleId> moduleForLocalClassMap() {
        return Collections.unmodifiableMap(moduleForLocalClass);
    }

    /**
     * Find the id of the module in this context that will supply the
     * definition of the named class.
     *
     * @return  The requested module id, or {@code null} if no such
     *          module exists in this context
     */
    public final ModuleId findModuleForLocalClass(String cn) {
        return moduleForLocalClass.get(cn);
    }

    /**
     * Record the given local class as being provided by the given module.
     */
    protected void putModuleForLocalClass(String cn, ModuleId mid) {
        moduleForLocalClass.put(cn, mid);
    }

    /**
     * Return the set of classes defined in this context (read-only).
     */
    public final Set<String> localClasses() {
        return Collections.unmodifiableSet(moduleForLocalClass.keySet());
    }

    // For each package imported by this context, either directly
    // or indirectly, the context that will supply it
    //
    private Map<String,String> contextForRemotePackage
        = new HashMap<String,String>();

    /**
     * Find the name of the remote context that will supply class definitions
     * for the given package.
     *
     * @return  The requested context name, or {@code null} if
     *          no such context exists
     */
    public final String findContextForRemotePackage(String pn) {
        return contextForRemotePackage.get(pn);
    }

    /**
     * Return this context's map from remote package names to supplying
     * contexts (read-only).
     */
    public final Map<String,String> contextForRemotePackageMap() {
        return Collections.unmodifiableMap(contextForRemotePackage);
    }

    /**
     * Find the name of the remote context that will supply the definition
     * of the given class.
     *
     * @return  The requested context name, or {@code null} if
     *          no such context exists
     */
    public final String findContextForRemoteClass(String cn) {
        int i = cn.lastIndexOf('.');
        if (i < 0)
            return null;
        return contextForRemotePackage.get(cn.substring(0, i));
    }

    /**
     * Record the given remote package as being provided by the given context.
     */
    protected void putContextForRemotePackage(String pn, String cxn) {
        contextForRemotePackage.put(pn, cxn);
    }

    /**
     * Return the set of packages supplied to this context by remote contexts
     * (read-only).
     */
    public final Set<String> remotePackages() {
        return Collections.unmodifiableSet(contextForRemotePackage.keySet());
    }

    public boolean equals(Object ob) {
        if (!(ob instanceof Context))
            return false;
        Context that = (Context)ob;
        return (super.equals(that)
                && moduleForLocalClass.equals(that.moduleForLocalClass)
                && contextForRemotePackage.equals(that.contextForRemotePackage));
    }

}
