/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.module;

import java.lang.module.Dependence.Modifier;
import java.util.Set;


public final class ViewDependence extends Dependence {
    private final ModuleIdQuery midq;

    public ViewDependence(Set<Modifier> mods, ModuleIdQuery midq) {
        super(mods);
        this.midq = midq;
    }

    public ModuleIdQuery query() { return midq; }

    public boolean equals(Object ob) {
        if (!(ob instanceof ViewDependence))
            return false;
        ViewDependence that = (ViewDependence)ob;
        return (midq.equals(that.midq)
                && modifiers().equals(that.modifiers()));
    }

    public int hashCode() {
        return midq.hashCode() * 43 + modifiers().hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("requires");
        for (Modifier m : modifiers()) {
            sb.append(" ").append(m.toString().toLowerCase());
        }
        sb.append(" ").append(midq);
        return sb.toString();
    }

}
