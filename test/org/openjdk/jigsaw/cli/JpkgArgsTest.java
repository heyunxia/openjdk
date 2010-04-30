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
 * @bug 6948144
 * @summary jpkg throws NPE if resource directory does not exist
 */

import java.io.*;
import java.security.*;
import java.util.*;
import org.openjdk.jigsaw.*;
import org.openjdk.jigsaw.cli.*;

public class JpkgArgsTest {
    final String MNAME = "hello";
    final String MVER = "0.1";
    String moduleinfo = "module " + MNAME + " @ " + MVER + " {}";
 
    public static void main(String[] args) throws Exception {
        new JpkgArgsTest().run();
    }

    private void run() throws Exception {
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

    private void setUp(String name) throws IOException {
	System.err.println("Test: " + name);
        count++;
        reset();
        List<File> files = new ArrayList<File>();
        addFile(files, createFile("module-info.java", moduleinfo));
        compile(files);
    }

    private void testResourceArg() throws Exception {
	setUp("NPE if resource directory does not exist");
	// try to compress a module pretending there is a resource dir
	// without creating it first should result in an exception
	try {
	    compress(MNAME, true);
	}
	// The bug resulted in an NPE being thrown
	catch (NullPointerException e) {
	    // Rethrow the NPE if it ever occurs again.
	    throw (Exception) new Exception().initCause(e);
	}
	// Technically, we want to catch Command.Exception here,
	// but it's package private, so let's catch the next best thing.
	catch (Exception e) {
	    // yay! test passed.
	}
    }

    private void testNatLibArg() throws Exception {
	setUp("NPE if natlib directory does not exist");
	// try to compress a module pretending there is a natlib dir
	// without creating it first should result in an exception
	try {
	    compress(MNAME, false, true);
	}
	// The bug resulted in an NPE being thrown
	catch (NullPointerException e) {
	    // Rethrow the NPE if it ever occurs again.
	    throw (Exception) new Exception().initCause(e);
	}
	// Technically, we want to catch Command.Exception here,
	// but it's package private, so let's catch the next best thing.
	catch (Exception e) {
	    // yay! test passed.
	}
    }

    private void test() throws Exception {
	testResourceArg();
	testNatLibArg();
    }

    /**
     * Compress a module.
     */
    private void compress(String name) throws Exception {
	compress(name, false);
    }

    private void compress(String name, boolean haveResources) throws Exception {
	compress(name, haveResources, false);
    }

    private void compress(String name, boolean haveResources, 
			  boolean haveNatLibs) throws Exception {
	compress(name, haveResources, haveNatLibs, false);
    }

    private void compress(String name, boolean haveResources,
			  boolean haveNatLibs, boolean haveNatCmds)
	throws Exception {
	compress(name, haveResources, haveNatLibs, haveNatCmds, false);
    }

    private void compress(String name, boolean haveResources,
			  boolean haveNatLibs, boolean haveNatCmds, 
			  boolean haveConfig) throws Exception {
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
	Packager.run(args.toArray(new String[0]));
    }

    /**
     * Compile a list of files.
     */
    private void compile(List<File> files) {
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
    private void addFile(List<File> files, File file) {
        if (file != null)
            files.add(file);
    }


    /**
     * Create a test file with given content if the content is not null.
     */
    private File createFile(String path, String body) throws IOException {
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
    private void reset() {
        resetDir(srcDir);
        resetDir(classesDir);
	resetDir(moduleDir);
	resetDir(new File(MNAME));
    }

    /**
     * Set up an empty directory.
     */
    private void resetDir(File dir) {
        if (dir.exists())
            deleteAll(dir);
        dir.mkdirs();
    }

    /**
     * Delete a file or a directory (including all its contents).
     */
    private boolean deleteAll(File file) {
        if (file.isDirectory()) {
            for (File f: file.listFiles())
                deleteAll(f);
        }
        return file.delete();
    }

    /**
     * Report an error.
     */
    private void error(String msg, String... more) {
        System.err.println("error: " + msg);
        for (String s: more)
            System.err.println(s);
        errors++;
    }

    private int count;
    private int errors;
    // use "tmp" to help avoid accidents
    private File srcDir = new File("tmp", "src"); 
    private File classesDir = new File("tmp", "classes");
    private File moduleDir = new File("tmp", "modules");
    private File resourceDir = new File(srcDir, "resources");
    private File natlibDir = new File(srcDir, "natlib");
    private File natcmdDir = new File(srcDir, "natcmd");
    private File configDir = new File(srcDir, "config");
}
