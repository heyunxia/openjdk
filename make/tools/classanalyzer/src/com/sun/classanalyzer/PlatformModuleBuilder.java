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

import com.sun.classanalyzer.Module.Factory;
import com.sun.classanalyzer.ModuleInfo.Dependence;
import static com.sun.classanalyzer.PlatformModuleBuilder.PlatformFactory.*;

import java.io.IOException;
import java.util.*;

/**
 * A platform module builder for JDK.  JDK's boot module, base module,
 * and JRE tools module must be defined in the modules.config files.
 * The platform module builder will create the following platform
 * modules in addition to the ones specified in the configuration files:
 * - JDK and JRE aggregator modules
 * - one jdk.<m> aggregator module for each sun.<m> module to
 *   reexport its public APIs
 *
 * @author Mandy Chung
 */
public class PlatformModuleBuilder extends ModuleBuilder {
    private final PlatformFactory factory;

    public PlatformModuleBuilder(List<String> configs, String version)
            throws IOException {
        this(configs, null, true, version);
    }

    public PlatformModuleBuilder(List<String> configs,
            List<String> depconfigs,
            boolean merge,
            String version)
            throws IOException {
        super(null, depconfigs, merge, version);
        // the factory will create the modules for the input config files
        this.factory = new PlatformFactory(configs, version);
    }

    @Override
    protected Factory getFactory() {
        return factory;
    }

    @Override
    public Set<Module> run() throws IOException {
        // assign classes and resource files to the modules
        // group fine-grained modules per configuration files
        buildModules();

        // build public jdk modules to reexport sun.* modules
        buildJDKModules();

        // generate package infos and determine if there is any split package
        buildPackageInfos();

        // analyze cross-module dependencies and generate ModuleInfo
        List<ModuleInfo> minfos = buildModuleInfos();

        // generate an ordered list from the module dependency graph
        result = Collections.unmodifiableSet(orderedModuleList(minfos));
        return result;
    }

    private void buildJDKModules() {
        Set<PlatformModule> pmodules = new LinkedHashSet<PlatformModule>();
        PlatformModule jreToolModule = (PlatformModule)
                factory.findModule(JRE_TOOLS_MODULE);
        BootModule bootModule = (BootModule)
                factory.findModule(BOOT_MODULE);
        PlatformModule jdkModule = (PlatformModule) factory.findModule(JDK_MODULE);
        PlatformModule jreModule = (PlatformModule) factory.findModule(JRE_MODULE);

        for (Module m : factory.getAllModules()) {
            if (m.isTopLevel()) {
                PlatformModule pm = (PlatformModule) m;
                pmodules.add(pm);
            }
        }

        // set exporter
        for (PlatformModule pm : pmodules) {
            PlatformModule exporter = pm;
            String name = pm.name();
            if (name.startsWith("sun.")) {
                // create an aggregate module for each sun.* module
                String mn = name.replaceFirst("sun", "jdk");
                String mainClassName =
                        pm.mainClass() == null ? null : pm.mainClass().getClassName();

                PlatformModule rm = (PlatformModule) factory.findModule(mn);
                if (rm != null) {
                    if (pm.mainClass() != rm.mainClass()) {
                        // propagate the main class to its aggregator module
                        rm.setMainClass(mainClassName);
                    }
                    exporter = rm;
                } else if (pm.hasPlatformAPIs()) {
                    ModuleConfig config = new ModuleConfig(mn, version, mainClassName);
                    exporter = factory.addPlatformModule(config);
                }

                if (pm != exporter) {
                    pm.reexportBy(exporter);
                }
            }
        }

        // base module to reexport boot module
        bootModule.reexportBy((PlatformModule) factory.findModule(BASE_MODULE));

        // set up the jdk and jdk.jre modules
        for (Module m : factory.getAllModules()) {
            if (m.isTopLevel()) {
                PlatformModule pm = (PlatformModule) m;
                String name = pm.name();
                if (name.startsWith("jdk.") || name.startsWith("sun.")) {
                    if (pm != jdkModule && pm != jreModule) {
                        Module exp = pm.exporter(jdkModule);
                        // the "jdk" module requires all platform modules (public ones)
                        jdkModule.config().reexportModule(exp);
                        if (pm.isBootConnected() || pm == jreToolModule) {
                            // add all modules that are strongly connected to jdk.boot to JRE
                            jreModule.config().reexportModule(exp);
                        }
                    }
                }
            }
        }
    }

    /*
     * Returns an ordered list of platform modules according to
     * their dependencies with jdk.boot always be the first.
     */
    private Set<Module> orderedModuleList(Collection<ModuleInfo> minfos) {
        Set<Module> visited = new TreeSet<Module>();
        Set<Module> orderedList = new LinkedHashSet<Module>();
        Dependence.Filter filter = new Dependence.Filter() {

            @Override
            public boolean accept(Dependence d) {
                return !d.isOptional();
            }
        };

        BootModule bootModule = (BootModule)
                factory.findModule(BOOT_MODULE);

        // put the boot module first
        visited.add(bootModule);
        orderedList.add(bootModule);
        factory.findModule(BASE_MODULE).getModuleInfo().visitDependence(filter, visited, orderedList);
        factory.findModule(JDK_MODULE).getModuleInfo().visitDependence(filter, visited, orderedList);
        for (ModuleInfo mi : minfos) {
            mi.visitDependence(filter, visited, orderedList);
        }

        return orderedList;
    }

    @Override
    protected ModuleInfo buildModuleInfo(Module m) {
        ModuleInfo mi = super.buildModuleInfo(m);

        // use the module's exporter in the dependence
        Set<Dependence> depset = new TreeSet<Dependence>();
        for (Dependence d : mi.requires()) {
            Dependence dep = d;
            if (!d.isInternal() && !d.isLocal()) {
                Module exp = ((PlatformModule)d.getModule()).exporter(m);
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

        // return a new ModuleInfo with patched dependences
        return new ModuleInfo(m, depset, mi.permits());
    }

    static class PlatformFactory extends Factory {
       /**
        * Platform modules that must be defined in the modules.properties
        */
        static final String BOOT_MODULE =
                getValue("platform.boot.module");
        static final String BASE_MODULE =
                getValue("platform.base.module");
        static final String JDK_MODULE =
                getValue("platform.jdk.module");
        static final String JRE_MODULE =
                getValue("platform.jre.module");
        static final String JRE_TOOLS_MODULE =
                getValue("platform.jre.tools.module");

        static String getValue(String key) {
            String value = Module.getModuleProperty(key);
            if (value == null || value.isEmpty()) {
                throw new RuntimeException("Null or empty module property: " + key);
            }
            return value;
        }

        PlatformFactory(List<String> configs, String version) throws IOException {
            Module.setBaseModule(BASE_MODULE);

            // create modules based on the input config files
            List<ModuleConfig> mconfigs = new ArrayList<ModuleConfig>();
            for (String file : configs) {
                mconfigs.addAll(ModuleConfig.readConfigurationFile(file, version));
            }
            init(mconfigs);

            // Create the full jdk and jre modules
            addModule(new NoClassModule(JDK_MODULE, version));
            addModule(new NoClassModule(JRE_MODULE, version));
        }

        @Override
        public Module newModule(String name, String version) {
            return newPlatformModule(new ModuleConfig(name, version));
        }

        @Override
        public Module newModule(ModuleConfig config) {
            return newPlatformModule(config);
        }

        private PlatformModule newPlatformModule(ModuleConfig config) {
            if (config.module.equals(BOOT_MODULE)) {
                return new BootModule(config);
            } else {
                return new PlatformModule(config);
            }
        }

        PlatformModule addPlatformModule(String name, String version) {
            return addPlatformModule(new ModuleConfig(name, version));
        }

        PlatformModule addPlatformModule(ModuleConfig config) {
            PlatformModule m = newPlatformModule(config);
            addModule(m);
            return m;
        }
    }

    static class PlatformModule extends Module {
        private Module exporter;  // module that reexports this platform module
        public PlatformModule(ModuleConfig config) {
            super(config);
            this.exporter = this;
        }

        // support for incremental build
        // an aggregate module "jdk.*" is not defined in modules.config
        // files but created by the platform module builder
        // Set to the main class of sun.* module
        void setMainClass(String classname) {
            String mn = name();
            if (!mn.startsWith("jdk") || !isEmpty()) {
                throw new RuntimeException("module " + name()
                        + " not an aggregator");
            }

            if (classname == null) {
                throw new RuntimeException("Null main class for module " + name());
            }

            mainClassName = classname;
        }

        // requires local for JRE modules that are strongly
        // connected with the boot module
        boolean isBootConnected() {
            // ## should it check for local?
            Dependence d = config().requires.get(BOOT_MODULE);
            return d != null; // && d.isLocal());
        }
        private int platformAPIs;

        boolean hasPlatformAPIs() {
            platformAPIs = 0;
            Visitor<Void, PlatformModule> v = new Visitor<Void, PlatformModule>() {

                public Void visitClass(Klass k, PlatformModule pm) {
                    if (PackageInfo.isExportedPackage(k.getPackageName())) {
                        pm.platformAPIs++;
                    }
                    return null;
                }

                public Void visitResource(ResourceFile r, PlatformModule pm) {
                    return null;
                }
            };

            this.visit(v, this);
            return platformAPIs > 0;
        }

        // returns the module that is used by the requires statement
        // in other module's module-info
        Module exporter(Module from) {
            PlatformModule pm = (PlatformModule) from;
            if (pm.isBootConnected()) {
                // If it's a local module requiring jdk.boot, retain
                // the original requires; otherwise, use its external
                // module
                return this;
            } else {
                return exporter;
            }
        }

        void reexportBy(PlatformModule pm) {
            exporter = pm;
            // sun.<m> permits jdk.<m>
            this.config().addPermit(pm);
            // jdk.<m> requires public sun.<m>;
            pm.config().reexportModule(this);
        }
    }

    static class BootModule extends PlatformModule {
        BootModule(ModuleConfig config) {
            super(config);
        }

        @Override
        boolean isBootConnected() {
            return true;
        }
    }
    static class NoClassModule extends PlatformModule {
        NoClassModule(String name, String version) {
            super(new ModuleConfig(name, version));
        }

        @Override
        boolean allowEmpty() {
            return true;
        }
    }
}
