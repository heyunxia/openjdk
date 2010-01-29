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

package java.lang.module;


public final class ModuleIdQuery {

    private String name;
    private VersionQuery versionQuery;

    public ModuleIdQuery(String name, VersionQuery versionQuery) {
        this.name = ModuleSystem.checkModuleName(name);
        this.versionQuery = versionQuery;
    }

    public String name() { return name; }

    public VersionQuery versionQuery() { return versionQuery; }

    public boolean matches(ModuleId mid) {
        if (!name.equals(mid.name()))
            return false;
        if (versionQuery == null)
            return true;
        if (mid.version() == null)
            return false;
        return versionQuery.matches(mid.version());
    }

    @Override
    public boolean equals(Object ob) {
        if (!(ob instanceof ModuleIdQuery))
            return false;
        ModuleIdQuery that = (ModuleIdQuery)ob;
        if (!this.name.equals(that.name))
            return false;
        if (versionQuery == that.versionQuery)
            return true;
        if (versionQuery == null || that.versionQuery == null)
            return false;
        return this.versionQuery.equals(that.versionQuery);
    }

    @Override
    public int hashCode() {
        return (name.hashCode() * 43
                + ((versionQuery == null) ? 7919 : versionQuery.hashCode()));
    }

    @Override
    public String toString() {
        return (versionQuery == null ? name : name + "@" + versionQuery);
    }

}
