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

import com.sun.classanalyzer.ResourceFile.ServiceProviderConfigFile;
import com.sun.classanalyzer.ClassPath.*;
import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Modularize classes and resources from legacy classpath to
 * a module path containing a list of modules, one directory per module.
 *
 */
public class Modularizer {

    private final File modulepath;
    private final Set<Module> modules;
    private final ClassPath cpath;
    public Modularizer(ClassPath cpath, File modulepath, Set<Module> modules) {
        this.cpath = cpath;
        this.modulepath = modulepath;
        this.modules = modules;
    }

    /**
     * Modularizes the legacy class path files into
     * multiple modules.
     * @param update true if only modules with newer classes
     *               resources are updated.
     */
    void run(boolean update) throws IOException {
        for (Module m : modules) {
            File mdir = new File(modulepath, m.name());
            ModuleContent mc = new ModuleContent(m, mdir);
            mc.copy(update);
            if (mc.isUpdated()) {
                mc.printStats();
            }
        }
    }

    class ModuleContent {
        int classes = 0;
        int resources = 0;
        long classBytes = 0;
        long resourceBytes = 0;
        final Module module;
        final File classDir;  // destination for classes and resources
        final File mdir;

        /**
         * Module content.  The
         *
         * @param m module
         * @param dir directory of the module content
         */
        ModuleContent(Module m, File dir) throws IOException {
            this.module = m;
            this.mdir = dir;
            this.classDir = new File(dir, "classes");
        }

        /**
         * Tests if any file in this module content is updated.
         */
        boolean isUpdated() {
            return (classes + resources) > 0;
        }

        /**
         * Copies the module content (classes and resources file)
         * to the destination.
         *
         * @param update true if only modified files are copied;
         * otherwise, all classes and resource files for this module
         * are copied.
         */
        void copy(boolean update) throws IOException {
            if (!classDir.exists()) {
                Files.mkdirs(classDir);
                // override all files
                update = false;
            }

            final boolean copyAll = update == false;
            Module.Visitor<Void, File> visitor = new Module.Visitor<Void, File>() {
                @Override
                public Void visitClass(Klass k, File dir) {
                    String pathname = k.getClassFilePathname();
                    Filter filter = copyAll ? null : new Filter(classDir, pathname);
                    long bytes;
                    try {
                        bytes = writeClass(k, filter);
                        if (bytes > 0) {
                            classes++;
                            classBytes += bytes;
                        }
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                    return null;
                }

                @Override
                public Void visitResource(ResourceFile r, File dir) {
                    String pathname = r.getPathname();
                    Filter filter = copyAll ? null : new Filter(classDir, pathname);
                    try {
                        long bytes = writeResource(r, filter);
                        if (bytes > 0) {
                            resources++;
                            resourceBytes += bytes;
                        }
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                    return null;
                }
            };

            module.visit(visitor, mdir);
        }

        void printStats() {
            System.out.format("%s: %d classes (%d bytes) %d resource files (%d bytes) copied%n",
                    module.name(), classes, classBytes, resources, resourceBytes);
        }

        private ClassPathEntry lastVisitedClassPath = null;
        /**
         * Write the classfile of the given class if not filtered
         *
         * @param k  a Klass
         * @param filter a Filter
         * @return the number of bytes copied
         */
        long writeClass(Klass k, Filter filter) throws IOException {
            String pathname = k.getClassFilePathname();
            Copier visitor = new Copier(classDir, filter);
            if (lastVisitedClassPath != null) {
                ClassPathEntry cp = lastVisitedClassPath.accept(visitor, pathname);
                if (cp != null) {
                    assert cp == lastVisitedClassPath;
                    return visitor.bytes;
                }
            }

            // locate the source of the given class from the classpath
            for (ClassPathEntry cp : cpath.entries()) {
                ClassPathEntry src = cp.accept(visitor, pathname);
                if (src != null) {
                    // cache the ClassPathEntry from which this class is copied
                    // Most of the files in a module likely come from the
                    // same jar or directory.
                    lastVisitedClassPath = src;
                    return visitor.bytes;
                }
            }
            return 0;
        }

        /**
         * Write the resource file if not filtered
         *
         * @param res a ResourceFile
         * @param filter a Filter
         * @return the number of bytes copied
         */
        long writeResource(ResourceFile res, Filter filter) throws IOException {
            if (res.isService())
                return writeService(res, filter);

            String pathname = res.getPathname();
            Copier visitor = new Copier(classDir, filter);
            if (lastVisitedClassPath != null) {
                ClassPathEntry cp = lastVisitedClassPath.accept(visitor, pathname);
                if (cp != null) {
                    assert cp == lastVisitedClassPath;
                    return visitor.bytes;
                }
            }

            // locate the source of the given resource file from the classpath
            for (ClassPathEntry cp : cpath.entries()) {
                ClassPathEntry src = cp.accept(visitor, pathname);
                if (src != null) {
                    // cache the ClassPathEntry from which this class is copied
                    // Most of the files in a module likely come from the
                    // same jar or directory.
                    lastVisitedClassPath = src;
                    return visitor.bytes;
                }
            }
            return 0;
        }

        /**
         * Write the service descriptor file if not filtered
         *
         * @param res a ResourceFile
         * @param filter a Filter
         * @return the number of bytes copied
         */
        long writeService(ResourceFile res, Filter filter) throws IOException {
            String pathname = res.getPathname();
            Copier visitor = new Copier(classDir, filter);
            boolean foundOne = false;
            int bytes = 0;

            // scan all class path entries for services
            for (ClassPathEntry cp : cpath.entries()) {
                ClassPathEntry src = cp.accept(visitor, pathname);
                if (src != null) {
                    bytes += visitor.bytes;
                    if (foundOne == false) {
                        foundOne = true;
                        visitor = new Copier(classDir, null, true); // append subsequent
                    }
                }
            }
            return bytes;
        }

        /**
         * A ClassPathEntry visitor to copy a file to the given destination
         * if not filtered.
         */
        class Copier implements ClassPathEntry.Visitor<ClassPathEntry, String> {
            final Filter filter;
            final File dest;
            final boolean append;
            long bytes = 0;

            Copier(File dest, Filter filter) {
                this(dest, filter, false);
            }

            Copier(File dest, Filter filter, boolean append) {
                this.filter = filter;
                this.dest = dest;
                this.append = append;
            }

            private boolean isService(String name) {
                return name.startsWith("META-INF/services") ? true : false;
            }

            @Override
            public ClassPathEntry visitFile(File src, ClassPathEntry cp, String pathname) throws IOException {
                String name = pathname.replace(File.separatorChar, '/');
                if (cp.getName().endsWith(File.separator + pathname)
                        && matches(src, name)) {
                    if (filter == null || filter.accept(src)) {
                        File dst = new File(dest, pathname);
                        bytes += copy(src, dst);
                    }
                    return cp;
                } else {
                    return null;
                }
            }

            @Override
            public ClassPathEntry visitDir(File dir, ClassPathEntry cp, String pathname) throws IOException {
                File src = new File(cp.getFile(), pathname);
                File dst = new File(dest, pathname);
                String name = pathname.replace(File.separatorChar, '/');

                if (src.exists() && matches(src, name)) {
                    if (filter == null || filter.accept(src)) {
                        bytes += copy(src, dst);
                    }
                    return cp;
                } else {
                    return null;
                }
            }

            @Override
            public ClassPathEntry visitJarFile(JarFile jf, ClassPathEntry cp, String pathname) throws IOException {
                String name = pathname.replace(File.separatorChar, '/');
                JarEntry e = jf.getJarEntry(name);
                if (e != null && matches(jf, e, name)) {
                    if (filter == null || filter.accept(jf, e)) {
                        bytes += copy(jf, e);
                    }
                    return cp;
                } else {
                    return null;
                }
            }

            boolean matches(File src, String name) throws IOException {
                if (!isService(name)) {
                    return true;
                }

                try (FileInputStream fis = new FileInputStream(src);
                     BufferedInputStream in = new BufferedInputStream(fis)) {
                    return matches(in, name);
                }
            }

            boolean matches(JarFile jf, JarEntry e, String name) throws IOException {
                if (!isService(name)) {
                    return true;
                }
                return matches(jf.getInputStream(e), name);
            }

            boolean matches(InputStream in, String name) throws IOException {
                ServiceProviderConfigFile sp = new ServiceProviderConfigFile(name, in);
                for (String p : sp.providers) {
                    Klass k = Klass.findKlass(p);
                    if (k == null) {
                        Trace.trace("Service %s: provider class %s not found%n", sp.service, p);
                        continue;
                    }
                    if (module.contains(k)) {
                        return true;
                    }
                }
                // return true if no provider; otherwise false
                return sp.providers.isEmpty();
            }

            long copy(JarFile jf, JarEntry e) throws IOException {
                File dst = new File(dest, e.getName().replace('/', File.separatorChar));
                if (!dst.exists()) {
                    Files.createFile(dst);
                }

                byte[] buf = new byte[8192];
                long bytes = 0;
                try (InputStream in = jf.getInputStream(e);
                     FileOutputStream out = new FileOutputStream(dst, append)) {
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        bytes += n;
                    }
                }

                long lastModified = e.getTime();
                if (lastModified > 0) {
                    dst.setLastModified(lastModified);
                }
                return bytes;
            }

            long copy(File src, File dst)
                    throws IOException {
                assert src.exists();

                if (!dst.exists()) {
                    Files.createFile(dst);
                }

                byte[] buf = new byte[8192];
                long bytes = 0;
                try (InputStream fin = new FileInputStream(src);
                     BufferedInputStream in = new BufferedInputStream(fin);
                     FileOutputStream out = new FileOutputStream(dst, append)) {
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        bytes += n;
                    }
                }
                dst.setLastModified(src.lastModified());
                if (src.canExecute()) {
                    dst.setExecutable(true, false);
                }
                return bytes;
            }
        }
    }

    /**
     * A filter that accepts files that don't exist in the given
     * location or modified since it's copied.
     */
    class Filter implements ClassPath.Filter {
        private final long timestamp;
        Filter(File dir, String pathname) {
            File destfile = new File(dir, pathname);
            this.timestamp = destfile.exists() ? destfile.lastModified() : -1L;
        }

        @Override
        public boolean accept(File f) throws IOException {
            if (f.isDirectory()) {
                return true;
            }

            long ts = f.lastModified();
            return (timestamp < 0 || ts < 0 || timestamp < ts);
        }

        @Override
        public boolean accept(JarFile jf, JarEntry e) throws IOException {
            long ts = e.getTime();
            return timestamp < 0 || ts < 0 || timestamp < ts;
        }
    }

    public static void main(String[] args) throws Exception {
        String jdkhome = null;
        String classpath = null;
        String classlistDir = null;
        String modulepath = null;
        boolean update = false;

        // process arguments
        int i = 0;
        while (i < args.length) {
            String arg = args[i++];
            if (arg.equals("-jdkhome")) {
                jdkhome = getOption(args, i++);
            } else if (arg.equals("-classpath")) {
                classpath = getOption(args, i++);
            } else if (arg.equals("-modulepath")) {
                modulepath = getOption(args, i++);
            } else if (arg.equals("-classlistdir")) {
                classlistDir = getOption(args, i++);
            } else if (arg.equals("-update")) {
                // copy new files only
                update = true;
            } else {
                error("Invalid option: " + arg);
            }
        }

        if (jdkhome == null && classpath == null) {
            error("-jdkhome and -classpath not set");
        }

        if (jdkhome != null && classpath != null) {
            error("Both -jdkhome and -classpath are set");
        }

        if (classlistDir == null || modulepath == null) {
            error("-modulepath or -classlist not set");
        }

        ClassPath cpath = null;
        if (jdkhome != null) {
            cpath = ClassPath.newJDKClassPath(jdkhome);
        } else if (classpath != null) {
            cpath = ClassPath.newInstance(classpath);
        }

        ClassListReader reader = new ClassListReader(classlistDir, "default");
        Set<Module> modules = reader.run();
        Modularizer modularizer = new Modularizer(cpath, new File(modulepath), modules);
        modularizer.run(update);
    }

    private static String getOption(String[] args, int index) {
        if (index < args.length) {
            return args[index];
        } else {
            error(args[index-1] + ": Missing argument");
        }
        return null;
    }

    private static void error(String msg) {
        System.err.println("ERROR: " + msg);
        System.out.println(usage());
        System.exit(-1);
    }

    private static String usage() {
        StringBuilder sb = new StringBuilder();
        sb.append("Usage: Modularizer <options>\n");
        sb.append("Options: \n");
        sb.append("\t-jdkhome      <JDK home> where all jars will be parsed\n");
        sb.append("\t-classpath    <classpath> where classes and jars will be parsed\n");
        sb.append("\t              Either -jdkhome or -classpath option can be used.\n");
        sb.append("\t-classlistdir <classlist dir>\n");
        sb.append("\t-update       update modules with newer files\n");
        sb.append("\t-modulepath   <module-path> for writing modules\n");
        return sb.toString();
    }
}
