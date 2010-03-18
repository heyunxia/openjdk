/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @summary test reading of module files with left over bytes at the end.
 */

import java.io.*;
import java.util.*;
import org.openjdk.jigsaw.*;
import org.openjdk.jigsaw.cli.*;

public class ModuleFormatTestLeftOverBytes {
    final String MNAME = "hello";
    final String MVER = "0.1";
    String moduleinfo = "module " + MNAME + " @ " + MVER + " {}";
 
    public static void main(String[] args) throws Exception {
        new ModuleFormatTestLeftOverBytes().run();
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
	append(MNAME, MVER, "These bytes are not made for parsing!");
	try {
	    extract(MNAME, MVER);
	} catch (Exception e) {
	    /* swallow expected IOException */
	    if (! e.getMessage().startsWith("I/O error:"))
		throw e;
	}
    }

    void test() throws Exception {
	testEmptyModule();
    }

    /**
     * Extract a module.
     */
    void extract(String name, String version) throws Exception {
	File module = new File(moduleDir, name + "@" + version + ".jmod"); 
	String [] args = {"extract", module.getAbsolutePath()};
	Librarian.run(args);
    }

    /**
     * Append some content at the end of a module file.
     */
    void append(String name, String version, String content) throws Exception {
        String fname = moduleDir + File.separator + name + "@" + version + ".jmod";
        RandomAccessFile module = new RandomAccessFile(fname, "rw");
        module.seek(module.length());
        module.writeUTF(content);
        module.close();
    }

    /**
     * Compress a module.
     */
    void compress(String name) throws Exception {
	compress(name, false);
    }

    void compress(String name, boolean haveResources) throws Exception {
	compress(name, haveResources, false);
    }

    void compress(String name, boolean haveResources, boolean haveNatLibs) 
	throws Exception {
	compress(name, haveResources, haveNatLibs, false);
    }

    void compress(String name, boolean haveResources, boolean haveNatLibs,
		  boolean haveNatCmds) throws Exception {
	compress(name, haveResources, haveNatLibs, haveNatCmds, false);
    }

    void compress(String name, boolean haveResources, boolean haveNatLibs,
		  boolean haveNatCmds, boolean haveConfig) 
	throws Exception {
	List<String> args = new ArrayList<String>();
	args.add("-m");
	args.add(classesDir.getAbsolutePath()); 
	args.add("-d"); 
	args.add(moduleDir.getAbsolutePath());
	if (haveResources) {
	    args.add("-r");
	    args.add(resourceDir.toString());
	}
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
        options.addAll(Arrays.asList("-source", "7", "-d", classesDir.getPath()));
        for (File f: files)
            options.add(f.getPath());

        String[] opts = options.toArray(new String[options.size()]);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        int rc = com.sun.tools.javac.Main.compile(opts, pw);
        pw.close();

        String out = sw.toString();
        if (out.trim().length() > 0)
            System.err.println(out);
        if (rc != 0)
            throw new Error("compilation failed: rc=" + rc);
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
        FileWriter out = new FileWriter(file);
        out.write(body);
        out.close();
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
    File resourceDir = new File(srcDir, "resources");
    File natlibDir = new File(srcDir, "natlib");
    File natcmdDir = new File(srcDir, "natcmd");
    File configDir = new File(srcDir, "config");
}
