/*
 *  Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 */
package org.openjdk.jigsaw;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleId;
import java.lang.module.ModuleIdQuery;
import java.lang.module.ModuleInfo;
import java.net.URI;
import java.net.URL;
import java.util.*;
import static org.openjdk.jigsaw.Trace.*;
import sun.misc.VM;
import sun.net.www.ParseUtil;

/**
 * A ClassPathContext represents the default search path for bootclasspath,
 * extension directory, and the tools.jar.
 *
 * <p>Open issue:</p>
 * -Xbootclasspath sets to the default module image is not supported.
 * One potential compatibility risk is when one VM execs another
 * VM with -Xboothclasspath:<value of "sun.boot.class.path" property>
 * it will no longer work.
 */
public class ClassPathContext {
    private static final String javaHome = System.getProperty("java.home");
    private ClassPathContext(LibraryPool libpool, Set<Context> cxs) {
        this.libPool = libpool;
        this.cxs = cxs;
    }

    private final Set<Context> cxs;
    private Set<Context> contexts() { return cxs; }

    private final LibraryPool libPool;
    private Library library(Context cx, ModuleId mid)
        throws IOException
    {
        return libPool.get(cx, mid);
    }

    /**
     * Find a resource within the given ClassPathContext.
     *
     * ## This method is called to find both classes and resource files
     * ## from sun.misc.URLClassPath. This can be optimized for each case.
     * ## Also consider returning other information such as the size
     * ## of the resource data and code source (currently returned by
     * ## a separate findModuleURL method call).
     *
     * @param   rn
     *          The name of the requested resource, in the usual
     *          slash-separated form
     *
     * @return  A {@code URL} object naming the location of the resource,
     *          or {@code null} if this context does not define that
     *          resource
     */
    public URL findLocalResource(String rn)
        throws IOException
    {
        if (rn.startsWith("/")) {
            rn = rn.substring(1);
        }
        for (Context cx : contexts()) {
            for (ModuleId mid : cx.modules()) {
                URI u = library(cx, mid).findLocalResource(mid, rn);
                if (u != null) {
                    if (u != null) {
                        if (tracing) {
                            trace(0, "%s: found resource %s (%s)", mid, rn, u);
                        }
                        return u.toURL();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Return a code source URL for the module containing a resource.
     *
     * @param   rn
     *          The name of the requested resource, in the usual
     *          slash-separated form
     *
     * @return  A {@code URL} object representing the code source of the module
     *          or {@code null} if this context does not define that
     *          resource
     */
    public URL findModuleURL(String rn)
        throws IOException
    {
        if (rn.startsWith("/")) {
            rn = rn.substring(1);
        }
        for (Context cx : contexts()) {
            for (ModuleId mid : cx.modules()) {
                URI u = library(cx, mid).findLocalResource(mid, rn);
                if (u != null) {
                    // ## return the code source for the given module
                    return new URL("module:///" + mid.name());
                }
            }
        }
        return null;
    }

    /**
     * Find a native library within this ClassPathContext.
     *
     * @param   name
     *          The name of the requested library, in platform-specific
     *          form, <i>i.e.</i>, that returned by the {@link
     *          java.lang.System#mapLibraryName System.mapLibraryName} method
     *
     * @return  A {@code File} object naming the location of the native library,
     *          or {@code null} if this context does not contain such a
     *          library
     */
    public File findLocalNativeLibrary(String fn) throws IOException {
        for (Context cx : contexts()) {
            for (ModuleId mid : cx.modules()) {
                File nlf = (library(cx, mid).findLocalNativeLibrary(mid, fn));
                if (nlf != null) {
                    if (tracing) {
                        trace(0, "%s (%s): lib %s", this, mid, nlf);
                    }
                    return nlf;
                }
            }
        }
        return null;
    }

    private static ClassPathContext[] classpathContexts;
    public static ClassPathContext get(LoaderType type) {
        return classpathContexts[type.ordinal()];
    }

    /*
     * Load the classpath configuration that contains ClassPathContext
     * representing the default bootclasspath search, extension, and tools.jar.
     */
    public static void loadClassPathConfiguration() {
        Library mlib = null;
        Configuration<Context> config = null;

        try {
            File mlp = Launcher.getModuleLibraryPath();
            if (mlp.exists()) {
                mlib = SimpleLibrary.open(mlp);
                if (mlib == null) {
                    throw new Error(mlp + ": does not exist");
                }

                JigsawModuleSystem jms = JigsawModuleSystem.instance();
                ModuleIdQuery midq = jms.parseModuleIdQuery("jdk.classpath");
                ModuleId mid = mlib.findLatestModuleId(midq);
                if (mid == null) {
                    // During jdk build, the library may have the base module installed
                    midq = jms.parseModuleIdQuery(Platform.BASE_MODULE_NAME);
                    mid = mlib.findLatestModuleId(midq);
                }
                if (mid != null) {
                    ModuleInfo mi = mlib.readModuleInfo(mid);
                    if (mi == null) {
                        throw new Error(mid + ": Can't read module-info");
                    }
                    // a config for the jdk.classpath is precomputed at install time
                    // for faster startup
                    config = mlib.readConfiguration(mid);
                    if (config == null) {
                        throw new Error(mid + ": Module not configured");
                    }
                }
            }
        } catch (IOException e) {
            throw new Error("Fail to load contexts for classpath mode", e);
        }

        // initialize ClassPathContexts
        int numLoaders = LoaderType.values().length;
        classpathContexts = new ClassPathContext[numLoaders];

        List<Set<Context>> contextSets = new ArrayList<>(numLoaders);
        for (int i=0; i < numLoaders; i++) {
             contextSets.add(new HashSet<Context>());
        }

        String[] ext = extModules();
        String[] tools = toolsModules();
        if (mlib != null && config != null) {
            // ## jdk.classpath module is created during the jdk build with
            // ## an explicit module-info requiring all jdk modules.  The
            // ## configuration does not contain any information which context
            // ## or module belongs to which ClassLoader.
            // ##
            // ## Another alternative is to generate a configuration with
            // ## 3 contexts, each represents bootclasspath, extension, and
            // ## tools on the classpath

            Set<Context> bootCxs = contextSets.get(LoaderType.BOOTSTRAP.ordinal());
            Set<Context> extCxs = contextSets.get(LoaderType.EXTENSION.ordinal());
            Set<Context> toolsCxs = contextSets.get(LoaderType.SYSTEM.ordinal());

            for (String mn : ext) {
                Context cx = config.findContextForModuleName(mn);
                if (cx != null)
                    extCxs.add(cx);
            }
            for (String mn : tools) {
                Context cx = config.findContextForModuleName(mn);
                if (cx != null) {
                    assert !extCxs.contains(cx);
                    toolsCxs.add(cx);
                }
            }
            for (Context cx : config.contexts()) {
                if (!extCxs.contains(cx) && !toolsCxs.contains(cx)) {
                    bootCxs.add(cx);
                }
            }
        }
        LibraryPool libpool = mlib != null ? new LibraryPool(mlib) : null;
        for (int i=0; i < numLoaders; i++) {
            classpathContexts[i] = new ClassPathContext(libpool, contextSets.get(i));
        }
        // mark the modules for the bootstrap contexts that are stored in native
        initBootstrapContexts(ext, ext.length, tools, tools.length);
    }

    private static File file(String... names) {
        File p = new File(javaHome);
        for (String fn : names) {
            p = new File(p, fn);
        }
        return p.exists() ? p : null;
    }

    private static String[] extModules() {
        // ## temporarily exclude this module due to its split package "jdk.sunec"
        String[] modules = new String[] {
            "jdk.zipfs", "jdk.sctp",
            "jdk.sunpkcs11", "jdk.sunmscapi", "jdk.ucrypto"
        };
        // ## modulepath
        // JDK development build - all classes are loaded by bootstrap loader
        return file("classes") == null ? modules : new String[0];
    }

    private static String[] toolsModules() {
        // jdk.tools.base and jdk.tools.jre are not in this list because some classes
        // (e.g. keytool, policytool) are included in rt.jar in the legacy image.
        String[] modules = new String[] {
            "jdk.tools.jaxws", "jdk.tools", "jdk.devtools"
        };
        // ## modulepath
        // JDK development build - all classes are loaded by bootstrap loader
        return file("classes") == null ? modules : new String[0];
    }

    static native void initBootstrapContexts(String[] extModules, int extCount,
                                             String[] cpathModules, int cpathCount);

    // ## handle FX later
    public enum LoaderType {
        BOOTSTRAP("BootstrapClassLoader", bootClassPath()),
        EXTENSION("ExtClassLoader", file("lib", "ext")),
        SYSTEM("AppClassLoader", file("lib", "tools.jar"));

        private final String loaderName;
        private final File defaultPath;
        LoaderType(String name, File defaultPath) {
            this.loaderName = name;
            this.defaultPath = defaultPath;
        }

        public File defaultPath() {
            return defaultPath;
        }

        boolean isDefaultPath(URL u) throws IOException {
            if (defaultPath != null && "file".equals(u.getProtocol().toLowerCase())) {
                String fileName = u.getFile();
                if (fileName != null) {
                    fileName = ParseUtil.decode(fileName);
                    File f = new File(fileName);
                    if (f.exists()) {
                        return java.nio.file.Files.isSameFile(defaultPath.toPath(), f.toPath());
                    }
                }
            }
            return false;
        }

        public String toString() {
            return loaderName + " [" + defaultPath + "]";
        }

        public static LoaderType loaderTypeForPath(URL u) throws IOException {
            for (LoaderType type : values()) {
                if (type.isDefaultPath(u)) {
                    return type;
                }
            }
            return null;
        }
    };

    private static File bootClassPath() {
        // sun.boot.module.classpath is set by the VM to the value
        // representing the default bootclasspath loading classes/resources
        // from the system module image
        String bcp = VM.getSavedProperty("sun.boot.module.classpath");
        if (bcp != null) {
            File f = new File(bcp);
            if (f.exists())
                return f;
        }
        return null;
    }
}
