/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.sun.tools.jdeps;

import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.Dependencies.ClassFileError;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.jar.*;
import java.util.stream.Collectors;

import static com.sun.tools.jdeps.ClassFileReader.*;

/**
 * ClassPath for Java SE and JDK
 */
class PlatformClassPath {
    private static List<Archive> modules;
    static synchronized List<Archive> getModules(Path mpath) throws IOException {
        if (modules == null) {
            initPlatformArchives(mpath);
        }
        return modules;
    }

    /**
     * Returns JAR files in $java.home/lib.  This is for transition until
     * all components are linked into jimage.
     */
    static List<Archive> getJarFiles() throws IOException {
        Path home = Paths.get(System.getProperty("java.home"), "lib");
        return Files.find(home, 1, (Path p, BasicFileAttributes attr)
                              -> p.getFileName().toString().endsWith(".jar"))
                    .map(Archive::getInstance)
                    .collect(Collectors.toList());
    }

    /**
     * Finds the module with the given name. Returns null
     * if such module doesn't exist.
     *
     * @param mn module name
     */
    static Module findModule(String mn) {
        for (Archive a : modules) {
            if (Module.class.isInstance(a)) {
                Module m = (Module)a;
                if (mn.equals(m.name())) {
                    return m;
                }
            }
        }
        return null;
    }

    private static List<Archive> initPlatformArchives(Path mpath) throws IOException {
        Path home = Paths.get(System.getProperty("java.home"));
        if (mpath == null && !home.endsWith("jre")) {
            if (Files.isDirectory(home.resolve("lib").resolve("modules")) ||
                Files.isDirectory(home.resolve("modules"))) {
                // jimage or exploded image
                mpath = home;
            }
        }
        assert mpath != null;
        modules = initModules(mpath);
        if (findModule("java.base") != null) {
            Profile.initProfiles(modules);
        }
        return modules;
    }

    private static List<Archive> initModules(Path mpath) throws IOException {
        Path home = Paths.get(System.getProperty("java.home"));
        ImageHelper helper;
        if (mpath.equals(home)) {
            Path mlib = home.resolve("lib").resolve("modules");
            if (Files.isDirectory(mlib)) {
                // jimage
                helper = new JimageHelper(mlib);
            } else {
                // exploded modules
                mlib = home.resolve("modules");
                helper = new ModulePathHelper(mlib);
            }
        } else {
            helper = new ModulePathHelper(mpath);
        }

        String fn = System.getProperty("jdeps.modules.xml");
        if (fn != null) {
            Path p = Paths.get(fn);
            try (InputStream in = new BufferedInputStream(Files.newInputStream(p))) {
                return new ArrayList<>(ModulesXmlReader.load(helper, in));
            }
        } else {
            try (InputStream in = PlatformClassPath.class
                    .getResourceAsStream("resources/jdeps-modules.xml")) {
                return new ArrayList<>(ModulesXmlReader.load(helper, in));
            }
        }
    }

    interface ImageHelper {
        /**
         * Returns a ClassFileReader that only reads classes for the given modulename.
         */
        ClassFileReader getClassFileReader(String modulename, Set<String> packages) throws IOException;
    }

    static class JimageHelper implements ImageHelper {
        final Path mlib;
        final FileSystemReader reader;
        JimageHelper(Path mlib) throws IOException {
            this.mlib = mlib;
            this.reader = new FileSystemReader(mlib, "*.jimage");
        }

        public ClassFileReader getClassFileReader(String modulename, Set<String> packages) throws IOException {
            return new ModuleClassReader(modulename, packages);
        }

        /**
         * ModuleClassFile reads classes for the specified module from the jimage.
         */
        class ModuleClassReader extends ClassFileReader {
            private final Set<String> packages;
            private final String module;
            private final FileSystemReader.Container container;
            ModuleClassReader(String module, Set<String> packages) throws IOException {
                super(reader.path);
                this.module = module;
                this.packages = packages;
                this.container = packages.isEmpty() ? null : reader.findContainer(module, packages);
            }

            private boolean includes(Path p) {
                String pn = container.toPackage(p);
                return packages.contains(pn);
            }

            public String toString() {
                return module + " " + packages.size() + " " + packages;
            }

            @Override
            public ClassFile getClassFile(String name) throws IOException {
                if (container == null) {
                    return null;
                }
                String pn;
                if (name.indexOf('.') > 0) {
                    int i = name.lastIndexOf('.');
                    pn = i > 0 ? name.substring(0, i) : "";
                } else {
                    int i = name.lastIndexOf('/');
                    pn = i > 0 ? name.substring(0, i).replace('/', '.') : "";
                }
                if (packages.contains(pn)) {
                    Path p = container.findClass(name);
                    if (p != null && includes(p)) {
                        return readClassFile(p);
                    }
                }
                return null;
            }

            @Override
            public Iterable<ClassFile> getClassFiles() throws IOException {
                final Iterator<ClassFile> iter = new ModuleClassIterator(container);
                return new Iterable<ClassFile>() {
                    public Iterator<ClassFile> iterator() {
                        return iter;
                    }
                };
            }

            class ModuleClassIterator implements Iterator<ClassFile> {
                private final FileSystemReader.Container container;
                private final List<Path> entries;
                private Path nextEntry;
                private int index;
                ModuleClassIterator(FileSystemReader.Container container) throws IOException {
                    this.container = container;
                    this.entries = container != null ? container.entries() : Collections.emptyList();
                    this.index = 0;
                }
                public synchronized boolean hasNext() {
                    if (nextEntry == null) {
                        while (index < entries.size()) {
                            Path p = entries.get(index++);
                            if (includes(p)) {
                                nextEntry = p;
                            }
                        }
                    }
                    return nextEntry != null;
                }

                public synchronized ClassFile next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    Path path = nextEntry;
                    nextEntry = null;
                    try {
                        return readClassFile(path);
                    } catch (IOException e) {
                        throw new ClassFileError(e);
                    }
                }

                public void remove() {
                    throw new UnsupportedOperationException("Not supported yet.");
                }
            }
        }
    }

    static class ModulePathHelper implements ImageHelper {
        final Path mpath;
        final ClassFileReader defaultReader;
        ModulePathHelper(Path mp) throws IOException {
            this.mpath = mp;
            this.defaultReader = new NonExistModuleReader(mpath);
        }

        public ClassFileReader getClassFileReader(String modulename, Set<String> packages)
            throws IOException
        {
            Path mdir = mpath.resolve(modulename);
            if (Files.exists(mdir) && Files.isDirectory(mdir)) {
                return ClassFileReader.newInstance(mdir);
            } else {
                // aggregator module or os-specific module in jdeps-modules.xml
                // mdir not exist
                return defaultReader;
            }
        }

        class NonExistModuleReader extends ClassFileReader {
            private final List<ClassFile> classes = Collections.emptyList();
            private NonExistModuleReader(Path mpath) {
                super(mpath);
            }

            public ClassFile getClassFile(String name) throws IOException {
                return null;
            }

            public Iterable<ClassFile> getClassFiles() throws IOException {
                return classes;
            }
        }
    }
}
