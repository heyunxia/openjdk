/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package com.sun.classanalyzer;

import com.sun.classanalyzer.ModuleInfo.Dependence;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Module contains a list of classes and resources.
 *
 */
public class Module implements Comparable<Module> {
    private static String baseModuleName = "base";
    static void setBaseModule(String name) {
        if (name == null || name.isEmpty()) {
            throw new RuntimeException("Null or empty base module");
        }
        baseModuleName = name;
    }

    static Properties moduleProps = new Properties();
    static String getModuleProperty(String key) {
        return getModuleProperty(key, null);
    }
    static String getModuleProperty(String key, String defaultValue) {
        String value = moduleProps.getProperty(key);
        if (value == null)
            return defaultValue;
        else
            return value;
    }


    static void setModuleProperties(String file) throws IOException {
        File f = new File(file);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(f));
            moduleProps.load(reader);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private final String name;
    private final String version;
    private final ModuleConfig config;
    private final Set<Klass> classes;
    private final Set<ResourceFile> resources;
    private final Set<Reference> unresolved;
    private final Set<Module> members;
    // update during the analysis

    private Module group;
    private ModuleInfo minfo;
    private Set<PackageInfo> pkgInfos;
    private Set<PackageInfo> resourcePkgInfos;

    private boolean isBaseModule;
    protected String mainClassName;

    protected Module(ModuleConfig config) {
        this.name = config.module;
        this.version = config.version;
        this.isBaseModule = name.equals(baseModuleName);
        this.classes = new TreeSet<Klass>();
        this.resources = new TreeSet<ResourceFile>();
        this.config = config;
        this.mainClassName = config.mainClass();
        this.unresolved = new HashSet<Reference>();
        this.members = new HashSet<Module>();
        this.group = this; // initialize to itself
    }

    String name() {
        return name;
    }

    String version() {
        return version;
    }

    ModuleConfig config() {
        return config;
    }

    Module group() {
        return group;
    }

    boolean isBase() {
        return isBaseModule;
    }

    Set<Klass> classes() {
        return Collections.unmodifiableSet(classes);
    }

    synchronized Set<PackageInfo> packages() {
        if (pkgInfos == null) {
            pkgInfos = new TreeSet<PackageInfo>();
            resourcePkgInfos = new TreeSet<PackageInfo>();
            for (PackageInfo pi : PackageInfo.getPackageInfos(this)) {
                if (pi.classCount > 0) {
                    pkgInfos.add(pi);
                }
                if (pi.resourceCount > 0) {
                    resourcePkgInfos.add(pi);
                }
            }
        }
        return Collections.unmodifiableSet(pkgInfos);
    }

    Set<ResourceFile> resources() {
        return Collections.unmodifiableSet(resources);
    }

    Set<Module> members() {
        return Collections.unmodifiableSet(members);
    }

    boolean contains(Klass k) {
        return k != null && classes.contains(k);
    }

    boolean isEmpty() {
        return classes.isEmpty()
                && resources.isEmpty()
                && mainClass() == null;
    }

    boolean allowEmpty() {
        return moduleProps.getProperty(name + ".allow.empty") != null;
    }

    boolean exportAllPackages() {
        // default - only exported packages
        String value = moduleProps.getProperty(name + ".exports.all");
        return value != null && Boolean.valueOf(value);
    }

    protected boolean isTopLevel() {
        // module with no class is not included except the base module
        // or reexporting APIs from required modules
        boolean reexports = false;
        for (Dependence d : config().requires()) {
            reexports = reexports || d.isPublic();
        }
        return this.group() == this
                && (isBase() || !isEmpty() || allowEmpty() || reexports);
    }

    Klass mainClass() {
        Klass k = mainClassName != null ?
                      Klass.findKlass(mainClassName) : null;
        return k;
    }

    @Override
    public int compareTo(Module o) {
        if (o == null) {
            return -1;
        }
        return name.compareTo(o.name);
    }

    @Override
    public String toString() {
        return name;
    }

    void addKlass(Klass k) {
        classes.add(k);
        k.setModule(this);
    }

    void addResource(ResourceFile res) {
        resources.add(res);
        res.setModule(this);
    }

    void processRootsAndReferences() {
        // start with the root set
        Deque<Klass> pending = new ArrayDeque<Klass>();
        for (Klass k : Klass.getAllClasses()) {
            if (k.getModule() != null) {
                continue;
            }

            String classname = k.getClassName();
            if (config.matchesRoot(classname) && !config.isExcluded(classname)) {
                addKlass(k);
                pending.add(k);
            }
        }

        // follow all references
        Klass k;
        while ((k = pending.poll()) != null) {
            if (!classes.contains(k)) {
                addKlass(k);
            }

            for (Klass other : k.getReferencedClasses()) {
                Module otherModule = other.getModule();
                if (otherModule != null && otherModule != this) {
                    // this module is dependent on otherModule
                    continue;
                }

                if (!classes.contains(other)) {
                    if (config.isExcluded(other.getClassName())) {
                        // reference to an excluded class
                        unresolved.add(new Reference(k, other));
                    } else {
                        pending.add(other);
                    }
                }
            }
        }

        // add other matching classes that don't require dependency analysis
        for (Klass c : Klass.getAllClasses()) {
            if (c.getModule() == null) {
                String classname = c.getClassName();
                if (config.matchesIncludes(classname) && !config.isExcluded(classname)) {
                    addKlass(c);
                    // dependencies
                    for (Klass other : c.getReferencedClasses()) {
                        Module otherModule = other.getModule();
                        if (otherModule == null) {
                            unresolved.add(new Reference(c, other));
                        }
                    }
                }
            }
        }

        // add other matching classes that don't require dependency analysis
        for (ResourceFile res : ResourceFile.getAllResources()) {
            if (res.getModule() == null) {
                String name = res.getName();
                if (config.matchesIncludes(name) && !config.isExcluded(name)) {
                    addResource(res);
                }
            }
        }
    }

    boolean isModuleDependence(Klass k) {
        Module m = k.getModule();
        return m == null || (!classes.contains(k) && !m.isBase());
    }

    Module getModuleDependence(Klass k) {
        if (isModuleDependence(k)) {
            Module m = k.getModule();
            if (group() == this && m != null) {
                // top-level module
                return m.group();
            } else {
                return m;
            }

        }
        return null;
    }

    <P> void visitMembers(Set<Module> visited, ModuleVisitor<P> visitor, P p) {
        if (!visited.contains(this)) {
            visited.add(this);
            visitor.preVisit(this, p);
            for (Module m : members) {
                m.visitMembers(visited, visitor, p);
                visitor.visited(this, m, p);
            }
            visitor.postVisit(this, p);
        } else {
            throw new RuntimeException("Cycle detected: module " + this.name);
        }
    }

    void addMember(Module m) {
        // merge class list
        for (Klass k : m.classes) {
            classes.add(k);
        }

        // merge resource list
        for (ResourceFile res : m.resources) {
            resources.add(res);
        }

        // propagate the main entry point
        if (m.mainClassName != null) {
            if (mainClassName == null) {
                mainClassName = m.mainClassName;
            } else {
                Trace.trace("Module %s already has an entry point: " +
                    "%s member: %s class %s%n",
                    name, mainClassName, m.name, m, m.name);
            }
        }
    }

    private static Factory INSTANCE = new Factory();
    public static Factory getFactory() {
        return INSTANCE;
    }

    static class Factory {
        protected Map<String, Module> modules =
                new LinkedHashMap<String, Module>();
        protected final void addModule(Module m) {
            // ## For now, maintain the static all modules list.
            // ## Need to revisit later
            String name = m.name();
            if (modules.containsKey(name)) {
                throw new RuntimeException("module \"" + name + "\" already exists");
            }
            modules.put(name, m);
        }

        public final Module findModule(String name) {
            return modules.get(name);
        }

        public final Set<Module> getAllModules() {
            Set<Module> ms = new LinkedHashSet<Module>(modules.values());
            return ms;
        }

        public void init(List<ModuleConfig> mconfigs) {
            for (ModuleConfig mconfig : mconfigs) {
                Module m = this.newModule(mconfig);
                addModule(m);
            }
        }

        public Module newModule(String name, String version) {
            return this.newModule(new ModuleConfig(name, version));
        }

        public Module newModule(ModuleConfig config) {
            return new Module(config);
        }

        public final void addModules(Set<Module> ms) {
            for (Module m : ms) {
                addModule(m);
            }
        }

        private static Module unknown;
        Module unknownModule() {
            synchronized (Factory.class) {
                if (unknown == null) {
                    unknown = this.newModule(new ModuleConfig("unknown", "unknown"));
                    addModule(unknown);
                }
            }
            return unknown;
        }

        void buildModuleMembers() {
            // set up module member relationship
            for (Module m : getAllModules()) {
                m.group = m; // initialize to itself
                for (String name : m.config.members()) {
                    Module member = findModule(name);
                    if (member != null) {
                        m.members.add(member);
                    }
                }
            }

            // set up the top-level module
            ModuleVisitor<Module> groupSetter = new ModuleVisitor<Module>() {
                public void preVisit(Module m, Module p) {
                    m.group = p;
                    if (p.isBaseModule) {
                        // all members are also base
                        m.isBaseModule = true;
                   }
                }

                public void visited(Module m, Module child, Module p) {
                    // nop - breadth-first search
                }

                public void postVisit(Module m, Module p) {
                    // nop - breadth-first search
                }
            };

            // propagate the top-level module to all its members
            for (Module p : getAllModules()) {
                for (Module m : p.members) {
                    if (m.group == m) {
                        m.visitMembers(new HashSet<Module>(), groupSetter, p);
                    }
                }
            }

            ModuleVisitor<Module> mergeClassList = new ModuleVisitor<Module>() {
                public void preVisit(Module m, Module p) {
                    // nop - depth-first search
                }

                public void visited(Module m, Module child, Module p) {
                    m.addMember(child);
                }

                public void postVisit(Module m, Module p) {
                }
            };

            Set<Module> visited = new HashSet<Module>();
            Set<Module> groups = new HashSet<Module>();
            for (Module m : getAllModules()) {
                if (m.group() == m) {
                    groups.add(m);
                    if (m.members().size() > 0) {
                        // merge class list from all its members
                        m.visitMembers(visited, mergeClassList, m);
                    }
                }
            }
        }
    }

    ModuleInfo getModuleInfo() {
        return minfo;
    }

    void setModuleInfo(ModuleInfo mi) {
        if (minfo != null)
            throw new AssertionError("ModuleInfo already created for " + name);
        minfo = mi;
    }

    public interface Visitor<R, P> {

        R visitClass(Klass k, P p);

        R visitResource(ResourceFile r, P p);
    }

    public <R, P> void visit(Visitor<R, P> visitor, P p) {
        for (Klass c : classes) {
            visitor.visitClass(c, p);
        }
        for (ResourceFile res : resources) {
            visitor.visitResource(res, p);
        }
    }

    static class Reference implements Comparable<Reference> {

        final Klass referrer, referree;

        Reference(Klass referrer, Klass referree) {
            this.referrer = referrer;
            this.referree = referree;
        }

        Klass referrer() {
            return referrer;
        }

        Klass referree() {
            return referree;
        }

        @Override
        public int hashCode() {
            return referrer.hashCode() ^ referree.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Reference)) {
                return false;
            }
            if (this == obj) {
                return true;
            }

            Reference r = (Reference) obj;
            return (this.referrer.equals(r.referrer) && this.referree.equals(r.referree));
        }

        @Override
        public int compareTo(Reference r) {
            int ret = referrer.compareTo(r.referrer);
            if (ret == 0) {
                ret = referree.compareTo(r.referree);
            }
            return ret;
        }
    }

    interface ModuleVisitor<P> {

        public void preVisit(Module m, P param);

        public void visited(Module m, Module child, P param);

        public void postVisit(Module m, P param);
    }
}
