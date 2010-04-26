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
 * @bug 6729471
 * @summary javap does not output inner interfaces of an interface
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

public class T6729471
{
    public static void main(String... args) throws Exception {
        new T6729471().run();
    }

    void run() throws Exception {
        // simple class
        verify("java.util.Map",
                "public abstract boolean containsKey(java.lang.Object)");

        // inner class
        verify("java.util.Map.Entry",
                "public abstract K getKey()");

        // file name
        verify("../classes/tools/javap/T6729471.class",
                "public static void main(java.lang.String...)");

        // file url
        verify("file:../classes/tools/javap/T6729471.class",
                "public static void main(java.lang.String...)");

        // jar url: local jar
	File my_jar = createJar("my.jar", getClasses(Map.class));
        try {
            verify("jar:" + my_jar.toURL() + "!/java/util/Map.class",
                "public abstract boolean containsKey(java.lang.Object)");
        } catch (MalformedURLException e) {
            error(e.toString());
        }

        // jar url: rt.jar
        File java_home = new File(System.getProperty("java.home"));
        if (java_home.getName().equals("jre"))
            java_home = java_home.getParentFile();
        File rt_jar = new File(new File(new File(java_home, "jre"), "lib"), "rt.jar");
	if (rt_jar.exists()) {
            try {
                verify("jar:" + rt_jar.toURL() + "!/java/util/Map.class",
                    "public abstract boolean containsKey(java.lang.Object)");
            } catch (MalformedURLException e) {
                error(e.toString());
            }
	} else {
	    System.err.println("warning: rt.jar not found; test skipped");
	}

        // jar url: ct.sym, if it exists
        File ct_sym = new File(new File(java_home, "lib"), "ct.sym");
        if (ct_sym.exists()) {
            try {
                verify("jar:" + ct_sym.toURL() + "!/META-INF/sym/rt.jar/java/util/Map.class",
                    "public abstract boolean containsKey(java.lang.Object)");
            } catch (MalformedURLException e) {
                error(e.toString());
            }
        } else {
            System.err.println("warning: ct.sym not found; test skipped");
	}

        if (errors > 0)
            throw new Error(errors + " found.");
    }

    void verify(String className, String... expects) {
        String output = javap(className);
        for (String expect: expects) {
            if (output.indexOf(expect)< 0)
                error(expect + " not found");
        }
    }

    void error(String msg) {
        System.err.println(msg);
        errors++;
    }

    int errors;

    String javap(String className) {
        String testClasses = System.getProperty("test.classes", ".");
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        String[] args = { "-classpath", testClasses, className };
        int rc = com.sun.tools.javap.Main.run(args, out);
        out.close();
        String output = sw.toString();
        System.out.println("class " + className);
        System.out.println(output);
        if (rc != 0)
            throw new Error("javap failed. rc=" + rc);
        if (output.indexOf("Error:") != -1)
            throw new Error("javap reported error.");
        return output;
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
}

