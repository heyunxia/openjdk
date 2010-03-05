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


/**
 * A module's identification, which consists of a name and a version.
 */

public final class ModuleId
    implements Comparable<ModuleId>
{

    private final String name;
    private final Version version;
    private final int hash;

    // ## Why do we allow ModuleIds to have null versions, anyway?

    public ModuleId(String name, Version version) {
        this.name = ModuleSystem.checkModuleName(name);
        this.version = version;
        hash = (43 * name.hashCode()
                + ((version != null) ? version.hashCode() : 0));
    }

    /* package */ static ModuleId parse(ModuleSystem ms, String nm, String v) {
        return new ModuleId(nm, ms.parseVersion(v));
    }

    /* package */ static ModuleId parse(ModuleSystem ms, String s) {
        if (s == null)
            throw new IllegalArgumentException();
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == ' ' || c == '@') break;
            i++;
        }
        if (i >= n)
            return new ModuleId(s, null);
        if (i == 0)
            throw new IllegalArgumentException();
        String nm = (i < n) ? s.substring(0, i) : s;
        while (i < n && s.charAt(i) == ' ')
            i++;
        if (i >= n || s.charAt(i) != '@')
            throw new IllegalArgumentException();
        i++;
        if (i >= n)
            throw new IllegalArgumentException();
        while (i < n && s.charAt(i) == ' ')
            i++;
        if (i >= n)
            throw new IllegalArgumentException();
        return parse(ms, nm, s.substring(i));
    }

    public String name() { return name; }

    public Version version() { return version; }

    public int compareTo(ModuleId that) {
        int c = name.compareTo(that.name);
        if (c != 0)
            return c;
        if (version == null) {
            if (that.version == null)
                return 0;
            return -1;
        }
        if (that.version == null)
            return +1;
        return version.compareTo(that.version);
    }

    public ModuleIdQuery toQuery() {
        return new ModuleIdQuery(name, version.toQuery());
    }

    @Override
    public boolean equals(Object ob) {
        if (!(ob instanceof ModuleId))
            return false;
        ModuleId that = (ModuleId)ob;
        if (!name.equals(that.name))
            return false;
        if (version == that.version)
            return true;
        if (version == null || that.version == null)
            return false;
        return version.equals(that.version());
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return (version == null ? name : name + "@" + version);
    }

}
