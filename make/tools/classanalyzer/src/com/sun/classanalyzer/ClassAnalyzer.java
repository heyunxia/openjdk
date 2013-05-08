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

import com.sun.classanalyzer.AnnotatedDependency.*;
import com.sun.classanalyzer.Module.*;
import com.sun.classanalyzer.ModuleInfo.*;
import java.io.IOException;
import java.io.File;
import java.io.PrintWriter;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Analyze the class dependencies of all classes in a given classpath
 * and assign classes and resource files into modules as defined
 * in the input configuration files.
 *
 * The ClassAnalyzer tool will generate the following reports
 *   modules.list
 *   modules.summary
 *   modules.dot
 * and for each module named <m>,
 *   <m>.classlist
 *   <m>.resources
 *   <m>.summary
 *   <m>.dependencies
 *
 * If -moduleinfo option is specified, <m>/module-info.java
 * will be created under the given directory.
 *
 * The -update option can be specified to perform an incremental analysis
 * rather than parsing all class files.
 *
 */
public class ClassAnalyzer {

    public static void main(String[] args) throws Exception {
        String jdkhome = null;
        String cparg = null;
        List<String> configs = new ArrayList<String>();
        List<String> depconfigs = new ArrayList<String>();
        String version = null;
        String classlistDir = ".";
        String minfoDir = null;
        String jigsawLibrary = null;
        ClassPath cpath = null;
        boolean mergeModules = true;
        boolean apiOnly = false;
        boolean showDynamic = false;
        boolean update = false;

        // process arguments
        int i = 0;
        while (i < args.length) {
            String arg = args[i++];
            if (arg.equals("-jdkhome")) {
                if (cparg != null) {
                    error("Both -jdkhome and -classpath are set");
                }
                jdkhome = getOption(args, i++);
                cpath = ClassPath.newJDKClassPath(jdkhome);
            } else if (arg.equals("-base")) {
                String base = getOption(args, i++);
                Module.setBaseModule(base);
            } else if (arg.equals("-classpath")) {
                if (jdkhome != null) {
                    error("Both -jdkhome and -classpath are set");
                }
                cparg = getOption(args, i++);
                cpath = ClassPath.newInstance(cparg);
            } else if (arg.equals("-config")) {
                configs.add(getOption(args, i++));
            } else if (arg.equals("-depconfig")) {
                depconfigs.add(getOption(args, i++));
            } else if (arg.equals("-properties")) {
                Module.setModuleProperties(getOption(args, i++));
            } else if (arg.equals("-output")) {
                classlistDir = getOption(args, i++);
            } else if (arg.equals("-update")) {
                update = true;
            } else if (arg.equals("-moduleinfo")) {
                minfoDir = getOption(args, i++);
            } else if (arg.equals("-version")) {
                version = getOption(args, i++);
            } else if (arg.equals("-jigsawLibrary")) {
                jigsawLibrary = getOption(args, i++);
            } else if (arg.equals("-api")) {
                // analyze the fine-grained module dependencies
                apiOnly = true;
            } else if (arg.equals("-nomerge")) {
                // analyze the fine-grained module dependencies
                mergeModules = false;
            } else if (arg.equals("-showdynamic")) {
                showDynamic = true;
            } else {
                error("Invalid option: " + arg);
            }
        }

        if (jdkhome == null && cparg == null) {
            error("-jdkhome and -classpath not set");
        }

        if (configs.isEmpty()) {
            error("-config not set");
        }

        if (version == null) {
            error("-version not set");
        }

        ModuleBuilder builder = new ModuleBuilder(configs, depconfigs, mergeModules, version);

        File systemLib;
        if (jigsawLibrary != null) {
            systemLib = new File(jigsawLibrary);
        } else {
            systemLib = new File(new File(System.getProperty("java.home"),
                                          "lib"),
                                 "modules");
        }
        if (systemLib.exists() && jdkhome == null) {
            // if running on a modular JDK, first load the jigsaw modules
            // The jigsaw modules are not added to the ModuleBuilder
            // as they are only used in the module dependency graph.
            JigsawModuleBuilder jb = new JigsawModuleBuilder(systemLib);
            builder.addModules(jb.run());
        }

        // incremental build
        boolean incremental = update && doIncremental(classlistDir);
        if (incremental) {
            // Load modules from the existing class list and resource list
            // Add them in the builder to be included in the analysis
            ClassListReader reader = new ClassListReader(builder.getFactory(), classlistDir, version);
            reader.run();
        }

        ClassAnalyzer analyzer = new ClassAnalyzer(cpath, builder, classlistDir);
        // parse class and resource files
        analyzer.run(incremental, apiOnly);
        // print classlist and dependencies reports
        analyzer.generateReports(showDynamic);
        // print module-info.java
        if (minfoDir != null) {
            analyzer.printModuleInfos(minfoDir);
        }
    }

    static boolean doIncremental(String classlistDir) {
        File moduleList = new File(classlistDir, "modules.list");
        return (moduleList.exists() && moduleList.lastModified() > 0);
    }

    private final ClassPath cpath;
    private final ModuleBuilder builder;
    private final String classlistDir;
    private final File moduleList;
    private final Set<Module> updatedModules; // updated modules

    ClassAnalyzer(ClassPath cpath, ModuleBuilder builder, String clistDir) {
        this.cpath = cpath;
        this.builder = builder;
        this.classlistDir = clistDir;
        this.moduleList = new File(new File(clistDir), "modules.list");
        this.updatedModules = new TreeSet<Module>();
    }

    void run(boolean update, boolean apiOnly) throws IOException {
        // parse class and resource files
        processClassPath(update, apiOnly);

        // build modules & packages
        builder.run();

        if (update) {
            updatedModules.addAll(cpath.getModules());
        } else {
            updatedModules.addAll(builder.getModules());
        }

        Module unknown = builder.getFactory().unknownModule();
        if (unknown != null && builder.getModules().contains(unknown))
            System.out.println("WARNING: classes are not assigned to any module."
                    + " Please see the unknown.classlist report.");
    }

    public void generateReports(boolean showDynamic)
            throws IOException {
        File outputDir = new File(classlistDir);
        if (!outputDir.exists())
            Files.mkdirs(outputDir);

        if (updatedModules.size() > 0) {
            printModulesList();
        }

        // only print classlist of the recompiled modules
        for (Module m : updatedModules) {
            // write classlist and resourcelist of a module
            ClassListWriter writer = new ClassListWriter(outputDir, m);
            writer.printClassList();
            writer.printResourceList();
            writer.printDependencies();

            // write the summary and modules.list files
            printModuleSummary(outputDir, m);
            printModuleList(outputDir, m);
        }

        printSummary(outputDir, showDynamic);
    }

    void processClassPath(boolean update, boolean apiOnly) throws IOException {
        // TODO: always parseDeps?
        boolean parseDeps = update == false;
        ClassPath.Filter filter = null;

        long timestamp = update ? moduleList.lastModified() : -1L;
        // for incremental build, only update the modules with
        // recompiled classes or resources files.
        // Filter out files starting with _the. which are touch files from the
        // build system.
        final long ts = timestamp;
        filter = new ClassPath.Filter() {

            @Override
            public boolean accept(File f) {
                if (f.getName().startsWith("_the.")) {
                    return false;
                }
                return (f.isDirectory()
                        ? true
                        : f.lastModified() > ts);
            }

            @Override
            public boolean accept(JarFile jf, JarEntry e) throws IOException {
                if (e.getName().startsWith("_the.")) {
                    return false;
                }
                long lastModified = e.getTime();
                return lastModified <= 0 || lastModified > ts;
            }
        };

        // parse class and resource files
        cpath.parse(filter, parseDeps, apiOnly);
        cpath.printStats();
    }

    public void printModuleInfos(String minfoDir) throws IOException {
        for (Module m : updatedModules) {
            ModuleInfo minfo = m.getModuleInfo();
            File mdir = new File(minfoDir, m.name());
            PrintWriter writer = new PrintWriter(Files.resolve(mdir, "module-info", "java"));
            try {
                writer.println(minfo.toString());
            } finally {
                writer.close();
            }
        }
    }

    public void printSummary(File dir, boolean showDynamic) throws IOException {
        printModulesSummary(dir, showDynamic);
        printModulesDot(dir, showDynamic);
        printPackagesSummary(dir);
    }

    private static String getOption(String[] args, int index) {
        if (index < args.length) {
            return args[index];
        } else {
            usage();
        }
        return null;
    }

    private void printModuleSummary(File dir, Module m) throws IOException {
        PrintWriter summary =
                new PrintWriter(Files.resolve(dir, m.name(), "summary"));
        try {
            long total = 0;
            int count = 0;
            long resBytes = 0;
            int resCount = 0;
            summary.format("%10s\t%10s\t%s%n", "Bytes", "Classes", "Package name");
            Set<PackageInfo> pkgs = new TreeSet<>(m.packages());
            for (PackageInfo info : pkgs) {
                summary.format("%10d\t%10d\t%s%n",
                        info.classBytes, info.classCount, info.pkgName);
                total += info.classBytes;
                count += info.classCount;
            }
            for (ResourceFile rf : m.resources()) {
                resCount++;
                resBytes += rf.getFileSize();
            }

            summary.format("%nTotal: %d bytes (uncompressed) %d classes " +
                    "%d bytes %d resources %n",
                    total, count, resBytes, resCount);
        } finally {
            summary.close();
        }
    }

    private void printModuleList(File dir, Module m) throws IOException {
        String value = Module.getModuleProperty(m.name() + ".modules.list");
        if (value == null || value.equals("false")) {
            return;
        }

        PrintWriter mlist = new PrintWriter(Files.resolve(dir, m.name(), "modules.list"));
        try {
            Set<Module> deps = m.getModuleInfo().dependences(
                    new Dependence.Filter() {
                        @Override
                        public boolean accept(Dependence d) {
                            return !d.isOptional();
                        }
                    });
            for (Module dm : deps) {
                mlist.format("%s\n", dm.name());
            }
            if (!value.equals("true")) {
                for (String mn : value.split("\\s+")) {
                    mlist.format("%s\n", mn);
                }
            }
        } finally {
            mlist.close();
        }
    }

    private void printModuleGroup(Module group, PrintWriter writer) {
        ModuleVisitor<Set<Module>> visitor = new ModuleVisitor<Set<Module>>() {

            public void preVisit(Module p, Set<Module> leafnodes) {
            }

            public void visited(Module p, Module m, Set<Module> leafnodes) {
                if (m.members().isEmpty()) {
                    leafnodes.add(m);
                }
            }

            public void postVisit(Module p, Set<Module> leafnodes) {
            }
        };

        Set<Module> visited = new TreeSet<Module>();
        Set<Module> members = new TreeSet<Module>();
        group.visitMembers(visited, visitor, members);

        // prints leaf members that are the modules defined in
        // the modules.config files
        writer.format("%s ", group);
        for (Module m : members) {
            writer.format("%s ", m);
        }
        writer.println();
    }

    public void printModulesList() throws IOException {
        // print module group / members relationship in
        // the dependences order so that its dependences are listed first
        PrintWriter writer = new PrintWriter(moduleList);
        try {
            for (Module m : builder.getModules()) {
                printModuleGroup(m, writer);
            }
        } finally {
            writer.close();
        }
    }

    public void printModulesSummary(File dir, boolean showDynamic) throws IOException {
        // print summary of dependencies
        PrintWriter writer = new PrintWriter(new File(dir, "modules.summary"));
        try {
            for (Module m : builder.getModules()) {
                ModuleInfo mi = m.getModuleInfo();
                for (Dependence dep : mi.requires()) {
                    if (!dep.getModule().isBase()) {
                        String prefix = "";
                        if (dep.isOptional()) {
                            prefix = "[optional] ";
                        }

                        Module other = dep.getModule();
                        writer.format("%s%s -> %s%n", prefix, m, other);
                    }
                }
            }
        } finally {
            writer.close();
        }
    }

    private void printModulesDot(File dir, boolean showDynamic) throws IOException {
        PrintWriter writer = new PrintWriter(new File(dir, "modules.dot"));
        try {
            writer.println("digraph modules {");
            for (Module m : builder.getModules()) {
                ModuleInfo mi = m.getModuleInfo();
                for (Dependence dep : mi.requires()) {
                    if (!dep.getModule().isBase()) {
                        String style = "";
                        String color = "";
                        String property = "";
                        if (dep.isOptional()) {
                            style = "style=dotted";
                        }
                        if (style.length() > 0 || color.length() > 0) {
                            String comma = "";
                            if (style.length() > 0 && color.length() > 0) {
                                comma = ", ";
                            }
                            property = String.format(" [%s%s%s]", style, comma, color);
                        }
                        Module other = dep.getModule();
                        writer.format("    \"%s\" -> \"%s\"%s;%n", m, other, property);
                    }
                }
            }
            writer.println("}");
        } finally {
            writer.close();
        }
    }

    private void printPackagesSummary(File dir) throws IOException {
        // print package / module relationship
        PrintWriter writer = new PrintWriter(new File(dir, "modules.pkginfo"));
        try {
            // packages that are splitted among multiple modules
            writer.println("Packages splitted across modules:-\n");
            writer.format("%-60s  %s\n", "Package", "Module");

            Map<String, Set<Module>> splitPackages = builder.getSplitPackages();
            for (Map.Entry<String, Set<Module>> e : splitPackages.entrySet()) {
                String pkgname = e.getKey();
                writer.format("%-60s", pkgname);
                for (Module m : e.getValue()) {
                    writer.format("  %s", m);
                }
                writer.println();
            }

            writer.println("\nPackage-private dependencies:-");
            for (String pkgname : splitPackages.keySet()) {
                for (Klass k : Klass.getAllClasses()) {
                    if (k.getPackageName().equals(pkgname)) {
                        Module m = k.getModule();
                        // check if this klass references a package-private
                        // class that is in a different module
                        for (Klass other : k.getReferencedClasses()) {
                            if (other.getModule() != m
                                    && !other.isPublic()
                                    && other.getPackageName().equals(pkgname)) {
                                String from = k.getClassName() + " (" + m + ")";
                                writer.format("%-60s -> %s (%s)\n", from, other, other.getModule());
                            }
                        }
                    }
                }
            }
        } finally {
            writer.close();
        }

    }

    private static void error(String msg) {
        System.err.println("ERROR: " + msg);
        System.out.println(usage());
        System.exit(-1);
    }

    private static String usage() {
        StringBuilder sb = new StringBuilder();
        sb.append("Usage: ClassAnalyzer <options>\n");
        sb.append("Options: \n");
        sb.append("\t-jdkhome     <JDK home> where all jars will be parsed\n");
        sb.append("\t-classpath   <classpath> where classes and jars will be parsed\n");
        sb.append("\t             Either -jdkhome or -classpath option can be used.\n");
        sb.append("\t-config      <module config file>\n");
        sb.append("\t             This option can be repeated for multiple module config files\n");
        sb.append("\t-output      <output dir>\n");
        sb.append("\t-moduleinfo  <output dir of module-info.java>\n");
        sb.append("\t-jigsawLibrary <module-library-path>\n");
        sb.append("\t-platform    platform modules\n");
        sb.append("\t-properties  module's properties\n");
        sb.append("\t-version     <module's version>\n");
        sb.append("\t-update      update modules with newer files\n");
        sb.append("\t-nomerge     specify not to merge modules\n");
        return sb.toString();
    }
}
