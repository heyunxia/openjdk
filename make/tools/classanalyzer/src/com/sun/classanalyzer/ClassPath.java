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

import com.sun.classanalyzer.ClassPath.*;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Legacy class path.  Each entry can be a directory containing
 * classes and resources, or a jar file.  It supports classpath
 * wildcard "*" that lists all jar files in the given directory
 * and also recursive wildcard "**" that lists all jar files
 * recursively in the given directory and its subdirectories.
 *
 */
public class ClassPath {

    protected final List<ClassPathEntry> entries = new LinkedList<ClassPathEntry>();
    private final Set<Klass> classes = new LinkedHashSet<Klass>();
    private final Set<ResourceFile> resources = new LinkedHashSet<ResourceFile>();
    private long parseTime;

    private ClassPath() {
    }

    public ClassPath(String... paths) throws IOException {
        for (String p : paths) {
            String cp = p;
            int index = p.indexOf("*");
            String wildcard = "";
            if (index >= 0) {
                cp = p.substring(0, index);
                wildcard = p.substring(index, p.length());
            }

            File f = new File(cp);
            if (!f.exists()) {
                throw new RuntimeException("\"" + f + "\" doesn't exist");
            }
            if (wildcard.isEmpty()) {
                if (f.isDirectory()) {
                    entries.add(new DirClassPathEntry(f));
                } else if (cp.endsWith(".jar")) {
                    entries.add(new JarFileClassPathEntry(f));
                } else {
                    entries.add(new ClassPathEntry(f));
                }
            } else {
                if (wildcard.equals("*")) {
                    // add jar files in the specified directory
                    String[] ls = Files.list(f);
                    for (String s : ls) {
                        File sf = new File(f, s);
                        if (s.endsWith(".jar")) {
                            entries.add(new JarFileClassPathEntry(f));
                        }
                    }
                } else if (wildcard.equals("**")) {
                    // add all jar files in all directories under f
                    addJarFileEntries(f);
                }
            }
        }
    }

    public List<ClassPathEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * Returns the modules containing the classes and resources being
     * processed.
     */
    public Set<Module> getModules() {
        Set<Module> modules = new LinkedHashSet<Module>();
        for (Klass k : classes) {
            modules.add(k.getModule().group());
        }
        for (ResourceFile r : resources) {
            modules.add(r.getModule().group());
        }
        return modules;
    }

    public void parse() throws IOException {
        parse(null, true, false);
    }

    public void parse(boolean deps, boolean apiOnly) throws IOException {
        parse(null, deps, apiOnly);
    }

    public void parse(Filter filter, boolean deps, boolean apiOnly) throws IOException {
        long start = System.nanoTime();
        ClassResourceVisitor crv = new ClassResourceVisitor(classes, resources, deps, apiOnly);
        ClassPathVisitor cpvisitor = new ClassPathVisitor(crv, filter);
        visit(cpvisitor, filter, null);
        parseTime = System.nanoTime() - start;
    }

    public void printStats() {
        System.out.format("%d classes %d resource files processed in %d ms%n",
                classes.size(), resources.size(), ((long) parseTime/1000000));
    }

    protected void addJarFileEntries(File f) throws IOException {
        List<File> ls;
        if (f.isDirectory()) {
            ls = Files.walkTree(f, new Files.Filter<File>() {
                @Override
                public boolean accept(File f) throws IOException {
                    return f.isDirectory() || f.getName().endsWith(".jar");
                }
            });
        } else {
            ls = Collections.singletonList(f);
        }
        for (File jf : ls) {
            // add all jars except alt-rt.jar or tzdb.jar
            if (!jf.getName().endsWith("alt-rt.jar") && !jf.getName().endsWith("tzdb.jar")) {
                entries.add(new JarFileClassPathEntry(jf));
            }
        }
    }
    // FIXME - used by open() method
    static ClassPath instance = null;

    static ClassPath newInstance(String cpath) throws IOException {
        String[] paths = cpath.split(File.pathSeparator);
        instance = new ClassPath(paths);
        return instance;
    }

    static ClassPath newJDKClassPath(String jdkhome) throws IOException {
        instance = new JDKClassPath(jdkhome);
        return instance;
    }

    static class JDKClassPath extends ClassPath {
        JDKClassPath(String jdkhome) throws IOException {
            super();
            List<File> files = new ArrayList<File>();
            File jre = new File(jdkhome, "jre");
            File lib = new File(jdkhome, "lib");

            if (jre.exists() && jre.isDirectory()) {
                addJarFiles(new File(jre, "lib"));
                addJarFiles(lib);
            } else if (lib.exists() && lib.isDirectory()) {
                // either a JRE or a jdk build image
                File classes = new File(jdkhome, "classes");
                if (classes.exists() && classes.isDirectory()) {
                    // jdk build outputdir
                    this.entries.add(new DirClassPathEntry(classes));
                }
                addJarFiles(lib);
            } else {
                throw new RuntimeException("\"" + jdkhome + "\" not a JDK home");
            }
        }

        // Filter the jigsaw module library, if any
        final void addJarFiles(File dir) throws IOException {
            String[] ls = dir.list();
            for (String fn : ls) {
                File f = new File(dir, fn);
                if (f.isDirectory() && !fn.equals("modules")) {
                    addJarFileEntries(f);
                } else if (f.isFile() && fn.endsWith(".jar")) {
                    addJarFileEntries(f);
                }
            }
        }
    }

    /**
     * Visits all entries in the class path.
     */
    public <R, P> List<R> visit(final ClassPathEntry.Visitor<R, P> visitor,
            final Filter filter, P p)
            throws IOException {
        List<R> result = new ArrayList<R>();
        for (ClassPathEntry cp : entries) {
            if (filter != null && !filter.accept(cp.file)) {
                continue;
            }
            R r = cp.accept(visitor, p);
            result.add(r);
        }
        return result;
    }

    public static interface FileVisitor {
        public void visitClass(File f, String cn) throws IOException;
        public void visitClass(JarFile jf, JarEntry e) throws IOException;
        public void visitResource(File f, String rn) throws IOException;
        public void visitResource(JarFile jf, JarEntry e) throws IOException;
    }

    public static interface Filter {
        // any file, jar file, directory
        public boolean accept(File f) throws IOException;
        public boolean accept(JarFile jf, JarEntry e) throws IOException;
    }

    public static class ClassPathEntry {

        private final File file;
        private final String name;

        ClassPathEntry(File f) throws IOException {
            this.file = f;
            this.name = file.getCanonicalPath();
        }

        File getFile() {
            return file;
        }

        String getName() {
            return name;
        }

        <R, P> R accept(Visitor<R, P> visitor, P p) throws IOException {
            return visitor.visitFile(file, this, p);
        }

        public interface Visitor<R, P> {
            public R visitFile(File f, ClassPathEntry cp, P p) throws IOException;
            public R visitDir(File dir, ClassPathEntry cp, P p) throws IOException;
            public R visitJarFile(JarFile jf, ClassPathEntry cp, P p) throws IOException;
        }
    }

    static class JarFileClassPathEntry extends ClassPathEntry {
        JarFile jarfile;
        JarFileClassPathEntry(File f) throws IOException {
            super(f);
            this.jarfile = new JarFile(f);
        }

        @Override
        <R, P> R accept(Visitor<R, P> visitor, P p) throws IOException {
            return visitor.visitJarFile(jarfile, this, p);
        }
    }

    class DirClassPathEntry extends ClassPathEntry {

        DirClassPathEntry(File f) throws IOException {
            super(f);
        }

        @Override
        <R, P> R accept(final Visitor<R, P> visitor, P p) throws IOException {
            return visitor.visitDir(getFile(), this, p);
        }
    }

    static class ClassResourceVisitor implements FileVisitor {

        private final Set<Klass> classes;
        private final Set<ResourceFile> resources;
        private final boolean parseDeps;
        private final boolean apiOnly;

        ClassResourceVisitor(Set<Klass> classes,
                Set<ResourceFile> resources,
                boolean parseDeps,
                boolean apiOnly) {
            this.classes = classes;
            this.resources = resources;
            this.apiOnly = apiOnly;
            this.parseDeps = parseDeps;
        }

        @Override
        public void visitClass(File f, String cn) throws IOException {
            ClassFileParser cfparser = ClassFileParser.newParser(f, parseDeps);
            classes.add(cfparser.this_klass);
            if (parseDeps) {
                cfparser.parseDependency(apiOnly);
            }
        }

        @Override
        public void visitClass(JarFile jf, JarEntry e) throws IOException {
            ClassFileParser cfparser = ClassFileParser.newParser(jf.getInputStream(e), e.getSize(), parseDeps);
            classes.add(cfparser.this_klass);
            if (parseDeps) {
                cfparser.parseDependency(apiOnly);
            }
        }

        @Override
        public void visitResource(File f, String rn) throws IOException {
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(f));
            try {
                ResourceFile res = ResourceFile.addResource(rn, in, f.length());
                resources.add(res);
            } finally {
                in.close();
            }
        }

        @Override
        public void visitResource(JarFile jf, JarEntry e) throws IOException {
            ResourceFile res = ResourceFile.addResource(e.getName(), jf.getInputStream(e), e.getSize());
            resources.add(res);
        }
    }

    static class ClassPathVisitor implements ClassPathEntry.Visitor<Void, Void> {

        private final FileVisitor visitor;
        private final Filter filter;

        ClassPathVisitor(FileVisitor fv, Filter filter) {
            this.visitor = fv;
            this.filter = filter;
        }

        @Override
        public Void visitFile(File f, ClassPathEntry cp, Void v) throws IOException {
            if (filter != null && !filter.accept(f)) {
                return null;
            }

            String name = f.getName();
            String pathname = f.getCanonicalPath();
            String root = cp.getName();
            if (!name.equals(root) && !pathname.equals(root)) {
                if (!pathname.startsWith(root)) {
                    throw new RuntimeException("Incorrect pathname " + pathname);
                }

                name = pathname.substring(root.length() + 1, pathname.length());
            }

            if (name.endsWith(".class")) {
                visitor.visitClass(f, name);
            } else if (!f.isDirectory() && ResourceFile.isResource(f.getCanonicalPath())) {
                visitor.visitResource(f, name);
            }
            return null;
        }

        @Override
        public Void visitDir(final File dir, final ClassPathEntry cp, Void v) throws IOException {
            List<File> ls = Files.walkTree(dir, null);
            for (File f : ls) {
                visitFile(f, cp, null);
            }
            return null;
        }

        @Override
        public Void visitJarFile(JarFile jf, ClassPathEntry cp, Void v) throws IOException {
            if (filter != null && !filter.accept(cp.getFile())) {
                return null;
            }

            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                String name = e.getName();
                if (filter != null && !filter.accept(jf, e)) {
                    continue;
                }
                if (name.endsWith(".class")) {
                    visitor.visitClass(jf, e);
                } else if (!e.isDirectory() && ResourceFile.isResource(name)) {
                    visitor.visitResource(jf, e);
                }
            }
            return null;
        }
    };

    public static InputStream open(String pathname) throws IOException {
        ClassPathEntry.Visitor<InputStream, String> fv = new ClassPathEntry.Visitor<InputStream, String>() {

            @Override
            public InputStream visitFile(File f, ClassPathEntry cp, String pathname) throws IOException {
                if (cp.getName().endsWith(File.separator + pathname)) {
                    return new FileInputStream(f);
                } else {
                    return null;
                }
            }

            @Override
            public InputStream visitDir(File dir, ClassPathEntry cp, String pathname) throws IOException {
                File f = new File(cp.getFile(), pathname);
                if (f.exists()) {
                    return new FileInputStream(f);
                } else {
                    return null;
                }
            }

            @Override
            public InputStream visitJarFile(JarFile jf, ClassPathEntry cp, String pathname) throws IOException {
                String p = pathname.replace(File.separatorChar, '/');
                JarEntry e = jf.getJarEntry(p);
                if (e != null) {
                    return jf.getInputStream(e);
                } else {
                    return null;
                }
            }
        };

        for (ClassPathEntry cp : instance.entries) {
            InputStream in = cp.accept(fv, pathname);
            if (in != null) {
                return in;
            }
        }
        return null;
    }

    public ClassFileParser parserForClass(String classname) throws IOException {
        String pathname = classname.replace('.', File.separatorChar) + ".class";
        ClassPathEntry.Visitor<ClassFileParser, String> fv = new ClassPathEntry.Visitor<ClassFileParser, String>() {

            @Override
            public ClassFileParser visitFile(File f, ClassPathEntry cp, String pathname) throws IOException {
                if (cp.getName().endsWith(File.separator + pathname)) {
                    return ClassFileParser.newParser(f, true);
                } else {
                    return null;
                }
            }

            @Override
            public ClassFileParser visitDir(File dir, ClassPathEntry cp, String pathname) throws IOException {
                File f = new File(cp.getFile(), pathname);
                if (f.exists()) {
                    return ClassFileParser.newParser(f, true);
                } else {
                    return null;
                }
            }

            @Override
            public ClassFileParser visitJarFile(JarFile jf, ClassPathEntry cp, String pathname) throws IOException {
                String p = pathname.replace(File.separatorChar, '/');
                JarEntry e = jf.getJarEntry(p);
                if (e != null) {
                    return ClassFileParser.newParser(jf.getInputStream(e), e.getSize(), true);
                } else {
                    return null;
                }
            }
        };

        for (ClassPathEntry cp : entries) {
            ClassFileParser cfparser = cp.accept(fv, pathname);
            if (cfparser != null) {
                return cfparser;
            }
        }
        return null;
    }
}
