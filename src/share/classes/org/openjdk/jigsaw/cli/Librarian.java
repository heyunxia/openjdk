/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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

package org.openjdk.jigsaw.cli;

import java.lang.module.*;
import java.io.*;
import java.util.*;

import static java.lang.System.out;
import static java.lang.System.err;

import org.openjdk.jigsaw.*;
import org.openjdk.internal.joptsimple.*;


public class Librarian {

    private static void formatCommaList(PrintStream out,
					String prefix, Collection<?> list)
    {
	if (list.isEmpty())
	    return;
	out.format("  %s", prefix);
	boolean first = true;
	for (Object ob : list) {
	    if (first) {
		out.format(" %s", ob);
		first = false;
	    } else {
		out.format(", %s", ob);
	    }
	}
	out.format("%n");
    }

    public static class Create extends Command<Library> {
	protected void go(Library lib) { }
    }

    public static class Identify extends Command<Library> {
	protected void go(Library lib)
	    throws Command.Exception
	{
	    finishArgs();
	    try {
		out.format("path %s%n", lib.path().getCanonicalPath());
		out.format("version %d.%d%n",
			   lib.majorVersion(), lib.minorVersion());
	    } catch (IOException x) {
		throw new Command.Exception(x);
	    }
	}
    }

    public static class Install extends Command<Library> {
	protected void go(Library lib)
	    throws Command.Exception
	{
	    File classes = new File(takeArg());
	    String mn = takeArg();
	    finishArgs();
	    try {
		lib.install(classes, mn);
	    } catch (IOException x) {
		throw new Command.Exception(x);
	    }
	}
    }

    public static class List extends Command<Library> {
	protected void go(Library lib)
	    throws Command.Exception
	{
	    final int[] n = new int[1];
	    finishArgs();
	    try {
		lib.visitModules(new Library.ModuleVisitor() {
			public void accept(ModuleInfo mi) {
			    if (verbose)
				out.format("%n");
			    out.format("%s%n", mi.id());
			    n[0]++;
			    if (verbose) {
				formatCommaList(out, "provides", mi.provides());
				for (Dependence d : mi.requires())
				    out.format("  %s%n", d);
				formatCommaList(out, "permits", mi.permits());
			    }
			}
		    });
	    } catch (IOException x) {
		throw new Command.Exception(x);
	    }
	    if (verbose && n[0] > 0)
		out.format("%n");
	}
    }


    private static Map<String,Class<? extends Command<Library>>> commands
        = new HashMap<String,Class<? extends Command<Library>>>();

    static {
	commands.put("create", Create.class);
	commands.put("id", Identify.class);
	commands.put("identify", Identify.class);
	commands.put("install", Install.class);
	commands.put("list", List.class);
	commands.put("ls", List.class);
    }


    private OptionParser parser;

    private void usage() {
	out.format("%n");
	out.format("usage: jlib [-L <path>] create%n");
	out.format("       jlib [-L <path>] install <classes> <module-id>%n");
	out.format("       jlib [-L <path>] list [-v]%n");
	out.format("%n");
	try {
	    parser.printHelpOn(out);
	} catch (IOException x) {
	    throw new AssertionError(x);
	}
	out.format("%n");
	System.exit(0);
    }

    private void run(String[] args) {

	parser = new OptionParser();

	OptionSpec<File> libPath
	    = (parser.acceptsAll(Arrays.asList("L", "library"),
				 "Module-library location (default $JAVA_MODULES)")
	       .withRequiredArg()
	       .describedAs("path")
	       .ofType(File.class));
	parser.acceptsAll(Arrays.asList("v", "verbose"),
			  "Enable verbose output");
	parser.acceptsAll(Arrays.asList("h", "?", "help"),
			  "Show this help message");

	if (args.length == 0)
	    usage();

	try {
	    OptionSet opts = parser.parse(args);
	    if (opts.has("h"))
		usage();
	    java.util.List<String> words = opts.nonOptionArguments();
	    if (words.isEmpty())
		usage();
	    String verb = words.get(0);
	    Class<? extends Command<Library>> cmd = commands.get(verb);
	    if (cmd == null)
		throw new Command.Exception("%s: unknown command", verb);
	    File lp = null;
	    if (opts.has(libPath)) {
		lp = opts.valueOf(libPath);
	    } else {
		String jm = System.getenv("JAVA_MODULES");
		if (jm == null)
		    throw new Command.Exception("No module library specified");
		lp = new File(jm);
	    }
	    Library lib = null;
	    try {
		lib = Library.open(lp, verb.equals("create"));
	    } catch (FileNotFoundException x) {
		throw new Command.Exception("%s: No such library", lp);
	    } catch (IOException x) {
		throw new Command.Exception(x);
	    }
	    try {
		cmd.newInstance().run(lib, opts);
	    } catch (InstantiationException x) {
		throw new AssertionError(x);
	    } catch (IllegalAccessException x) {
		throw new AssertionError(x);
	    }
	} catch (OptionException x) {
	    err.println(x.getMessage());
	    System.exit(1);
	} catch (Command.Exception x) {
	    err.println(x.getMessage());
	    System.exit(1);
	}

    }

    private Librarian() { }

    public static void main(String[] args) throws Exception {
	new Librarian().run(args);
    }

}
