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

import com.sun.classanalyzer.ModuleInfo.Dependence;
import static com.sun.classanalyzer.PlatformModuleBuilder.PlatformModuleNames.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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
    /**
     * Platform modules that must be defined in the modules.properties
     */
    static class PlatformModuleNames {
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

        private static String getValue(String key) {
            String value = Module.getModuleProperty(key);
            if (value == null || value.isEmpty()) {
                throw new RuntimeException("Null or empty module property: " + key);
            }
            return value;
        }
    }

    private BootModule bootModule;
    private final PlatformModule jdkModule;
    private final PlatformModule jreModule;

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

        Module.setBaseModule(BASE_MODULE);

        // create modules based on the input config files
        for (String file : configs) {
            for (ModuleConfig mconfig : ModuleConfig.readConfigurationFile(file)) {
                newModule(mconfig);
            }
        }

        // Create the full jdk and jre modules
        jdkModule = (PlatformModule) newModule(JDK_MODULE);
        jreModule = (PlatformModule) newModule(JRE_MODULE);
    }

    @Override
    public Module newModule(ModuleConfig mconfig) {
        return addPlatformModule(mconfig);
    }

    @Override
    public void run() throws IOException {
        // assign classes and resource files to the modules
        // group fine-grained modules per configuration files
        buildModules();

        // build public jdk modules to reexport sun.* modules
        buildJDKModules();

        // generate package information
        buildPackageInfos();

        // analyze cross-module dependencies and generate ModuleInfo
        buildModuleInfos();

        // ## Hack: add local to all requires
        for (Module m : Module.getAllModules()) {
            if (m.isTopLevel()) {
                PlatformModule pm = (PlatformModule) m;
                if (pm.isBootConnected()) {
                    for (Dependence d : pm.getModuleInfo().requires()) {
                        d.addModifier(Dependence.Modifier.LOCAL);
                    }
                }
            }
        }
    }

    private void buildJDKModules() {
        Set<PlatformModule> modules = new LinkedHashSet<PlatformModule>();
        PlatformModule jreToolModule = (PlatformModule)
                Module.findModule(JRE_TOOLS_MODULE);

        for (Module m : Module.getAllModules()) {
            if (m.isTopLevel()) {
                PlatformModule pm = (PlatformModule) m;
                modules.add(pm);
            }
        }

        // set exporter
        for (PlatformModule pm : modules) {
            PlatformModule exporter = pm;
            String name = pm.name();
            if (name.startsWith("sun.")) {
                // create an aggregate module for each sun.* module
                String mn = name.replaceFirst("sun", "jdk");
                String mainClassName =
                        pm.mainClass() == null ? null : pm.mainClass().getClassName();

                PlatformModule rm = (PlatformModule) Module.findModule(mn);
                if (rm != null) {
                    if (pm.mainClass() != rm.mainClass()) {
                        // propagate the main class to its aggregator module
                        rm.setMainClass(mainClassName);
                    }
                    exporter = rm;
                } else if (pm.hasPlatformAPIs()) {
                    ModuleConfig config = null;
                    try {
                        config = new ModuleConfig(mn, mainClassName);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                    exporter = addPlatformModule(config);
                }

                if (pm != exporter) {
                    pm.reexportBy(exporter);
                }
            }
        }

        // base module to reexport boot module
        bootModule.reexportBy((PlatformModule) Module.findModule(BASE_MODULE));

        // set up the jdk, jdk.jre and jdk.legacy modules
        for (Module m : Module.getAllModules()) {
            if (m.isTopLevel()) {
                PlatformModule pm = (PlatformModule) m;
                String name = pm.name();
                if (name.startsWith("jdk.") || name.startsWith("sun.")) {
                    if (pm != jdkModule && pm != jreModule) {
                        Module exp = pm.exporter(jdkModule);
                        // the "jdk" module requires all platform modules (public ones)
                        jdkModule.config().export(exp);
                        if (pm.isBootConnected() || pm == jreToolModule) {
                            // add all modules that are strongly connected to jdk.boot to JRE
                            jreModule.config().export(exp);
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
    @Override
    public Set<Module> getModules() {
        Set<Module> modules = new LinkedHashSet<Module>();
        // put the boot module first
        modules.add(bootModule);
        Module base = Module.findModule(BASE_MODULE);
        modules.addAll(base.getModuleInfo().dependences(
                new Dependence.Filter() {
                    @Override
                    public boolean accept(Dependence d) {
                        return !d.isOptional();
                    }
                }));
        modules.addAll(jdkModule.getModuleInfo().dependences(null));
        for (Module m : Module.getAllModules()) {
            if (m.isTopLevel() && !modules.contains(m)) {
                modules.addAll(m.getModuleInfo().dependences(null));
            }
        }
        return modules;
    }

    void readNonCorePackagesFrom(String nonCorePkgsFile) throws IOException {
        PlatformPackage.addNonCorePkgs(nonCorePkgsFile);
    }

    private PlatformModule addPlatformModule(ModuleConfig config) {
        PlatformModule m;
        if (config.module.equals(BOOT_MODULE)) {
            bootModule = new BootModule(config);
            m = bootModule;
        } else {
            m = new PlatformModule(config);
        }
        Module.addModule(m);
        return m;
    }

    public class PlatformModule extends Module {
        private Module exporter;  // module that reexports this platform module
        private String mainClass;
        public PlatformModule(ModuleConfig config) {
            super(config);
            this.exporter = this;
            this.mainClass = config.mainClass();
        }

        // support for incremental build
        // an aggregate module "jdk.*" is not defined in modules.config
        // files but created by the platform module builder
        // Set to the main class of sun.* module
        void setMainClass(String classname) {
            String mn = name();
            if (!mn.startsWith("jdk") || !isEmpty()) {
                throw new RuntimeException("module " + name() +
                    " not an aggregator");
            }

            if (classname == null)
                throw new RuntimeException("Null main class for module " + name());

            mainClass = classname;
        }

        @Override
        Klass mainClass() {
            if (mainClass != null)
                return Klass.findKlass(mainClass);
            else
                return null;
        }

        @Override
        boolean allowEmpty() {
            return this == jdkModule || this == jreModule || super.allowEmpty();
        }

        // requires local for JRE modules that are strongly
        // connected with the boot module
        boolean isBootConnected() {
            // ## should it check for local?
            return config().requires.containsKey(BOOT_MODULE);
        }

        private int platformAPIs;
        boolean hasPlatformAPIs() {
            platformAPIs = 0;
            Visitor<Void, PlatformModule> v = new Visitor<Void, PlatformModule>() {
                public Void visitClass(Klass k, PlatformModule pm) {
                    if (PlatformPackage.isOfficialClass(k.getClassName())) {
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
        @Override
        public Module exporter(Module from) {
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
            pm.config().export(this);
        }
    }

    public class BootModule extends PlatformModule {
        BootModule(ModuleConfig config) {
            super(config);
        }

        @Override
        boolean isBootConnected() {
            return true;
        }
    }

    static class PlatformPackage {

        private static String[] corePkgs = new String[]{
            "java", "javax",
            "org.omg", "org.w3c.dom",
            "org.xml.sax", "org.ietf.jgss"
        };
        private static Set<String> nonCorePkgs = new TreeSet<String>();

        static boolean isOfficialClass(String classname) {
            for (String pkg : corePkgs) {
                if (classname.startsWith(pkg + ".")) {
                    return true;
                }
            }

            // TODO: include later
            /*
            for (String pkg : nonCorePkgs) {
            if (classname.startsWith(pkg + ".")) {
            return true;
            }
            }
             */
            return false;
        }

        // process a properties file listing the non core packages
        static void addNonCorePkgs(String file) throws IOException {
            File f = new File(file);
            Properties props = new Properties();
            BufferedReader reader = null;

            try {
                reader = new BufferedReader(new FileReader(f));
                props.load(reader);
                String s = props.getProperty("NON_CORE_PKGS");
                String[] ss = s.split("\\s+");
                Deque<String> values = new LinkedList<String>();

                for (String v : ss) {
                    values.add(v.trim());
                }

                String pval;
                while ((pval = values.poll()) != null) {
                    if (pval.startsWith("$(") && pval.endsWith(")")) {
                        String key = pval.substring(2, pval.length() - 1);
                        String value = props.getProperty(key);
                        if (value == null) {
                            throw new RuntimeException("key " + key + " not found");
                        }
                        ss = value.split("\\s+");
                        for (String v : ss) {
                            values.add(v.trim());
                        }
                        continue;
                    }
                    if (pval.startsWith("java.") || pval.startsWith("javax")
                            || pval.startsWith("com.") || pval.startsWith("org.")) {
                        nonCorePkgs.add(pval);
                    } else {
                        throw new RuntimeException("Invalid non core package: " + pval);
                    }
                }
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }
        }
    }
}
