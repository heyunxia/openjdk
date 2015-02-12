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
import java.io.*;
import java.util.*;

import static org.openjdk.jigsaw.Trace.*;


/**
 * <p> A collection of {@linkplain java.lang.module.ModuleInfo module-info}
 * objects </p>
 *
 * @see Library
 * @see SimpleLibrary
 */

public abstract class Catalog {

    private static final JigsawModuleSystem jms
        = JigsawModuleSystem.instance();

    protected Catalog() { }

    /**
     * <p> This catalogs's name </p>
     *
     * <p> Catalog names are not guaranteed to be unique; they should be used
     * for diagnostic purposes only. </p>
     */
    public abstract String name();

    /**
     * <p> This catalogs's parent catalog, for delegation </p>
     *
     * <p> If a catalog has a parent then every module in the parent catalog
     * that is not also present in the child appears to be in the child.  This
     * property is recursive: A catalog may have a parent which in turn has a
     * parent, which would be the first catalog's grandparent, and so on. </p>
     *
     * <p> When searching for modules the child catalog is always considered
     * first; this is the opposite of the old class-loader delegation
     * model. </p>
     *
     * <p> In the case of a {@link Library} catalog, the modules in a parent
     * catalog cannot be installed, uninstalled, or configured. </p>
     *
     * @return  This catalog's parent catalog, or {@code null}
     *          if it has no parent
     */
    public abstract Catalog parent();

    /**
     * <p> Gather the {@link java.lang.module.ModuleId ModuleIds} of the
     * module views and aliases available locally in this catalog,
     * ignoring any parent catalogs. </p>
     *
     * @param  name
     *         The name of the module being sought; if {@code null} then all
     *         module ids will be gathered
     *
     * @param  mids
     *         A mutable set to which the gathered ids will be added
     */
    protected abstract void gatherLocalModuleIds(String name,
                                                 Set<ModuleId> mids)
        throws IOException;

   /**
     * <p> Gather the {@link java.lang.module.ModuleId ModuleIds} of all
     * declaring modules available locally in this catalog, ignoring any parent
     * catalogs. </p>
     *
     * @param  mids
     *         A mutable set to which the gathered ids will be added
     */
    protected abstract void gatherLocalDeclaringModuleIds(Set<ModuleId> mids)
        throws IOException;

    /**
     * <p> List all of the module views and aliases present locally in this catalog,
     * without regard to any parent catalogs. </p>
     *
     * @return  The list of requested module ids, sorted in their natural
     *          order ## why?
     */
    public List<ModuleId> listLocalModuleIds()
        throws IOException
    {
        Set<ModuleId> mids = new HashSet<ModuleId>();
        gatherLocalModuleIds(null, mids);
        List<ModuleId> rv = new ArrayList<ModuleId>(mids);
        Collections.sort(rv);
        return rv;
    }

    /**
     * <p> List all of the module views and aliases present in this catalog
     * and in any parent catalogs. </p>
     *
     * @return  The list of requested module ids, sorted in their natural
     *          order
     */
    public List<ModuleId> listModuleIds()
        throws IOException
    {
        Set<ModuleId> mids = new HashSet<>();
        Catalog c = this;
        while (c != null) {
            c.gatherLocalModuleIds(null, mids);
            c = c.parent();
        }
        List<ModuleId> rv = new ArrayList<>(mids);
        Collections.sort(rv);
        return rv;
    }

    /**
     * <p> List all of the declaring modules present locally in this catalog,
     * without regard to any parent catalogs.
     *
     * @return  The list of requested module ids, sorted in their natural
     *          order
     */
    public List<ModuleId> listLocalDeclaringModuleIds()
        throws IOException
    {
        Set<ModuleId> mids = new HashSet<>();
        gatherLocalDeclaringModuleIds(mids);
        List<ModuleId> rv = new ArrayList<>(mids);
        Collections.sort(rv);
        return rv;
    }

    /**
     * <p> List all of the declaring modules present in this catalog and in
     * any parent catalogs. </p>
     *
     * @return  The list of requested module ids, sorted in their natural
     *          order
     */
    public List<ModuleId> listDeclaringModuleIds()
        throws IOException
    {
        Set<ModuleId> mids = new HashSet<>();
        Catalog c = this;
        while (c != null) {
            c.gatherLocalDeclaringModuleIds(mids);
            c = c.parent();
        }
        List<ModuleId> rv = new ArrayList<>(mids);
        Collections.sort(rv);
        return rv;
    }

    /**
     * <p> Find all module views and aliases with the given name in this catalog
     * and in any parent catalogs. </p>
     *
     * @param   name
     *          The name of the modules being sought
     *
     * @return  An unsorted list containing the module identifiers of the
     *          found modules; if no modules were found then the list will
     *          be empty
     */
    public List<ModuleId> findModuleIds(String name)
        throws IOException
    {
        ModuleSystem.checkModuleName(name);
        Set<ModuleId> mids = new HashSet<ModuleId>();
        Catalog c = this;
        while (c != null) {
            c.gatherLocalModuleIds(name, mids);
            c = c.parent();
        }
        // ## Perhaps this method should return a set after all?
        return new ArrayList<ModuleId>(mids);
    }

    /**
     * <p> Find all module views and aliases matching the given query in this
     * catalog and in any parent catalogs. </p>
     *
     * @param   midq
     *          The query to match against
     *
     * @return  An unsorted list containing the module identifiers of the
     *          found modules; if no modules were found then the list will
     *          be empty
     *
     * @throws  IllegalArgumentException
     *          If the given module-identifier query is not a Jigsaw
     *          module-identifier query
     */
    public List<ModuleId> findModuleIds(ModuleIdQuery midq)
        throws IOException
    {
        List<ModuleId> ans = findModuleIds(midq.name());
        if (ans.isEmpty() || midq.versionQuery() == null)
            return ans;
        for (Iterator<ModuleId> i = ans.iterator(); i.hasNext();) {
            ModuleId mid = i.next();
            if (!midq.matches(mid))
                i.remove();
        }
        return ans;
    }

    /**
     * <p> Find the most recently-versioned module matching the given query in
     * this catalog or in any parent catalogs. </p>
     *
     * @param   midq
     *          The query to match against
     *
     * @return  The identification of the latest module matching the given
     *          query, or {@code null} if none is found
     *
     * @throws  IllegalArgumentException
     *          If the given module-identifier query is not a Jigsaw
     *          module-identifier query
     */
    public ModuleId findLatestModuleId(ModuleIdQuery midq)
        throws IOException
    {
        List<ModuleId> mids = findModuleIds(midq);
        if (mids.isEmpty())
            return null;
        if (mids.size() == 1)
            return mids.get(0);
        Collections.sort(mids);
        return mids.get(0);
    }

    /**
     * <p> Find the {@link java.lang.module.ModuleInfo ModuleInfo} object for
     * the module with the given identifier, in this catalog only. </p>
     *
     * @param   mid
     *          The identifier of the module being sought
     *
     * @return  The requested {@link java.lang.module.ModuleInfo ModuleInfo},
     *          or {@code null} if no such module is present in this catalog
     *
     * @throws  IllegalArgumentException
     *          If the given module identifier is not a Jigsaw module
     *          identifier
     */
    protected abstract ModuleInfo readLocalModuleInfo(ModuleId mid)
        throws IOException;

    /**
     * <p> Find the {@link java.lang.module.ModuleInfo ModuleInfo} object for
     * the module with the given identifier, in this catalog or in any parent
     * catalogs. </p>
     *
     * @param   mid
     *          The identifier of the module being sought
     *
     * @return  The requested {@link java.lang.module.ModuleInfo ModuleInfo},
     *          or {@code null} if no such module is present in this catalog
     *
     * @throws  IllegalArgumentException
     *          If the given module identifier is not a Jigsaw module
     *          identifier
     */
    public ModuleInfo readModuleInfo(ModuleId mid)
        throws IOException
    {
        Catalog c = this;
        while (c != null) {
            ModuleInfo mi = c.readLocalModuleInfo(mid);
            if (mi != null)
                return mi;
            c = c.parent();
        }
        return null;
    }
}
