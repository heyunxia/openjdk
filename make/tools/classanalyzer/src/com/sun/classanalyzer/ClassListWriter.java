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

import java.io.File;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.*;

import com.sun.classanalyzer.AnnotatedDependency.*;
import com.sun.classanalyzer.Module.*;

/**
 * ClassListWriter outputs the following files of a given module:
 * &lt;module&gt;.classlist,
 * &lt;module&gt;.resources,
 * &lt;module&gt;.summary,
 * &lt;module&gt;.dependencies.
 *
 */
public class ClassListWriter {
    private final Module module;
    private final File dir;
    private final Set<Klass> classes;
    private final Set<ResourceFile> resources;

    ClassListWriter(File dir, Module m) {
        this.module = m;
        this.dir = dir;
        // ordered list for printing
        this.classes = new TreeSet<>(m.classes());
        this.resources = new TreeSet<>(m.resources());
    }

    void printClassList() throws IOException {
        if (classes.isEmpty()) {
            return;
        }

        PrintWriter writer = new PrintWriter(Files.resolve(dir, module.name(), "classlist"));
        try {
            for (Klass c : classes) {
                writer.format("%s\n", c.getClassFilePathname());
            }
        } finally {
            writer.close();
        }

    }

    void printResourceList() throws IOException {
        // no file created if the module doesn't have any resource file
        if (resources.isEmpty()) {
            return;
        }

        PrintWriter writer = new PrintWriter(Files.resolve(dir, module.name(), "resources"));
        try {
            for (ResourceFile res : resources) {
                writer.format("%s\n", res.getPathname());
            }

        } finally {
            writer.close();
        }

    }

    void printDependencies() throws IOException {
        printDependencies(false);
    }

    void printDependencies(boolean showDynamic) throws IOException {
        PrintWriter writer = new PrintWriter(Files.resolve(dir, module.name(), "dependencies"));
        try {
            // classes that this klass may depend on due to the AnnotatedDependency
            Map<Reference, Set<AnnotatedDependency>> annotatedDeps =
                AnnotatedDependency.getReferences(module);

            for (Klass klass : classes) {
                Set<Klass> references = klass.getReferencedClasses();
                for (Klass other : references) {
                    String classname = klass.getClassName();
                    boolean optional = OptionalDependency.isOptional(klass, other);
                    if (optional) {
                        classname = "[optional] " + classname;
                    }

                    Module m = module.getRequiresModule(other);
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
                    Module m = module.getRequiresModule(ref.referree);
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
                        if (!showDynamic && dynamic) {
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
}
