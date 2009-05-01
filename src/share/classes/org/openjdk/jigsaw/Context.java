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
import java.util.*;


/**
 * <p> A context is the configuration-time view of a module class loader at run
 * time. </p>
 *
 * @see Configuration
 * @see Resolver
 */

public class Context {

    /**
     * Construct a new, empty context.
     */
    public Context() { }

    // The set of modules in this context
    //
    private Set<ModuleId> modules = new HashSet<ModuleId>();

    /**
     * Add the given module to this context.
     */
    protected void add(ModuleId mid) {
        modules.add(mid);
    }

    private Set<ModuleId> roModules;

    /**
     * The set of modules in this context (read-only).
     */
    public final Set<ModuleId> modules() {
        if (roModules == null)
            roModules = Collections.unmodifiableSet(modules);
        return roModules;
    }

    // This context's name
    //
    private String name;

    /**
     * Freeze this context, so that its name does not change.
     *
     * @throws IllegalStateException
     *         If this context is already frozen
     */
    public void freeze() {
        if (name != null)
            throw new IllegalStateException();
        name = makeName();
    }

    /**
     * Freeze this context, assigning it the given name.
     *
     * @throws IllegalStateException
     *         If this context is already frozen
     */
    protected void freeze(String cxn) {
        if (name != null)
            throw new IllegalStateException();
        name = cxn;
    }

    // Construct this context's name
    //
    private String makeName() {
        StringBuilder sb = new StringBuilder();
        ModuleId[] mids = modules.toArray(new ModuleId[] { });
        Arrays.sort(mids);
        for (ModuleId mid : mids)
            sb.append("+").append(mid.name());
        return sb.toString();
    }

    /**
     * Return this context's name.
     *
     * @throws IllegalStateException
     *         If this context is not yet frozen
     */
    public String name() {
        if (name == null)
            throw new IllegalStateException();
        return name;
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

    public String toString() {
        if (name == null)
            return makeName() + "*";
        return name;
    }

    public int hashCode() {
        int hc = (name != null) ? name.hashCode() : 0;
        hc = hc * 43 + modules.hashCode();
        hc = hc * 43 + moduleForLocalClass.hashCode();
        hc = hc * 43 + contextForRemotePackage.hashCode();
        return hc;
    }

    public boolean equals(Object ob) {
        if (!(ob instanceof Context))
            return false;
        Context that = (Context)ob;
        if (name == null && that.name != null)
            return false;
        return ((name == that.name || name.equals(that.name))
                && modules.equals(that.modules)
                && moduleForLocalClass.equals(that.moduleForLocalClass)
                && contextForRemotePackage.equals(that.contextForRemotePackage));
    }

}
