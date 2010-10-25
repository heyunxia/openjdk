/*
 * Copyright (c) 2009, 2010 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;


/**
 * Module contains a list of classes and resources.
 *
 * @author Mandy Chung
 */
public class Module implements Comparable<Module> {

    private static final Map<String, Module> modules =
            new LinkedHashMap<String, Module>();

    public static Collection<Module> getAllModules() {
        return Collections.unmodifiableCollection(modules.values());
    }

    public static void addModule(Module m) {
        String name = m.name();
        if (modules.containsKey(name)) {
            throw new RuntimeException("module \"" + name + "\" already exists");
        }
        modules.put(name, m);
    }

    public static Module addModule(ModuleConfig config) {
        String name = config.module;
        if (modules.containsKey(name)) {
            throw new RuntimeException("module \"" + name + "\" already exists");
        }
        Module m = new Module(config);
        addModule(m);
        return m;
    }

    public static Module findModule(String name) {
        return modules.get(name);
    }

    private static String baseModuleName = "base";
    static void setBaseModule(String name) {
        if (name == null || name.isEmpty()) {
            throw new RuntimeException("Null or empty base module");
        }
        baseModuleName = name;
    }

    private static Properties moduleProps = new Properties();
    static String getModuleProperty(String key) {
        return moduleProps.getProperty(key);
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
    private final ModuleConfig config;
    private final Set<Klass> classes;
    private final Set<ResourceFile> resources;
    private final Set<Reference> unresolved;
    private final Set<Module> members;
    // update during the analysis
    private Module group;
    private ModuleInfo minfo;
    private boolean isBaseModule;

    protected Module(ModuleConfig config) {
        this.name = config.module;
        this.isBaseModule = name.equals(baseModuleName);
        this.classes = new TreeSet<Klass>();
        this.resources = new TreeSet<ResourceFile>();
        this.config = config;
        this.unresolved = new HashSet<Reference>();
        this.members = new TreeSet<Module>();
        this.group = this; // initialize to itself
    }

    String name() {
        return name;
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

    // returns itself.
    public Module exporter(Module from) {
        return this;
    }

    protected boolean isTopLevel() {
        // module with no class is not included except the base module
        return this.group == this
                && (isBase() || !isEmpty() || !config.requires().isEmpty() || allowEmpty());
    }

    Klass mainClass() {
        String cls = config.mainClass();
        if (cls == null) {
            return null;
        }

        Klass k = Klass.findKlass(cls);
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
            if (group == this && m != null) {
                // top-level module
                return m.group;
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
    }

    static void buildModuleMembers() {
        // set up module member relationship
        for (Module m : modules.values()) {
            m.group = m; // initialize to itself
            for (String name : m.config.members()) {
                Module member = modules.get(name);
                if (member == null) {
                    throw new RuntimeException("module \"" + name + "\" doesn't exist");
                }
                m.members.add(member);
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
        for (Module p : modules.values()) {
            for (Module m : p.members) {
                if (m.group == m) {
                    m.visitMembers(new TreeSet<Module>(), groupSetter, p);
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

        Set<Module> visited = new TreeSet<Module>();
        Set<Module> groups = new TreeSet<Module>();
        for (Module m : modules.values()) {
            if (m.group() == m) {
                groups.add(m);
                if (m.members().size() > 0) {
                    // merge class list from all its members
                    m.visitMembers(visited, mergeClassList, m);
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
