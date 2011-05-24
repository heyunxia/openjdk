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
 *
 */
package com.sun.classanalyzer;

import java.io.*;
import java.util.*;

/**
 * ClassListReader constructs modules from the .classlist and
 * .resources files
 *
 * @author Mandy Chung
 *
 * @see ClassListWriter
 */
public class ClassListReader {
    private final ModuleBuilder builder;
    ClassListReader() {
        ModuleBuilder mb = null;
        try {
            mb = new ModuleBuilder(null, ""); // use default module builder
        } catch (IOException e) {
            // should not reach here
        }
        this.builder = mb;
    }
    ClassListReader(ModuleBuilder builder) {
        this.builder = builder;
    }

    Module loadModule(String name, Set<String> classes, Set<String> resources)
            throws IOException {
        Module module = Module.findModule(name);
        if (module == null) {
            module = builder.newModule(name);
        }

        for (String pathname : classes) {
            String cn = pathname.substring(0, pathname.length() - ".class".length())
                            .replace(File.separatorChar, '.');
            Klass k = Klass.getKlass(cn);
            module.addKlass(k);
        }
        for (String pathname : resources) {
            if (!ResourceFile.isResource(pathname)) {
                throw new RuntimeException("\"" + pathname + "\" not a resource file");
            }
            ResourceFile res = new ResourceFile(pathname);
            module.addResource(res);
        }
        return module;
    }

    /**
     * Returns the list of modules constructed from the classlist
     * and resources list in the given directory.
     */
    public Set<Module> loadModulesFrom(File dir) throws IOException {
        String[] summaryFiles = dir.list(new FilenameFilter() {
            public boolean accept(File f, String fname) {
                if (fname.endsWith(".summary")) {
                    return true;
                }
                return false;
            }
        });

        Set<Module> modules = new LinkedHashSet<Module>();
        for (String fn : summaryFiles) {
            String name = fn.substring(0, fn.length() - ".summary".length());
            Module m = loadModuleFrom(dir, name);
            if (m != null) {
                modules.add(m);
            }
        }

        return modules;
    }

    private Module loadModuleFrom(File dir, String name) throws IOException {
        File clist = new File(dir, name + ".classlist");
        File rlist = new File(dir, name + ".resources");
        assert clist.exists() || rlist.exists();

        Module module = loadModule(name, readFile(clist), readFile(rlist));

        // read dependencies
        addDependencies(new File(dir, name + ".dependencies"));

        return module;
    }

    private static Set<String> readFile(File f) throws IOException {
        if (!f.exists()) {
            return Collections.emptySet();
        }

        // parse configuration file
        FileInputStream in = new FileInputStream(f);
        Set<String> fnames = new TreeSet<String>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                fnames.add(line);
            }
            return fnames;
        } finally {
            in.close();
        }
    }

    private static void addDependencies(File f) throws IOException {
        if (!f.exists()) {
            return;
        }

        // parse configuration file
        FileInputStream in = new FileInputStream(f);
        Set<String> fnames = new TreeSet<String>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] ss = line.split("\\s+");
                // skip [optional] if exists
                int i = ss[0].startsWith("[") ? 1 : 0;
                if (ss.length < (i+2) || !ss[i+1].equals("->")) {
                    throw new RuntimeException("Invalid dependency: " + line);
                }

                Klass from = Klass.getKlass(ss[i]);
                Klass to = Klass.getKlass(ss[i+2]);
                ResolutionInfo ri = ResolutionInfo.resolved(from, to);
                from.addDep(to, ri);
                to.addReferrer(from, ri);
            }
        } finally {
            in.close();
        }
    }
}
