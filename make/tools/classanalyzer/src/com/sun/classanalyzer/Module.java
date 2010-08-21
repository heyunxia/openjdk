/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.classanalyzer.AnnotatedDependency.OptionalDependency;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.sun.classanalyzer.Platform.*;
import static com.sun.classanalyzer.Trace.*;

/**
 *
 * @author Mandy Chung
 */
public class Module implements Comparable<Module> {

    private static final Map<String, Module> modules = new LinkedHashMap<String, Module>();

    /**
     * Returns the top-level modules that are defined in
     * the input module config files.
     *
     */
    static Collection<Module> getTopLevelModules() {
        Set<Module> result = new LinkedHashSet<Module>();
        // always put the boot module first and then the base
        if (Platform.bootModule() != null) {
            result.add(Platform.bootModule());
        }
        result.add(findModule(baseModuleName));

        for (Module m : modules.values()) {
            if (m.isTopLevel()) {
                result.add(m);
            }
        }
        return Collections.unmodifiableCollection(result);
    }

    public static Module addModule(ModuleConfig config) {
        String name = config.module;
        if (modules.containsKey(name)) {
            throw new RuntimeException("module \"" + name + "\" already exists");
        }
        Module m;
        if (Platform.isBootModule(config.module)) {
            m = Platform.createBootModule(config);
        } else {
            m = new Module(config);
        }
        modules.put(name, m);
        return m;
    }

    public static Module findModule(String name) {
        return modules.get(name);
    }
    private static String baseModuleName = "base";
    private static String version = "7-ea";

    static void setBaseModule(String name) {
        baseModuleName = name;
    }

    static void setVersion(String ver) {
        version = ver;
    }
    private static Properties moduleProps = new Properties();

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
    private final Map<String, PackageInfo> packages;
    private final Set<Dependency> dependents;
    private final Set<Module> members;
    private final Set<RequiresModule> requires;
    // update during the analysis
    private Set<Module> permits;
    private Module group;
    private boolean isBaseModule;
    private int platformApiCount;

    protected Module(ModuleConfig config) {
        this.name = config.module;
        this.isBaseModule = name.equals(baseModuleName);
        this.classes = new TreeSet<Klass>();
        this.resources = new TreeSet<ResourceFile>();
        this.config = config;
        this.unresolved = new HashSet<Reference>();
        this.dependents = new TreeSet<Dependency>();
        this.packages = new TreeMap<String, PackageInfo>();
        this.members = new TreeSet<Module>();
        this.requires = new TreeSet<RequiresModule>(config.requires());
        this.group = this; // initialize to itself
        this.platformApiCount = 0;
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

    // requires local for JRE modules that are strongly
    // connected with the boot module
    boolean isBootConnected() {
        for (RequiresModule rm : requires) {
            if (Platform.isBootModule(rm.modulename)) {
                return true;
            }
        }
        return false;
    }
    private Module moduleForRequires;

    synchronized Module toRequiredModule() {
        if (moduleForRequires == null) {
            // create a module for external requires if needed
            moduleForRequires = Platform.toRequiresModule(this);
        }
        return moduleForRequires;
    }

    Set<Module> members() {
        return members;
    }

    boolean hasPlatformAPIs() {
        return platformApiCount > 0;
    }

    boolean contains(Klass k) {
        return k != null && classes.contains(k);
    }

    boolean isEmpty() {
        return classes.isEmpty() &&
                resources.isEmpty() &&
                mainClass() == null;
    }

    boolean allowEmpty() {
        return moduleProps.getProperty(name + ".allow.empty") != null;
    }

    Module alias() {
        String mn = moduleProps.getProperty(name + ".alias");
        Module m = this;
        if (mn != null) {
            m = findModule(mn);
            if (m == null) {
                throw new RuntimeException(name + ".alias = " + mn + " not found");
            }
        }
        return m;
    }

    protected boolean isTopLevel() {
        // module with no class is not included except the base module
        return this.group == this &&
                (isBase() || !isEmpty() || isAggregator() || allowEmpty());
    }

    boolean isAggregator() {
        // a module is an aggregator if it has no class and resource and no main class
        // but has a list of requires.
        if (isEmpty() && requires.size() > 0) {
            // return false if it requires only jdk.boot
            if (requires.size() == 1) {
                for (RequiresModule rm : requires) {
                    if (Platform.isBootModule(rm.modulename)) {
                        return false;
                    }
                }
            }
            return true;
        }

        return false;
    }

    // fixup permits and requires set after modules are merged
    void fixupModuleInfo() {
        Set<Module> newPermits = new TreeSet<Module>();
        for (Module m : permits()) {
            // in case multiple permits from the same group
            newPermits.add(m.group());
        }
        permits.clear();
        permits.addAll(newPermits);

        // fixup requires set
        Set<RequiresModule> newRequires = new TreeSet<RequiresModule>();
        for (RequiresModule rm : requires) {
            Module req = rm.module();
            if (req.isEmpty() && !req.isAggregator()) {
                // remove from requires set if empty and not a module aggregator
                continue;
            }

            newRequires.add(rm);
            if (req.requirePermits()) {
                req.permits().add(this.group());
            }
        }
        requires.clear();
        requires.addAll(newRequires);

        // add this to the permits set of its dependences if needed
        for (Dependency d : dependences()) {
            if (d.dynamic && !d.optional) {
                // ignore dynamic dependencies for now
                continue;
            }

            // add permits for all local dependencies
            Module dm = d.module();
            if (dm.requirePermits()) {
                dm.permits().add(this.group());
            }
        }
    }

    Klass mainClass() {
        String cls = config.mainClass();
        if (cls == null) {
            return null;
        }

        Klass k = Klass.findKlass(cls);
        return k;
    }

    synchronized Set<Module> permits() {
        if (permits == null) {
            this.permits = new TreeSet<Module>();
            // initialize the permits set
            for (String s : config.permits()) {
                Module m = findModule(s);
                if (m != null) {
                    permits.add(m.group());
                } else {
                    throw new RuntimeException("module " + s +
                            " specified in the permits rule for " + name + " doesn't exist");
                }
            }
        }
        return permits;
    }

    Set<RequiresModule> requires() {
        return requires;
    }

    Collection<Dependency> dependents() {
        Map<Module, Dependency> deps = new LinkedHashMap<Module, Dependency>();
        for (Dependency dep : dependents) {
            Dependency d = deps.get(dep.module());
            if (d == null || dep.compareTo(d) > 0) {
                deps.put(dep.module(), dep);
            }
        }
        return deps.values();
    }

    boolean requires(Module m) {
        for (RequiresModule rm : requires()) {
            if (rm.module() == m)
                return true;
        }
        return false;
    }
    /**
     * Returns a Collection of Dependency, only one for each dependent
     * module of the strongest dependency (i.e.
     * hard static > hard dynamic > optional static > optional dynamic
     */
    Collection<Dependency> dependences() {
        Set<Dependency> result = new TreeSet<Dependency>();
        for (Dependency d : dependents()) {
            Module dm = d.module();
            Module rm = dm;
            if (!dm.alias().requires(this)) {
                // use alias as the dependence except this module
                // is required by the alias that will result in
                // a recursive dependence.
                rm = dm.alias();
            }
            if (!isBootConnected()) {
                // If it's a local module requiring jdk.boot, retain
                // the original requires; otherwise, use its external
                // module
                rm = rm.toRequiredModule();
            }

            result.add(new Dependency(rm, d.optional, d.dynamic));
        }
        return result;
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
        if (k.isPlatformAPI()) {
            platformApiCount++;
        }

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

    void addRequiresModule(Module m) {
        addRequiresModule(m, false);
    }

    void addRequiresModule(Module m, boolean optional) {
        requires.add(new RequiresModule(m, optional));
        if (m.requirePermits()) {
            m.permits().add(this);
        }
    }

    boolean requirePermits() {
        return (name().startsWith("sun.") ||
                permits().size() > 0);
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

    private Set<Module> getDepModules() {
        Set<Module> deps = new TreeSet<Module>();
        for (Dependency d : dependences()) {
            if (d.dynamic || d.optional) {
                // ignore dynamic or optional dependencies for now
                continue;
            }
            deps.add(d.module());
        }
        for (RequiresModule req : requires) {
            if (req.optional) {
                // ignore optional dependencies for now
                continue;
            }
            deps.add(req.module());
        }
        return deps;
    }

    <P> void visitDependence(Set<Module> visited, Visitor<P> visitor, P p) {
        if (!visited.contains(this)) {
            visited.add(this);

            visitor.preVisit(this, p);
            for (Module m : getDepModules()) {
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

        platformApiCount += m.platformApiCount;

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
        permits().addAll(m.permits());
        requires().addAll(m.requires());
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
        Set<Module> groups = new TreeSet<Module>();
        for (Module m : modules.values()) {
            if (m.group() == m) {
                groups.add(m);
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
            int nonCoreAPIs = 0;
            writer.format("%10s\t%10s\t%s%n", "Bytes", "Classes", "Package name");
            for (String pkg : packages.keySet()) {
                PackageInfo info = packages.get(pkg);
                if (info.count > 0) {
                    if (Platform.isNonCoreAPI(pkg)) {
                        nonCoreAPIs += info.count;
                        writer.format("%10d\t%10d\t%s (*)%n",
                                info.filesize, info.count, pkg);
                    } else {
                        writer.format("%10d\t%10d\t%s%n",
                                info.filesize, info.count, pkg);
                    }
                    total += info.filesize;
                    count += info.count;
                }
            }


            writer.format("%nTotal: %d bytes (uncompressed) %d classes%n",
                    total, count);
            writer.format("APIs: %d core %d non-core (*)%n",
                    platformApiCount, nonCoreAPIs);
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
                            classname = "[optional] " + classname;
                        } else if (dynamic) {
                            classname = "[dynamic] " + classname;
                        }
                        writer.format("%-40s -> %s (%s) %s%n", classname, ref.referree, m, tag);
                    }
                }
            }
        } finally {
            writer.close();
        }

    }

    // print module dependency list
    void printDepModuleListTo(String output) throws IOException {
        PrintWriter writer = new PrintWriter(output);
        try {
            for (Module m : orderedDependencies()) {
                writer.format("%s\n", m.name());
            }
            if (Platform.legacyModule() != null &&
                    (this == Platform.jdkBaseModule() ||
                    this == Platform.jdkModule() ||
                    this == Platform.jreModule())) {
                // add legacy module in the modules.list
                // so that it will install legacy module as well.
                writer.format("%s\n", Platform.legacyModule());
            }
        } finally {
            writer.close();
        }
    }

    void printModuleInfoTo(String output) throws IOException {
        PrintWriter writer = new PrintWriter(output);
        try {
            writer.format("module %s @ %s {%n", name, version);
            String formatSep = "    requires";
            Map<String, RequiresModule> reqs = new TreeMap<String, RequiresModule>();
            for (RequiresModule rm : requires()) {
                reqs.put(rm.module().name(), rm);
            }

            for (Dependency dep : dependences()) {
                Module dm = dep.module();
                if (!isBootConnected()) {
                    // If it's a local module requiring jdk.boot, retain
                    // the original requires
                    dm = dm.toRequiredModule();
                }

                if (dm == null) {
                    System.err.format("WARNING: module %s has a dependency on null module%n", name);
                }

                StringBuilder attributes = new StringBuilder();
                RequiresModule rm = reqs.get(dm.name());

                if (rm != null && rm.reexport) {
                    attributes.append(" public");
                }

                if (isBootConnected() || (rm != null && rm.local)) {
                    attributes.append(" local");
                }

                if (dep.optional || (rm != null && rm.optional)) {
                    attributes.append(" optional");
                }

                // FIXME: ignore dynamic dependencies
                // Filter out optional dependencies for the boot module
                // which are addded in the jdk.base module instead
                if (!dep.dynamic || dep.optional) {
                    reqs.remove(dm.name());
                    writer.format("%s%s %s @ %s;%n",
                            formatSep,
                            attributes.toString(),
                            dep != null ? dm : "null", version);
                }

            }
            // additional requires
            if (reqs.size() > 0) {
                for (RequiresModule rm : reqs.values()) {
                    StringBuilder attributes = new StringBuilder();
                    if (rm.reexport) {
                        attributes.append(" public");
                    }
                    if (rm.optional) {
                        attributes.append(" optional");
                    }
                    if (isBootConnected() || rm.local) {
                        attributes.append(" local");
                    }

                    writer.format("%s%s %s @ %s;%n", formatSep, attributes.toString(), rm.module(), version);
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

    static class Dependency implements Comparable<Dependency> {

        protected Module module;
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

        Module module() {
            return module;
        }

        public boolean isLocal(Module from) {
            if (module().isBootConnected()) {
                // local requires if the requesting module is the boot module
                // or it's an aggregate platform module
                return true;
            }

            for (PackageInfo pkg : from.getPackageInfos()) {
                // local dependence if any package this module owns is splitted
                // across its dependence
                for (PackageInfo p : module().getPackageInfos()) {
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
            if (this.module() != d.module()) {
                return false;
            } else {
                return this.optional == d.optional && this.dynamic == d.dynamic;
            }
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 19 * hash + (this.module() != null ? this.module().hashCode() : 0);
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
            if (this.module() == d.module()) {
                if (this.optional == d.optional) {
                    return this.dynamic ? -1 : 1;
                } else {
                    return this.optional ? -1 : 1;
                }
            } else if (this.module() != null && d.module() != null) {
                return (this.module().compareTo(d.module()));
            } else {
                return (this.module() == null) ? -1 : 1;
            }
        }

        @Override
        public String toString() {
            String s = module().name();
            if (optional) {
                s += " (optional)";
            } else if (dynamic) {
                s += " (dynamic)";
            }
            return s;
        }
    }

    static class RequiresModule extends Dependency {

        final String modulename;
        final boolean reexport;
        final boolean local;

        public RequiresModule(String name, boolean optional, boolean reexport, boolean local) {
            super(null, optional, false /* dynamic */);
            this.modulename = name;
            this.reexport = reexport;
            this.local = local;
        }

        public RequiresModule(Module m, boolean optional) {
            super(m, optional, false);
            this.modulename = m.name();
            this.reexport = true;
            this.local = false;
        }

        // deferred initialization until it's called.
        // must call after all modules are merged.
        synchronized Module fixupModule() {
            if (module == null) {
                Module m = findModule(modulename);
                if (m == null) {
                    throw new RuntimeException("Required module \"" + modulename + "\" doesn't exist");
                }
                module = m.group();
            }
            return module;
        }

        @Override
        Module module() {
            return fixupModule();
        }

        @Override
        public int compareTo(Dependency d) {
            RequiresModule rm = (RequiresModule) d;
            if (this.equals(rm)) {
                return 0;
            }
            return modulename.compareTo(rm.modulename);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof RequiresModule)) {
                return false;
            }
            if (this == obj) {
                return true;
            }

            RequiresModule d = (RequiresModule) obj;
            return this.modulename.equals(d.modulename);
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 19 * hash + this.modulename.hashCode();
            return hash;
        }

        @Override
        public String toString() {
            String s = reexport ? "public " : "";
            if (optional) {
                s += "optional ";
            }
            s += modulename;
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

    interface Visitor<P> {

        public void preVisit(Module m, P param);

        public void visited(Module m, Module child, P param);

        public void postVisit(Module m, P param);
    }
}
