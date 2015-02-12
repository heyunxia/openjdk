/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.sun.classanalyzer;

import java.util.Set;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.TreeSet;

public class Dependence implements Comparable<Dependence> {

    static enum Modifier {

        PUBLIC("public"),
        OPTIONAL("optional"),
        LOCAL("local");
        private final String name;

        Modifier(String n) {
            this.name = n;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static EnumSet<Modifier> modifier(boolean optional) {
        return optional ? EnumSet.of(Modifier.OPTIONAL)
                : EnumSet.noneOf(Modifier.class);
    }

    final String module;
    private final EnumSet<Modifier> mods;
    final Set<Module.View> views = new HashSet<>();

    public static Dependence newDependence(Klass k, boolean optional) {
        Module dm = k.getModule().group();
        Dependence dep = new Dependence(dm.name(), optional);
        Module.View view = dm.getView(k);
        if (view == null)
            throw new RuntimeException("No view exporting " + k);
        dep.addView(view);
        return dep;
    }

    public Dependence(String module, boolean optional) {
        this(module, modifier(optional));
    }

    public Dependence(String module, EnumSet<Modifier> mods) {
        this.module = module;
        this.mods = mods;
    }

    public Dependence(String module, boolean optional, boolean reexport, boolean local) {
        Set<Modifier> ms = new TreeSet<>();
        if (optional) {
            ms.add(Modifier.OPTIONAL);
        }
        if (reexport) {
            ms.add(Modifier.PUBLIC);
        }
        if (local) {
            ms.add(Modifier.LOCAL);
        }
        this.module = module;
        this.mods = ms.isEmpty()
                ? EnumSet.noneOf(Modifier.class)
                : EnumSet.copyOf(ms);
    }

    void requiresLocal(Module m) {
        mods.add(Modifier.LOCAL);
        Module.View v = m.getView(module);
        if (v == m.defaultView() && !m.moduleProperty("allows.permits")) {
            // requires local should use the internal view unless the
            // default view permits it.
            v = m.internalView();
        }
        addView(v);
    }

    void requiresOptional(Module m) {
        mods.add(Modifier.OPTIONAL);
    }

    void addView(Module.View v) {
        views.add(v);
        v.addRefCount();
    }

    private Module.View view;
    synchronized Module.View getModuleView() {
        if (view == null) {
            // if this dependency requires a view name rather than a module name
            // uses that view; otherwise, return the internal view if exists.
            Module.View mv = null;
            for (Module.View v : views) {
                if (v.name.equals(module)) {
                    mv = v;
                    if (v.module.defaultView() != v) {
                        view = v;
                        break;
                    }
                } else if (v.module.internalView() == v) {
                    view = v;   // continue to match the view name
                }
            }
            if (view == null) {
                if (mv == null)
                    throw new RuntimeException("requires module view not found: " + this);
                view = mv;
            }
        }
        return view;
    }

    // ## remove it once clean up
    Module getModule() {
        return getModuleView().module;
    }

    public boolean isOptional() {
        return mods.contains(Modifier.OPTIONAL);
    }

    public boolean isLocal() {
        return mods.contains(Modifier.LOCAL);
    }

    public boolean isPublic() {
        return mods.contains(Modifier.PUBLIC);
    }

    public EnumSet<Modifier> modifiers() {
        return mods;
    }

    static interface Filter {

        public boolean accept(Dependence d);
    }

    @Override
    public int compareTo(Dependence d) {
        if (this.equals(d)) {
            return 0;
        }
        return module.compareTo(d.module);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Dependence)) {
            return false;
        }
        if (this == obj) {
            return true;
        }

        Dependence d = (Dependence) obj;
        return this.module.equals(d.module) && mods.equals(d.mods);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 19 * hash + this.module.hashCode();
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Dependence.Modifier mod : mods) {
            sb.append(mod).append(" ");
        }
        sb.append(module).append(" (");
        sb.append("view ");
        for (Module.View v : views)
            sb.append(v.name).append(" ");
        sb.append(")");
        return sb.toString();
    }
}
