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

    // Suppliers (i.e. remote contexts)
    //
    private Set<String> suppliers = new HashSet<String>();
    protected void addSupplier(String cxn) {
        suppliers.add(cxn);
    }

    // Services provided by this context (service name -> implementation types)
    //
    private Map<String,Set<String>> services = new HashMap<>();

    // returns an unmodifiable map of the services provided by this context
    private Map<String,Set<String>> unmodifiableServices() {
        Map<String,Set<String>> result = new HashMap<>();
        for (Map.Entry<String,Set<String>> entry: services.entrySet()) {
            String cn = entry.getKey();
            Set<String> impls = entry.getValue();
            result.put(cn, Collections.unmodifiableSet(impls));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns the map of the services implementations supplied by this context.
     * The key is the service name, the value is the set of implementation
     * classes.
     */
    public final Map<String,Set<String>> services() {
        return unmodifiableServices();
    }

    protected void putService(String sn, String impl) {
        Set<String> impls = services.get(sn);
        if (impls != null) {
            impls.add(impl);
        } else {
            impls = new LinkedHashSet<>();
            impls.add(impl);
            services.put(sn, impls);
        }
    }

    /**
     * Return the set of remote contexts (read-only).  This includes
     * contexts supplying remote classes as well as any suppliers
     * re-exporting remote classes.
     *
     * @return this context's remote-context set
     */
    public final Set<String> remoteContexts()  {
        return Collections.unmodifiableSet(suppliers);
    }


    public boolean equals(Object ob) {
        if (!(ob instanceof Context))
            return false;
        Context that = (Context)ob;
        return (super.equals(that)
                && moduleForLocalClass.equals(that.moduleForLocalClass)
                && contextForRemotePackage.equals(that.contextForRemotePackage)
                && services.equals(that.services));
    }

}
