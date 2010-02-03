/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */
package com.sun.classanalyzer;

import com.sun.classanalyzer.AnnotatedDependency.OptionalDependency;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 *
 * @author Mandy Chung
 */
public class Module implements Comparable<Module> {

    private static final Map<String, Module> modules = new LinkedHashMap<String, Module>();
    private static final Set<Module> platformModules = new TreeSet<Module>();

    public static Module addModule(ModuleConfig config) {
        String name = config.module;
        if (modules.containsKey(name)) {
            throw new RuntimeException("module \"" + name + "\" already exists");
        }

        Module m = new Module(config);
        modules.put(name, m);
        return m;
    }

    public static Module findModule(String name) {
        return modules.get(name);
    }
    static final String DEFAULT_BOOT_MODULE = "jdk.boot";
    static final String JDK_MODULE = "jdk";
    static final String JRE_MODULE = "jdk.jre";
    static final String JDK_TOOLS = "jdk.tools";
    static final String JRE_TOOLS = "jdk.jre.tools";
    static final String JDK_BASE_TOOLS = "jdk.base.tools";

    static void initPlatformModules() {
        if (platformModules.isEmpty()) {
            try {
                Module boot = bootModule();
                Module jreTools = findModule(JRE_TOOLS);
                Module jdkTools = findModule(JDK_TOOLS);
                Module jdkBaseTools = findModule(JDK_BASE_TOOLS);

                // Add the full jdk and jre modules
                Module jdk = new Module(new ModuleConfig(JDK_MODULE));
                Module jre = new Module(new ModuleConfig(JRE_MODULE));
                platformModules.add(jdk);
                platformModules.add(jre);

                // Create one aggregrate module for each sun module
                for (Module m : getTopLevelModules()) {
                    Module pm = m;
                    if (!m.name().startsWith("jdk") && !m.name().startsWith("sun.")) {
                        trace("Non-platform module: %s%n", m.name());
                    }
                    if (m.name().startsWith("sun.")) {
                        // create an aggregate module for each sun.* module
                        String name = m.name().replaceFirst("sun", "jdk");
                        pm = new Module(new ModuleConfig(name, m.config.mainClass()));
                        platformModules.add(pm);
                        pm.requires.add(m);
                        m.permits.add(pm);
                        if (m.isBase() && boot != null) {
                            // permits the base module to require the boot module
                            m.requires.add(boot);
                            boot.permits.add(m);
                        }
                    }
                    if (pm.isPlatformModule()) {
                        // add all platform modules in jdk module
                        jdk.requires.add(pm);

                        if (pm != jdkTools && pm != jdkBaseTools) {
                            // add non-tools module to JRE
                            jre.requires.add(pm);
                        }
                    }
                    if (pm == jdkTools || pm == jdkBaseTools) {
                        pm.permits.add(jdk);
                    }
                    if (pm == jreTools) {
                        // make jdk.tools only useable by jdk
                        pm.permits.add(jdk);
                        pm.permits.add(jre);
                    }
                }

                // fixup non-platform modules to require non-local dependence
                for (Module m : getTopLevelModules()) {
                    if (m != boot && !m.isPlatformModule() && !m.name().startsWith("sun.")) {
                        fixupModuleRequires(m);
                    }
                }

                // fixup the tools modules that are defined in modules.group
                fixupModuleRequires(jdkTools);
                fixupModuleRequires(jreTools);
                fixupModuleRequires(jdkBaseTools);

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static Module mapPlatformModule(Module rm) {
        if (rm.name().startsWith("sun.")) {
            String name = rm.name().replaceFirst("sun", "jdk");
            for (Module pm : platformModules) {
                if (pm.name().equals(name)) {
                    return pm;
                }
            }
            throw new RuntimeException("Platform module for " + rm + " not found");
        }
        return null;
    }

    private static void fixupModuleRequires(Module m) {
        Set<Module> requires = new TreeSet<Module>(m.requires);
        Module boot = bootModule();
        Module base = findModule(baseModuleName);

        if (!m.isPlatformModule()) {
            requires.add(base);
        }
        for (Module rm : m.requires) {
            Module req = rm;
            if (rm == boot && !m.isPlatformModule()) {
                // skip boot module
                rm.permits.remove(m);
                continue;
            }
            if (rm.name().startsWith("sun.")) {
                req = mapPlatformModule(rm);
                rm.permits.remove(m);
            }
            requires.add(req);
        }
        m.requires.clear();
        m.requires.addAll(requires);

        Set<Dependency> deps = new TreeSet<Dependency>();
        for (Dependency d : m.dependents()) {
            Module dm = d.module.group();
            if (dm == boot && !m.isPlatformModule()) {
                // skip boot module
                dm.permits.remove(m);
                continue;
            }
            if (dm.name().startsWith("sun.")) {
                dm.permits.remove(m);
                dm = mapPlatformModule(dm);
            }
            deps.add(new Dependency(dm, d.optional, d.dynamic));
        }
        m.dependents.clear();
        m.dependents.addAll(deps);

    }

    static Collection<Module> platformModules() {
        return Collections.unmodifiableCollection(platformModules);
    }
    private static Module boot;

    static Module bootModule() {
        if (boot == null) {
            boot = findModule(DEFAULT_BOOT_MODULE);
        }
        return boot;
    }

    static Collection<Module> getTopLevelModules() {
        Set<Module> result = new LinkedHashSet<Module>();
        // always put the boot module first and then the base
        if (bootModule() != null) {
            result.add(bootModule());
        }

        result.add(findModule(baseModuleName));
        for (Module m : modules.values()) {
            if (m.group == m) {
                // include only modules with classes except the base module
                if (m.isBase() || m.isPlatformModule() || !m.isEmpty()) {
                    result.add(m);
                }
            }
        }
        return Collections.unmodifiableCollection(result);
    }
    private static String baseModuleName = "base";

    static void setBaseModule(String name) {
        baseModuleName = name;
    }
    private final String name;
    private final ModuleConfig config;
    private final Set<Klass> classes;
    private final Set<ResourceFile> resources;
    private final Set<Reference> unresolved;
    private final Set<Dependency> dependents;
    private final Map<String, PackageInfo> packages;
    private final Set<Module> members;
    private final Set<Module> requires;
    private final Set<Module> permits;
    private Module group;
    private boolean isBaseModule;
    private final boolean reexport;
    private final boolean isLocalModule;
    private final boolean isPlatformModule;

    private Module(ModuleConfig config) {
        this.name = config.module;
        this.isBaseModule = name.equals(baseModuleName);
        this.isPlatformModule = (name.startsWith("jdk.") && !name.equals(DEFAULT_BOOT_MODULE)) || name.equals("jdk");
        this.classes = new TreeSet<Klass>();
        this.resources = new TreeSet<ResourceFile>();
        this.config = config;
        this.unresolved = new HashSet<Reference>();
        this.dependents = new TreeSet<Dependency>();
        this.packages = new TreeMap<String, PackageInfo>();
        this.members = new TreeSet<Module>();
        this.permits = new TreeSet<Module>();
        this.requires = new TreeSet<Module>();
        this.group = this; // initialize to itself
        this.isLocalModule = name.equals(DEFAULT_BOOT_MODULE) || name.startsWith("sun.");
        this.reexport = name.startsWith("jdk") &&
                !(name.equals(DEFAULT_BOOT_MODULE) || name.equals(JDK_TOOLS) ||
                name.equals(JRE_TOOLS) || name.equals(JDK_BASE_TOOLS));
    }

    String name() {
        return name;
    }

    Module group() {
        return group;
    }

    boolean isBase() {
        return isBaseModule;
    }

    boolean isLocalModule() {
        return isLocalModule;
    }

    boolean reexport() {
        return reexport;
    }

    boolean isPlatformModule() {
        return isPlatformModule;
    }

    Set<Module> members() {
        return members;
    }

    boolean contains(Klass k) {
        return k != null && classes.contains(k);
    }

    boolean isEmpty() {
        return classes.isEmpty() && resources.isEmpty() && mainClass() == null;
    }

    private void initModuleInfo() {
        // initialize the permits set
        for (String s : config.permits()) {
            Module m = findModule(s);
            if (m != null) {
                permits.add(m.group());
            } else {
                throw new RuntimeException("module " + s +
                        " specified in the permits rule for " + name +
                        " doesn't exist");
            }
        }
        // add the requires set to the dependents list
        for (String s : config.requires()) {
            Module m = findModule(s);
            if (m != null) {
                requires.add(m.group());
            } else {
                throw new RuntimeException("module " + s +
                        " specified in the permits rule for " + name +
                        " doesn't exist");
            }
        }
    }

    private void fixupModuleInfo() {
        // fixup permits set
        Set<Module> newPermits = new TreeSet<Module>();
        for (Module m : permits) {
            newPermits.add(m.group());
        }

        permits.clear();
        permits.addAll(newPermits);

        for (Dependency d : dependents()) {
            if (d.dynamic || d.optional) {
                // ignore optional dependencies for now
                continue;
            }

            // add permits for all local dependencies
            if (d.module.group().isLocalModule()) {
                d.module.group().permits.add(this.group());
            }
        }

        // fixup requires set
        Set<Module> newRequires = new TreeSet<Module>();
        for (Module m : requires) {
            Module req = m.group();
            if (req.isEmpty()) {
                // remove from requires set if empty
                continue;
            }

            newRequires.add(req);
            if (req == bootModule() || req.name().startsWith("sun.")) {
                req.permits.add(this);
            }
        }

        requires.clear();
        requires.addAll(newRequires);
    }

    Set<Module> permits() {
        return permits;
    }

    Set<Module> requires() {
        return requires;
    }

    Klass mainClass() {
        String cls = config.mainClass();
        if (cls == null) {
            return null;
        }

        Klass k = Klass.findKlass(cls);
        if (k == null) {
            trace("module %s: main-class %s does not exist%n", name, cls);
        }
        return k;
    }

    /**
     * Returns a Collection of Dependency, only one for each dependent
     * module of the strongest dependency (i.e.
     * hard static > hard dynamic > optional static > optional dynamic
     */
    Collection<Dependency> dependents() {
        Map<Module, Dependency> deps = new LinkedHashMap<Module, Dependency>();
        for (Dependency dep : dependents) {
            Dependency d = deps.get(dep.module);
            if (d == null || dep.compareTo(d) > 0) {
                deps.put(dep.module, dep);
            }
        }
        return deps.values();
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

        // update package statistics
        String pkg = k.getPackageName();
        PackageInfo pkginfo = packages.get(pkg);
        if (pkginfo == null) {
            pkginfo = new PackageInfo(pkg);
            packages.put(pkg, pkginfo);
        }

        if (k.exists()) {
            // only count the class that is parsed
            pkginfo.add(k.getFileSize());
        }

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
                    addDependency(k, other);
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
                        } else {
                            if (otherModule != this) {
                                // this module is dependent on otherModule
                                addDependency(c, other);
                            }
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

    void addDependency(Klass from, Klass to) {
        Dependency dep = new Dependency(from, to);
        dependents.add(dep);
    }

    void fixupDependencies() {
        // update dependencies for classes that were allocated to modules after
        // this module was processed.

        for (Reference ref : unresolved) {
            Module m = ref.referree().getModule();
            if (m == null || m != this) {
                addDependency(ref.referrer, ref.referree);
            }
        }

        // add dependency due to the main class
        Klass k = mainClass();
        if (k != null) {
            dependents.add(new Dependency(k.getModule(), false, false));
        }
        fixupAnnotatedDependencies();

        // fixup requires and permits set
        initModuleInfo();

    }

    private void fixupAnnotatedDependencies() {
        // add dependencies that this klass may depend on due to the AnnotatedDependency
        dependents.addAll(AnnotatedDependency.getDependencies(this));
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

    <P> void visitMember(Set<Module> visited, Visitor<P> visitor, P p) {
        if (!visited.contains(this)) {
            visited.add(this);
            visitor.preVisit(this, p);
            for (Module m : members) {
                m.visitMember(visited, visitor, p);
                visitor.visited(this, m, p);
            }
            visitor.postVisit(this, p);
        } else {
            throw new RuntimeException("Cycle detected: module " + this.name);
        }
    }

    private Set<Module> getDependencies() {
        Set<Module> deps = new TreeSet<Module>(requires);
        for (Dependency d : dependents()) {
            if (d.dynamic || d.optional) {
                // ignore optional dependencies for now
                continue;
            }
            deps.add(d.module);
        }
        return deps;
    }

    <P> void visitDependence(Set<Module> visited, Visitor<P> visitor, P p) {
        if (!visited.contains(this)) {
            visited.add(this);

            visitor.preVisit(this, p);
            for (Module m : getDependencies()) {
                m.visitDependence(visited, visitor, p);
                visitor.visited(this, m, p);
            }
            visitor.postVisit(this, p);
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

        // merge the package statistics
        for (PackageInfo pinfo : m.getPackageInfos()) {
            String packageName = pinfo.pkgName;
            PackageInfo pkginfo = packages.get(packageName);
            if (pkginfo == null) {
                pkginfo = new PackageInfo(packageName);
                packages.put(packageName, pkginfo);
            }

            pkginfo.add(pinfo);
        }

        // merge all permits and requires set
        permits.addAll(m.permits);
        requires.addAll(m.requires);
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
        Visitor<Module> groupSetter = new Visitor<Module>() {

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
                    m.visitMember(new TreeSet<Module>(), groupSetter, p);
                }
            }
        }

        Visitor<Module> mergeClassList = new Visitor<Module>() {

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
        for (Module m : modules.values()) {
            if (m.group() == m) {
                if (m.members().size() > 0) {
                    // merge class list from all its members
                    m.visitMember(visited, mergeClassList, m);
                }

                // clear the dependencies before fixup
                m.dependents.clear();

                // fixup dependencies
                for (Klass k : m.classes) {
                    for (Klass other : k.getReferencedClasses()) {
                        if (m.isModuleDependence(other)) {
                            // this module is dependent on otherModule
                            m.addDependency(k, other);
                        }
                    }
                }

                // add dependency due to the main class
                Klass k = m.mainClass();
                if (k != null && m.isModuleDependence(k)) {
                    m.dependents.add(new Dependency(k.getModule().group(), false, false));
                }

                // add dependencies that this klass may depend on due to the AnnotatedDependency
                m.fixupAnnotatedDependencies();
            }
        }

        for (Module m : modules.values()) {
            if (m.group() == m) {
                // fixup permits set
                m.fixupModuleInfo();
            }
        }
    }

    Set<Module> orderedDependencies() {
        Visitor<Set<Module>> walker = new Visitor<Set<Module>>() {

            public void preVisit(Module m, Set<Module> result) {
                // nop - depth-first search
            }

            public void visited(Module m, Module child, Set<Module> result) {
            }

            public void postVisit(Module m, Set<Module> result) {
                result.add(m);
            }
        };

        Set<Module> visited = new TreeSet<Module>();
        Set<Module> result = new LinkedHashSet<Module>();

        visitDependence(visited, walker, result);
        return result;
    }

    class PackageInfo implements Comparable {

        final String pkgName;
        int count;
        long filesize;

        PackageInfo(String name) {
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
        public int compareTo(Object o) {
            return pkgName.compareTo(((PackageInfo) o).pkgName);
        }
    }

    Set<PackageInfo> getPackageInfos() {
        return new TreeSet<PackageInfo>(packages.values());
    }

    void printSummaryTo(String output) throws IOException {
        PrintWriter writer = new PrintWriter(output);
        try {
            long total = 0L;
            int count = 0;
            writer.format("%10s\t%10s\t%s\n", "Bytes", "Classes", "Package name");
            for (String pkg : packages.keySet()) {
                PackageInfo info = packages.get(pkg);
                if (info.count > 0) {
                    writer.format("%10d\t%10d\t%s\n", info.filesize, info.count, pkg);
                    total +=
                            info.filesize;
                    count +=
                            info.count;
                }

            }

            writer.format("\nTotal: %d bytes (uncompressed) %d classes\n", total, count);
        } finally {
            writer.close();
        }

    }

    void printClassListTo(String output) throws IOException {
        if (classes.isEmpty()) {
            return;
        }

        PrintWriter writer = new PrintWriter(output);
        try {
            for (Klass c : classes) {
                if (c.exists()) {
                    writer.format("%s\n", c.getClassFilePathname());
                } else {
                    trace("%s in module %s missing\n", c, this);
                }

            }

        } finally {
            writer.close();
        }

    }

    void printResourceListTo(String output) throws IOException {
        // no file created if the module doesn't have any resource file
        if (resources.isEmpty()) {
            return;
        }

        PrintWriter writer = new PrintWriter(output);
        try {
            for (ResourceFile res : resources) {
                writer.format("%s\n", res.getPathname());
            }

        } finally {
            writer.close();
        }

    }

    void printDependenciesTo(String output, boolean showDynamic) throws IOException {
        PrintWriter writer = new PrintWriter(output);
        try {
            // classes that this klass may depend on due to the AnnotatedDependency
            Map<Reference, Set<AnnotatedDependency>> annotatedDeps = AnnotatedDependency.getReferences(this);

            for (Klass klass : classes) {
                Set<Klass> references = klass.getReferencedClasses();
                for (Klass other : references) {
                    String classname = klass.getClassName();
                    boolean optional = OptionalDependency.isOptional(klass, other);
                    if (optional) {
                        classname = "[optional] " + classname;
                    }

                    Module m = getModuleDependence(other);
                    if (m != null || other.getModule() == null) {
                        writer.format("%-40s -> %s (%s)", classname, other, m);
                        Reference ref = new Reference(klass, other);
                        if (annotatedDeps.containsKey(ref)) {
                            for (AnnotatedDependency ad : annotatedDeps.get(ref)) {
                                writer.format(" %s", ad.getTag());
                            }
                            // printed; so remove the dependency from the annotated deps list
                            annotatedDeps.remove(ref);
                        }
                        writer.format("\n");
                    }

                }
            }


            // print remaining dependencies specified in AnnotatedDependency list
            if (annotatedDeps.size() > 0) {
                for (Map.Entry<Reference, Set<AnnotatedDependency>> entry : annotatedDeps.entrySet()) {
                    Reference ref = entry.getKey();
                    Module m = getModuleDependence(ref.referree);
                    if (m != null || ref.referree.getModule() == null) {
                        String classname = ref.referrer.getClassName();
                        boolean optional = true;
                        boolean dynamic = true;
                        String tag = "";
                        for (AnnotatedDependency ad : entry.getValue()) {
                            if (optional && !ad.isOptional()) {
                                optional = false;
                                tag = ad.getTag();
                            }

                            if (!ad.isDynamic()) {
                                dynamic = false;
                            }
                        }
                        if (!showDynamic && optional && dynamic) {
                            continue;
                        }

                        if (optional) {
                            if (dynamic) {
                                classname = "[dynamic] " + classname;
                            } else {
                                classname = "[optional] " + classname;
                            }
                        }
                        writer.format("%-40s -> %s (%s) %s%n", classname, ref.referree, m, tag);
                    }

                }
            }

        } finally {
            writer.close();
        }

    }

    void printModuleInfoTo(String output) throws IOException {
        String version = "7-ea";  // hardcode for now
        PrintWriter writer = new PrintWriter(output);
        try {
            writer.format("module %s @ %s {%n", name, version);
            String formatSep = "    requires";
            Set<Module> reqs = new TreeSet<Module>(requires);
            for (Dependency dep : dependents()) {
                if (dep.module == null) {
                    System.err.format("WARNING: module %s has a dependency on null module%n", name);
                }

                if (dep.module.isBase()) {
                    // skip base module
                    continue;
                }

                StringBuilder attributes = new StringBuilder();
                if (reexport) {
                    attributes.append(" public");
                }

                if (dep.module.group().isLocalModule()) {
                    attributes.append(" local");
                }

                if (dep.optional) {
                    if (!dep.dynamic) {
                        attributes.append(" optional");
                    }
                }

                // FIXME: ignore optional dependencies
                if (!dep.dynamic && !dep.optional) {
                    reqs.remove(dep.module);
                    writer.format("%s%s %s @ %s;%n",
                            formatSep,
                            attributes.toString(),
                            dep != null ? dep.module : "null", version);
                }

            }
            // additional requires
            if (reqs.size() > 0) {
                for (Module p : reqs) {
                    StringBuilder attributes = new StringBuilder();
                    if (reexport) {
                        attributes.append(" public");
                    }

                    if (p.isLocalModule()) {
                        attributes.append(" local");
                    }

                    writer.format("%s%s %s @ %s;%n", formatSep, attributes.toString(), p, version);
                }
            }

            // permits
            if (permits().size() > 0) {
                formatSep = "    permits";
                for (Module p : permits()) {
                    writer.format("%s %s", formatSep, p);
                    formatSep = ",";
                }

                writer.format(";%n");
            }
            if (mainClass() != null) {
                writer.format("    class %s;%n", mainClass().getClassName());
            }
            writer.format("}%n");
        } finally {
            writer.close();
        }
    }

    void printModuleDependenciesTo(String output) throws IOException {

        PrintWriter writer = new PrintWriter(output);
        try {
            for (Module m : orderedDependencies()) {
                writer.format("%s\n", m.name());
            }

        } finally {
            writer.close();
        }

    }

    static class Dependency implements Comparable<Dependency> {

        final Module module;
        final boolean optional;
        final boolean dynamic;

        Dependency(Klass from, Klass to) {
            // static dependency
            this.module = to.getModule() != null ? to.getModule().group() : null;
            this.optional = OptionalDependency.isOptional(from, to);
            this.dynamic = false;
        }

        Dependency(Module m, boolean optional, boolean dynamic) {
            this.module = m != null ? m.group() : null;
            this.optional = optional;
            this.dynamic = dynamic;
        }

        public boolean isLocal(Module from) {
            if (module.name().startsWith("sun.") || module == bootModule()) {
                // local requires if the requesting module is the boot module
                // or it's an aggregate platform module
                return true;
            }

            for (PackageInfo pkg : from.getPackageInfos()) {
                // local dependence if any package this module owns is splitted
                // across its dependence
                for (PackageInfo p : module.getPackageInfos()) {
                    if (pkg.pkgName.equals(p.pkgName)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Dependency)) {
                return false;
            }
            if (this == obj) {
                return true;
            }

            Dependency d = (Dependency) obj;
            if (this.module != d.module) {
                return false;
            } else {
                return this.optional == d.optional && this.dynamic == d.dynamic;
            }
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 19 * hash + (this.module != null ? this.module.hashCode() : 0);
            hash = 19 * hash + (this.optional ? 1 : 0);
            hash = 19 * hash + (this.dynamic ? 1 : 0);
            return hash;
        }

        @Override
        public int compareTo(Dependency d) {
            if (this.equals(d)) {
                return 0;
            }

            // Hard static > hard dynamic > optional static > optional dynamic
            if (this.module == d.module) {
                if (this.optional == d.optional) {
                    return this.dynamic ? -1 : 1;
                } else {
                    return this.optional ? -1 : 1;
                }
            } else if (this.module != null && d.module != null) {
                return (this.module.compareTo(d.module));
            } else {
                return (this.module == null) ? -1 : 1;
            }
        }

        @Override
        public String toString() {
            String s = module.name();
            if (dynamic && optional) {
                s += " (dynamic)";
            } else if (optional) {
                s += " (optional)";
            }
            return s;
        }
    }

    static class Reference implements Comparable<Reference> {

        private final Klass referrer, referree;

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
            return (this.referrer.equals(r.referrer) &&
                    this.referree.equals(r.referree));
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

    interface Visitor<P> {

        public void preVisit(Module m, P param);

        public void visited(Module m, Module child, P param);

        public void postVisit(Module m, P param);
    }
    private static boolean traceOn = System.getProperty("classanalyzer.debug") != null;

    private static void trace(String format, Object... params) {
        System.err.format(format, params);
    }
}
