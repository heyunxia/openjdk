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

import java.io.*;
import java.util.*;

/*
 * TestRunner provides a framework for tests to set up parameters for
 * a compilation, such as options, paths and files, and then to have
 * that compilation executed with different module resolution modes:
 * primarily ZeroMod and Jigsaw, providing they're available.
 */ 
public class TestRunner {
    enum ModuleResolutionMode {
	JIGSAW,
//	JSR294RI, // not yet supported in javac
	ZEROMOD("-XDzeroMod"),
	NONE("-XDnomodules");

	ModuleResolutionMode() {
	    this(null);
	}

	ModuleResolutionMode(String opt) {
	    this.opt = opt;
	}

	final String opt;
    };

    enum ModuleCompilationMode {
	NO_MODULES,
	SINGLE_MODULE,
	MULTI_MODULE
    };

    protected TestRunner() { 
	srcDir = new File("src");

	supportedModuleResolutionModes = EnumSet.of(ModuleResolutionMode.NONE);
	File javaHome = new File(System.getProperty("java.home"));
	if (javaHome.getName().equals("jre")) javaHome = javaHome.getParentFile();
	if (file(javaHome, "lib", "modules", "%jigsaw-library").exists())
	    supportedModuleResolutionModes.add(ModuleResolutionMode.JIGSAW);
	try {
	    Class.forName("com.sun.tools.javac.comp.Modules");
	    supportedModuleResolutionModes.add(ModuleResolutionMode.ZEROMOD);
	} catch (Exception e) {
	}
    }

    void setCommandLineFiles(File... files) {
	cmdLineFiles = Arrays.asList(files);
    }

    void setExpectedClasses(String... names) {
	expectedClassNames = Arrays.asList(names);
    }

    void setModuleCompilationMode(ModuleCompilationMode mcm) {
	this.mcm = mcm;
    }

    void setClassPath(File... files) {
	classPath = Arrays.asList(files);
    }

    void setModulePath(File... files) {
	modulePath = Arrays.asList(files);
    }

    void setSourcePath(File... files) {
	sourcePath = Arrays.asList(files);
    }

    void test() {
	for (ModuleResolutionMode mrm: ModuleResolutionMode.values()) {
	    try {
	        test(mrm);
	    } catch (Exception e) {
		error(e.toString());
	    }
	}
    }

    void test(ModuleResolutionMode mrm) throws Exception {
	if (!supportedModuleResolutionModes.contains(mrm)) {
	    skip("Module resolution mode " + mrm + " not supported");
	    return;
	}
	if (mrm == ModuleResolutionMode.NONE && mcm != ModuleCompilationMode.NO_MODULES) {
	    skip("Module resolution mode " + mrm + " not applicable for " + mcm);
	    return;
	}
	
        out.println("Test " + (++count) + ": module resolution mode " + mrm + ": module compilation mode " + mcm);

	List<String> args = new ArrayList<String>();
	if (mrm.opt != null)
	    args.add(mrm.opt);

	File classesDir = new File("classes." + count + "." + mrm);
	classesDir.mkdirs();
	args.add("-d");
	args.add(classesDir.getPath());

	if (classPath != null && !classPath.isEmpty()) {
	    assert mcm != ModuleCompilationMode.MULTI_MODULE;
	    // note class output directory not added to classpath
	    args.add("-classpath");
	    args.add(filesToPath(classPath));
        }

	if (modulePath != null && !modulePath.isEmpty()) {
	    // note class output directory not added to modulepath
	    args.add("-modulepath");
	    args.add(filesToPath(modulePath));
        } else {
	    assert mcm != ModuleCompilationMode.MULTI_MODULE;
	}

	if (sourcePath != null && !sourcePath.isEmpty()) {
	    args.add("-sourcepath");
	    args.add(filesToPath(sourcePath));
        }
	
	for (File file: cmdLineFiles) 
	    args.add(file.getPath());

        compile(args);

	// check class files
	for (String c: expectedClassNames) {
	    File cf = new File(classesDir, c.replace(".", "/") + ".class");
	    if (cf.exists())
		out.println("class " + c + " found as expected");
	    else
		error("class " + c + " not found");
	}

	out.println();
    }

    void compile(File dir, File... files) throws Exception {
	List<String> args = new ArrayList<String>();
	args.add("-d");
	args.add(dir.getPath());
	for (File file: files) 
	    args.add(file.getPath());
        compile(args);
    }

    void compile(List<String> args) throws Exception {
	out.println("compile: " + args);

	StringWriter sw = new StringWriter();
	PrintWriter pw = new PrintWriter(sw);
	int rc = com.sun.tools.javac.Main.compile(args.toArray(new String[args.size()]), pw);
	pw.close();
	String javac_out = sw.toString();
	if (!javac_out.isEmpty())
	    out.println(javac_out);
	if (rc != 0)
	    throw new Exception("unexpected exit from javac: " + rc);
    }

    File createFile(String path, String... lines) throws IOException {
        File f = new File(srcDir, path); // subdir?
	f.getParentFile().mkdirs();
        BufferedWriter out = new BufferedWriter(new FileWriter(f));
	try {
	    for (String line: lines) {
		out.write(line);
		out.newLine();
	    }
	} finally {
	    out.close();
	}
	return f;
    }

    File file(File dir, String... path) {
	File f = dir;
	for (String p: path) 
	    f = new File(f, p);
	return f;
    }

    String filesToPath(List<File> files) {
	StringBuilder sb = new StringBuilder();
	for (File f: files) {
	    if (sb.length() > 0)
	 	sb.append(File.pathSeparator);
	    sb.append(f.getPath());
	}
	return sb.toString();
    }

    void skip(String reason) {
        out.println("Skip: " + reason);
	out.println();
    }

    /**
     * Report an error.
     */
    void error(String msg, String... more) {
        out.println("error: " + msg);
        for (String s: more)
            out.println(s);
        errors++;
    }

    void summary() throws Exception {
        if (errors == 0) {
            out.println(count + " tests passed");
        } else {
            throw new Exception(errors + "/" + count + " tests failed");
	}
    }

    Set<ModuleResolutionMode> supportedModuleResolutionModes;
    File srcDir;
    File classesDir;
    List<File> sourcePath;
    List<File> classPath;
    List<File> modulePath;
    List<File> cmdLineFiles;
    List<String> expectedClassNames;
    ModuleCompilationMode mcm;
    int errors;
    int count;

    static PrintStream out = System.out;
}
