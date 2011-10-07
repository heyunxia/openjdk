/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

/**
 * Information about a module.  ModuleInfo.toString() returns
 * a string representation of the module-info.java source file.
 *
 */
public class ModuleInfo {

    private final Module module;
    private final Set<Dependence> requires;
    private final Set<Module> permits;

    ModuleInfo(Module m,
            Collection<Dependence> reqs,
            Collection<Module> permits) {
        this.module = m;
        this.permits = new TreeSet<Module>(permits);
        this.requires = new TreeSet<Dependence>();
        // filter non-top level module
        for (Dependence d : reqs) {
            if (d.getModule().isTopLevel()) {
                requires.add(d);
            }
        }
    }

    public Module getModule() {
        return module;
    }

    /**
     * This module's identifier
     */
    public String name() {
        return module.name();
    }

    public String id() {
        return module.name() + " @ " + module.version();
    }

    /**
     * The dependences of this module
     */
    public Set<Dependence> requires() {
        return Collections.unmodifiableSet(requires);
    }

    /**
     * The modules that are permitted to require this module
     */
    public Set<Module> permits() {
        return Collections.unmodifiableSet(permits);
    }

    public void addPermit(Module m) {
        permits.add(m);
    }

    /**
     * The fully qualified name of the main class of this module
     */
    public String mainClass() {
        Klass k = module.mainClass();
        return k != null ? k.getClassName() : "";
    }

    void visitDependence(Dependence.Filter filter, Set<Module> visited, Set<Module> result) {
        if (!visited.contains(module)) {
            visited.add(module);

            for (Dependence d : requires()) {
                if (filter == null || filter.accept(d)) {
                    ModuleInfo dmi = d.getModule().getModuleInfo();
                    dmi.visitDependence(filter, visited, result);
                }
            }
            result.add(module);
        }
    }

    Set<Module> dependences(Dependence.Filter filter) {
        Set<Module> visited = new TreeSet<Module>();
        Set<Module> result = new LinkedHashSet<Module>();

        visitDependence(filter, visited, result);
        return result;
    }

    private Set<String> reexports;

    private synchronized Set<String> reexports() {
        if (reexports != null) {
            return reexports;
        }

        final Module m = module;
        Set<Module> deps = dependences(new Dependence.Filter() {

            @Override
            public boolean accept(Dependence d) {
                // filter itself
                return d.isPublic();
            }
        });

        reexports = new TreeSet<String>();
        for (Module dm : deps) {
            if (dm != module) {
                // exports all local packages
                for (PackageInfo p : dm.packages()) {
                    if (PackageInfo.isExportedPackage(p.pkgName)) {
                        reexports.add(p.pkgName + ".*");
                    }
                }
                reexports.addAll(dm.getModuleInfo().reexports());
            }
        }
        return reexports;
    }

    // a system property to specify to use "requires public"
    // or the "exports" statement
    private static final boolean requiresPublic =
        Boolean.parseBoolean(System.getProperty("classanalyzer.requiresPublic", "true"));
    private static final String INDENT = "    ";


    /**
     * Returns a string representation of module-info.java for
     * this module.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("module %s {%n", id()));

        for (Dependence d : requires()) {
            String mods = "";
            for (Dependence.Modifier mod : d.mods) {
                if (requiresPublic || mod != Dependence.Modifier.PUBLIC) {
                    mods += mod.toString() + " ";
                }
            }
            sb.append(String.format("%srequires %s%s;%n", INDENT,
                                    mods,
                                    d.getModule().getModuleInfo().id()));
        }

        String permits = INDENT + "permits ";
        int i = 0;
        for (Module pm : permits()) {
            if (i > 0) {
                permits += ", ";
                if ((i % 5) == 0) {
                    permits += "\n" + INDENT + "        "; // "permits"
                }
            }
            permits += pm.name();
            i++;
        }

        if (permits().size() > 0) {
            sb.append(permits).append(";\n");
        }
        if (module.mainClass() != null) {
            sb.append(String.format("%sclass %s;%n", INDENT, mainClass()));
        }

        if (!requiresPublic)
            printExports(sb);

        sb.append("}\n");
        return sb.toString();
    }

    private void printExports(StringBuilder sb) {
        Set<Module> modules = dependences(new Dependence.Filter() {

            @Override
            public boolean accept(Dependence d) {
                // filter itself
                return d.isPublic();
            }
        });

        // explicit exports in the given config file
        Set<String> cexports = new TreeSet<String>();
        for (Module m : modules) {
            cexports.addAll(m.config().exports());
        }

        if (cexports.size() > 0) {
            sb.append("\n" + INDENT + "// explicit exports\n");
            for (String e : cexports) {
                sb.append(String.format("%sexport %s;%n", INDENT, e));
            }
        }

        // exports all local packages
        Set<String> pkgs = new TreeSet<String>();
        for (PackageInfo pi : module.packages()) {
            String p = pi.pkgName;
            if (module.exportAllPackages() || PackageInfo.isExportedPackage(p))
                pkgs.add(p);
        }

        if (pkgs.size() > 0) {
            sb.append(String.format("%n%s// exports %s packages%n", INDENT,
                                    module.exportAllPackages() ? "all local" : "supported"));
            for (String p : pkgs) {
                sb.append(String.format("%sexport %s.*;%n", INDENT, p));
            }
        }

        // reexports
        if (reexports().size() > 0) {
            Set<String> rexports = new TreeSet<String>();
            if (modules.size() == 2) {
                // special case?
                rexports.addAll(reexports());
            } else {
                for (String e : reexports()) {
                    int j = e.indexOf('.');
                    rexports.add(e.substring(0, j) + ".**");
                }
            }
            sb.append("\n" + INDENT + "// reexports\n");
            for (String p : rexports) {
                sb.append(String.format("%sexport %s;%n", INDENT, p));
            }
        }
    }

    static class Dependence implements Comparable<Dependence> {

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
        final String id;
        private EnumSet<Modifier> mods;
        private Module dm = null;
        private boolean internal = false;

        public Dependence(Module dm) {
            this(dm, false);
        }

        public Dependence(Module dm, boolean optional) {
            this(dm, modifier(optional));
        }

        public Dependence(Module dm, EnumSet<Modifier> mods) {
            this.dm = dm.group();
            this.id = dm.name();
            this.mods = mods;
        }

        public Dependence(String name, boolean optional) {
            this(name, optional, false, false);
        }

        public Dependence(String name, boolean optional, boolean reexport, boolean local) {
            Set<Modifier> ms = new TreeSet<Modifier>();
            if (optional) {
                ms.add(Modifier.OPTIONAL);
            }
            if (reexport) {
                ms.add(Modifier.PUBLIC);
            }
            if (local) {
                ms.add(Modifier.LOCAL);
            }
            this.id = name;
            this.mods = ms.isEmpty()
                    ? EnumSet.noneOf(Modifier.class)
                    : EnumSet.copyOf(ms);
        }

        private static EnumSet<Modifier> modifier(boolean optional) {
            return optional ? EnumSet.of(Modifier.OPTIONAL)
                    : EnumSet.noneOf(Modifier.class);
        }

        void setModule(Module m) {
            assert dm == null && m != null;
            dm = m.group();
        }

        void setInternal(boolean b) {
            internal = b;
        }

        boolean isInternal() {
            return internal;
        }

        Module getModule() {
            return dm;
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

        public void addModifier(Modifier e) {
            mods.add(e);
        }

        public void update(Dependence d) {
            // static dependence overrides the optional
            if (isOptional() && !d.isOptional()) {
                mods.remove(Modifier.OPTIONAL);
            }
            // local dependence overrides non-local dependence
            if (!isLocal() && d.isLocal()) {
                mods.add(Modifier.LOCAL);
            }

            // reexport
            if (!isPublic() && d.isPublic()) {
                mods.add(Modifier.PUBLIC);
            }
            internal = internal || d.internal;
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
            return id.compareTo(d.id);
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
            return this.id.equals(d.id) && mods.equals(d.mods);
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 19 * hash + this.id.hashCode();
            return hash;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Dependence.Modifier mod : mods) {
                sb.append(mod).append(" ");
            }
            sb.append(getModule().name());
            return sb.toString();
        }
    }
}
