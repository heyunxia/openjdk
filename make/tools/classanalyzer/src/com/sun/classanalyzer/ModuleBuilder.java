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
import com.sun.classanalyzer.Module.ModuleVisitor;
import com.sun.classanalyzer.ModuleInfo.Dependence;
import com.sun.classanalyzer.ModuleInfo.PackageInfo;
import static com.sun.classanalyzer.ModuleInfo.Dependence.Modifier.*;
import java.io.File;
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
 * @author mchung
 */
public class ModuleBuilder {

    private final List<String> depConfigs = new ArrayList<String>();
    private final Map<Module, ModuleInfo> moduleinfos =
            new LinkedHashMap<Module, ModuleInfo>();
    private final String version;
    private final boolean mergeModules;

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
            // create modules based on the input config files
            for (String file : configs) {
                for (ModuleConfig mconfig : ModuleConfig.readConfigurationFile(file)) {
                    newModule(mconfig);
                }
            }
        }
        if (depconfigs != null) {
            this.depConfigs.addAll(depconfigs);
        }
        this.mergeModules = merge;
        this.version = version;
    }

    /**
     * Returns a module of a given name with no main entry point.
     */
    public Module newModule(String name) throws IOException {
        return newModule(new ModuleConfig(name, null));
    }

    /**
     * Returns a module of a given ModuleConfig.
     */
    public Module newModule(ModuleConfig mconfig) {
        return Module.addModule(mconfig);
    }

    /**
     * Loads modules from the .classlist and .resources files in
     * the given classListDir.
     *
     */
    public Set<Module> loadModulesFrom(File classlistDir) throws IOException {
        ClassListReader reader = new ClassListReader(this);
        return reader.loadModulesFrom(classlistDir);
    }

    /**
     * This method assigns the classes and resource files
     * to modules and generates the package information and
     * the module information.
     *
     * This method can be overridden in a subclass implementation.
     */
    public void run() throws IOException {
        // assign classes and resource files to the modules and
        // group fine-grained modules per configuration files
        buildModules();

        // generate package information
        buildPackageInfos();

        // analyze cross-module dependencies and generate ModuleInfo
        buildModuleInfos();
    }

    /**
     * Returns the resulting top-level, non-empty modules.
     */
    public Set<Module> getModules() {
        return moduleinfos.keySet();
    }

    /**
     * Builds modules from the existing list of classes and resource
     * files according to the module configuration files.
     *
     */
    protected void buildModules() throws IOException {
        // Add additional dependencies after classes are added to the modules
        DependencyConfig.parse(depConfigs);

        // process the roots and dependencies to get the classes for each module
        Collection<Module> modules = Module.getAllModules();
        for (Module m : modules) {
            m.processRootsAndReferences();
        }

        if (mergeModules) {
            // group fine-grained modules
            Module.buildModuleMembers();
        }
    }

    /**
     * Build ModuleInfo for the top level modules.
     */
    protected void buildModuleInfos() {
        // backedges (i.e. reverse dependences)
        Map<Module, Set<Module>> backedges = new TreeMap<Module, Set<Module>>();

        // analyze the module's dependences and create ModuleInfo
        for (Module m : Module.getAllModules()) {
            if (m.isTopLevel()) {
                ModuleInfo mi = buildModuleInfo(m);
                m.setModuleInfo(mi);
                moduleinfos.put(m, mi);
                // keep track of the backedges
                for (Dependence d : mi.requires()) {
                    // only add the top level modules
                    Module dep = d.getModule();
                    Set<Module> set = backedges.get(dep);
                    if (set == null) {
                        set = new TreeSet<Module>();
                        backedges.put(dep, set);
                    }
                    set.add(m);
                }
            }
        }

        // fixup permits after all ModuleInfo are created in two passes:
        // 1. permits the requesting module if it requires local dependence
        // 2. if permits set is non-empty, permits
        //    all of its requesting modules
        for (ModuleInfo mi : moduleinfos.values()) {
            for (Dependence d : mi.requires()) {
                if (d.isLocal()) {
                    Module dm = d.getModule();
                    moduleinfos.get(dm).addPermit(mi.getModule());
                }
            }
        }

        for (Map.Entry<Module, Set<Module>> e : backedges.entrySet()) {
            Module dm = e.getKey();
            ModuleInfo dmi = moduleinfos.get(dm);
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

    // module to packages
    private final Map<Module, Set<PackageInfo>> packagesForModule =
            new TreeMap<Module, Set<PackageInfo>>();
    // package name to PackageInfo set
    private final Map<String, Set<PackageInfo>> packages =
            new TreeMap<String, Set<PackageInfo>>();
    // module with split packages
    private final Map<Module, Set<PackageInfo>> modulesWithSplitPackage =
            new TreeMap<Module, Set<PackageInfo>>();

    /**
     * Builds PackageInfo for each top level module.
     */
    protected void buildPackageInfos() {
        for (Module m : Module.getAllModules()) {
            if (m.isTopLevel()) {
                Set<PackageInfo> pkgs = getPackageInfos(m);
                packagesForModule.put(m, pkgs);
            }
        }

        for (Map.Entry<Module, Set<PackageInfo>> e : packagesForModule.entrySet()) {
            Module m = e.getKey();
            for (PackageInfo p : e.getValue()) {
                Set<PackageInfo> set = packages.get(p.pkgName);
                if (set == null) {
                    set = new TreeSet<PackageInfo>();
                    packages.put(p.pkgName, set);
                }
                set.add(p);
            }
        }

        for (Map.Entry<String, Set<PackageInfo>> e : packages.entrySet()) {
            String pkg = e.getKey();
            if (e.getValue().size() > 1) {
                for (PackageInfo pi : e.getValue()) {
                    Set<PackageInfo> set = modulesWithSplitPackage.get(pi.module);
                    if (set == null) {
                        set = new TreeSet<PackageInfo>();
                        modulesWithSplitPackage.put(pi.module, set);
                    }
                    set.add(pi);
                }
            }
        }
    }

    public Map<String, Set<Module>> getSplitPackages() {
        Map<String, Set<Module>> result = new LinkedHashMap<String, Set<Module>>();
        for (Map.Entry<String, Set<PackageInfo>> e : packages.entrySet()) {
            String pkg = e.getKey();
            if (e.getValue().size() > 1) {
                for (PackageInfo pi : e.getValue()) {
                    Set<Module> set = result.get(pkg);
                    if (set == null) {
                        set = new TreeSet<Module>();
                        result.put(pkg, set);
                    }
                    set.add(pi.module);
                }
            }
        }
        return result;
    }

    private Set<PackageInfo> getPackageInfos(final Module m) {
        Map<String, PackageInfo> packages = new TreeMap<String, PackageInfo>();
        Module.Visitor<Void, Map<String, PackageInfo>> visitor =
                new Module.Visitor<Void, Map<String, PackageInfo>>() {

                    @Override
                    public Void visitClass(Klass k, Map<String, PackageInfo> packages) {
                        // update package statistics
                        String pkg = k.getPackageName();
                        PackageInfo pkginfo = packages.get(pkg);
                        if (pkginfo == null) {
                            pkginfo = new PackageInfo(m, pkg);
                            packages.put(pkg, pkginfo);
                        }

                        if (k.exists()) {
                            // only count the class that is parsed
                            pkginfo.add(k.getFileSize());
                        }
                        return null;
                    }

                    @Override
                    public Void visitResource(ResourceFile r, Map<String, PackageInfo> packages) {
                        // nop
                        return null;
                    }
                };

        m.visit(visitor, packages);
        return new TreeSet<PackageInfo>(packages.values());
    }

    private ModuleInfo buildModuleInfo(Module m) {
        Map<Module, Dependence> requires = new LinkedHashMap<Module, Dependence>();
        Set<Module> permits = new TreeSet<Module>();

        // add static dependences
        for (Klass from : m.classes()) {
            for (Klass to : from.getReferencedClasses()) {
                if (m.isModuleDependence(to)) {
                    // is this dependence overridden as optional?
                    boolean optional = OptionalDependency.isOptional(from, to);
                    addDependence(requires, new Dependence(from, to, optional));
                }
            }
        }

        // add dependency due to the main class
        Klass k = m.mainClass();
        if (k != null && m.isModuleDependence(k)) {
            addDependence(requires, new Dependence(k.getModule()));
        }

        // add requires and permits specified in the config files
        processModuleConfigs(m, requires, permits);

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
        Set<PackageInfo> splitPkgs = modulesWithSplitPackage.get(m);
        if (splitPkgs != null) {
            for (PackageInfo sp : splitPkgs) {
                Set<PackageInfo> pis = packages.get(sp.pkgName);
                for (PackageInfo pi : pis) {
                    // is the package splitted with its dependence?
                    if (requires.containsKey(pi.module)) {
                        // If so, the dependence has to be LOCAL
                        requires.get(pi.module).addModifier(LOCAL);
                    }
                }
            }
        }

        // use the module's exporter in the dependence
        Set<Dependence> depset = new TreeSet<Dependence>();
        for (Dependence d : requires.values()) {
            Dependence dep = d;
            if (!d.isLocal()) {
                Module exp = d.getModule().exporter(m);
                if (exp == null) {
                    throw new RuntimeException(d.getModule() + " null exporter");
                }
                if (d.getModule() != exp && exp != m) {
                    dep = new Dependence(exp, d.modifiers());
                }
            }
            // ## not to include optional dependences in jdk.boot
            // ## should move this to jdk.base
            if (m instanceof PlatformModuleBuilder.BootModule && d.isOptional()) {
                continue;
            }

            depset.add(dep);
        }
        ModuleInfo mi = new ModuleInfo(m, version, packagesForModule.get(m), depset, permits);
        return mi;
    }

    private void addDependence(Map<Module, Dependence> requires, Dependence d) {
        Module dm = d.getModule();
        Dependence dep = requires.get(dm);
        if (dep == null || dep.equals(d)) {
            requires.put(dm, d);
        } else {
            if (dep.getModule() != d.getModule()) {
                throw new RuntimeException("Unexpected dependence " + dep + " != " + d);
            }

            // update the modifiers
            dep.update(d);
            requires.put(dm, dep);
        }
    }

    private void processModuleConfigs(final Module module,
            final Map<Module, Dependence> requires,
            final Set<Module> permits) {
        ModuleVisitor<Void> v = new ModuleVisitor<Void>() {

            public void preVisit(Module p, Void dummy) {
            }

            public void visited(Module p, Module m, Void dummy) {
                for (Dependence d : m.config().requires()) {
                    addDependence(requires, d);
                }
                for (String name : m.config().permits()) {
                    Module pm = Module.findModule(name);
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

        Set<Module> visited = new TreeSet<Module>();
        // first add requires and permits for the module
        v.visited(module, module, null);
        // then visit their members
        module.visitMembers(visited, v, null);
    }
}
