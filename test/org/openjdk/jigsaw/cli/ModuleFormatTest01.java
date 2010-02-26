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
 * @summary test module format writing/reading
 */

import java.io.*;
import java.util.*;
import org.openjdk.jigsaw.cli.*;
import org.openjdk.jigsaw.*;
import static org.openjdk.jigsaw.FileConstants.ModuleFile.SectionType;

public class ModuleFormatTest01 {
    final String MNAME = "hello";
    final String MVER = "0.1";
    String moduleinfo = "module " + MNAME + " @ " + MVER + " {}";
    String code = "public class World {}";
    String code2 = "public class Another {}";
    String resource = "Yo!";
    String resource2 = "Hey!";

    public static void main(String[] args) throws Exception {
        new ModuleFormatTest01().run();
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
	extract(MNAME, MVER);
	compare(MNAME);
    }

    void testSingleClassModule() throws Exception {
	System.err.println("Test: Single class module");
        count++;
        reset();
        List<File> files = new ArrayList<File>();
        addFile(files, createFile("module-info.java", moduleinfo));
        addFile(files, createFile("World.java", code));
        compile(files);
	compress(MNAME);
	extract(MNAME, MVER);
	compare(MNAME);
    }

    void testMultiClassModule() throws Exception {
	System.err.println("Test: Multiple class module");
        count++;
        reset();
        List<File> files = new ArrayList<File>();
        addFile(files, createFile("module-info.java", moduleinfo));
        addFile(files, createFile("World.java", code));
        addFile(files, createFile("Another.java", code2));
        compile(files);
	compress(MNAME);
	extract(MNAME, MVER);
	compare(MNAME);
    }

    void testSingleResourceModule() throws Exception {
	System.err.println("Test: Single resource module");
        count++;
        reset();
        List<File> files = new ArrayList<File>();
        addFile(files, createFile("module-info.java", moduleinfo));
        addFile(files, createFile("World.java", code));
        addFile(files, createFile("Another.java", code2));
        compile(files);
	createFile("resources" + File.separator + "yo", resource);
	compress(MNAME, true);
	extract(MNAME, MVER);
	compare(MNAME);
    }

    void testMultiResourceModule() throws Exception {
	System.err.println("Test: Multiple resource module");
        count++;
        reset();
        List<File> files = new ArrayList<File>();
        addFile(files, createFile("module-info.java", moduleinfo));
        addFile(files, createFile("World.java", code));
        addFile(files, createFile("Another.java", code2));
        compile(files);
	createFile("resources" + File.separator + "yo", resource);
	createFile("resources" + File.separator + "hey", resource2);
	compress(MNAME, true);
	extract(MNAME, MVER);
	compare(MNAME);
    }

    void testSingleNatLibModule() throws Exception {
	System.err.println("Test: Single native library module");
        count++;
        reset();
        List<File> files = new ArrayList<File>();
        addFile(files, createFile("module-info.java", moduleinfo));
        compile(files);
	createFile("natlib" + File.separator + "yo.so", resource);
	compress(MNAME, false, true);
	extract(MNAME, MVER);
	compare(MNAME);
    }

    void testMultiNatLibModule() throws Exception {
	System.err.println("Test: Multiple native library module");
        count++;
        reset();
        List<File> files = new ArrayList<File>();
        addFile(files, createFile("module-info.java", moduleinfo));
        compile(files);
	createFile("natlib" + File.separator + "yo.so", resource);
	createFile("natlib" + File.separator + "yo.dll", resource);
	compress(MNAME, false, true);
	extract(MNAME, MVER);
	compare(MNAME);
    }

    void testSingleNatCmdModule() throws Exception {
	System.err.println("Test: Single native command module");
        count++;
        reset();
        List<File> files = new ArrayList<File>();
        addFile(files, createFile("module-info.java", moduleinfo));
        compile(files);
	createFile("natcmd" + File.separator + "yo", resource);
	compress(MNAME, false, false, true);
	extract(MNAME, MVER);
	compare(MNAME);
    }

    void testMultiNatCmdModule() throws Exception {
	System.err.println("Test: Multi native command module");
        count++;
        reset();
        List<File> files = new ArrayList<File>();
        addFile(files, createFile("module-info.java", moduleinfo));
        compile(files);
	createFile("natcmd" + File.separator + "yo", resource);
	createFile("natcmd" + File.separator + "yo.exe", resource);
	compress(MNAME, false, false, true);
	extract(MNAME, MVER);
	compare(MNAME);
    }

    void testSingleConfigModule() throws Exception {
	System.err.println("Test: Single config module");
        count++;
        reset();
        List<File> files = new ArrayList<File>();
        addFile(files, createFile("module-info.java", moduleinfo));
        compile(files);
	createFile("config" + File.separator + "yo", resource);
	compress(MNAME, false, false, false, true);
	extract(MNAME, MVER);
	compare(MNAME);
    }

    void testMultiConfigModule() throws Exception {
	System.err.println("Test: Multi config module");
        count++;
        reset();
        List<File> files = new ArrayList<File>();
        addFile(files, createFile("module-info.java", moduleinfo));
        compile(files);
	createFile("config" + File.separator + "yo", resource);
	createFile("config" + File.separator + "hey", resource);
	compress(MNAME, false, false, false, true);
	extract(MNAME, MVER);
	compare(MNAME);
    }

    void testBloatedModule() throws Exception {
	System.err.println("Test: Bloated module");
        count++;
        reset();
        List<File> files = new ArrayList<File>();
        addFile(files, createFile("module-info.java", moduleinfo));
        addFile(files, createFile("World.java", code));
        addFile(files, createFile("Another.java", code2));
        compile(files);
	String largefile = "0";
	for (int i = 0; i < 10000; i++) {
	    largefile += i;
	}
	createFile("resources" + File.separator + "yo", largefile);
	createFile("resources" + File.separator + "hey", largefile);
	createFile("natlib" + File.separator + "yo.so", largefile);
	createFile("natlib" + File.separator + "yo.dll", largefile);
	createFile("natcmd" + File.separator + "yo", largefile);
	createFile("natcmd" + File.separator + "yo.exe", largefile);
	createFile("config" + File.separator + "yo", largefile);
	createFile("config" + File.separator + "hey", largefile);
	compress(MNAME, true, true, true, true);
	extract(MNAME, MVER);
	compare(MNAME);
    }

    void test() throws Exception {
	testEmptyModule();
	testSingleClassModule();
	testMultiClassModule();
	testSingleResourceModule();
	testMultiResourceModule();
	testSingleNatLibModule();
       	testMultiNatLibModule();
	testSingleNatCmdModule();
	testMultiNatCmdModule();
	testSingleConfigModule();
	testMultiConfigModule();
	testBloatedModule();
    }

    /**
     * Check if files are executable.
     */
    void checkIfExecutable(String [] fnames, File dir, SectionType type) 
	throws IOException {

	for (String fname : fnames) {
	    File file = new File(dir, fname);
	    if (file.exists() 
		&& (type == SectionType.NATIVE_CMDS
		    || (type == SectionType.NATIVE_LIBS
			&& System.getProperty("os.name").startsWith("Windows")))
		&& !file.canExecute())
		throw new IOException("file not marked executable " + file);
	}
    }

    /**
     * Compare an extracted module with original.
     */
    void compare(String [] fnames, File origDir, File copyDir) 
	throws IOException {
	for (String fname : fnames) {
	    File file = new File(origDir, fname);
	    File copy = null;

	    // Module-info class is extracted into info file.
	    if (fname.equals("module-info.class"))
		copy = new File(copyDir, "info");
	    else 
		copy = new File(copyDir, fname);

	    if (file.exists()) {
	        // System.out.println("Comparing " + file.toString());
		compare(file, copy);
	    }
	}
    }

    void compare(String module, String [] fnames, SectionType type) 
	throws IOException {
	File extractedDir = new File(module);
	String sectionDir =  ModuleFileFormat.getSubdirOfSection(type);
	File copyDir = new File(extractedDir, sectionDir);

	switch(type) {
	case MODULE_INFO:
	    compare(fnames, classesDir, copyDir);
	    break;
	case CLASSES:
	    compare(fnames, classesDir, copyDir);
	    break;
	case RESOURCES:
	    compare(fnames, resourceDir, copyDir);
	    break;
	case NATIVE_LIBS:
	    compare(fnames, natlibDir, copyDir);
	    checkIfExecutable(fnames, copyDir, type);
	    break;
	case NATIVE_CMDS:
	    compare(fnames, natcmdDir, copyDir);
	    checkIfExecutable(fnames, copyDir, type);
	    break;
	case CONFIG:
	    compare(fnames, configDir, copyDir);
	    break;
	}
    }

    void compare(String module) throws IOException {
	compare(module, new String [] {"module-info.class"}, 
		SectionType.MODULE_INFO);
	compare(module, new String [] {"World.class", "Another.class"}, 
		SectionType.CLASSES);
	compare(module, new String [] {"yo", "hey"}, 
		SectionType.RESOURCES);
	compare(module, new String [] {"yo.so", "yo.dll"}, 
		SectionType.NATIVE_LIBS);
	compare(module, new String [] {"yo", "yo.exe"}, 
		SectionType.NATIVE_CMDS);
	compare(module, new String [] {"yo", "hey"}, 
		SectionType.CONFIG);
    }

    /**
     * Compare two files for identity.
     */
	
    static void compare (File f1, File f2) throws IOException {
        InputStream i1 = new BufferedInputStream (new FileInputStream(f1));
        InputStream i2 = new BufferedInputStream (new FileInputStream(f2));

        int c1,c2;
        try {
            while ((c1=i1.read()) != -1) {
                c2 = i2.read();
                if (c1 != c2) {
                    throw new RuntimeException ("file compare failed 1");
                }
            }
            if (i2.read() != -1) {
                throw new RuntimeException ("file compare failed 2");
            }
        } finally {
            i1.close();
            i2.close();
        }
    }

    /**
     * Extract a module.
     */
    void extract(String name, String version) throws Exception {
	File module = new File(moduleDir, name + "@" + version + ".jmod"); 
	String [] args = {"extract", module.getAbsolutePath()};
	Librarian.main(args);	
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
