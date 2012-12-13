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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

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
    private final Map<String,View> views;
    private final View defaultView;
    private final View internalView;
    private final Map<String, PackageInfo> packageForClass;
    private final Map<String, PackageInfo> packageForResource;
    private final Set<Dependence> requires; // requires came from ModuleConfig

    // update during the analysis
    private boolean isBaseModule;
    private Module group;
    private ModuleInfo minfo;
    protected Module(ModuleConfig config) {
        this.name = config.module;
        this.version = config.version;
        this.isBaseModule = name.equals(baseModuleName);
        this.classes = new HashSet<Klass>();
        this.resources = new HashSet<ResourceFile>();
        this.packageForClass = new HashMap<>();
        this.packageForResource = new HashMap<>();
        this.requires = new HashSet<>(config.requires());
        this.config = config;
        this.unresolved = new HashSet<Reference>();
        this.members = new HashSet<Module>();
        this.group = this; // initialize to itself

        this.views = new LinkedHashMap<>();
        for (ModuleConfig.View mcv : config.viewForName.values()) {
            addView(mcv.name, mcv);
        }
        this.defaultView = views.get(name);

        // create an internal view
        this.internalView = addView(name + ".internal");
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

    Collection<PackageInfo> packages() {
        return packageForClass.values();
    }

    Set<ResourceFile> resources() {
        return Collections.unmodifiableSet(resources);
    }

    Set<Dependence> configRequires() {
        return requires;
    }

    Set<Module> members() {
        return Collections.unmodifiableSet(members);
    }

    Module.View defaultView() {
        return defaultView;
    }

    Module.View internalView() {
        return internalView;
    }

    Collection<View> views() {
        return views.values();
    }

    View addView(String name) {
        View v = new View(this, name);
        views.put(name, v);
        return v;
    }

    private View addView(String name, ModuleConfig.View mcv) {
        View v = new View(this, mcv, name);
        views.put(name, v);
        return v;
    }

    Module.View getView(String name) {
        return views.get(name);
    }

    Module.View getView(Klass k) {
        String pn = k.getPackageName();
        View view = internalView;
        for (View v : views.values()) {
            if (v.exports.contains(pn)) {
                view = v;
                break;
            }
        }
        // make sure a package referenced by other modules
        // is exported either in the default view or internal view
        PackageInfo pinfo = packageForClass.get(pn);
        if (contains(k) && !defaultView.exports.contains(pn)) {
            if (!pinfo.isExported && !internalView.exports.contains(pn)) {
                internalView.exports.add(pn);
            }
        }
        assert view.exports.contains(pn);

        return view;


    }

    boolean contains(Klass k) {
        return k != null && classes.contains(k);
    }

    // returns true if a property named <module-name>.<key> is set to "true"
    // otherwise; return false
    boolean moduleProperty(String key) {
        String value = moduleProps.getProperty(name + "." + key);
        if (value == null)
            return false;
        else
            return Boolean.parseBoolean(value);
    }

    boolean isEmpty() {
        if (!classes.isEmpty() || !resources.isEmpty())
            return false;

        for (View v : views.values()) {
            if (v.mainClass() != null)
                return false;
        }
        return true;
    }

    boolean allowsEmpty() {
        return moduleProperty("allows.empty");
    }

    protected boolean isTopLevel() {
        // module with no class is not included except the base module
        // or reexporting APIs from required modules
        boolean reexports = false;
        for (Dependence d : config().requires()) {
            reexports = reexports || d.isPublic();
        }
        return this.group() == this
                && (isBase() || !isEmpty() || allowsEmpty() || reexports);
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

        // add PackageInfo
        String pkg = k.getPackageName();
        PackageInfo pkginfo = getPackageInfo(pkg, packageForClass);
        pkginfo.addKlass(k);
    }

    private PackageInfo getPackageInfo(String pkg, Map<String, PackageInfo> packageMap) {
        PackageInfo pkginfo = packageMap.get(pkg);
        if (pkginfo == null) {
            pkginfo = new PackageInfo(this, pkg);
            packageMap.put(pkg, pkginfo);
        }
        return pkginfo;
    }

    void addResource(ResourceFile res) {
        resources.add(res);
        res.setModule(this);

        String pkg = "";
        int i = res.getName().lastIndexOf('/');
        if (i > 0) {
            pkg = res.getName().substring(0, i).replace('/', '.');
        }
        PackageInfo pkginfo = getPackageInfo(pkg, packageForResource);
        pkginfo.addResource(res);
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

        buildExports();
    }

    private void buildExports() {
        boolean all = moduleProperty("exports.all");
        for (PackageInfo pi : packageForClass.values()) {
            if (all || pi.isExported)
                defaultView.exports.add(pi.pkgName);
        }
    }


    boolean requiresModuleDependence(Klass k) {
        if (classes.contains(k))
            return false;

        if (k.getModule() == null)
            return true;

        // Returns true if class k is exported from another module
        // and not from the base's default view
        Module m = k.getModule().group();
        return !(m.isBase() && m.defaultView.exports.contains(k.getPackageName()));
    }

    Module getRequiresModule(Klass k) {
        if (requiresModuleDependence(k)) {
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

        // merge class list and resource list
        classes.addAll(m.classes);
        resources.addAll(m.resources);

        // merge package infos
        for (Map.Entry<String,PackageInfo> e : m.packageForClass.entrySet()) {
            String pn = e.getKey();
            PackageInfo pinfo = getPackageInfo(pn, packageForClass);
            pinfo.add(e.getValue());
        }

        for (Map.Entry<String,PackageInfo> e : m.packageForResource.entrySet()) {
            String pn = e.getKey();
            PackageInfo pinfo = getPackageInfo(pn, packageForResource);
            pinfo.add(e.getValue());
        }

        // rebuild default view's exports after PackageInfo are merged
        buildExports();

        // merge requires from module configs
        requires.addAll(m.requires);

        // merge views
        for (View v : m.views.values()) {
            if (views.containsKey(v.name)) {
                throw new RuntimeException(name + " and member " + m.name
                        + " already has view " + v.name);
            }
            if (v == m.defaultView) {
                // merge default view
                defaultView.merge(v);
            } else if (v == m.internalView) {
                internalView.merge(v);
            } else {
                views.put(v.name, v);
            }
        }
    }

    public static class View implements Comparable<View> {
        final Module module;
        final String name;
        private final Set<String> exports;
        private final Set<String> permitNames;
        private final Set<String> aliases;
        private String mainClass;
        private final Set<Module> permits;
        int refCount;

        // specified in modules.config; always include it in module-info.java
        View(Module m, ModuleConfig.View mcv, String name) {
            this.module = m;
            this.name = name;
            this.refCount = 0;
            this.exports = new HashSet<>();
            this.permits = new HashSet<>();
            this.permitNames = new HashSet<>();
            this.aliases = new HashSet<>();
            if (mcv != null) {
                exports.addAll(mcv.exports);
                permitNames.addAll(mcv.permits);
                aliases.addAll(mcv.aliases);
                this.mainClass = mcv.mainClass;
            }
        }

        // only show up in module-info.java if there is a reference to it.
        View(Module m, String name) {
            this.module = m;
            this.name = name;
            this.refCount = -1;
            this.exports = new HashSet<>();
            this.permits = new HashSet<>();
            this.permitNames = new HashSet<>();
            this.aliases = new HashSet<>();
        }

        boolean isEmpty() {
            // Internal view may have non-empty exports but it's only
            // non-empty if any module requires it
            return mainClass() == null &&
                    (refCount < 0 || exports.isEmpty()) &&
                    permits.isEmpty() &&
                    aliases.isEmpty();
        }

        Set<String> permitNames() {
            return Collections.unmodifiableSet(permitNames);
        }

        Set<Module> permits() {
            return Collections.unmodifiableSet(permits);
        }

        Set<String> aliases() {
            return Collections.unmodifiableSet(aliases);
        }

        Set<String> exports() {
            return Collections.unmodifiableSet(exports);
        }

        void addPermit(Module m) {
            permits.add(m);
        }

        void addExports(Set<String> packages) {
            exports.addAll(packages);
        }

        void merge(View v) {
            // main class is not propagated to the default view
            this.aliases.addAll(v.aliases);
            this.permitNames.addAll(v.permitNames);
        }

        Klass mainClass() {
            Klass k = mainClass != null
                    ? Klass.findKlass(mainClass) : null;
            return k;
        }

        void addRefCount() {
            refCount++;
        }

        String id() {
            return name + "@" + module.version();
        }

        public String toString() {
            return id();
        }

        public int compareTo(Module.View o) {
            if (o == null) {
                return -1;
            }
            int rc = module.compareTo(o.module);
            if (rc == 0) {
                return name.compareTo(o.name);
            }
            return rc;
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

        public final Module getModuleForView(String name) {
            Module m = findModule(name);
            if (m != null)
                return m;

            String[] suffices = getModuleProperty("module.view.suffix", "").split("\\s+");
            for (String s : suffices) {
                int i = name.lastIndexOf("." + s);
                if (i != -1 && name.endsWith("." + s)) {
                    String mn = name.substring(0, i);
                    if ((m = findModule(mn)) != null) {
                        if (m.getView(name) == null)
                            throw new RuntimeException("module view " + name + " doesn't exist");
                        return m;
                    }
                }
            }
            throw new RuntimeException("module " + name + " doesn't exist");
        }

        public final Module baseModule() {
            return findModule(Module.baseModuleName);
        }

        public final Set<Module> getAllModules() {
            // initialize unknown module (last to add to the list)
            unknownModule();
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
                    unknown = this.newModule(ModuleConfig.moduleConfigForUnknownModule());
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
