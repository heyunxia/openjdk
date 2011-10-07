/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.io.IOException;
import java.util.*;

import java.lang.module.ModuleId;
import org.openjdk.jigsaw.*;

/**
 * Builds modules from a given jigsaw module library.
 * To run ClassAnalyzer on JDK7 (non-jigsaw JDK),
 * the JigsawModuleBuilder can also take the jdk's classlists
 * from the jigsaw build.
 */
public class JigsawModuleBuilder extends ClassListReader {
    private static JigsawFactory factory = new JigsawFactory();
    private static String DEFAULT_VERSION = "8-ea";

    private final File path;
    public JigsawModuleBuilder(File path) {
        super(factory, path, DEFAULT_VERSION);
        this.path = path;
    }

    public Set<Module> run() throws IOException {
        File f = new File(path, "%jigsaw-library");
        if (f.exists()) {
            // create modules from the jigsaw system module library
            loadModulesFromLibrary();
        } else {
            // create modules from the input class lists
            super.run();
        }
        return factory.getAllModules();
    }

    private void loadModulesFromLibrary() throws IOException {
        Library lib = SimpleLibrary.open(path);
        List<ModuleId> mids = lib.listLocalModuleIds();
        for (ModuleId mid : mids) {
            java.lang.module.ModuleInfo minfo = lib.readLocalModuleInfo(mid);
            Module m = factory.newModule(mid.name(), mid.version().toString());
            factory.addModule(m);

            // ## probably list only exported classes??
            for (String cn : lib.listLocalClasses(mid, true)) {
                Klass k = Klass.findKlass(cn);
                if (k != null) {
                    throw new RuntimeException(mid.name() + ":" + cn + " already exists");
                }
                k = Klass.getKlass(cn);
                m.addKlass(k);
            }
        }
    }


    private static class JigsawFactory extends Factory {

        @Override
        public Module newModule(String name, String version) {
            return new JigsawModule(new ModuleConfig(name, version));
        }

        @Override
        public Module newModule(ModuleConfig config) {
            return new JigsawModule(config);
        }
    };

    private static class JigsawModule extends Module {
        Module altgroup;

        JigsawModule(ModuleConfig config) {
            super(config);
        }

        /*
         * Only present the "jdk." modules for application
         * to use.
         */
        @Override
        public synchronized Module group() {
            if (altgroup == null) {
                altgroup = this;
                String n = name();
                if (n.startsWith("sun.")) {
                    String mn = "jdk." + n.substring(4);
                    Module pm = factory.findModule(mn);
                    if (pm != null) {
                        altgroup = pm;
                    }
                } else if (n.equals("jdk.boot")) {
                    altgroup = factory.findModule("jdk.base");
                    if (altgroup == null) {
                        throw new RuntimeException("jdk.base not found");
                    }
                }
            }
            return altgroup;
        }

        @Override
        boolean allowEmpty() {
            // jdk.* module that reexports sun.* module is empty
            return true;
        }
    }
}
