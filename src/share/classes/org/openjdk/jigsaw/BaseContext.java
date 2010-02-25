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
 * <p> Definitions common to all types of contexts </p>
 *
 * @see Configuration
 * @see Resolver
 */

public class BaseContext {

    protected BaseContext() { }

    // The set of modules in this context
    //
    protected Set<ModuleId> modules = new HashSet<ModuleId>();

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
     * Freeze this context so that its name, module set, and hash code
     * do not change.
     *
     * @throws IllegalStateException
     *         If this context is already frozen
     */
    public void freeze() {
        if (name != null)
            throw new IllegalStateException("Context already frozen");
        name = makeName();
        modules = Collections.unmodifiableSet(modules);
    }

    protected boolean isFrozen() {
        return name != null;
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

    public String toString() {
        if (name == null)
            return makeName() + "*";
        return name;
    }

    private Integer hash = null;

    // The hash code is based only on the module-id set, not on
    // any additional mutable state defined in subclasses
    //
    public final int hashCode() {
        if (hash != null)
            return hash;
        if (!isFrozen())
            throw new IllegalStateException("Context not frozen");
        int hc = modules.hashCode();
        hash = hc;
        return hc;
    }

    public boolean equals(Object ob) {
        if (!(ob instanceof BaseContext))
            return false;
        BaseContext that = (BaseContext)ob;
        if (name == null && that.name != null)
            return false;
        return ((name == that.name || name.equals(that.name))
                && modules.equals(that.modules));
    }

}
