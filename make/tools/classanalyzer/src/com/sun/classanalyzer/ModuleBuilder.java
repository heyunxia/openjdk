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

import com.sun.classanalyzer.AnnotatedDependency.OptionalDependency;
import com.sun.classanalyzer.Module.Factory;
import com.sun.classanalyzer.Module.ModuleVisitor;
import com.sun.classanalyzer.ModuleInfo.Dependence;
import static com.sun.classanalyzer.ModuleInfo.Dependence.Modifier.*;
import java.io.IOException;
import java.util.*;

/**
 * Module builder that creates modules as defined in the given
 * module configuration files.  The run() method assigns
 * all classes and resources according to the module definitions.
 * Additional dependency information can be specified e.g.
 * Class.forName, JNI_FindClass, and service providers.
 *
 * @see DependencyConfig
 */
public class ModuleBuilder {
    protected Set<Module> result = new LinkedHashSet<Module>();

    protected final List<ModuleConfig> mconfigs = new ArrayList<ModuleConfig>();
    protected final List<String> depConfigs = new ArrayList<String>();
    protected final boolean mergeModules;
    protected final String version;

    public ModuleBuilder(List<String> configs, String version)
            throws IOException {
        this(configs, null, true, version);
    }

    public ModuleBuilder(List<String> configs,
            List<String> depconfigs,
            boolean merge,
            String version)
            throws IOException {
        if (configs != null) {
            for (String file : configs) {
                mconfigs.addAll(ModuleConfig.readConfigurationFile(file, version));
            }
        }
        if (depconfigs != null) {
            this.depConfigs.addAll(depconfigs);
        }
        this.mergeModules = merge;
        this.version = version;

    }

    /**
     * Returns the module factory.
     */
    protected Factory getFactory() {
        return Module.getFactory();
    }

    /**
     * Returns the resulting modules from this builder.
     */
    public final Set<Module> getModules() {
        return result;
    }

    /**
     * This method assigns the classes and resource files
     * to modules and generates the package information and
     * the module information.
     *
     * This method can be overridden in a subclass implementation.
     */
    public Set<Module> run() throws IOException {
        // assign classes and resource files to the modules and
        // group fine-grained modules per configuration files
        buildModules();

        // generate package infos and determine if there is any split package
        buildPackageInfos();

        // analyze cross-module dependencies and generate ModuleInfo
        List<ModuleInfo> minfos = buildModuleInfos();

        // generate an ordered list from the module dependency graph
        result = Collections.unmodifiableSet(orderedModuleList(minfos));
        return result;
    }

    /**
     * Builds modules from the existing list of classes and resource
     * files according to the module configuration files.
     *
     */
    protected void buildModules() throws IOException {
        // create the modules for the given configs
        getFactory().init(mconfigs);

        // Add additional dependencies after classes are added to the modules
        DependencyConfig.parse(depConfigs);

        // process the roots and dependencies to get the classes for each module
        for (Module m : getFactory().getAllModules()) {
            m.processRootsAndReferences();
        }

        // add classes with null module to the default unknown module
        for (Klass k : Klass.getAllClasses()) {
            if (k.getModule() == null)
                getFactory().unknownModule().addKlass(k);
        }

        if (mergeModules) {
            // group fine-grained modules
            getFactory().buildModuleMembers();
        }
    }

    /**
     * Build ModuleInfo for the top level modules.
     */
    protected List<ModuleInfo> buildModuleInfos() {

        List<ModuleInfo> minfos = new LinkedList<ModuleInfo>();
        Set<Module> ms = new LinkedHashSet<Module>();
        // analyze the module's dependences and create ModuleInfo
        // for all modules including the system modules
        for (Module m : getFactory().getAllModules()) {
            if (m.isTopLevel()) {
                ModuleInfo mi = buildModuleInfo(m);
                m.setModuleInfo(mi);
                minfos.add(mi);
            }
        }

        fixupPermits(minfos);

        return minfos;
    }

    private void fixupPermits(List<ModuleInfo> minfos) {
        // backedges (i.e. reverse dependences)
        Map<Module, Set<Module>> backedges = new HashMap<Module, Set<Module>>();
        Map<Module, ModuleInfo> map = new LinkedHashMap<Module, ModuleInfo>();

        // fixup permits after all ModuleInfo are created in two passes:
        // 1. permits the requesting module if it requires local dependence
        // 2. if permits set is non-empty, permits
        //    all of its requesting modules
        for (ModuleInfo mi : minfos) {
            // keep track of the backedges
            map.put(mi.getModule(), mi);
            for (Dependence d : mi.requires()) {
                // only add the top level modules
                Module dep = d.getModule();
                Set<Module> set = backedges.get(dep);
                if (set == null) {
                    set = new HashSet<Module>();
                    backedges.put(dep, set);
                }
                set.add(mi.getModule());
            }
        }

        for (ModuleInfo mi : minfos) {
            for (Dependence d : mi.requires()) {
                if (d.isLocal()) {
                    Module dm = d.getModule();
                    map.get(dm).addPermit(mi.getModule());
                }
            }
        }

        for (Map.Entry<Module, Set<Module>> e : backedges.entrySet()) {
            Module dm = e.getKey();
            ModuleInfo dmi = map.get(dm);
            if (dmi == null) {
                throw new RuntimeException(dm + " null moduleinfo");
            }
            if (dmi.permits().size() > 0) {
                for (Module m : e.getValue()) {
                    dmi.addPermit(m);
                }
            }
        }
    }

    private Set<Module> otherModules = new LinkedHashSet<Module>();
    public final void addModules(Set<Module> ms) {
        otherModules.addAll(ms);
        // ## current implementation requires ModuleInfo be created
        // ## for all modules for the analysis.  Need to add them
        // ## in the factory's modules list.
        getFactory().addModules(ms);
    }

    private Set<Module> orderedModuleList(Collection<ModuleInfo> minfos) {
        // add modules to the moduleinfos map in order
        // its dependences first before the module
        // TODO: what if there is a cycle??
        Set<Module> visited = new HashSet<Module>();
        Set<Module> orderedList = new LinkedHashSet<Module>();
        Dependence.Filter filter = new Dependence.Filter() {

            @Override
            public boolean accept(Dependence d) {
                return !d.isOptional();
            }
        };

        for (ModuleInfo mi : minfos) {
            mi.visitDependence(filter, visited, orderedList);
        }
        // only return the modules that this builder is interested in
        Set<Module> ms = new LinkedHashSet<Module>(orderedList);
        ms.removeAll(otherModules);
        return ms;
    }

    // module with split packages
    private final Map<String, Set<Module>> splitPackages =
            new TreeMap<String, Set<Module>>();
    public Map<String, Set<Module>> getSplitPackages() {
        return splitPackages;
    }
    /**
     * Builds PackageInfo for each top level module.
     */
    protected void buildPackageInfos() {
        // package name to PackageInfo set
        Map<String, Set<PackageInfo>> packages =
                new HashMap<String, Set<PackageInfo>>();
        // build the map of a package name to PackageInfo set
        // It only looks at its own list of modules.
        // Subclass of ModuleBuilder can exclude any modules
        for (Module m : getFactory().getAllModules()) {
            if (m.isTopLevel() && !otherModules.contains(m)) {
                for (PackageInfo p : m.packages()) {
                    Set<PackageInfo> set = packages.get(p.pkgName);
                    if (set == null) {
                        set = new HashSet<PackageInfo>();
                        packages.put(p.pkgName, set);
                    }
                    set.add(p);
                }
            }
        }

        for (Map.Entry<String, Set<PackageInfo>> e : packages.entrySet()) {
            String pkg = e.getKey();
            // split package if there are more than one PackageInfo
            if (e.getValue().size() > 1) {
                for (PackageInfo pi : e.getValue()) {
                    Set<Module> mset = splitPackages.get(pkg);
                    if (mset == null) {
                        mset = new TreeSet<Module>();
                        splitPackages.put(pkg, mset);
                    }
                    mset.add(pi.module);
                }
            }
        }
    }

    protected ModuleInfo buildModuleInfo(Module m) {
        Map<Module, Dependence> requires = new LinkedHashMap<Module, Dependence>();
        Set<Module> permits = new HashSet<Module>();

        // add static dependences
        for (Klass from : m.classes()) {
            for (Klass to : from.getReferencedClasses()) {
                if (m.isModuleDependence(to)) {
                    // is this dependence overridden as optional?
                    boolean optional = OptionalDependency.isOptional(from, to);
                    addDependence(requires, to, optional);
                }
            }
        }

        // add requires and permits specified in the config files
        processModuleConfigs(m, requires, permits);

        // add dependency due to the main class
        Klass k = m.mainClass();
        if (k != null && m.isModuleDependence(k)) {
            addDependence(requires, k);
        }

        // add dependencies due to the AnnotatedDependency
        for (Dependence d : AnnotatedDependency.getDependencies(m)) {
            if (d.isOptional()) {
                Trace.trace("Warning: annotated dependency from %s to %s ignored%n",
                        m.name(), d.toString());
                continue;
            }
            addDependence(requires, d);
        }

        // Add LOCAL to the dependence and permits will be added
        // in the separate phase
        for (PackageInfo pi : m.packages()) {
            Set<Module> mset = splitPackages.get(pi.pkgName);
            if (mset == null) {
                continue;
            }

            assert mset.contains(m);
            for (Module sm : mset) {
                // is the package splitted with its dependence?
                if (requires.containsKey(sm)) {
                    // If so, the dependence has to be LOCAL
                    requires.get(sm).addModifier(LOCAL);
                }
            }
        }

        ModuleInfo mi = new ModuleInfo(m,
                new HashSet<Dependence>(requires.values()),
                permits);
        return mi;
    }

    private void addDependence(Map<Module, Dependence> requires, Klass k) {
        addDependence(requires, k, false);
    }

    private void addDependence(Map<Module, Dependence> requires, Klass k, boolean optional) {
        Dependence d = new Dependence(k.getModule(), optional);
        d.setInternal(PackageInfo.isExportedPackage(k.getPackageName()) == false);
        addDependence(requires, d);
    }

    private void addDependence(Map<Module, Dependence> requires, Dependence d) {
        Module dm = d.getModule();
        Dependence dep = requires.get(dm);
        if (dep != null && !dep.equals(d)) {
            if (dep.getModule() != d.getModule()) {
                throw new RuntimeException("Unexpected dependence " + dep + " != " + d);
            }

            // update the modifiers
            dep.update(d);
            d = dep;
        }
        requires.put(dm, d);
    }

    private void processModuleConfigs(final Module module,
            final Map<Module, Dependence> requires,
            final Set<Module> permits) {
        ModuleVisitor<Void> v = new ModuleVisitor<Void>() {
            public void preVisit(Module p, Void dummy) {
            }

            public void visited(Module p, Module m, Void dummy) {
                for (Dependence d : m.config().requires()) {
                    if (d.getModule() == null) {
                        // set the module in the Dependence as it
                        // was unknown when ModuleConfig was initialized.
                        Module dm = getFactory().findModule(d.id);
                        if (dm == null)
                            throw new RuntimeException("Module " + d.id + " doesn't exist");
                        d.setModule(dm);
                    }
                    addDependence(requires, d);
                }
                for (String name : m.config().permits()) {
                    Module pm = getFactory().findModule(name);
                    if (pm != null) {
                        permits.add(pm.group());
                    } else {
                        throw new RuntimeException("module " + name
                                + " specified in the permits rule for " + m.name()
                                + " doesn't exist");
                    }
                }
            }

            public void postVisit(Module p, Void dummy) {
            }
        };

        Set<Module> visited = new HashSet<Module>();
        // first add requires and permits for the module
        v.visited(module, module, null);
        // then visit their members
        module.visitMembers(visited, v, null);
    }
}
