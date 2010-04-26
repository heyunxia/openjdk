/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @key jigsaw
 * @bug 6802521
 * @summary add support for modules: test path combinations
 */

import java.io.*;
import java.util.*;

public class ModulePathTest02 {
    enum PathKind { 
	NONE, 
	LOCAL_PKGS, 
	OTHER_PKGS, 
	ALL_PKGS;
	boolean local() { return (this == LOCAL_PKGS || this == ALL_PKGS); }
	boolean other() { return (this == OTHER_PKGS || this == ALL_PKGS); }
    };
    enum LibraryKind { NO, YES };

    public static void main(String... args) throws Exception {
	try {
	    getJMod();
	} catch (IllegalStateException e) {
	    System.err.println("PASS BY DEFAULT: jmod not available");
	    return;
	}

        new ModulePathTest02().run();
    }

    public void run() throws Exception {
	setup();

        for (PathKind cpk: PathKind.values()) {
            for (PathKind spk: PathKind.values()) {
                for (PathKind mpk: PathKind.values()) {
		    for (LibraryKind lk: LibraryKind.values()) {
			try {
                            test(cpk, spk, mpk, lk);
		        } catch (Exception e) {
			    error("Error: " + e);
			    throw e;
		        }
		    }
                }
            }
        }

        if (errors == 0)
            System.out.println(count + " tests passed");
        else
            throw new Exception(errors + "/" + count + " tests failed");
    }

    void setup() throws Exception {
        File tmpSrcDir = new File("tmp/src");
        File tmpModulesDir = new File("tmp/modules");

	// setup and compile files for library module
	File f1 = createFile(true, tmpSrcDir, "m5", "m5p1", "m5p1C1");
        File f2 = new File(tmpSrcDir, "m5/module-info.java");
	writeFile(f2, "module m5@1.0 { }\n");
	compile(true, tmpModulesDir, f1, f2);
	// create library
        libDir = new File("lib");
	jmod(libDir, "create");
	jmod(libDir, "install", tmpModulesDir.getPath(), "m5");
	libClass = f1; 
    }

    void test(PathKind cpk, PathKind spk, PathKind mpk, LibraryKind lk) throws Exception {
	boolean multiModuleMode = (mpk != PathKind.NONE && cpk == PathKind.NONE);
	if (!multiModuleMode && (mpk.local() || spk.other())) {
	    // invalid combinations: in javac single module mode, we can't use
	    // modulepath for local module or sourcepath for other modules
	    return;
	}

        System.err.println("Test " + (++count) 
		+ ": classPathKind " + cpk 
		+ ": sourcePathKind " + spk 
		+ ": modulePathKind " + mpk
		+ ": libraryKind " + lk);
	System.err.println("Mode: " + (multiModuleMode ? "MULTIPLE" : "SINGLE"));

        File testDir = new File("test" + count);
        File srcDir = new File(testDir, "src");
        File classesDir = new File(testDir, "classes");
        File libSrcDir = new File(testDir, "lib/src");
        File libClassesDir = new File(testDir, "lib/classes");
        File modulesDir = new File(testDir, "modules");
        File tmpSrcDir = new File(testDir, "tmp/src");

	Set<File> classpath = new LinkedHashSet<File>();
	Set<File> sourcepath = new LinkedHashSet<File>();
	Set<File> modulepath = new LinkedHashSet<File>();

	File outDir = multiModuleMode ? modulesDir : classesDir;
	outDir.mkdirs();

	// initialize args for compilation
	List<String> args = new ArrayList<String>();
	append(args, "-d", outDir.getPath());

        // init files
	List<File> mainRefs = new ArrayList<File>();
	List<String> mainRequires = new ArrayList<String>();

	// if class path specified, it must be single module mode
        if (cpk.local()) {
	    File f = createFile(multiModuleMode, tmpSrcDir, "m1", "m1p1", "m1p1C1");
	    compile(multiModuleMode, outDir, f);
  	    mainRefs.add(f);
	    classpath.add(outDir);
        }
	if (cpk.other()) {
	    File f = createFile(multiModuleMode, tmpSrcDir, "m2", "m2p1", "m2p1C1");
	    compile(multiModuleMode, libClassesDir, f);
  	    mainRefs.add(f);
	    classpath.add(libClassesDir);
	}

        if (spk.local()) {
	    File f = createFile(multiModuleMode, srcDir, "m1", "m1p1", "m1p1C1");
  	    mainRefs.add(f);
	    sourcepath.add(srcDir);
        }
	if (spk.other()) {
	    assert multiModuleMode; // see code at top of method to skip case if !multiModuleMode
	    File f1 = createFile(multiModuleMode, libSrcDir, "m3", "m3p1", "m3p1C1");
	    File f2 = new File(libSrcDir, "m3/module-info.java");
	    writeFile(f2, "module m3@1.0 { }\n");
  	    mainRefs.add(f1);
	    mainRequires.add("m3");
	    sourcepath.add(libSrcDir);
	}

        if (mpk.local()) {
	    assert multiModuleMode; // see code at top of method to skip case if !multiModuleMode
	    File f = createFile(multiModuleMode, tmpSrcDir, "m1", "m1p1", "m1p1C1");
	    compile(multiModuleMode, outDir, f);
  	    mainRefs.add(f);
	    modulepath.add(outDir);
        }
	if (mpk.other()) {
	    File f1 = createFile(true, tmpSrcDir, "m4", "m4p1", "m4p1C1");
	    File f2 = new File(tmpSrcDir, "m4/module-info.java");
	    writeFile(f2, "module m4@1.0 { }\n");
	    compile(true, modulesDir, f1, f2);
  	    mainRefs.add(f1);
	    mainRequires.add("m4");
	    modulepath.add(modulesDir);
	}

        if (lk == LibraryKind.YES) {
  	    mainRefs.add(libClass);
	    mainRequires.add("m5");
	    append(args, "-L", libDir.getPath());
	}

	StringBuilder mainBody = new StringBuilder();
	for (File f: mainRefs) {
	    String pkgName = f.getParentFile().getName();
	    String className = f.getName().replace(".java", "");
	    mainBody.append("import " + pkgName + "." + className + ";\n");
	}
	mainBody.append("public class Main {\n");
	mainBody.append("    public static void main(String... args) {\n");
	for (File f: mainRefs) {
	    String className = f.getName().replace(".java", "");
	    mainBody.append("        " + className + ".m();\n");
	}
	mainBody.append("    }\n");
	mainBody.append("}\n");

	List<File> filesToCompile = new ArrayList<File>();
        File mainDir = multiModuleMode ? new File(srcDir, "m1") : testDir;
	File main = new File(mainDir, "Main.java");
	writeFile(main, mainBody.toString());
	filesToCompile.add(main);

	if (multiModuleMode || mainRequires.size() > 0) {	
	    StringBuilder moduleInfoBody = new StringBuilder();
	    moduleInfoBody.append("module m1@1.0 {\n");
	    for (String r: mainRequires)
	        moduleInfoBody.append("    requires " + r + ";\n");
	    moduleInfoBody.append("}\n");
	    File moduleInfo = new File(mainDir, "module-info.java");
	    writeFile(moduleInfo, moduleInfoBody.toString());
	    filesToCompile.add(moduleInfo); 
	}

	addPath(args, "-classpath",  classpath);
	addPath(args, "-sourcepath", sourcepath);
	addPath(args, "-modulepath", modulepath);

	// run the test compilation
	javac(args, filesToCompile);

    }

    File createFile(boolean multiModuleMode, File dir, String moduleName, String packageName, String className) 
		throws IOException {
	File f = new File(new File((multiModuleMode ? new File(dir, moduleName) : dir), packageName), className + ".java");
	String body = 
	    "package " + packageName + ";\n"
	    + "public class " + className + " {\n"
	    + "    public static void m() {\n"
	    + "        System.out.println(\"" + className + "\");\n"
	    + "    }\n"
  	    + "}\n";
	writeFile(f, body);
	return f;
    }

    void writeFile(File f, String body) throws IOException {
	if (f.getParentFile() != null)
	    f.getParentFile().mkdirs();
	Writer out = new FileWriter(f);
	try {
	    out.write(body);
	} finally {
	    out.close();
	}
    }

    void addReference(StringBuilder sb, File f) {
	String className = f.getName().replaceAll(".java", "");
	sb.append("        " + className + ".m();\n");
    }

    void addPath(List<String> args, String pathOption, Set<File> pathValue) {
	if (pathValue.size() > 0) {
	    StringBuilder sb = new StringBuilder();
	    for (File f: pathValue) {
	    	if (sb.length() > 0)
		    sb.append(File.pathSeparator);
		sb.append(f.getPath());
	    }
	    args.add(pathOption);
	    args.add(sb.toString());
	}
    }

    void compile(boolean multiModuleMode, File outDir, File... srcFiles) throws Exception {
	outDir.mkdirs();
	List<String> args = new ArrayList<String>();
	append(args, "-d", outDir.getPath());
	append(args, (multiModuleMode ? "-modulepath" : "-classpath"), outDir.getPath());
	javac(args, Arrays.asList(srcFiles));
    }

    void javac(List<String> opts, List<File> files) throws Exception {
	System.err.println(("javac: " + opts + ", " + files).replace(userDir + File.separator, ""));
	final List<String> args = new ArrayList<String>();
	args.addAll(opts);
	for (File f: files)
	    args.add(f.getPath());

	int rc;
	StringWriter sw = new StringWriter();
	PrintWriter pw = new PrintWriter(sw);
	ByteArrayOutputStream bos = new ByteArrayOutputStream();
	PrintStream ps = new PrintStream(bos);
	PrintStream prevOut = System.out;
	PrintStream prevErr = System.err;
	try {
	    System.setOut(ps);
	    System.setErr(ps);
	    rc = com.sun.tools.javac.Main.compile(args.toArray(new String[args.size()]), pw);
	} finally {
	    pw.close();
	    ps.close();
	    System.setOut(prevOut);
	    System.setErr(prevErr);
	}
	String out = sw.toString() + bos.toString();
	if (rc != 0) {
	    System.err.println(out);
	    throw new Exception("compilation failed: rc=" + rc);
        }
	if (out.length() > 0) {
	    System.err.println(out);
	    throw new Exception("unexpected output from compiler");
        }
    }

    void jmod(File libDir, String... args) throws Exception {
	File jmod = getJMod();
	List<String> cmd = new ArrayList<String>();
	cmd.add(jmod.getPath());
	cmd.add("-L");
	cmd.add(libDir.getPath());
	cmd.addAll(Arrays.asList(args));
	System.err.println(("jmod: " + cmd.subList(1, cmd.size())).replace(userDir + File.separator, ""));	
	Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
	byte[] buf = new byte[4096];
        int offset = 0;
	int n;
	while (offset < buf.length && (n = p.getInputStream().read(buf, offset, buf.length - offset)) > 0)
	    offset += n;
	int rc = p.waitFor();
	String out = new String(buf, 0, offset);
	if (rc != 0) {
	    System.err.println(out);
	    throw new Exception("jmod failed: rc=" + rc);
        }
	if (out.length() > 0) {
	    System.err.println(out);
	    throw new Exception("unexpected output from jmod");
        }
    }

    <T> void append(List<T> list, T... items) {
        list.addAll(Arrays.asList(items));
    }

    /**
     * Set up empty directories.
     */
    void resetDirs(File... dirs) {
        for (File dir: dirs) {
            if (dir.exists())
                deleteAll(dir);
            dir.mkdirs();
        }
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
        //throw new Error(msg);
    }

    static File getJMod() throws IllegalStateException {
	boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
	File javaHome = new File(System.getProperty("java.home"));
	if (javaHome.getName().equals("jre"))
	    javaHome = javaHome.getParentFile();
	File jmod = new File(new File(javaHome, "bin"), "jmod" + (isWindows ? ".exe" : ""));
	if (!jmod.exists())
	    throw new IllegalStateException("jmod not available");
	return jmod;
    }

    File libDir;
    File libClass;
    int count;
    int errors;
    static final String userDir = System.getProperty("user.dir");
}
