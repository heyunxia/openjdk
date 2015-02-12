/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary test module file hashing.
 */

import java.io.*;
import java.security.*;
import java.util.*;
import org.openjdk.jigsaw.*;
import org.openjdk.jigsaw.cli.*;

public class ModuleFormatHeaderHashTest {
    final String MNAME = "hello";
    final String MVER = "0.1";
    String moduleinfo = "module " + MNAME + " @ " + MVER + " {}";

    public static void main(String[] args) throws Exception {
        new ModuleFormatHeaderHashTest().run();
    }

    void run() throws Exception {
        try {
            test();
        } catch (Throwable t) {
            t.printStackTrace();
            errors++;
        }


        if (errors == 0)
            System.out.println(count + " tests passed");
        else
            throw new Exception(errors + "/" + count + " tests failed");
    }

    void testEmptyModule() throws Exception {
        System.err.println("Test: Empty module");
        count++;
        reset();
        List<File> files = new ArrayList<File>();
        addFile(files, createFile("module-info.java", moduleinfo));
        compile(files);
        compress(MNAME);
        byte [] expected = readHash(MNAME, MVER);
        byte [] computed = hash(MNAME, MVER, "SHA-256");
        if (!MessageDigest.isEqual(expected, computed))
            throw new IOException("Expected and computed file hashes don't match");
    }

    void test() throws Exception {
        testEmptyModule();
    }

    /**
     * Get a module file's stored hash.
     */
    byte [] readHash(String name, String version) throws Exception {
        String fname = moduleDir + File.separator + name + "@" + version + ".jmod";
        try (FileInputStream fis = new FileInputStream(fname);
             DataInputStream in = new DataInputStream(fis);
             ModuleFile.Reader r = new ModuleFile.Reader(in);) {
             return r.getHash();
        }
    }

    /**
     * Hash a module file (without the file hash in the module file header).
     */
    byte [] hash(String name, String version, String digest) throws Exception {
        String fname = moduleDir + File.separator + name + "@" + version + ".jmod";
        MessageDigest md = MessageDigest.getInstance(digest);
        try (FileInputStream fis = new FileInputStream(fname);
             DigestInputStream dis = new DigestInputStream(fis, md)) {
            dis.read(new byte[ModuleFile.ModuleFileHeader.LENGTH_WITHOUT_HASH]);
            dis.on(false);
            dis.read(new byte [md.getDigestLength()]);
            dis.on(true);
            for (int c = dis.read() ; c != -1 ; c = dis.read())
                ;
            return md.digest();
        }
    }

    /**
     * Compress a module.
     */
    void compress(String name) throws Exception {
        compress(name, false);
    }

    void compress(String name, boolean haveNatLibs)
        throws Exception {
        compress(name, haveNatLibs, false);
    }

    void compress(String name, boolean haveNatLibs,
                  boolean haveNatCmds) throws Exception {
        compress(name, haveNatLibs, haveNatCmds, false);
    }

    void compress(String name, boolean haveNatLibs,
                  boolean haveNatCmds, boolean haveConfig)
        throws Exception {
        List<String> args = new ArrayList<String>();
        args.add("-m");
        args.add(classesDir.getAbsolutePath());
        args.add("-d");
        args.add(moduleDir.getAbsolutePath());
        if (haveNatLibs) {
            args.add("--natlib");
            args.add(natlibDir.toString());
        }
        if (haveNatCmds) {
            args.add("--natcmd");
            args.add(natcmdDir.toString());
        }
        if (haveConfig) {
            args.add("--config");
            args.add(configDir.toString());
        }
        args.add("jmod");
        args.add("hello");
        Packager.main(args.toArray(new String[0]));
    }

    /**
     * Compile a list of files.
     */
    void compile(List<File> files) {
        List<String> options = new ArrayList<String>();
        options.addAll(Arrays.asList("-source", "8", "-d", classesDir.getPath()));
        for (File f: files)
            options.add(f.getPath());

        String[] opts = options.toArray(new String[options.size()]);
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            int rc = com.sun.tools.javac.Main.compile(opts, pw);

            String out = sw.toString();
            if (out.trim().length() > 0)
                System.err.println(out);
            if (rc != 0)
                throw new Error("compilation failed: rc=" + rc);
        }
    }

    /**
     * Add a file to a list if the file is not null.
     */
    void addFile(List<File> files, File file) {
        if (file != null)
            files.add(file);
    }


    /**
     * Create a test file with given content if the content is not null.
     */
    File createFile(String path, String body) throws IOException {
        if (body == null)
            return null;
        File file = new File(srcDir, path);
        file.getAbsoluteFile().getParentFile().mkdirs();
        try (FileWriter out = new FileWriter(file)) {
            out.write(body);
        }
        return file;
    }

    /**
     * Set up empty src and classes directories for a test.
     */
    void reset() {
        resetDir(srcDir);
        resetDir(classesDir);
        resetDir(moduleDir);
        resetDir(new File(MNAME));
    }

    /**
     * Set up an empty directory.
     */
    void resetDir(File dir) {
        if (dir.exists())
            deleteAll(dir);
        dir.mkdirs();
    }

    /**
     * Delete a file or a directory (including all its contents).
     */
    boolean deleteAll(File file) {
        if (file.isDirectory()) {
            for (File f: file.listFiles())
                deleteAll(f);
        }
        return file.delete();
    }

    /**
     * Report an error.
     */
    void error(String msg, String... more) {
        System.err.println("error: " + msg);
        for (String s: more)
            System.err.println(s);
        errors++;
    }

    int count;
    int errors;
    File srcDir = new File("tmp", "src"); // use "tmp" to help avoid accidents
    File classesDir = new File("tmp", "classes");
    File moduleDir = new File("tmp", "modules");
    File natlibDir = new File(srcDir, "natlib");
    File natcmdDir = new File(srcDir, "natcmd");
    File configDir = new File(srcDir, "config");
}
