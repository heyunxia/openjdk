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
 * have any questions.30
 */

package org.openjdk.jigsaw;

import java.util.*;
import java.lang.module.*;


/**
 * <p> A compile-time view of a run-time module class loader </p>
 *
 * @see Configuration
 * @see Configurator
 */

public class PathContext
    extends BaseContext
    implements LinkingContext
{

    protected PathContext() { }

    // The ModuleInfos of the modules in this context
    //
    Set<ModuleInfo> moduleInfos = new HashSet<>();

    public Set<ModuleInfo> moduleInfos() { return moduleInfos; }

    // This context's supplying contexts
    //
    Set<PathContext> suppliers = new HashSet<>();

    // This context's re-exported supplying contexts
    //
    Set<PathContext> reExportedSuppliers = new HashSet<>();

    // This context's local path
    //
    List<ModuleId> localPath = new ArrayList<>();

    /**
     * <p> A list of the ids of the modules in this context </p>
     *
     * <p> The elements of this list are ordered, in a class-path-like fashion,
     * according dominance.  To find the definition of a given type name,
     * examine each module in the list in order.  This ensures that, in the
     * case of multiple definitions of a type, the dominant definition will
     * always take precedence. </p>
     *
     * @return This context's local module path
     */
    public List<ModuleId> localPath() { return localPath; }

    /**
     * <p> The remote contexts upon which this context depends </p>
     *
     * <p> If a type is not found in the local module path then examine the
     * remote contexts, searching the local path of each one as described
     * above. </p>
     *
     * <p> Any given type name will be defined in at most one of the remote
     * contexts.  (A thorough compiler will enforce this constraint.)  The
     * order in which the remote contexts are searched is therefore
     * irrelevant. </p>
     *
     * @return This context's remote-context set
     */
    public Set<PathContext> remoteContexts() { return suppliers; }

    public boolean equals(Object ob) {
        if (!(ob instanceof PathContext))
            return false;
        PathContext that = (PathContext)ob;
        if (!super.equals(that))
            return false;
        if (!localPath.equals(that.localPath))
            return false;
        if (!suppliers.equals(that.suppliers)) // ## Can't cope with cycles!
            return false;
        return true;
    }

}
