/*
 * Copyright 2005-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

import javax.lang.model.SourceVersion;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.ModuleFileManager;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import static javax.tools.StandardLocation.*;

import com.sun.tools.javac.file.Paths.Path;
import com.sun.tools.javac.file.Paths.PathEntry;
import com.sun.tools.javac.file.Paths.PathLocation;
import com.sun.tools.javac.file.RelativePath.RelativeFile;
import com.sun.tools.javac.file.RelativePath.RelativeDirectory;
import com.sun.tools.javac.main.OptionName;
import com.sun.tools.javac.util.BaseFileManager;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

import javax.tools.ExtendedLocation;
import static com.sun.tools.javac.main.OptionName.*;

/**
 * This class provides access to the source, class and other files
 * used by the compiler and related tools.
 *
 * <p><b>This is NOT part of any API supported by Sun Microsystems.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class JavacFileManager 
        extends BaseFileManager 
        implements StandardJavaFileManager, ModuleFileManager {

    boolean useZipFileIndex;

    public static char[] toArray(CharBuffer buffer) {
        if (buffer.hasArray())
            return ((CharBuffer)buffer.compact().flip()).array();
        else
            return buffer.toString().toCharArray();
    }

    /** Encapsulates knowledge of paths
     */
    private Paths paths;

    private FSInfo fsInfo;

    private final File uninited = new File("U N I N I T E D");

    private final Set<JavaFileObject.Kind> sourceOrClass =
        EnumSet.of(JavaFileObject.Kind.SOURCE, JavaFileObject.Kind.CLASS);

    /** The standard output directory, primarily used for classes.
     *  Initialized by the "-d" option.
     *  If classOutDir = null, files are written into same directory as the sources
     *  they were generated from.
     */
    private File classOutDir = uninited;

    /** The output directory, used when generating sources while processing annotations.
     *  Initialized by the "-s" option.
     */
    private File sourceOutDir = uninited;

    protected boolean mmappedIO;
    protected boolean ignoreSymbolFile;

    /**
     * Register a Context.Factory to create a JavacFileManager.
     */
    public static void preRegister(final Context context) {
        context.put(JavaFileManager.class, new Context.Factory<JavaFileManager>() {
            public JavaFileManager make() {
                return new JavacFileManager(context, true, null);
            }
        });
    }

    /**
     * Create a JavacFileManager using a given context, optionally registering
     * it as the JavaFileManager for that context.
     */
    public JavacFileManager(Context context, boolean register, Charset charset) {
        super(charset);
        if (register)
            context.put(JavaFileManager.class, this);
        setContext(context);
    }

    /**
     * Set the context for JavacFileManager.
     */
    @Override
    public void setContext(Context context) {
        super.setContext(context);
        if (paths == null) {
            paths = Paths.instance(context);
        } else {
            // Reuse the Paths object as it stores the locations that
            // have been set with setLocation, etc.
            paths.setContext(context);
        }

        fsInfo = FSInfo.instance(context);

        useZipFileIndex = System.getProperty("useJavaUtilZip") == null;// TODO: options.get("useJavaUtilZip") == null;

        mmappedIO = options.get("mmappedIO") != null;
        ignoreSymbolFile = options.get("ignore.symbol.file") != null;
    }

    public JavaFileObject getFileForInput(String name) {
        return getRegularFile(new File(name));
    }

    public JavaFileObject getRegularFile(File file) {
        return new RegularFileObject(this, file);
    }

    public JavaFileObject getFileForOutput(String classname,
                                           JavaFileObject.Kind kind,
                                           JavaFileObject sibling)
        throws IOException
    {
        return getJavaFileForOutput(CLASS_OUTPUT, classname, kind, sibling);
    }

    public Iterable<? extends JavaFileObject> getJavaFileObjectsFromStrings(Iterable<String> names) {
        ListBuffer<File> files = new ListBuffer<File>();
        for (String name : names)
            files.append(new File(nullCheck(name)));
        return getJavaFileObjectsFromFiles(files.toList());
    }

    public Iterable<? extends JavaFileObject> getJavaFileObjects(String... names) {
        return getJavaFileObjectsFromStrings(Arrays.asList(nullCheck(names)));
    }

    private static boolean isValidName(String name) {
        // Arguably, isValidName should reject keywords (such as in SourceVersion.isName() ),
        // but the set of keywords depends on the source level, and we don't want
        // impls of JavaFileManager to have to be dependent on the source level.
        // Therefore we simply check that the argument is a sequence of identifiers
        // separated by ".".
        for (String s : name.split("\\.", -1)) {
            if (!SourceVersion.isIdentifier(s))
                return false;
        }
        return true;
    }

    private static void validateClassName(String className) {
        if (!isValidName(className))
            throw new IllegalArgumentException("Invalid class name: " + className);
    }

    private static void validatePackageName(String packageName) {
        if (packageName.length() > 0 && !isValidName(packageName))
            throw new IllegalArgumentException("Invalid packageName name: " + packageName);
    }

    public static void testName(String name,
                                boolean isValidPackageName,
                                boolean isValidClassName)
    {
        try {
            validatePackageName(name);
            if (!isValidPackageName)
                throw new AssertionError("Invalid package name accepted: " + name);
            printAscii("Valid package name: \"%s\"", name);
        } catch (IllegalArgumentException e) {
            if (isValidPackageName)
                throw new AssertionError("Valid package name rejected: " + name);
            printAscii("Invalid package name: \"%s\"", name);
        }
        try {
            validateClassName(name);
            if (!isValidClassName)
                throw new AssertionError("Invalid class name accepted: " + name);
            printAscii("Valid class name: \"%s\"", name);
        } catch (IllegalArgumentException e) {
            if (isValidClassName)
                throw new AssertionError("Valid class name rejected: " + name);
            printAscii("Invalid class name: \"%s\"", name);
        }
    }

    private static void printAscii(String format, Object... args) {
        String message;
        try {
            final String ascii = "US-ASCII";
            message = new String(String.format(null, format, args).getBytes(ascii), ascii);
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new AssertionError(ex);
        }
        System.out.println(message);
    }

    /**
     * Insert all files in subdirectory `subdirectory' of `directory' which end
     * in one of the extensions in `extensions' into packageSym.
     */
    private void listDirectory(File directory,
                               RelativeDirectory subdirectory,
                               Set<JavaFileObject.Kind> fileKinds,
                               boolean recurse,
                               ListBuffer<JavaFileObject> l) {
        Archive archive = archives.get(directory);

        boolean isFile = fsInfo.isFile(directory);

        if (archive != null || isFile) {
            if (archive == null) {
                try {
                    archive = openArchive(directory);
                } catch (IOException ex) {
                    log.error("error.reading.file",
                       directory, getMessage(ex));
                    return;
                }
            }

            List<String> files = archive.getFiles(subdirectory);
            if (files != null) {
                for (String file; !files.isEmpty(); files = files.tail) {
                    file = files.head;
                    if (isValidFile(file, fileKinds)) {
                        l.append(archive.getFileObject(subdirectory, file));
                    }
                }
            }
            if (recurse) {
                for (RelativeDirectory s: archive.getSubdirectories()) {
                    if (subdirectory.contains(s)) {
                        // Because the archive map is a flat list of directories,
                        // the enclosing loop will pick up all child subdirectories.
                        // Therefore, there is no need to recurse deeper.
                        listDirectory(directory, s, fileKinds, false, l);
                    }
                }
            }
        } else {
            File d = subdirectory.getFile(directory);
            if (!caseMapCheck(d, subdirectory))
                return;

            File[] files = d.listFiles();
            if (files == null)
                return;

            for (File f: files) {
                String fname = f.getName();
                if (f.isDirectory()) {
                    if (recurse && SourceVersion.isIdentifier(fname)) {
                        listDirectory(directory,
                                      new RelativeDirectory(subdirectory, fname),
                                      fileKinds,
                                      recurse,
                                      l);
                    }
                } else {
                    if (isValidFile(fname, fileKinds)) {
                        JavaFileObject fe =
                            new RegularFileObject(this, fname, new File(d, fname));
                        l.append(fe);
                    }
                }
            }
        }
    }

    private boolean isValidFile(String s, Set<JavaFileObject.Kind> fileKinds) {
        JavaFileObject.Kind kind = getKind(s);
        return fileKinds.contains(kind);
    }

    private static final boolean fileSystemIsCaseSensitive =
        File.separatorChar == '/';

    /** Hack to make Windows case sensitive. Test whether given path
     *  ends in a string of characters with the same case as given name.
     *  Ignore file separators in both path and name.
     */
    private boolean caseMapCheck(File f, RelativePath name) {
        if (fileSystemIsCaseSensitive) return true;
        // Note that getCanonicalPath() returns the case-sensitive
        // spelled file name.
        String path;
        try {
            path = f.getCanonicalPath();
        } catch (IOException ex) {
            return false;
        }
        char[] pcs = path.toCharArray();
        char[] ncs = name.path.toCharArray();
        int i = pcs.length - 1;
        int j = ncs.length - 1;
        while (i >= 0 && j >= 0) {
            while (i >= 0 && pcs[i] == File.separatorChar) i--;
            while (j >= 0 && ncs[j] == '/') j--;
            if (i >= 0 && j >= 0) {
                if (pcs[i] != ncs[j]) return false;
                i--;
                j--;
            }
        }
        return j < 0;
    }

    private ModuleMode moduleMode;

    public ModuleMode getModuleMode() {
        if (moduleMode == null) {
            if (options.get(MODULEPATH) != null && options.get(CLASSPATH) == null)
                moduleMode = ModuleMode.MULTIPLE;
            else
                moduleMode = ModuleMode.SINGLE;
        }
        return moduleMode;
    }

    public Location getModuleLocation(Location locn, JavaFileObject fo, String pkgName)
            throws InvalidLocationException, InvalidFileObjectException {
        if (getModuleMode() == ModuleMode.SINGLE)
            return locn;
        else {
            fo.getClass(); // null check
            if (!(fo instanceof BaseFileObject))
                throw new IllegalArgumentException();

            if (!hasLocation(locn))
                throw new InvalidLocationException();
            String tag = ((BaseFileObject) fo).inferModuleTag(pkgName);
            if (tag == null)
                throw new InvalidFileObjectException();
            return getModuleLocation(locn, tag);
        }
    }

    private Map<Location,Iterable<Location>> moduleLocations =
            new LinkedHashMap<Location,Iterable<Location>>();

    public Iterable<Location> getModuleLocations(Location locn) {
        //System.err.println("JavacFileManager.getModuleLocations " + getModuleMode() + " " + locn);

        Iterable<Location> result = moduleLocations.get(locn);
        if (result == null) {
            Iterable<PathEntry> pathEntries = getEntriesForLocation(locn);
            if (pathEntries == null)
                result = List.<Location>nil();
            else {
                Set<Location> locns = new LinkedHashSet<Location>();
                for (PathEntry pe: pathEntries) {
                    if (pe.file.isDirectory()) {
                        for (File f: pe.file.listFiles()) {
                            String tag = null;
                            if (f.isDirectory())
                                tag = f.getName();
//                            else if (isArchive(f)) {
//                                String name = f.getName();
//                                tag = name.substring(0, name.lastIndexOf("."));
//                            }
                            if (tag != null)
                                locns.add(getModuleLocation(locn, tag));
                        }
                    } else {
                        // ignore archive files for now, these would be "module archive
                        // files", containing multiple modules in a new but obvious way
                    }

                }
                result = locns;
            }
        }

//        System.err.println("JavacFileManager.getModuleLocations.result " + result);
        return result;
    }


    private Map<String, Location> locationCache = new HashMap<String,Location>();

    public Location join(Iterable<? extends Location> locations)
            throws IllegalArgumentException {
        StringBuilder sb = new StringBuilder("{");
        String sep = "";
        for (Location l: locations) {
            if (l instanceof StandardLocation || l instanceof PathLocation || l instanceof ExtendedLocation) {
                sb.append(sep);
                sb.append(l.getName());
                sep = ",";
            } else
                throw new IllegalArgumentException(l.toString());
        }
        sb.append("}");
        String name = sb.toString();

        Location result = locationCache.get(name);
        if (result == null) {
            ListBuffer<Location> mergeList = new ListBuffer<Location>();
            Path currPath = null;
            for (Location l: locations) {
                if (l instanceof StandardLocation) {
                    if (hasLocation(l)) {
                        Set<JavaFileObject.Kind> kinds = allKinds;
                        switch ((StandardLocation) l) {
                            case CLASS_PATH:
                                kinds = (hasLocation(SOURCE_PATH) ? noSourceKind : allKinds);
                                break;
                            case SOURCE_OUTPUT:
                            case SOURCE_PATH:
                                kinds = noClassKind;
                                break;
                            case ANNOTATION_PROCESSOR_PATH:
                            case PLATFORM_CLASS_PATH:
                            case CLASS_OUTPUT:
                                kinds = noSourceKind;
                                break;
                        }
                        if (currPath == null)
                            currPath = paths.new Path();
                        currPath.addAll(getEntriesForLocation(l), kinds);
                    }
                } else if (l instanceof PathLocation) {
                    if (currPath == null)
                        currPath = paths.new Path();
                    currPath.addAll(((PathLocation) l).path);
                } else if (l instanceof ExtendedLocation) {
                    if (currPath != null) {
                        mergeList.add(new PathLocation(currPath));
                        currPath = null;
                    }
                    mergeList.add(l);
                }
            }
            if (currPath != null) {
                mergeList.add(new PathLocation(currPath));
                currPath = null;
            }
            result = (mergeList.size() == 1) ? mergeList.first() : new CompositeLocation(mergeList, this);
            locationCache.put(name, result);
        }

//        System.err.println("JavacFileManager.join: " + toString(locations) + " = " + result);
        return result;
    }

    // Get a location for all the containers named "tag" on given location
    // Containers may be either directories or archive files.
    private Location getModuleLocation(Location location, String tag) {
        // TODO: should reject bad use when location is already a module location
        // TODO: should honor location.isOutput()
        String name = location.getName() + "[" + tag + "]";
        Location result = locationCache.get(name);
        if (result == null) {
            Iterable<? extends PathEntry> pathEntries;
            if (location instanceof StandardLocation)
                pathEntries = getEntriesForLocation(location);
            else if (location instanceof PathLocation)
                pathEntries = ((PathLocation) location).path;
            else
                throw new IllegalArgumentException(location.getName());
            Path p = paths.new Path();
            if (pathEntries != null) {
                for (PathEntry e: pathEntries) {
                    File dir = new File(e.file, tag);
                    if (dir.exists() && dir.isDirectory() || location.isOutputLocation())
                        p.add(paths.new PathEntry(dir, e.kinds));
                    else {
                        File jar = new File(e.file, tag + ".jar");
                        if (jar.exists() && jar.isFile())
                            p.add(paths.new PathEntry(dir, e.kinds));
                    }
                }
            }
            result = new PathLocation(p, name);
            locationCache.put(name, result);
        }
        return result;
    }

    /**
     * Update a location based on the bootclasspath options.
     * @param l the default platform location if no bootclasspath options are given
     * @param first whether or not this is the first platform location
     * @param last whether or not this is the last platform location
     * @return a platform location based on the default location and on the
     *  values of any bootclasspath options.
     */
    public Location augmentPlatformLocation(Location l, boolean first, boolean last) {
        Path ppPrepend = first ? paths.getPlatformPathPrepend() : null;
        Path ppBase = paths.getPlatformPathBase();
        Path ppAppend = last ? paths.getPlatformPathAppend() : null;
        if (ppPrepend == null && ppAppend == null)
            return (ppBase == null) ? l : new PathLocation(ppBase);
        ListBuffer<Location> lb = new ListBuffer<Location>();
        if (ppPrepend != null)
            lb.append(new PathLocation(ppPrepend));
        lb.append((ppBase == null) ? l : new PathLocation(ppBase));
        if (ppAppend != null)
            lb.append(new PathLocation(ppAppend));
        return join(lb);
    }

    private static Set<JavaFileObject.Kind> allKinds, noSourceKind, noClassKind;
    static {
        allKinds = EnumSet.allOf(JavaFileObject.Kind.class);
        noSourceKind = EnumSet.allOf(JavaFileObject.Kind.class);
        noSourceKind.remove(JavaFileObject.Kind.SOURCE);
        noClassKind = EnumSet.allOf(JavaFileObject.Kind.class);
        noClassKind.remove(JavaFileObject.Kind.CLASS);
    }


    /**
     * An archive provides a flat directory structure of a ZipFile by
     * mapping directory names to lists of files (basenames).
     */
    public interface Archive {
        void close() throws IOException;

        boolean contains(RelativePath name);

        JavaFileObject getFileObject(RelativeDirectory subdirectory, String file);

        List<String> getFiles(RelativeDirectory subdirectory);

        Set<RelativeDirectory> getSubdirectories();
    }

    public class MissingArchive implements Archive {
        final File zipFileName;
        public MissingArchive(File name) {
            zipFileName = name;
        }
        public boolean contains(RelativePath name) {
            return false;
        }

        public void close() {
        }

        public JavaFileObject getFileObject(RelativeDirectory subdirectory, String file) {
            return null;
        }

        public List<String> getFiles(RelativeDirectory subdirectory) {
            return List.nil();
        }

        public Set<RelativeDirectory> getSubdirectories() {
            return Collections.emptySet();
        }

        @Override
        public String toString() {
            return "MissingArchive[" + zipFileName + "]";
        }
    }

    /** A directory of zip files already opened.
     */
    Map<File, Archive> archives = new HashMap<File,Archive>();

    private static final String[] symbolFileLocation = { "lib", "ct.sym" };
    private static final RelativeDirectory symbolFilePrefix
            = new RelativeDirectory("META-INF/sym/rt.jar/");

    /** Open a new zip file directory.
     */
    protected Archive openArchive(File zipFileName) throws IOException {
        Archive archive = archives.get(zipFileName);
        if (archive == null) {
            File origZipFileName = zipFileName;
            if (!ignoreSymbolFile && paths.isBootClassPathRtJar(zipFileName)) {
                File file = zipFileName.getParentFile().getParentFile(); // ${java.home}
                if (new File(file.getName()).equals(new File("jre")))
                    file = file.getParentFile();
                // file == ${jdk.home}
                for (String name : symbolFileLocation)
                    file = new File(file, name);
                // file == ${jdk.home}/lib/ct.sym
                if (file.exists())
                    zipFileName = file;
            }

            try {

                ZipFile zdir = null;

                boolean usePreindexedCache = false;
                String preindexCacheLocation = null;

                if (!useZipFileIndex) {
                    zdir = new ZipFile(zipFileName);
                }
                else {
                    usePreindexedCache = options.get("usezipindex") != null;
                    preindexCacheLocation = options.get("java.io.tmpdir");
                    String optCacheLoc = options.get("cachezipindexdir");

                    if (optCacheLoc != null && optCacheLoc.length() != 0) {
                        if (optCacheLoc.startsWith("\"")) {
                            if (optCacheLoc.endsWith("\"")) {
                                optCacheLoc = optCacheLoc.substring(1, optCacheLoc.length() - 1);
                            }
                           else {
                                optCacheLoc = optCacheLoc.substring(1);
                            }
                        }

                        File cacheDir = new File(optCacheLoc);
                        if (cacheDir.exists() && cacheDir.canWrite()) {
                            preindexCacheLocation = optCacheLoc;
                            if (!preindexCacheLocation.endsWith("/") &&
                                !preindexCacheLocation.endsWith(File.separator)) {
                                preindexCacheLocation += File.separator;
                            }
                        }
                    }
                }

                if (origZipFileName == zipFileName) {
                    if (!useZipFileIndex) {
                        archive = new ZipArchive(this, zdir);
                    } else {
                        archive = new ZipFileIndexArchive(this,
                                ZipFileIndex.getZipFileIndex(zipFileName,
                                    null,
                                    usePreindexedCache,
                                    preindexCacheLocation,
                                    options.get("writezipindexfiles") != null));
                    }
                }
                else {
                    if (!useZipFileIndex) {
                        archive = new SymbolArchive(this, origZipFileName, zdir, symbolFilePrefix);
                    }
                    else {
                        archive = new ZipFileIndexArchive(this,
                                ZipFileIndex.getZipFileIndex(zipFileName,
                                    symbolFilePrefix,
                                    usePreindexedCache,
                                    preindexCacheLocation,
                                    options.get("writezipindexfiles") != null));
                    }
                }
            } catch (FileNotFoundException ex) {
                archive = new MissingArchive(zipFileName);
            } catch (IOException ex) {
                if (zipFileName.exists())
                    log.error("error.reading.file", zipFileName, getMessage(ex));
                archive = new MissingArchive(zipFileName);
            }

            archives.put(origZipFileName, archive);
        }
        return archive;
    }

    /** Flush any output resources.
     */
    public void flush() {
        contentCache.clear();
    }

    /**
     * Close the JavaFileManager, releasing resources.
     */
    public void close() {
        for (Iterator<Archive> i = archives.values().iterator(); i.hasNext(); ) {
            Archive a = i.next();
            i.remove();
            try {
                a.close();
            } catch (IOException e) {
            }
        }
    }

    private String defaultEncodingName;
    private String getDefaultEncodingName() {
        if (defaultEncodingName == null) {
            defaultEncodingName =
                new OutputStreamWriter(new ByteArrayOutputStream()).getEncoding();
        }
        return defaultEncodingName;
    }

    public ClassLoader getClassLoader(Location location) {
        nullCheck(location);
        Iterable<? extends File> path = getLocation(location);
        if (path == null)
            return null;
        ListBuffer<URL> lb = new ListBuffer<URL>();
        for (File f: path) {
            try {
                lb.append(f.toURI().toURL());
            } catch (MalformedURLException e) {
                throw new AssertionError(e);
            }
        }

        return getClassLoader(lb.toArray(new URL[lb.size()]));
    }

    public Iterable<JavaFileObject> list(Location location,
                                         String packageName,
                                         Set<JavaFileObject.Kind> kinds,
                                         boolean recurse)
        throws IOException
    {
        // validatePackageName(packageName);
        nullCheck(packageName);
        nullCheck(kinds);

        if (location instanceof ExtendedLocation) {
            return ((ExtendedLocation) location).list(packageName, kinds, recurse);
        }

        Iterable<? extends PathEntry> entries = getEntriesForLocation(location);
        if (entries == null)
            return List.nil();
        RelativeDirectory subdirectory = RelativeDirectory.forPackage(packageName);
        ListBuffer<JavaFileObject> results = new ListBuffer<JavaFileObject>();

        for (PathEntry e: entries) {
            Set<JavaFileObject.Kind> s = EnumSet.copyOf(kinds);
            s.retainAll(e.kinds);
            if (!s.isEmpty())
                listDirectory(e.file, subdirectory, s, recurse, results);
        }

        return results.toList();
    }

    public String inferBinaryName(Location location, JavaFileObject file) {
        file.getClass(); // null check
        location.getClass(); // null check

        if (location instanceof ExtendedLocation)
            return ((ExtendedLocation) location).inferBinaryName(file);

        // Need to match the path semantics of list(location, ...)
        Iterable<? extends File> path = getLocation(location);
        if (path == null) {
            return null;
        }

        if (file instanceof BaseFileObject) {
            return ((BaseFileObject) file).inferBinaryName(path);
        } else
//            throw new IllegalArgumentException(file.getClass().getName() + ":" + file.toString());
            return null; // FIXME -- seems OK per spec but need to check
    }

    public boolean isSameFile(FileObject a, FileObject b) {
        nullCheck(a);
        nullCheck(b);
        if (!(a instanceof BaseFileObject))
            throw new IllegalArgumentException("Not supported: " + a);
        if (!(b instanceof BaseFileObject))
            throw new IllegalArgumentException("Not supported: " + b);
        return a.equals(b);
    }

    public boolean hasLocation(Location location) {
        return getLocation(location) != null;
    }

    public JavaFileObject getJavaFileForInput(Location location,
                                              String className,
                                              JavaFileObject.Kind kind)
        throws IOException
    {
        nullCheck(location);
        // validateClassName(className);
        nullCheck(className);
        nullCheck(kind);
        if (!sourceOrClass.contains(kind))
            throw new IllegalArgumentException("Invalid kind " + kind);
        return getFileForInput(location, RelativeFile.forClass(className, kind), kind);
    }

    public FileObject getFileForInput(Location location,
                                      String packageName,
                                      String relativeName)
        throws IOException
    {
        nullCheck(location);
        // validatePackageName(packageName);
        nullCheck(packageName);
        if (!isRelativeUri(relativeName))
            throw new IllegalArgumentException("Invalid relative name: " + relativeName);
        RelativeFile name = packageName.length() == 0
            ? new RelativeFile(relativeName)
            : new RelativeFile(RelativeDirectory.forPackage(packageName), relativeName);
        return getFileForInput(location, name, getKindForName(name.path));
    }

    private JavaFileObject.Kind getKindForName(String name) {
        for (JavaFileObject.Kind k: JavaFileObject.Kind.values()) {
            if (k != JavaFileObject.Kind.OTHER && name.endsWith(k.extension)) {
                return k;
            }
        }
        return JavaFileObject.Kind.OTHER;
    }

    private JavaFileObject getFileForInput(Location location, RelativeFile name,
                JavaFileObject.Kind kind) throws IOException {
        Iterable<? extends PathEntry> entries = getEntriesForLocation(location);
        if (entries == null)
            return null;

        for (PathEntry e: entries) {
            if (kind != null && !e.kinds.contains(kind))
                continue;
            File dir = e.file;
            if (dir.isDirectory()) {
                File f = name.getFile(dir);
                if (f.exists())
                    return new RegularFileObject(this, f);
            } else {
                Archive a = openArchive(dir);
                if (a.contains(name)) {
                    return a.getFileObject(name.dirname(), name.basename());
                }
            }
        }

        return null;
    }

    public JavaFileObject getJavaFileForOutput(Location location,
                                               String className,
                                               JavaFileObject.Kind kind,
                                               FileObject sibling)
        throws IOException
    {
        nullCheck(location);
        // validateClassName(className);
        nullCheck(className);
        nullCheck(kind);
        if (!sourceOrClass.contains(kind))
            throw new IllegalArgumentException("Invalid kind " + kind);
        return getFileForOutput(location, RelativeFile.forClass(className, kind), sibling);
    }

    public FileObject getFileForOutput(Location location,
                                       String packageName,
                                       String relativeName,
                                       FileObject sibling)
        throws IOException
    {
        nullCheck(location);
        // validatePackageName(packageName);
        nullCheck(packageName);
        if (!isRelativeUri(relativeName))
            throw new IllegalArgumentException("relativeName is invalid");
        RelativeFile name = packageName.length() == 0
            ? new RelativeFile(relativeName)
            : new RelativeFile(RelativeDirectory.forPackage(packageName), relativeName);
        return getFileForOutput(location, name, sibling);
    }

    private JavaFileObject getFileForOutput(Location location,
                                            RelativeFile fileName,
                                            FileObject sibling)
        throws IOException
    {
        File dir;
        if (location == CLASS_OUTPUT) {
            if (getClassOutDir() != null) {
                dir = getClassOutDir();
            } else {
                File siblingDir = null;
                if (sibling != null && sibling instanceof RegularFileObject) {
                    siblingDir = ((RegularFileObject)sibling).file.getParentFile();
                }
                return new RegularFileObject(this, new File(siblingDir, fileName.basename()));
            }
        } else if (location == SOURCE_OUTPUT) {
            dir = (getSourceOutDir() != null ? getSourceOutDir() : getClassOutDir());
        } else {
            dir = null;
            Iterable<? extends PathEntry> path = getEntriesForLocation(location);
            if (path != null) {
                for (PathEntry e: path) {
                    dir = e.file;
                    break;
                }
            }
            //System.err.println("JavacFileManager.getFileForOutput location:" + location + " path:" + toString(path) + " dir:" + dir);
        }

        File file = fileName.getFile(dir); // null-safe
        return new RegularFileObject(this, file);

    }

    public Iterable<? extends JavaFileObject> getJavaFileObjectsFromFiles(
        Iterable<? extends File> files)
    {
        ArrayList<RegularFileObject> result;
        if (files instanceof Collection<?>)
            result = new ArrayList<RegularFileObject>(((Collection<?>)files).size());
        else
            result = new ArrayList<RegularFileObject>();
        for (File f: files)
            result.add(new RegularFileObject(this, nullCheck(f)));
        return result;
    }

    public Iterable<? extends JavaFileObject> getJavaFileObjects(File... files) {
        return getJavaFileObjectsFromFiles(Arrays.asList(nullCheck(files)));
    }

    public void setLocation(Location location,
                            Iterable<? extends File> path)
        throws IOException
    {
        nullCheck(location);
        paths.lazy();

        final File dir = location.isOutputLocation() ? getOutputDirectory(path) : null;

        if (location == CLASS_OUTPUT)
            classOutDir = getOutputLocation(dir, D);
        else if (location == SOURCE_OUTPUT)
            sourceOutDir = getOutputLocation(dir, S);
        else
            paths.setPathForLocation(location, path);
    }
    // where
        private File getOutputDirectory(Iterable<? extends File> path) throws IOException {
            if (path == null)
                return null;
            Iterator<? extends File> pathIter = path.iterator();
            if (!pathIter.hasNext())
                throw new IllegalArgumentException("empty path for directory");
            File dir = pathIter.next();
            if (pathIter.hasNext())
                throw new IllegalArgumentException("path too long for directory");
            if (!dir.exists())
                throw new FileNotFoundException(dir + ": does not exist");
            else if (!dir.isDirectory())
                throw new IOException(dir + ": not a directory");
            return dir;
        }

    private File getOutputLocation(File dir, OptionName defaultOptionName) {
        if (dir != null)
            return dir;
        String arg = options.get(defaultOptionName);
        if (arg == null)
            return null;
        return new File(arg);
    }

    public Iterable<File> getLocation(Location location) {
        nullCheck(location);
        paths.lazy();

        if (location == CLASS_OUTPUT) {
            File dir = getClassOutDir();
            return (dir == null ? null : List.of(dir));
        }

        if (location == SOURCE_OUTPUT) {
            File dir = getSourceOutDir();
            return (dir == null ? null : List.of(dir));
        }


        final Iterable<? extends PathEntry> entries;
        if (location instanceof PathLocation)
            entries = ((PathLocation) location).path;
        else
            entries = paths.getPathForLocation(location);

        if (entries == null)
            return null;

        // wrap the natural PathEntry iterator with one that just returns the file values
        return new Iterable<File>() {
            public Iterator<File> iterator() {
                return new Iterator<File>() {
                    public boolean hasNext() {
                        return iter.hasNext();
                    }

                    public File next() {
                        return iter.next().file;
                    }

                    public void remove() {
                        iter.remove();
                    }

                    final Iterator<? extends PathEntry> iter = entries.iterator();
                };
            }
        };
    }

    Iterable<PathEntry> getEntriesForLocation(Location location) {
        nullCheck(location);

        if (location instanceof PathLocation)
            return ((PathLocation) location).path;

        paths.lazy();

        if (location == CLASS_OUTPUT) {
            File dir = getClassOutDir();
            return (dir == null ? null : List.of(paths.new PathEntry(dir)));
        }

        if (location == SOURCE_OUTPUT) {
            File dir = getSourceOutDir();
            return (dir == null ? null : List.of(paths.new PathEntry(dir)));
        }

        return paths.getPathForLocation(location);
    }

    private File getClassOutDir() {
        if (classOutDir == uninited)
            classOutDir = getOutputLocation(null, D);
        return classOutDir;
    }

    private File getSourceOutDir() {
        if (sourceOutDir == uninited)
            sourceOutDir = getOutputLocation(null, S);
        return sourceOutDir;
    }

    /**
     * Enforces the specification of a "relative" URI as used in
     * {@linkplain #getFileForInput(Location,String,URI)
     * getFileForInput}.  This method must follow the rules defined in
     * that method, do not make any changes without consulting the
     * specification.
     */
    protected static boolean isRelativeUri(URI uri) {
        if (uri.isAbsolute())
            return false;
        String path = uri.normalize().getPath();
        if (path.length() == 0 /* isEmpty() is mustang API */)
            return false;
        char first = path.charAt(0);
        return first != '.' && first != '/';
    }

    // Convenience method
    protected static boolean isRelativeUri(String u) {
        try {
            return isRelativeUri(new URI(u));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Converts a relative file name to a relative URI.  This is
     * different from File.toURI as this method does not canonicalize
     * the file before creating the URI.  Furthermore, no schema is
     * used.
     * @param file a relative file name
     * @return a relative URI
     * @throws IllegalArgumentException if the file name is not
     * relative according to the definition given in {@link
     * javax.tools.JavaFileManager#getFileForInput}
     */
    public static String getRelativeName(File file) {
        if (!file.isAbsolute()) {
            String result = file.getPath().replace(File.separatorChar, '/');
            if (isRelativeUri(result))
                return result;
        }
        throw new IllegalArgumentException("Invalid relative path: " + file);
    }

    /**
     * Get a detail message from an IOException.
     * Most, but not all, instances of IOException provide a non-null result
     * for getLocalizedMessage().  But some instances return null: in these
     * cases, fallover to getMessage(), and if even that is null, return the
     * name of the exception itself.
     * @param e an IOException
     * @return a string to include in a compiler diagnostic
     */
    public static String getMessage(IOException e) {
        String s = e.getLocalizedMessage();
        if (s != null)
            return s;
        s = e.getMessage();
        if (s != null)
            return s;
        return e.toString();
    }
}
