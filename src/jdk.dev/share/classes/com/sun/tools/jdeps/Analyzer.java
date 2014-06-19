/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.classfile.Dependency.Location;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dependency Analyzer.
 */
public class Analyzer {
    /**
     * Type of the dependency analysis.  Appropriate level of data
     * will be stored.
     */
    public enum Type {
        SUMMARY,
        PACKAGE,
        CLASS,
        VERBOSE
    }

    private final Type type;
    private final boolean findJDKInternals;
    private final Map<Archive, ArchiveDeps> results = new ConcurrentHashMap<>();
    private final Map<Location, Archive> map = new ConcurrentHashMap<>();
    private final Archive NOT_FOUND
        = new Archive(JdepsTask.getMessage("artifact.not.found"));

    /**
     * Constructs an Analyzer instance.
     *
     * @param type Type of the dependency analysis
     */
    public Analyzer(Type type, boolean findJDKInternals) {
        this.type = type;
        this.findJDKInternals = findJDKInternals;
    }

    /**
     * Performs the dependency analysis on the given archives.
     */
    public boolean run(List<Archive> archives) {
        // build a map from Location to Archive
        buildLocationArchiveMap(archives);

        // traverse and analyze all dependencies
        for (Archive archive : archives) {
            ArchiveDeps deps = new ArchiveDeps(archive, type);
            archive.visitDependences(deps);
            results.put(archive, deps);
        }
        return true;
    }

    /**
     * Verify module access
     */
    public boolean verify(List<Archive> archives) {
        // build a map from Location to Archive
        buildLocationArchiveMap(archives);

        // traverse and analyze all dependencies
        int count = 0;
        for (Archive archive : archives) {
            ModuleAccessChecker checker = new ModuleAccessChecker(archive);
            archive.visitDependences(checker);
            count += checker.dependencies().size();
            checker.dependencies().forEach(d -> System.err.println(d));
            results.put(archive, checker);
        }
        return count == 0;
    }


    private void buildLocationArchiveMap(List<Archive> archives) {
        // build a map from Location to Archive
        for (Archive archive: archives) {
            for (Location l: archive.getClasses()) {
                if (!map.containsKey(l)) {
                    map.put(l, archive);
                } else {
                    // duplicated class warning?
                }
            }
        }
    }

    public boolean hasDependences(Archive source) {
        if (results.containsKey(source)) {
            return results.get(source).dependencies().size() > 0;
        }
        return false;
    }

    public interface Visitor {
        /**
         * Visits a recorded dependency from origin to target which can be
         * a fully-qualified classname, a package name, a module or
         * archive name depending on the Analyzer's type.
         */
        public void visitDependence(String origin, Archive originArchive,
                                    String target, Archive targetArchive);
    }

    public void visitDependences(Archive source, Visitor v, Type level) {
        ArchiveDeps result = results.get(source);
        if (level == type) {
            visit(result.dependencies(), v);
        } else if (level == Type.SUMMARY) {
            for (Archive d : result.requires()) {
                v.visitDependence(source.getName(), source, d.getName(), d);
            }
        } else {
            // requesting different level of analysis
            result = new ArchiveDeps(source, level);
            source.visitDependences(result);
            visit(result.dependencies(), v);
        }
    }

    private void visit(Set<Dependency> deps, Visitor v) {
        deps.stream()
            .sorted(Comparator.comparing(Dependency::origin)
                              .thenComparing(Dependency::target))
            .forEach(d -> v.visitDependence(d.origin(), d.originArchive(),
                    d.target(), d.targetArchive()));
    }

    public void visitDependences(Archive source, Visitor v) {
        visitDependences(source, v, type);
    }

    /**
     * ArchiveDeps contains the dependencies for an Archive that can have one or
     * more classes.
     */
    class ArchiveDeps implements Archive.Visitor {
        protected final Archive archive;
        protected final Set<Archive> requires;
        protected final Set<Dependency> deps;
        protected final Type level;
        ArchiveDeps(Archive archive, Type level) {
            this.archive = archive;
            this.deps = new LinkedHashSet<>();
            this.requires = new HashSet<>();
            this.level = level;
        }

        Set<Dependency> dependencies() {
            return deps;
        }

        Set<Archive> requires() {
            return requires;
        }

        Module findModule(Archive archive) {
            if (Module.class.isInstance(archive)) {
                return (Module) archive;
            } else {
                return null;
            }
        }

        Archive findArchive(Location t) {
            Archive target = archive.getClasses().contains(t) ? archive : map.get(t);
            if (target == null) {
                map.put(t, target = NOT_FOUND);
            }
            return target;
        }

        protected boolean accept(Location o, Location t) {
            Archive targetArchive = findArchive(t);
            if (findJDKInternals) {
                Module from = findModule(archive);
                Module to = findModule(targetArchive);
                if (to == null || Profile.JDK.contains(to)) {
                    // non-JDK module
                    return false;
                }
                return !to.isAccessibleTo(o.getClassName(), from);
            } else {
                // filter intra-dependency unless in verbose mode
                return level == Type.VERBOSE || archive != targetArchive;
            }
        }

        // return classname or package name depedning on the level
        private String getLocationName(Location o) {
            if (level == Type.CLASS || level == Type.VERBOSE) {
                return o.getClassName();
            } else {
                String pkg = o.getPackageName();
                return pkg.isEmpty() ? "<unnamed>" : pkg;
            }
        }

        @Override
        public void visit(Location o, Location t) {
            if (accept(o, t)) {
                addEdge(o, t);
                Archive targetArchive = findArchive(t);
                if (!requires.contains(targetArchive)) {
                    requires.add(targetArchive);
                }
            }
        }

        private Dependency curEdge;
        protected Dependency addEdge(Location o, Location t) {
            String origin = getLocationName(o);
            String target = getLocationName(t);
            Archive targetArchive = findArchive(t);
            if (curEdge != null &&
                    curEdge.origin().equals(origin) &&
                    curEdge.originArchive() == archive &&
                    curEdge.target().equals(target) &&
                    curEdge.targetArchive() == targetArchive) {
                return curEdge;
            }

            Dependency e = new Dependency(origin, archive, target, targetArchive);
            if (deps.contains(e)) {
                for (Dependency e1 : deps) {
                    if (e.equals(e1)) {
                        curEdge = e1;
                    }
                }
            } else {
                deps.add(e);
                curEdge = e;
            }
            return curEdge;
        }
    }

    class ModuleAccessChecker extends ArchiveDeps {
        ModuleAccessChecker(Archive m) {
            super(m, type);
        }

        // returns true if t is accessible by o
        protected boolean canAccess(Location o, Location t) {
            Archive targetArchive = findArchive(t);
            Module origin = findModule(archive);
            Module target = findModule(targetArchive);
            if (targetArchive == NOT_FOUND)
                return false;

            // unnamed module
            // ## should check public type?
            if (target == null)
                return true;

            // module-private
            if (origin == target)
                return true;

            return target.isAccessibleTo(t.getClassName(), origin);
        }

        @Override
        public void visit(Location o, Location t) {
            if (!canAccess(o, t)) {
                addEdge(o, t);
            }
            // include required archives
            Archive targetArchive = findArchive(t);
            if (targetArchive != archive && !requires.contains(targetArchive)) {
                requires.add(targetArchive);
            }
        }
    }

    /*
     * Class-level or package-level dependency
     */
    class Dependency {
        final String origin;
        final Archive originArchive;
        final String target;
        final Archive targetArchive;

        Dependency(String origin, Archive originArchive, String target,  Archive targetArchive) {
            this.origin = origin;
            this.originArchive = originArchive;
            this.target = target;
            this.targetArchive = targetArchive;
        }

        String origin() {
            return origin;
        }

        Archive originArchive() {
            return originArchive;
        }

        String target() {
            return target;
        }

        Archive targetArchive() {
            return targetArchive;
        }

        @Override
        public String toString() {
            return String.format("%s (%s) -> %s (%s)",
                                 origin, originArchive.getName(),
                                 target, targetArchive.getName());
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean equals(Object o) {
            if (o instanceof Dependency) {
                Dependency d = (Dependency) o;
                return this.origin.equals(d.origin) &&
                        this.originArchive == d.originArchive &&
                        this.target.equals(d.target) &&
                        this.targetArchive == d.targetArchive;
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 67*hash + Objects.hashCode(this.origin)
                           + Objects.hashCode(this.originArchive)
                           + Objects.hashCode(this.target)
                           + Objects.hashCode(this.targetArchive);
            return hash;
        }
    }
}
