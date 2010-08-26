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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import static com.sun.classanalyzer.Module.*;

/**
 * Platform module.
 *
 * The name of the platform modules starts with either "jdk." or "sun.".
 * All sun.* and jdk.boot are local modules.  Any requesting module
 * of a local module has to be explicitly permitted.
 *
 * The input module config files can define "sun.*" and "jdk.*"
 * modules.  For any sun.* module, it will have a corresponding
 * non-local platform module that is defined for application modules
 * to require.
 *
 * The tool will create the following platform modules:
 * 1) jdk.<name> for each sun.<name> module
 * 2) jdk module - represents the entire JDK
 * 3) jdk.jre module - represents the entire JRE
 *
 */
public class Platform {

    static final String DEFAULT_BOOT_MODULE = "jdk.boot";
    static final String JDK_BASE_MODULE = "jdk.base";
    // platform modules created but not defined in the input module configs.
    static final String JDK_MODULE = "jdk";
    static final String JRE_MODULE = "jdk.jre";
    static final String LEGACY_MODULE = "jdk.legacy";
    // the following modules are expected to be defined in
    // the input module config files.
    static final String JDK_TOOLS = "jdk.tools";
    static final String JRE_TOOLS = "jdk.tools.jre";
    static final String JDK_BASE_TOOLS = "jdk.tools.base";
    static final String JDK_LANGTOOLS = "jdk.langtools";

    static boolean isBootModule(String name) {
        return name.equals(DEFAULT_BOOT_MODULE);
    }
    private static BootModule bootModule;

    static Module createBootModule(ModuleConfig config) {
        bootModule = new BootModule(config);
        return bootModule;
    }

    static Module bootModule() {
        return bootModule;
    }
    private static Module jdkBaseModule;

    static Module jdkBaseModule() {
        if (jdkBaseModule == null) {
            jdkBaseModule = findModule(JDK_BASE_MODULE);
        }
        return jdkBaseModule;
    }
    private static Module jdkBaseToolModule;

    static Module jdkBaseToolModule() {
        if (jdkBaseToolModule == null) {
            jdkBaseToolModule = findModule(JDK_BASE_TOOLS);
        }
        return jdkBaseToolModule;
    }
    private static Module jdkModule;
    private static Module jreModule;
    private static Module legacyModule;

    static Module jdkModule() {
        return jdkModule;
    }

    static Module jreModule() {
        return jreModule;
    }

    static Module legacyModule() {
        return legacyModule;
    }

    private static Module addPlatformModule(String name, String mainClass) {
        ModuleConfig config = null;
        try {
            config = new ModuleConfig(name, mainClass);
            return Module.addModule(config);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    static boolean isAggregator(String name) {
        return name.equals(JDK_MODULE) ||
                name.equals(JRE_MODULE) ||
                name.equals(JDK_TOOLS) ||
                name.equals(JRE_TOOLS) ||
                name.equals(JDK_BASE_TOOLS) ||
                name.equals(LEGACY_MODULE) ||
                name.startsWith(JDK_LANGTOOLS);
    }

    // returns the module that is used by the requires statement
    // in other module's module-info
    static Module toRequiresModule(Module m) {
        Module moduleForRequires = m;
        if (m == bootModule()) {
            moduleForRequires = jdkBaseModule();
        } else if (m.name().startsWith("sun.")) {
            // create an aggregate module for each sun.* module
            String mn = m.name().replaceFirst("sun", "jdk");
            String mainClassName = m.mainClass() == null ? null : m.mainClass().getClassName();

            Module rm = findModule(mn);
            if (rm != null) {
                if (rm.mainClass() != m.mainClass()) {
                    throw new RuntimeException(mn +
                            " module already exists but mainClass not matched");
                }
                return rm;
            }

            if (m.hasPlatformAPIs()) {
                ModuleConfig config = null;
                try {
                    config = new ModuleConfig(mn, mainClassName);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                moduleForRequires = Module.addModule(config);
                moduleForRequires.addRequiresModule(m);
            }
        }
        return moduleForRequires;
    }

    static void fixupPlatformModules() {
        // Create the full jdk and jre modules
        jdkModule = addPlatformModule(JDK_MODULE, null /* no main class */);
        jreModule = addPlatformModule(JRE_MODULE, null /* no main class */);

        Module jreTools = findModule(JRE_TOOLS);
        Module jdkTools = findModule(JDK_TOOLS);
        Module jdkBaseTools = findModule(JDK_BASE_TOOLS);

        for (Module m : getTopLevelModules()) {
            // initialize module-info
            m.fixupModuleInfo();

            // set up the jdk, jdk.jre and jdk.legacy modules
            if (m.name().startsWith("jdk.") || m.name().startsWith("sun.")) {
                Module req = m.toRequiredModule();

                if (!(m.isAggregator() || isAggregator(m.name()))) {
                    // all platform modules are required jdk module
                    jdkModule.addRequiresModule(req);
                    if (m.isBootConnected()) {
                        // add all modules that are strongly connected to jdk.boot to JRE
                        jreModule.addRequiresModule(req);
                    }
                }
            } else {
                Trace.trace("Non-platform module: %s%n", m.name());
            }
        }
        // fixup the base module to include optional dependences from boot
        // ## It adds jndi, logging, and xml optional dependences
        // bootModule.fixupBase();
    }
    private static String[] corePkgs = new String[]{
        "java", "javax",
        "org.omg", "org.w3c.dom",
        "org.xml.sax", "org.ietf.jgss"
    };
    private static Set<String> nonCorePkgs = new TreeSet<String>();

    static void addNonCorePkgs(String file) throws FileNotFoundException, IOException {
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
                if (pval.startsWith("java.") || pval.startsWith("javax") ||
                        pval.startsWith("com.") || pval.startsWith("org.")) {
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

    static boolean isPlatformAPI(String classname) {
        for (String pkg : corePkgs) {
            if (classname.startsWith(pkg + ".")) {
                return true;
            }
        }
        return false;
    }

    static boolean isNonCoreAPI(String pkgName) {
        for (String pkg : nonCorePkgs) {
            if (pkgName.startsWith(pkg)) {
                return true;
            }
        }
        return false;
    }

    static class BootModule extends Module {

        BootModule(ModuleConfig config) {
            super(config);
        }

        Collection<Dependency> dependences() {
            Set<Dependency> result = new TreeSet<Dependency>();
            for (Dependency d : dependents()) {
                // filter out optional dependences from jdk.boot module
                if (!d.optional) {
                    result.add(d);
                }
            }
            return result;
        }

        Module toRequiresModule() {
            return jdkBaseModule();
        }

        boolean isBootConnected() {
            return true;
        }

        boolean requirePermits() {
            return true;
        }

        void fixupBase() {
            // fixup jdk.boot optional dependences
            for (Dependency d : dependents()) {
                if (d.optional) {
                    Module m = d.module().toRequiredModule();
                    jdkBaseModule().addRequiresModule(m);
                    Trace.trace("add requires %s to %s%n", m, jdkBaseModule().name());
                    if (m != d.module()) {
                        m.permits().remove(this);
                    }

                }
            }

        }
    }
}
