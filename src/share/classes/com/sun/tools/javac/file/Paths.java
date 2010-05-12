/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.javac.file;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.StringTokenizer;
import java.util.zip.ZipFile;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.main.OptionName;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;

import static javax.tools.StandardLocation.*;
import static com.sun.tools.javac.main.OptionName.*;

/**
 *  This class converts command line arguments, environment variables
 *  and system properties (in File.pathSeparator-separated String form)
 *  into a boot class path, user class path, and source path (in
 *  Collection<PathEntry> form).
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Paths {

    /** The context key for the todo list */
    protected static final Context.Key<Paths> pathsKey =
        new Context.Key<Paths>();

    /** Get the Paths instance for this context.
     *  @param context the context
     *  @return the Paths instance for this context
     */
    public static Paths instance(Context context) {
        Paths instance = context.get(pathsKey);
        if (instance == null)
            instance = new Paths(context);
        return instance;
    }

    /** The log to use for warning output */
    private Log log;

    /** Collection of command-line options */
    private Options options;

    /** Handler for -Xlint options */
    private Lint lint;

    /** Access to (possibly cached) file info */
    private FSInfo fsInfo;

    protected Paths(Context context) {
        context.put(pathsKey, this);
        pathsForLocation = new HashMap<Location,Path>(16);
        setContext(context);
    }

    void setContext(Context context) {
        log = Log.instance(context);
        options = Options.instance(context);
        lint = Lint.instance(context);
        fsInfo = FSInfo.instance(context);
    }

    /** Whether to warn about non-existent path elements */
    private boolean warn;

    private Map<Location, Path> pathsForLocation;

    private boolean inited = false; // TODO? caching bad?

    /**
     * rt.jar as found on the default bootclass path.  If the user specified a
     * bootclasspath, null is used.
     */
    private File bootClassPathRtJar = null;

    Path getPathForLocation(Location location) {
        Path path = pathsForLocation.get(location);
        if (path == null)
            setPathForLocation(location, null);
        return pathsForLocation.get(location);
    }

    void setPathForLocation(Location location, Iterable<? extends File> path) {
        // TODO? if (inited) throw new IllegalStateException
        // TODO: otherwise reset sourceSearchPath, classSearchPath as needed
        Path p;
        if (path == null) {
            if (location == CLASS_PATH)
                p = computeUserClassPath();
            else if (location == PLATFORM_CLASS_PATH)
                p = computeBootClassPath();
            else if (location == ANNOTATION_PROCESSOR_PATH)
                p = computeAnnotationProcessorPath();
            else if (location == SOURCE_PATH)
                p = computeSourcePath();
            else if (location == MODULE_PATH)
                p = computeModulePath();
            else
                // no defaults for other paths
                p = null;
        } else {
            p = new Path();
            for (File f: path)
                p.addFile(f, warn); // TODO: is use of warn appropriate?
        }
        pathsForLocation.put(location, p);
    }

    protected void lazy() {
        if (!inited) {
            warn = lint.isEnabled(Lint.LintCategory.PATH);

            pathsForLocation.put(PLATFORM_CLASS_PATH, computeBootClassPath());
            pathsForLocation.put(CLASS_PATH, computeUserClassPath());
            pathsForLocation.put(SOURCE_PATH, computeSourcePath());

            inited = true;
        }
    }

    public Collection<File> bootClassPath() {
        lazy();
        return getPathForLocation(PLATFORM_CLASS_PATH).toFiles();
    }

    public Collection<File> userClassPath() {
        lazy();
        return getPathForLocation(CLASS_PATH).toFiles();
    }

    public Collection<File> sourcePath() {
        lazy();
        Path p = getPathForLocation(SOURCE_PATH);
        return (p == null || p.size() == 0 ? null : p.toFiles());
    }

    boolean isBootClassPathRtJar(File file) {
        return file.equals(bootClassPathRtJar);
    }

    /**
     * Split a path into its elements. Empty path elements will be ignored.
     * @param path The path to be split
     * @return The elements of the path
     */
    private static Iterable<File> getPathEntries(String path) {
        return getPathEntries(path, null);
    }

    /**
     * Split a path into its elements. If emptyPathDefault is not null, all
     * empty elements in the path, including empty elements at either end of
     * the path, will be replaced with the value of emptyPathDefault.
     * @param path The path to be split
     * @param emptyPathDefault The value to substitute for empty path elements,
     *  or null, to ignore empty path elements
     * @return The elements of the path
     */
    private static Iterable<File> getPathEntries(String path, File emptyPathDefault) {
        ListBuffer<File> entries = new ListBuffer<File>();
        int start = 0;
        while (start <= path.length()) {
            int sep = path.indexOf(File.pathSeparatorChar, start);
            if (sep == -1)
                sep = path.length();
            if (start < sep)
                entries.add(new File(path.substring(start, sep)));
            else if (emptyPathDefault != null)
                entries.add(emptyPathDefault);
            start = sep + 1;
        }
        return entries;
    }

    static class PathLocation implements Location {
        final Path path;
        final String name;
        static int count;

        PathLocation(Path p) {
            path = p;
            //name = "pathLocation#" + (count++) + p;
            name = "pathLocation#" + (count++) + "(path=" + p + ")";
        }

        PathLocation(Path p, String name) {
            path = p;
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public boolean isOutputLocation() {
            return false;
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    class PathEntry {
        PathEntry(File file) {
            this(file, allKinds);
        }

        PathEntry(File file, Set<JavaFileObject.Kind> kinds) {
            file.getClass(); // null check
            kinds.getClass(); // null check
            this.file = file;
            this.canonFile = fsInfo.getCanonicalFile(file);
            this.kinds = kinds;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other)
                return true;
            if (!(other instanceof PathEntry))
                return false;
            PathEntry o = (PathEntry) other;
            return canonFile.equals(o.canonFile) && kinds.equals(o.kinds);
        }

        @Override
        public int hashCode() {
            return canonFile.hashCode() + kinds.hashCode();
        }

        @Override
        public String toString() {
            if (kinds.equals(allKinds))
                return file.getPath();
            else
                return "" + file + kinds;
        }

        final File file;
        final File canonFile;
        final Set<JavaFileObject.Kind> kinds;
    }

    private static final Set<JavaFileObject.Kind> allKinds =
                    EnumSet.allOf(JavaFileObject.Kind.class);

    class Path extends LinkedHashSet<PathEntry> {
        private static final long serialVersionUID = 0;

        private boolean expandJarClassPaths = false;

        public Path expandJarClassPaths(boolean x) {
            expandJarClassPaths = x;
            return this;
        }

        /** What to use when path element is the empty string */
        private File emptyPathDefault = null;

        public Path emptyPathDefault(File x) {
            emptyPathDefault = x;
            return this;
        }

        /** Notional set of acceptable file kinds for this type of path. */
        private Set<JavaFileObject.Kind> acceptedKinds = EnumSet.allOf(JavaFileObject.Kind.class);

        public Path acceptKinds(Set<JavaFileObject.Kind> kinds) {
            acceptedKinds = kinds;
            return this;
        }

        /** Add all the jar files found in one or more directories.
         *  @param dirs one or more directories separated by path separator char
         *  @param whether to generate a warning if a given directory does not exist
         */
        public Path addDirectories(String dirs, boolean warn) {
            if (dirs != null)
                for (File dir : getPathEntries(dirs))
                    addDirectory(dir, warn);
            return this;
        }

        /** Add all the jar files found in one or more directories.
         *  Warnings about non-existent directories are given iff Paths.warn is set.
         *  @param dirs one or more directories separated by path separator char
         */
        public Path addDirectories(String dirs) {
            return addDirectories(dirs, warn);
        }

        /** Add all the jar files found in a directory.
         *  @param dirs one or more directories separated by path separator char
         *  @param whether to generate a warning if a given directory does not exist
         */
        private void addDirectory(File dir, boolean warn) {
            if (!dir.isDirectory()) {
                if (warn)
                    log.warning("dir.path.element.not.found", dir);
                return;
            }

            File[] files = dir.listFiles();
            if (files == null)
                return;

            for (File direntry : files) {
                if (isArchive(direntry))
                    addFile(direntry, warn);
            }
        }

        /** Add directories and archive files.
         *  @param files one or more directories and archive files separated by path separator char
         *  @param whether to generate a warning if a given entry does not exist
         */
        public Path addFiles(String files, boolean warn) {
            if (files != null)
                for (File file : getPathEntries(files, emptyPathDefault))
                    addFile(file, warn);
            return this;
        }

        /** Add directories and archive files.
         *  Warnings about non-existent directories are given iff Paths.warn is set.
         *  @param files one or more directories and archive files separated by path separator char
         */
        public Path addFiles(String files) {
            return addFiles(files, warn);
        }

        /** Add a directory or archive file.
         *  @param directory or archive file to be added
         *  @param whether to generate a warning if the file does not exist
         */
        public void addFile(File file, boolean warn) {
            PathEntry entry = new PathEntry(file, acceptedKinds);
            if (contains(entry)) {
                /* Discard duplicates and avoid infinite recursion */
                return;
            }

            if (! fsInfo.exists(file)) {
                /* No such file or directory exists */
                if (warn)
                    log.warning("path.element.not.found", file);
            } else if (fsInfo.isFile(file)) {
                /* File is an ordinary file. */
                if (!isArchive(file)) {
                    /* Not a recognized extension; open it to see if
                     it looks like a valid zip file. */
                    try {
                        ZipFile z = new ZipFile(file);
                        z.close();
                        if (warn)
                            log.warning("unexpected.archive.file", file);
                    } catch (IOException e) {
                        // FIXME: include e.getLocalizedMessage in warning
                        if (warn)
                            log.warning("invalid.archive.file", file);
                        return;
                    }
                }
            }

            /* Now what we have left is either a directory or a file name
               confirming to archive naming convention */
            super.add(entry);

            if (expandJarClassPaths && fsInfo.exists(file) && fsInfo.isFile(file))
                addJarClassPath(file, warn);
        }

        // Adds referenced classpath elements from a jar's Class-Path
        // Manifest entry.  In some future release, we may want to
        // update this code to recognize URLs rather than simple
        // filenames, but if we do, we should redo all path-related code.
        private void addJarClassPath(File jarFile, boolean warn) {
            try {
                for (File f: fsInfo.getJarClassPath(jarFile)) {
                    addFile(f, warn);
                }
            } catch (IOException e) {
                log.error("error.reading.file", jarFile, JavacFileManager.getMessage(e));
            }
        }

        void addAll(Iterable<PathEntry> entries, Set<JavaFileObject.Kind> kinds) {
            for (PathEntry e: entries) {
                if (kinds.containsAll(e.kinds))
                    add(e);
                else {
                    Set<JavaFileObject.Kind> k = EnumSet.copyOf(kinds);
                    k.retainAll(e.kinds);
                    if (!k.isEmpty())
                        add(new PathEntry(e.file, k));
                }
            }
        }

        Collection<File> toFiles() {
            List<File> files = List.nil();
            for (PathEntry e: this)
                files = files.prepend(e.file);
            return files.reverse();
        }

        // DEBUG
        @Override
        public String toString() {
            return "Path(" + super.toString() + ")";
        }
    }

    private Path computeBootClassPath() {
        bootClassPathRtJar = null;
        String optionValue;
        Path path = new Path();

        path.addFiles(options.get(XBOOTCLASSPATH_PREPEND));

        if ((optionValue = options.get(ENDORSEDDIRS)) != null)
            path.addDirectories(optionValue);
        else
            path.addDirectories(System.getProperty("java.endorsed.dirs"), false);

        if ((optionValue = options.get(BOOTCLASSPATH)) != null) {
            path.addFiles(optionValue);
        } else {
            // Standard system classes for this compiler's release.
            String files = System.getProperty("sun.boot.class.path");
            path.addFiles(files, false);
            File rt_jar = new File("rt.jar");
            for (File file : getPathEntries(files)) {
                if (new File(file.getName()).equals(rt_jar))
                    bootClassPathRtJar = file;
            }
        }

        path.addFiles(options.get(XBOOTCLASSPATH_APPEND));

        // Strictly speaking, standard extensions are not bootstrap
        // classes, but we treat them identically, so we'll pretend
        // that they are.
        if ((optionValue = options.get(EXTDIRS)) != null)
            path.addDirectories(optionValue);
        else
            path.addDirectories(System.getProperty("java.ext.dirs"), false);

        return path;
    }

    private Path computeUserClassPath() {
        String cp = options.get(CLASSPATH);

        // CLASSPATH environment variable when run from `javac'.
        if (cp == null) cp = System.getProperty("env.class.path");

        // If invoked via a java VM (not the javac launcher), use the
        // platform class path
        if (cp == null && System.getProperty("application.home") == null)
            cp = System.getProperty("java.class.path");

        // Default to current working directory.
        if (cp == null) cp = ".";

        return new Path()
            .expandJarClassPaths(true)        // Only search user jars for Class-Paths
            .emptyPathDefault(new File("."))  // Empty path elt ==> current directory
            .addFiles(cp);
    }

    private Path computeSourcePath() {
        String sourcePathArg = options.get(SOURCEPATH);
        if (sourcePathArg == null)
            return null;

        return new Path().addFiles(sourcePathArg);
    }

    private Path computeModulePath() {
        String modulePathArg = options.get(MODULEPATH);
        if (modulePathArg == null)
            return null;

        return new Path().addFiles(modulePathArg);
    }

    private Path computeAnnotationProcessorPath() {
        String processorPathArg = options.get(PROCESSORPATH);
        if (processorPathArg == null)
            return null;

        return new Path().addFiles(processorPathArg);
    }

    /** The actual effective locations searched for sources */
    private Path sourceSearchPath;

    Collection<PathEntry> sourceSearchPath() {
        if (sourceSearchPath == null) {
            lazy();
            Path sourcePath = getPathForLocation(SOURCE_PATH);
            Path userClassPath = getPathForLocation(CLASS_PATH);
            sourceSearchPath = sourcePath != null ? sourcePath : userClassPath;
        }
        return Collections.unmodifiableCollection(sourceSearchPath);
    }

    /** The actual effective locations searched for classes */
    private Path classSearchPath;

    Collection<PathEntry> classSearchPath() {
        if (classSearchPath == null) {
            lazy();
            Path bootClassPath = getPathForLocation(PLATFORM_CLASS_PATH);
            Path userClassPath = getPathForLocation(CLASS_PATH);
            classSearchPath = new Path();
            classSearchPath.addAll(bootClassPath);
            classSearchPath.addAll(userClassPath);
        }
        return Collections.unmodifiableCollection(classSearchPath);
    }

    /** The actual effective locations for non-source, non-class files */
    private Path otherSearchPath;

    Collection<PathEntry> otherSearchPath() {
        if (otherSearchPath == null) {
            lazy();
            Path userClassPath = getPathForLocation(CLASS_PATH);
            Path sourcePath = getPathForLocation(SOURCE_PATH);
            if (sourcePath == null)
                otherSearchPath = userClassPath;
            else {
                otherSearchPath = new Path();
                otherSearchPath.addAll(userClassPath);
                otherSearchPath.addAll(sourcePath);
            }
        }
        return Collections.unmodifiableCollection(otherSearchPath);
    }

    /**
     * Get any classes that should appear before the main platform classes.
     * For compatibility, this is the classes defined by -Xbootclasspath/p:
     * and the contents of the endorsed directories.
     * See computeBootClassPath() for the full definition of the legacy
     * platform class path.
     */
    Path getPlatformPathPrepend() {
        //return getPathForOption(XBOOTCLASSPATH_PREPEND, EnumSet.of(JavaFileObject.Kind.CLASS));
        Path path = new Path().acceptKinds(EnumSet.of(JavaFileObject.Kind.CLASS));

        path.addFiles(options.get(XBOOTCLASSPATH_PREPEND));

        String optionValue;
        if ((optionValue = options.get(ENDORSEDDIRS)) != null)
            path.addDirectories(optionValue);
        else
            path.addDirectories(System.getProperty("java.endorsed.dirs"), false);

        return (path.size() == 0 ? null : path);
    }

    /**
     * Get the main platform classes.
     * For now, this is just the classes defined by -bootclasspath or -Xbootclasspath.
     * See computeBootClassPath() for the full definition of the legacy
     * platform class path.
     */
    Path getPlatformPathBase() {
        return getPathForOption(BOOTCLASSPATH, EnumSet.of(JavaFileObject.Kind.CLASS));
    }

    /**
     * Get any classes that should appear after the main platform classes.
     * For compatibility, this is the classes defined by -Xbootclasspath/a:
     * and the contents of the extension directories.
     * See computeBootClassPath() for the full definition of the legacy
     * platform class path.
     */
    Path getPlatformPathAppend() {
        //return getPathForOption(XBOOTCLASSPATH_APPEND, EnumSet.of(JavaFileObject.Kind.CLASS));
        Path path = new Path().acceptKinds(EnumSet.of(JavaFileObject.Kind.CLASS));

        path.addFiles(options.get(XBOOTCLASSPATH_APPEND));

        // Strictly speaking, standard extensions are not bootstrap
        // classes, but we treat them identically, so we'll pretend
        // that they are.
        String optionValue;
        if ((optionValue = options.get(EXTDIRS)) != null)
            path.addDirectories(optionValue);
        else
            path.addDirectories(System.getProperty("java.ext.dirs"), false);

        if (path.size() > 0)
            System.out.println("Paths.getPlatformPathAppend " + path);

        return (path.size() == 0 ? null : path);
    }

    private Path getPathForOption(OptionName o, Set<JavaFileObject.Kind> kinds) {
        String v = options.get(o);
        if (v == null)
            return null;

        return new Path().acceptKinds(kinds).addFiles(v);
    }

    /** Is this the name of an archive file? */
    private boolean isArchive(File file) {
        String n = file.getName().toLowerCase();
        return fsInfo.isFile(file)
            && (n.endsWith(".jar") || n.endsWith(".zip"));
    }

    /**
     * Utility method for converting a search path string to an array
     * of directory and JAR file URLs.
     *
     * Note that this method is called by apt and the DocletInvoker.
     *
     * @param path the search path string
     * @return the resulting array of directory and JAR file URLs
     */
    public static URL[] pathToURLs(String path) {
        StringTokenizer st = new StringTokenizer(path, File.pathSeparator);
        URL[] urls = new URL[st.countTokens()];
        int count = 0;
        while (st.hasMoreTokens()) {
            URL url = fileToURL(new File(st.nextToken()));
            if (url != null) {
                urls[count++] = url;
            }
        }
        if (urls.length != count) {
            URL[] tmp = new URL[count];
            System.arraycopy(urls, 0, tmp, 0, count);
            urls = tmp;
        }
        return urls;
    }

    /**
     * Returns the directory or JAR file URL corresponding to the specified
     * local file name.
     *
     * @param file the File object
     * @return the resulting directory or JAR file URL, or null if unknown
     */
    private static URL fileToURL(File file) {
        String name;
        try {
            name = file.getCanonicalPath();
        } catch (IOException e) {
            name = file.getAbsolutePath();
        }
        name = name.replace(File.separatorChar, '/');
        if (!name.startsWith("/")) {
            name = "/" + name;
        }
        // If the file does not exist, then assume that it's a directory
        if (!file.isFile()) {
            name = name + "/";
        }
        try {
            return new URL("file", "", name);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(file.toString());
        }
    }
}
