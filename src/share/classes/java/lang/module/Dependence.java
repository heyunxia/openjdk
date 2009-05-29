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

import java.util.EnumSet;
import java.util.Set;


public final class Dependence {

    public static enum Modifier { LOCAL, OPTIONAL, PUBLIC, SYNTHETIC; }

    private final Set<Modifier> mods;
    private final ModuleIdQuery midq;

    public Dependence(EnumSet<Modifier> mods, ModuleIdQuery midq) {
        this.mods = (mods != null) ? mods : EnumSet.noneOf(Modifier.class);
        this.midq = midq;
    }

    public ModuleIdQuery query() { return midq; }

    public Set<Modifier> modifiers() { return mods; }

    public boolean equals(Object ob) {
        if (!(ob instanceof Dependence))
            return false;
        Dependence that = (Dependence)ob;
        return (midq.equals(that.midq) && mods.equals(that.mods));
    }

    public int hashCode() {
        return midq.hashCode() * 43 + mods.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("requires");
        for (Modifier m : mods) {
            sb.append(" ").append(m.toString().toLowerCase());
        }
        sb.append(" ").append(midq);
        return sb.toString();
    }

}
