/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.classanalyzer.Module.Factory;
import java.io.*;
import java.util.*;

/**
 * ClassListReader constructs modules from the .classlist and
 * .resources files
 *
 * @see ClassListWriter
 */
public class ClassListReader {
    private final Factory factory;
    private final File cldir;
    private final String version;
    public ClassListReader(String dir, String version) {
        this(Module.getFactory(), new File(dir), version);
    }

    public ClassListReader(Factory factory, String dir, String version) {
        this(factory, new File(dir), version);
    }

    public ClassListReader(Factory factory, File dir, String version) {
        this.factory = factory;
        this.cldir = dir;
        this.version = version;
    }

    public Set<Module> run() throws IOException {
       String[] summaryFiles = cldir.list(new FilenameFilter() {
            public boolean accept(File f, String fname) {
                return fname.endsWith(".summary") && !fname.equals("modules.summary");
            }
        });

        for (String fn : summaryFiles) {
            String name = fn.substring(0, fn.length() - ".summary".length());
            Module m = loadModuleFrom(cldir, name);
        }
        return factory.getAllModules();
    }

    private Module loadModule(String name, Set<String> classes, Set<String> resources)
            throws IOException {
        Module module = factory.findModule(name);
        if (module == null) {
            module = factory.newModule(name, version);
            factory.addModule(module);
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

    private Module loadModuleFrom(File dir, String name) throws IOException {
        File clist = new File(dir, name + ".classlist");
        File rlist = new File(dir, name + ".resources");

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
