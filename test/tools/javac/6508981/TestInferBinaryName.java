/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6508981
 * @summary cleanup file separator handling in JavacFileManager
 * (This test is specifically to test the new impl of inferBinaryName)
 * @build p.A
 * @run main TestInferBinaryName
 */

import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import javax.tools.*;

import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Options;

import static javax.tools.JavaFileObject.Kind.*;
import static javax.tools.StandardLocation.*;


/**
 * Verify the various implementations of inferBinaryName, but configuring
 * different instances of a file manager, getting a file object, and checking
 * the impl of inferBinaryName for that file object.
 */
public class TestInferBinaryName {
    static final boolean IGNORE_SYMBOL_FILE = false;
    static final boolean USE_SYMBOL_FILE = true;
    static final boolean DONT_USE_ZIP_FILE_INDEX = false;
    static final boolean USE_ZIP_FILE_INDEX = true;

    public static void main(String... args) throws Exception {
        new TestInferBinaryName().run();
    }

    void run() throws Exception {
        javaHome = new File(System.getProperty("java.home"));
        if (javaHome.getName().equals("jre"))
            javaHome = javaHome.getParentFile();
        modularJDK = file(javaHome, "lib", "modules", "%jigsaw-library").exists();

        //System.err.println(System.getProperties());
        testDirectory();
        testUserJar_ZipArchive();
        testUserJar_ZipFileIndex();
        testSymbolArchive();
        testZipArchive();
        testZipFileIndexArchive();
        testZipFileIndexArchive2();
        if (errors > 0)
            throw new Exception(errors + " error found");
    }

    void testDirectory() throws IOException {
        String testName = "testDirectory";
        String testClassName = "p.A";
        JavaFileManager fm =
            getFileManager("test.classes", USE_SYMBOL_FILE, USE_ZIP_FILE_INDEX);
        test(testName,
             fm, testClassName, "com.sun.tools.javac.file.RegularFileObject");
    }

    void testUserJar_ZipArchive() throws IOException {
        String testName = "testUserJar_ZipArchive";
        String testClassName = "p.A";
        File my_jar = createJar("my.jar", getClasses(p.A.class));
        System.setProperty("my.jar", my_jar.getPath());

        JavaFileManager fm =
            getFileManager("my.jar", USE_SYMBOL_FILE, DONT_USE_ZIP_FILE_INDEX);
        test(testName,
             fm, testClassName, "com.sun.tools.javac.file.ZipArchive$ZipFileObject");
    }

    void testUserJar_ZipFileIndex() throws IOException {
        String testName = "testUserJar_ZipFileIndex";
        String testClassName = "p.A";
        File my_jar = createJar("my.jar", getClasses(p.A.class));
        System.setProperty("my.jar", my_jar.getPath());

        JavaFileManager fm =
            getFileManager("my.jar", USE_SYMBOL_FILE, USE_ZIP_FILE_INDEX);
        test(testName,
             fm, testClassName, "com.sun.tools.javac.file.ZipFileIndexArchive$ZipFileIndexFileObject");
    }

    void testSymbolArchive() throws IOException {
        String testName = "testSymbolArchive";
        if (modularJDK && !file(javaHome, "lib", "ct.sym").exists()) {
            skip(testName, "modular JDK found with no ct.sym");
            return;
        }

        String testClassName = "java.lang.String";
        JavaFileManager fm =
            getFileManager("sun.boot.class.path", USE_SYMBOL_FILE, DONT_USE_ZIP_FILE_INDEX);
        test(testName,
             fm, testClassName, "com.sun.tools.javac.file.SymbolArchive$SymbolFileObject");
    }

    void testZipArchive() throws IOException {
        String testName = "testZipArchive";
        if (modularJDK) {
            skip(testName, "assumes impl for platform classes");
            return;
        }

        String testClassName = "java.lang.String";
        JavaFileManager fm =
            getFileManager("sun.boot.class.path", IGNORE_SYMBOL_FILE, DONT_USE_ZIP_FILE_INDEX);
        test(testName,
             fm, testClassName, "com.sun.tools.javac.file.ZipArchive$ZipFileObject");
    }

    void testZipFileIndexArchive() throws IOException {
        String testName = "testZipFileIndexArchive";
        if (modularJDK) {
            skip(testName, " assumes impl for platform classes");
            return;
        }

        String testClassName = "java.lang.String";
        JavaFileManager fm =
            getFileManager("sun.boot.class.path", USE_SYMBOL_FILE, USE_ZIP_FILE_INDEX);
        test(testName,
             fm, testClassName, "com.sun.tools.javac.file.ZipFileIndexArchive$ZipFileIndexFileObject");
    }

    void testZipFileIndexArchive2() throws IOException {
        String testName = "testZipFileIndexArchive2";
        if (modularJDK) {
            skip(testName, "assumes impl for platform classes");
            return;
        }

        String testClassName = "java.lang.String";
        JavaFileManager fm =
            getFileManager("sun.boot.class.path", IGNORE_SYMBOL_FILE, USE_ZIP_FILE_INDEX);
        test(testName,
             fm, testClassName, "com.sun.tools.javac.file.ZipFileIndexArchive$ZipFileIndexFileObject");
    }

    /**
     * @param testName for debugging
     * @param fm suitably configured file manager
     * @param testClassName the classname to test
     * @param implClassName the expected classname of the JavaFileObject impl,
     *     used for checking that we are checking the expected impl of
     *     inferBinaryName
     */
    void test(String testName,
              JavaFileManager fm, String testClassName, String implClassName) throws IOException {
        JavaFileObject fo = fm.getJavaFileForInput(CLASS_PATH, testClassName, CLASS);
        if (fo == null) {
            System.err.println("Can't find " + testClassName);
            errors++;
            return;
        }

        String cn = fo.getClass().getName();
        String bn = fm.inferBinaryName(CLASS_PATH, fo);
        System.err.println(testName + " " + cn + " " + bn);
        check(cn, implClassName);
        check(bn, testClassName);
        System.err.println("OK");
    }

    JavaFileManager getFileManager(String classpathProperty,
                                   boolean symFileKind,
                                   boolean zipFileIndexKind)
            throws IOException {
        Context ctx = new Context();
        // uugh, ugly back door, should be cleaned up, someday
        if (zipFileIndexKind == USE_ZIP_FILE_INDEX)
            System.clearProperty("useJavaUtilZip");
        else
            System.setProperty("useJavaUtilZip", "true");
        Options options = Options.instance(ctx);
        if (symFileKind == IGNORE_SYMBOL_FILE)
            options.put("ignore.symbol.file", "true");
        JavacFileManager fm = new JavacFileManager(ctx, false, null);
        List<File> path = getPath(System.getProperty(classpathProperty));
        fm.setLocation(CLASS_PATH, path);
        return fm;
    }

    List<File> getPath(String s) {
        List<File> path = new ArrayList<File>();
        for (String f: s.split(File.pathSeparator)) {
            if (f.length() > 0)
                path.add(new File(f));
        }
        //System.err.println("path: " + path);
        return path;
    }

    void check(String found, String expect) {
        if (!found.equals(expect)) {
            System.err.println("Expected: " + expect);
            System.err.println("   Found: " + found);
            errors++;
        }
    }

    Map<String,byte[]> getClasses(Class... classes) throws IOException {
        ClassLoader cl = getClass().getClassLoader();
        Map<String,byte[]> results = new HashMap<String, byte[]>();
        for (Class c: classes) {
            String name = c.getName().replace(".", "/") + ".class";
            byte[] data = read(cl.getResourceAsStream(name));
            results.put(name, data);
        }
        return results;
    }

    byte[] read(InputStream in) throws IOException {
        try {
            byte[] data = new byte[8192];
            int offset = 0;
            int n;
            while ((n = in.read(data, offset, data.length - offset)) >= 0) {
                offset += n;
                if (offset == data.length)
                    data = Arrays.copyOf(data, 2 * data.length);
            }
            return data;
        } finally {
            in.close();
        }
    }

    File createJar(String name, Map<String, byte[]> entries) throws IOException {
        File jar = new File(name);
        OutputStream out = new FileOutputStream(jar);
        try {
            JarOutputStream jos = new JarOutputStream(out);
            for (Map.Entry<String,byte[]> e: entries.entrySet()) {
                jos.putNextEntry(new ZipEntry(e.getKey()));
                jos.write(e.getValue());
            }
            jos.close();
        } finally {
            out.close();
        }
        return jar;
    }

    void skip(String testName, String reason) {
        System.err.println(testName + " skipped: " + reason);
    }

    private int errors;

    File javaHome;
    boolean modularJDK;

    static File file(File dir, String... path) {
        File f = dir;
        for (String p: path)
            f = new File(f, p);
        return f;
    }
}

class A { }

