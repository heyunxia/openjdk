/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.*;

/**
 * A simple tool to print out the static dependencies for a given set of JAR,
 * class files, or combinations of. The tools supports an -ignore option to
 * ignore references to classes listed in the file (including .classlists
 * created by the ClassAnalyzer tool).
 */
public class ShowDeps {

    private final ClassPath cpath;
    private final Set<String> classes = new TreeSet<>();
    private final Set<Module.View> requires = new TreeSet<>();
    private final Map<String, Module> packages = new TreeMap<>();

    public ShowDeps(ClassPath cpath) {
        this.cpath = cpath;
    }

    public void run(Set<Module> modules, boolean showClassDeps) throws IOException {
        cpath.parse();

        // find the classes that don't exist
        Set<Klass> unresolved = new TreeSet<>();
        for (Klass k : Klass.getAllClasses()) {
            if (k.getFileSize() == 0) {
                unresolved.add(k);
            }
        }

        // print references to classes that don't exist
        for (Klass k : Klass.getAllClasses()) {
            for (Klass other : k.getReferencedClasses()) {
                if (unresolved.contains(other)) {
                    String cn = other.getClassName();
                    String pn = other.getPackageName();
                    Module m = other.getModule();
                    Module sm = packages.get(pn);
                    if (m == null) {
                        m = Module.getFactory().unknownModule();
                    }
                    if (!packages.containsKey(pn)) {
                        packages.put(pn, m);
                    } else if (sm != m) {
                        String mn = m == null ? "?" : m.name();
                        String pn1 = pn + " (" + mn + ")";
                        packages.put(pn1, m);
                    }
                    Module.View mv = null;
                    if (m.defaultView().exports().contains(pn)) {
                        mv = m.defaultView();
                    } else {
                        for (Module.View v : m.views()) {
                            if (v.exports().contains(pn)) {
                                mv = v;
                                break;
                            }
                        }
                    }
                    if (mv != null) {
                        requires.add(mv);
                    } else if (m != Module.getFactory().unknownModule()) {
                        System.out.format("Non-exported: %s -> %s (%s)%n",
                                k, other, m);
                    }
                    if (!ignore.contains(cn) && showClassDeps) {
                        System.out.format("%s -> %s (%s)%n", k, other, m);
                    }
                }
            }
        }
    }

    public void printPackageDeps(PrintStream out) {
        out.format("%-40s  %s%n", "package", "from module");
        out.format("%-40s  %s%n", "-------", "-----------");
        for (Map.Entry<String,Module> e : packages.entrySet()) {
            String pn = e.getKey();
            Module m = e.getValue();
            out.format("%-40s  %s%n", pn, m);
        }
    }

    public void printModuleInfo(PrintStream out, String mid) {
        out.format("module %s {%n", mid);
        for (Module.View mv : requires) {
            out.format("    requires %s;%n", mv.name);
        }
        out.println("}");
    }

    static void usage() {
        System.out.println("java ShowDeps [-L <module-lib>] [-id <moduleId>] [-v] file...");
        System.out.println("   where <file> is a class or JAR file, or a directory");
        System.out.println("By default, it shows the packages dependencies and class");
        System.out.println("dependencies if -v option is specified.  If the id option");
        System.out.println("is specified, it will print the module declaration instead.");
        System.out.println("");
        System.out.println("Example usages:");
        System.out.println("  java ShowDeps Foo.jar");
        System.out.println("  java ShowDeps -id \"foo@1.0\" Foo.jar");
        System.out.println("  java ShowDeps -L modulelibrary Foo.jar");
        System.out.println("  java ShowDeps -ignore base.classlist Foo.jar");
        System.out.println("  java ShowDeps -ignore base.classlist -ignore " +
                "jaxp-parsers.classlist <dir>");
        System.exit(-1);
    }
    private static Set<String> ignore = new HashSet<>();

    public static void main(String[] args) throws IOException {
        int argi = 0;
        File lib = null;
        String mid = null;
        boolean verbose = false;
        while (argi < args.length) {
            String arg = args[argi];
            if (arg.equals("-ignore")) {
                // process -ignore options
                try (Scanner s = new Scanner(new File(args[++argi]))) {
                    while (s.hasNextLine()) {
                        String line = s.nextLine();
                        if (!line.endsWith(".class")) {
                            continue;
                        }
                        int len = line.length();
                        // convert to class names
                        String clazz = line.replace('\\', '.').replace('/', '.').substring(0, len - 6);
                        ignore.add(clazz);
                    }
                }
            } else if (arg.equals("-id")) {
                mid = args[++argi];
            } else if (arg.equals("-L")) {
                lib = new File(args[++argi]);
            } else if (arg.equals("-v")) {
                verbose = true;
            } else {
                break;
            }
            argi++;
        }

        if (argi >= args.length) {
            usage();
        }

        // parse all classes
        ClassPath cpath = new ClassPath(Arrays.copyOfRange(args, argi, args.length));
        ShowDeps instance = new ShowDeps(cpath);
        instance.run(getPlatformModules(lib), verbose);

        if (mid == null) {
            if (!verbose)
                instance.printPackageDeps(System.out);
        } else {
            instance.printModuleInfo(System.out, mid);
        }
    }

    static Set<Module> getPlatformModules(File lib) throws IOException {
        String javahome = System.getProperty("java.home");
        if (lib == null) {
            lib = new File(new File(javahome,"lib"), "modules");
        }

        if (lib.exists()) {
            JigsawModuleBuilder mb = new JigsawModuleBuilder(lib);
            return mb.run();
        } else {
            return Collections.emptySet();
        }
    }
}
