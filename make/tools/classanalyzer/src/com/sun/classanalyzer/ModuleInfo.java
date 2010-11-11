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
 * @author Mandy Chung
 */
public class ModuleInfo {

    private final Module module;
    private final String version;
    private final Set<PackageInfo> packages;
    private final Set<Dependence> requires;
    private final Set<Module> permits;

    ModuleInfo(Module m, String version,
               Collection<PackageInfo> packages,
               Collection<Dependence> reqs,
               Collection<Module> permits) {
        this.module = m;
        this.version = version;
        this.packages = new TreeSet<PackageInfo>(packages);
        this.permits = new TreeSet<Module>(permits);

        this.requires = new TreeSet<Dependence>();
        // filter non-top level module
        for (Dependence d : reqs) {
            if (d.getModule().isTopLevel())
                requires.add(d);
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
        return module.name() + " @ " + version;
    }

    public Set<PackageInfo> packages() {
        return Collections.unmodifiableSet(packages);
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

    private void visitDependence(Dependence.Filter filter, Set<Module> visited, Set<Module> result) {
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

    private static final String INDENT = "    ";
    /**
     * Returns a string representation of module-info.java for
     * this module.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("module ").append(id()).append(" {\n");

        for (Dependence d : requires()) {
            sb.append(INDENT).append("requires");
            for (Dependence.Modifier mod : d.mods) {
                sb.append(" ").append(mod);
            }
            String did = d.getModule().getModuleInfo().id();
            sb.append(" ").append(did).append(";\n");
        }

        String permits = INDENT + "permits ";
        int i = 0;
        for (Module pm : permits()) {
            if (i > 0) {
                permits += ", ";
                if ((i % 5) == 0)
                    permits += "\n" + INDENT + "        "; // "permits"
            }
            permits += pm.name();
            i++;
        }

        if (permits().size() > 0) {
            sb.append(permits).append(";\n");
        }
        if (module.mainClass() != null) {
            sb.append(INDENT).append("class ").append(mainClass()).append(";\n");
        }
        sb.append("}\n");
        return sb.toString();
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
        private final String id;
        private EnumSet<Modifier> mods;
        private Module sm = null;

        public Dependence(Module sm) {
            this(sm, false);
        }

        public Dependence(Module sm, boolean optional) {
            this(sm, modifier(optional));
        }

        public Dependence(Klass from, Klass to, boolean optional) {
            this(to.getModule(), modifier(optional));
        }

        public Dependence(Module sm, EnumSet<Modifier> mods) {
            this.sm = sm.group();
            this.id = this.sm.name();
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
            return optional ? EnumSet.of(Modifier.OPTIONAL) :
                EnumSet.noneOf(Modifier.class);
        }

        synchronized Module getModule() {
            if (sm == null) {
                Module m = Module.findModule(id);
                if (m == null) {
                    throw new RuntimeException("Module " + id + " doesn't exist");
                }
                sm = m.group();
            }
            return sm;
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

    static class PackageInfo implements Comparable<PackageInfo> {

        final Module module;
        final String pkgName;
        int count;
        long filesize;

        public PackageInfo(Module m, String name) {
            this.module = m;
            this.pkgName = name;
            this.count = 0;
            this.filesize = 0;
        }

        void add(PackageInfo pkg) {
            this.count += pkg.count;
            this.filesize += pkg.filesize;
        }

        void add(long size) {
            count++;
            filesize += size;

        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 59 * hash + (this.module != null ? this.module.hashCode() : 0);
            hash = 59 * hash + (this.pkgName != null ? this.pkgName.hashCode() : 0);
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof PackageInfo) {
                PackageInfo p = (PackageInfo) o;
                return p.module.equals(this.module) && p.pkgName.equals(this.pkgName);
            }
            return false;
        }

        @Override
        public int compareTo(PackageInfo p) {
            if (this.equals(p)) {
                return 0;
            } else if (pkgName.compareTo(p.pkgName) == 0) {
                return module.compareTo(p.module);
            } else {
                return pkgName.compareTo(p.pkgName);
            }
        }
    }
}
