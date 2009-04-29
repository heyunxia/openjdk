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
import java.util.regex.*;

import static java.lang.System.out;
import static java.lang.System.err;

import org.openjdk.jigsaw.*;
import org.openjdk.internal.joptsimple.*;


public class Librarian {

    private static JigsawModuleSystem jms
	= JigsawModuleSystem.instance();

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
	protected void go(Library lib)
	    throws Command.Exception
	{
	    finishArgs();
	}
    }

    public static class Dump extends Command<Library> {
	protected void go(Library lib)
	    throws Command.Exception
	{
	    String mids = takeArg();
	    ModuleId mid = null;
	    try {
		mid = jms.parseModuleId(mids);
	    } catch (IllegalArgumentException x) {
		throw new Command.Exception(x.getMessage());
	    }
	    String cn = takeArg();
	    finishArgs();
	    byte[] bs = null;
	    try {
		bs = lib.readClass(mid, cn);
	    } catch (IllegalArgumentException x) {
		throw new Command.Exception(x.getMessage());
	    } catch (IOException x) {
		throw new Command.Exception(x);
	    }
	    if (bs == null)
		throw new Command.Exception("%s: No such class in module %s",
					    cn, mid);
	    int n = bs.length;
	    for (int i = 0; i < n;) {
		int d = Math.min(n - i, 8192);
		out.write(bs, i, d);
		i += d;
	    }
	}
    }

    public static class Identify extends Command<Library> {
	protected void go(Library lib)
	    throws Command.Exception
	{
	    finishArgs();
	    try {
		out.format("path %s%n", new File(lib.name()).getCanonicalPath());
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
	    java.util.List<String> mns = new ArrayList<String>();
	    mns.add(takeArg());
	    while (hasArg())
		mns.add(takeArg());
	    finishArgs();
	    try {
		lib.install(classes, mns);
	    } catch (ConfigurationException x) {
		throw new Command.Exception(x);
	    } catch (IOException x) {
		throw new Command.Exception(x);
	    }
	}
    }

    private static Pattern MIDQ_PATTERN
	= Pattern.compile("([a-zA-Z0-9_\\.]+)(@(.*))?");

    private static ModuleIdQuery parseModuleIdQuery(String s)
	throws Command.Exception
    {
	Matcher m = MIDQ_PATTERN.matcher(s);
	if (!m.matches())
	    throw new Command.Exception("%s: Malformed module-id query", s);
	String vq = (m.group(3) != null) ? m.group(3) : null;
	return new ModuleIdQuery(m.group(1),
				 jms.parseVersionQuery(vq));
    }

    public static class List extends Command<Library> {
	protected void go(Library lib)
	    throws Command.Exception
	{
	    final ModuleIdQuery midq;
	    if (hasArg())
		midq = parseModuleIdQuery(takeArg());
	    else
		midq = null;
	    finishArgs();
	    final int[] n = new int[1];
	    try {
		lib.visitModules(new Library.ModuleInfoVisitor() {
			public void accept(ModuleInfo mi) {
			    if (midq != null && !midq.matches(mi.id()))
				return;
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

    public static class Config extends Command<Library> {
	protected void go(Library lib)
	    throws Command.Exception
	{
	    String mids = takeArg();
	    ModuleId mid = null;
	    try {
		mid = jms.parseModuleId(mids);
	    } catch (IllegalArgumentException x) {
		throw new Command.Exception(x.getMessage());
	    }
	    finishArgs();
	    try {
		Configuration cf = lib.readConfiguration(mid);
		if (cf == null)
		    throw new Command.Exception(mid + ": No such module");
		cf.dump(out);
	    } catch (IOException x) {
		throw new Command.Exception(x);
	    }
	}
    }


    private static Map<String,Class<? extends Command<Library>>> commands
        = new HashMap<String,Class<? extends Command<Library>>>();

    static {
	commands.put("cf", Config.class);
	commands.put("config", Config.class);
	commands.put("create", Create.class);
	commands.put("dump", Dump.class);
	commands.put("id", Identify.class);
	commands.put("identify", Identify.class);
	commands.put("install", Install.class);
	commands.put("list", List.class);
	commands.put("ls", List.class);
    }


    private OptionParser parser;

    private void usage() {
	out.format("%n");
	out.format("usage: jmod [-L <path>] config <module-id>%n");
	out.format("       jmod [-L <path>] create%n");
	out.format("       jmod [-L <path>] dump <module-id> <class-name>%n");
	out.format("       jmod [-L <path>] identify%n");
	out.format("       jmod [-L <path>] install <classes-dir> <module-name>%n");
	out.format("       jmod [-L <path>] list [-v] [<module-id-query>]%n");
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
		lib = SimpleLibrary.open(lp, verb.equals("create"));
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
