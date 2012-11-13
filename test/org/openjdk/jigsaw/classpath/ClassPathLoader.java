/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @compile ClassPathLoader.java Bar.java
 * @summary basic class loading tests in classpath mode
 */

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

public class ClassPathLoader {
    static ClassLoader systemCL;
    static ClassLoader extCL;
    static Context bootstrap ;
    static Context ext;
    static Context tools;

    public static void main(String[] argv) throws Exception {
        int version = argv.length == 0 ? 0 : Integer.parseInt(argv[0]);

        initialize();
        checkSearchPath(version);
        checkFiles();
    }

    static void initialize() {
        systemCL = ClassLoader.getSystemClassLoader();
        extCL = systemCL.getParent();
        bootstrap = new BootstrapContext();
        System.out.println(bootstrap);

        ext = new ExtensionContext();
        System.out.println(ext);

        tools = new ToolsContext();
        System.out.println(tools);
    }

    static void checkSearchPath(int version) throws Exception {
        int v = Bar.version();
        System.out.println("Loaded Bar version " + v);

        if (version != v) {
            throw new RuntimeException("Incorrect Bar version: " + v +
                    " expected: " + version);
        }
    }

    static void checkFiles() throws Exception {
        // check jdk classes and resources
        checkContext(new BootstrapContext(), systemCL);
        checkContext(new ExtensionContext(), extCL);
        Context tools = new ToolsContext();
        checkURLClassLoader(tools);
    }

    static void checkContext(Context cx, ClassLoader ld) throws Exception {
        cx.checkClasses(ld);
        cx.checkResources(ld);
        cx.checkNativeLibs(ld);
    }

    static void checkURLClassLoader(Context cx) throws Exception {
        String home = System.getProperty("java.home");
        File f = new File(new File(home, "lib"), "tools.jar");
        boolean found = f.exists();
        URL[] urls = { f.toURI().toURL() };
        ClassLoader ld = new URLClassLoader(urls);
        if (!found) {
        } else {
            System.out.println("tools.jar not exists");
        }
        checkContext(cx, ld);
    }

    static abstract class Context {
        abstract String[] classes();
        abstract String[] resources();

        final String type;
        final String[] keys;

        Context(String type, String... keys) {
            this.type = type;
            this.keys = keys;
        }
        void checkClasses(ClassLoader cl) throws Exception {
            for (String cn : classes()) {
                System.out.format("load class %s%n", cn);
                Class.forName(cn, false, cl);
            }
        }

        void checkResources(ClassLoader cl) throws Exception {
            for (String rn : resources()) {
                System.out.format("load resource %s%n", rn);
                URL u = cl.getResource(rn);
                if (u == null) {
                    throw new RuntimeException(cl + " failed to load resource " + rn);
                }
            }
        }

        void checkNativeLibs(ClassLoader cl) throws Exception {
            for (String name : natlibs()) {
                System.out.format("load native library %s%n", name);
                System.loadLibrary(name);
            }
        }

        public String[] natlibs() {
            return new String[0];
        }

        public String toString() {
            StringBuilder sb = new StringBuilder("context for " + type);
            for (String k : keys) {
                sb.append("\n   ").append(k).append(" = ").append(System.getProperty(k));
            }
            return sb.toString();
        }
    }
    static class BootstrapContext extends Context {
        BootstrapContext() {
            super("Bootstrap", "sun.boot.class.path", "sun.boot.library.path");
        }

        @Override
        public String[] classes() {
            return new String[]{
                        "java.lang.ProcessBuilder",
                        "java.awt.Component",
                        "java.lang.management.RuntimeMXBean",
                        "java.util.logging.LogManager",
                        "com.sun.net.httpserver.HttpContext",
                    };
        }
        @Override
        public String[] resources() {
            return new String[] {
                "sun/management/resources/agent.class",
                "sun/text/resources/unorm.icu"
            };
        }
        @Override
        public String[] natlibs() {
            return new String[] {
                "verify",
                "instrument"
            };
        }

    }

    static class ExtensionContext extends Context  {
        ExtensionContext() {
            super("Extension", "java.ext.dirs");
        }
        @Override
        public String[] classes() {
            return new String[] {
                "com.sun.nio.zipfs.ZipFileSystem",
                "sun.security.ec.SunEC"
            };
        }

        @Override
        public String[] resources() {
            return new String[] {
                "sun/text/resources/th/thai_dict"
            };
        }

        @Override
        public String[] natlibs() {
            return new String[0];
        }
    }


    static class ToolsContext extends Context  {

        ToolsContext() {
            super("Tools", "java.class.path", "java.library.path");
        }
        @Override
        public String[] classes() {
            return new String[] {
                "com.sun.tools.javac.Main",
                "com.sun.jdi.Field"
            };
        }

        @Override
        public String[] resources() {
            return new String[] {
                "com/sun/tools/hat/resources/hat.js",
                "sun/tools/serialver/resources/serialver.properties"
            };
        }
    }
}
