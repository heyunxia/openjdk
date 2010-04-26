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

/**
 * Test runner to check if ct.sym exists and then to execute a series
 * of simple jtreg-like compile commands.
 * If this is a modular JDK image (as determined by the presence of a
 * jigsaw module library), and if ct.sym does not exist, the test will
 * pass by default.   If this is not a modular JDK image and ct.sym
 * does not exist, the test will fail.
 *
 * The equivalent of @compile and @compile/fail are supported, with
 * the leading "@" removed. Filenames are assumed to be in the test.src
 * directory.
 */

public class Test {
    public static void main(String... args) throws Exception {
	
	File javaHome = new File(System.getProperty("java.home"));
	if (javaHome.getName().equals("jre"))
	    javaHome = javaHome.getParentFile();
	if (file(javaHome, "lib", "modules", "%jigsaw-library").exists()
		&& !file(javaHome, "lib", "ct.sym").exists()) {
	    System.err.println("PASS BY DEFAULT: modular JDK found with no ct.sym");
	    return;
	}
	   
	new Test().run(args);
    }

    void run(String... args) throws Exception {
	List<String> compileArgs = new ArrayList<String>();
	boolean expectFail = false;

	for (String arg: args) {
	    if (arg.equals("compile")) {
	        if (compileArgs.size() > 0) 
		    compile(compileArgs, expectFail);
		compileArgs.clear();
		expectFail = false;
	    } else if (arg.equals("compile/fail")) {
	        if (compileArgs.size() > 0) 
		    compile(compileArgs, expectFail);
		compileArgs.clear();
		expectFail = true;
	    } else {
		compileArgs.add(arg);
	    }
	}

        if (compileArgs.size() > 0) 
	    compile(compileArgs, expectFail);

	if (errors > 0) 
	    throw new Exception(errors + " errors found");
    } 

    void compile(List<String> args, boolean expectFail) {
	System.err.println("javac: " + args);

	File testSrc = new File(System.getProperty("test.src"));
	for (int i = 0; i < args.size(); i++) {
	    String arg = args.get(i);
	    if (arg.endsWith(".java"))
		args.set(i, new File(testSrc, arg).getPath());
	}	

	StringWriter sw = new StringWriter();
	PrintWriter pw = new PrintWriter(sw);
	int rc = com.sun.tools.javac.Main.compile(args.toArray(new String[args.size()]), pw);
	String out = sw.toString();
	System.err.println(out);
	if (expectFail && rc == 0)
	    error("compilation succeeded unexpectedly");
	else if (!expectFail && rc != 0)
	    error("compilation failed unexpectedly, rc=" + rc);
    }

    void error(String msg) {
	System.err.println("Error: " + msg);
	errors++;
    }

    int errors;

    static File file(File dir, String... path) {
	File f = dir;
	for (String p: path) 
	    f = new File(f, p);
	return f;
    }
}
