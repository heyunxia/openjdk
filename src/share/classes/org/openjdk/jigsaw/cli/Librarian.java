/*
 * Copyright 2009-2010 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.net.*;
import java.util.*;
import java.util.regex.*;

import static java.lang.System.out;
import static java.lang.System.err;

import org.openjdk.jigsaw.*;
import org.openjdk.internal.joptsimple.*;


public class Librarian {

    private static JigsawModuleSystem jms
        = JigsawModuleSystem.instance();

    static class Create extends Command<SimpleLibrary> {
        protected void go(SimpleLibrary lib)
            throws Command.Exception
        {
            noDry();
            finishArgs();
        }
    }

    static class DumpClass extends Command<SimpleLibrary> {
        protected void go(SimpleLibrary lib)
            throws Command.Exception
        {
            noDry();
            String mids = takeArg();
            ModuleId mid = null;
            try {
                mid = jms.parseModuleId(mids);
            } catch (IllegalArgumentException x) {
                throw new Command.Exception(x.getMessage());
            }
            String cn = takeArg();
            String ops = takeArg();
            finishArgs();
            byte[] bs = null;
            try {
                bs = lib.readClass(mid, cn);
                if (bs == null)
                    throw new Command.Exception("%s: No such class in module %s",
                                                cn, mid);
                OutputStream fout = null;
                try {
                    fout = (ops.equals("-")
                            ? System.out
                            : new FileOutputStream(ops));
                    fout.write(bs);
                } finally {
                    if (fout != null)
                        fout.close();
                }
            } catch (IllegalArgumentException x) {
                throw new Command.Exception(x.getMessage());
            } catch (IOException x) {
                throw new Command.Exception(x);
            }
        }
    }

    static class Identify extends Command<SimpleLibrary> {
        protected void go(SimpleLibrary lib)
            throws Command.Exception
        {
            noDry();
            finishArgs();
            out.format("path %s%n", lib.root());
            out.format("version %d.%d%n",
                       lib.majorVersion(), lib.minorVersion());
            SimpleLibrary plib = lib.parent();
            while (plib != null) {
                out.format("parent %s%n", plib.root());
                plib = plib.parent();
            }
        }
    }

    static class Extract extends Command<SimpleLibrary> {
        protected void go(SimpleLibrary lib)
            throws Command.Exception
        {
            noDry();
            while (hasArg()) {
		File module = new File(takeArg());
		try { 
		    File classes = File.createTempFile("jigsaw",null);
		    classes.toPath().delete();
		    classes.toPath().createDirectory();
		    FileInputStream fis = new FileInputStream(module);
		    DataInputStream dis = new DataInputStream(fis);
		    ModuleFileFormat.Reader reader = 
			new ModuleFileFormat.Reader(dis);
		    reader.readModule(classes);
		    String name = module.getName();
		    name = name.substring(0, name.lastIndexOf('@'));
		    Files.copyTree(classes, new File(name));
		    Files.deleteTree(classes);
		}
		catch (IOException x) {
		    throw new Command.Exception(x);
		}
	    }
            finishArgs();
        }
    }

    static class Install extends Command<SimpleLibrary> {
        protected void go(SimpleLibrary lib)
            throws Command.Exception
        {
            String key = takeArg();
            File kf = new File(key);

            // Old form: install <classes-dir> <module-name> ...
            //
            if (kf.exists() && kf.isDirectory()) {
                noDry();
                List<Manifest> mfs = new ArrayList<Manifest>();
                while (hasArg())
                    mfs.add(Manifest.create(takeArg(), kf));
                finishArgs();
                if (!mfs.isEmpty() && opts.has(resourcePath)) {
                    // ## Single -r option only applies to first module
                    // ## Should support one -r option for each module
                    mfs.get(0).addResources(opts.valueOf(resourcePath));
                }
                try {
                    lib.installFromManifests(mfs);
                } catch (ConfigurationException x) {
                    throw new Command.Exception(x);
                } catch (IOException x) {
                    throw new Command.Exception(x);
                }
                return;
            }

            // Install one or more module file(s)
            //
            if (kf.exists() && kf.isFile()) {
                noDry();
                List<File> fs = new ArrayList<>();
                fs.add(kf);
                while (hasArg())
                    fs.add(new File(takeArg()));
                finishArgs();
                try {
                    lib.install(fs);
                } catch (ConfigurationException x) {
                    throw new Command.Exception(x);
                } catch (IOException x) {
                    throw new Command.Exception(x);
                }
                return;
            }

            // Otherwise treat args as module-id queries
            List<ModuleIdQuery> midqs = new ArrayList<>();
            String s = key;
            for (;;) {
                ModuleIdQuery mq = null;
                try {
                    mq = jms.parseModuleIdQuery(s);
                } catch (IllegalArgumentException x) {
                    throw new Command.Exception(x);
                }
                midqs.add(mq);
                if (!hasArg())
                    break;
                s = takeArg();
            }
            try {
                boolean quiet = false;  // ## Need -q
                Resolution res = lib.resolve(midqs);
                if (res.modulesNeeded().isEmpty()) {
                    if (!quiet)
                        out.format("Nothing to install%n");
                    return;
                }
                if (!quiet) {
                    out.format("To install: %s%n",
                               res.modulesNeeded()
                               .toString().replaceAll("^\\[|\\]$", ""));
                    out.format("%d bytes to download%n",
                               res.downloadRequired());
                    out.format("%d bytes to store%n",
                               res.spaceRequired());
                }
                if (dry)
                    return;
                lib.install(res);
            } catch (ConfigurationException x) {
                throw new Command.Exception(x);
            } catch (IOException x) {
                throw new Command.Exception(x);
            }

        }
    }

    static class PreInstall extends Command<SimpleLibrary> {
        protected void go(SimpleLibrary lib)
            throws Command.Exception
        {
            noDry();
            File classes = new File(takeArg());
            File dst = new File(takeArg());
            List<Manifest> mfs = new ArrayList<Manifest>();
            while (hasArg())
                mfs.add(Manifest.create(takeArg(), classes));
            if (!mfs.isEmpty() && opts.has(resourcePath)) {
                // ## Single -r option only applies to first module
                // ## Should support one -r option for each module
                mfs.get(0).addResources(opts.valueOf(resourcePath));
            }
            finishArgs();
            try {
                lib.preInstall(mfs, dst);
            } catch (IOException x) {
                throw new Command.Exception(x);
            }
        }
    }

    static class DumpConfig extends Command<SimpleLibrary> {
        protected void go(SimpleLibrary lib)
            throws Command.Exception
        {
            noDry();
            String midqs = takeArg();
            ModuleIdQuery midq = null;
            try {
                midq = jms.parseModuleIdQuery(midqs);
            } catch (IllegalArgumentException x) {
                throw new Command.Exception(x.getMessage());
            }
            finishArgs();
            try {
                ModuleId mid = lib.findLatestModuleId(midq);
                if (mid == null)
                    throw new Command.Exception(midq + ": No such module");
                Configuration<Context> cf = lib.readConfiguration(mid);
                if (cf == null)
                    throw new Command.Exception(mid + ": Not a root module");
                cf.dump(out, verbose);
            } catch (IOException x) {
                throw new Command.Exception(x);
            }
        }
    }

    static class Config extends Command<SimpleLibrary> {
        protected void go(SimpleLibrary lib)
            throws Command.Exception
        {
            noDry();
            List<ModuleId> mids = new ArrayList<ModuleId>();
            try {
                while (hasArg())
                    mids.add(jms.parseModuleId(takeArg()));
            } catch (IllegalArgumentException x) {
                throw new Command.Exception(x.getMessage());
            }
            try {
                lib.configure(mids);
            } catch (ConfigurationException x) {
                throw new Command.Exception(x);
            } catch (IOException x) {
                throw new Command.Exception(x);
            }
        }
    }

    static class ReIndex extends Command<SimpleLibrary> {
        protected void go(SimpleLibrary lib)
            throws Command.Exception
        {
            noDry();
            List<ModuleId> mids = new ArrayList<ModuleId>();
            try {
                while (hasArg())
                    mids.add(jms.parseModuleId(takeArg()));
            } catch (IllegalArgumentException x) {
                throw new Command.Exception(x.getMessage());
            }
            try {
                lib.reIndex(mids);
            } catch (ConfigurationException x) {
                throw new Command.Exception(x);
            } catch (IOException x) {
                throw new Command.Exception(x);
            }
        }
    }

    static class Repos extends Command<SimpleLibrary> {
        protected void go(SimpleLibrary lib)
            throws Command.Exception
        {
            noDry();
            finishArgs();
            try {
                RemoteRepositoryList rl = lib.repositoryList();
                int i = 0;
                for (RemoteRepository rr : rl.repositories())
                    out.format("%d %s%n", i++, rr.location());
            } catch (IOException x) {
                throw new Command.Exception(x);
            }
        }
    }

    static URI parseURI(String s)
        throws Command.Exception
    {
        try {
            return new URI(s);
        } catch (URISyntaxException x) {
            throw new Command.Exception("URI syntax error: "
                                        + x.getMessage());
        }
    }

    static class AddRepo extends Command<SimpleLibrary> {
        protected void go(SimpleLibrary lib)
            throws Command.Exception
        {
            noDry();
            URI u = parseURI(takeArg());
            int i = (opts.has(repoIndex)
                     ? opts.valueOf(repoIndex)
                     : Integer.MAX_VALUE);
            finishArgs();
            try {
                RemoteRepositoryList rl = lib.repositoryList();
                rl.add(u, i);
            } catch (IOException x) {
                throw new Command.Exception(x);
            }
        }
    }

    static class DelRepo extends Command<SimpleLibrary> {
        protected void go(SimpleLibrary lib)
            throws Command.Exception
        {
            noDry();
            URI u = null;
            int i = -1;
            if (hasArg()) {
                String s = takeArg();
                if (!s.endsWith("/"))
                    s += "/";
                u = parseURI(s);
                finishArgs();
            }
            if (opts.has(repoIndex))
                i = opts.valueOf(repoIndex);
            if (u != null && i != -1) {
                throw new Command.Exception("del-repo: Cannot specify"
                                            + " both -i and a URL");
            }
            if (u == null && i == -1) {
                throw new Command.Exception("del-repo: One of -i <index>"
                                            + " or a URL required");
            }
            try {
                RemoteRepositoryList rl = lib.repositoryList();
                if (i != -1) {
                    rl.remove(rl.repositories().get(i));
                    return;
                }
                for (RemoteRepository rr : rl.repositories()) {
                    if (rr.location().equals(u)) {
                        rl.remove(rr);
                        return;
                    }
                }
                throw new Command.Exception("No repository found for deletion");
            } catch (IOException x) {
                throw new Command.Exception(x);
            }
        }
    }

    static class Refresh extends Command<SimpleLibrary> {
        protected void go(SimpleLibrary lib)
            throws Command.Exception
        {
            finishArgs();
            try {
                RemoteRepositoryList rl = lib.repositoryList();
                int n = 0;
                for (RemoteRepository rr : rl.repositories()) {
                    out.format("%s - ", rr.location());
                    out.flush();
                    boolean stale
                        = dry ? rr.isCatalogStale() : rr.updateCatalog(force);
                    if (stale) {
                        n++;
                        out.format(dry ? "out of date%n" : "updated%n");
                    } else {
                        out.format("up to date%n");
                    }
                }
            } catch (IOException x) {
                throw new Command.Exception(x);
            }
        }
    }


    private static Map<String,Class<? extends Command<SimpleLibrary>>> commands
        = new HashMap<>();

    static {
        commands.put("add-repo", AddRepo.class);
        commands.put("config", Config.class);
        commands.put("create", Create.class);
        commands.put("del-repo", DelRepo.class);
        commands.put("dump-class", DumpClass.class);
        commands.put("dump-config", DumpConfig.class);
        commands.put("extract", Extract.class);
        commands.put("id", Identify.class);
        commands.put("identify", Identify.class);
        commands.put("install", Install.class);
        commands.put("list", Commands.ListLibrary.class);
        commands.put("ls", Commands.ListLibrary.class);
        commands.put("preinstall", PreInstall.class);
        commands.put("refresh", Refresh.class);
        commands.put("reindex", ReIndex.class);
        commands.put("repos", Repos.class);
    }

    private OptionParser parser;

    private static OptionSpec<File> resourcePath; // ##
    private static OptionSpec<Integer> repoIndex; // ##

    private void usage() {
        out.format("%n");
        out.format("usage: jmod add-repo [-i <index>] URL%n");
        out.format("       jmod extract <module-file> ...%n");
        out.format("       jmod config [<module-id> ...]%n");
        out.format("       jmod create [-L <library>] [-P <parent>]%n");
        out.format("       jmod del-repo URL%n");
        out.format("       jmod dump-class <module-id> <class-name> <output-file>%n");
        out.format("       jmod dump-config <module-id>%n");
        out.format("       jmod identify%n");
        out.format("       jmod install [-n] <module-id-query> ...%n");
        out.format("       jmod install <module-file> ...%n");
        out.format("       jmod install <classes-dir> [-r <resource-dir>] <module-name> ...%n");
        out.format("       jmod list [-v] [-p] [-R] [<module-id-query>]%n");
        out.format("       jmod preinstall <classes-dir> <dst-dir> <module-name> ...%n");
        out.format("       jmod refresh [-f] [-n] [-v]%n");
        out.format("       jmod reindex [<module-id> ...]%n");
        out.format("       jmod repos [-v]%n");
        out.format("%n");
        try {
            parser.printHelpOn(out);
        } catch (IOException x) {
            throw new AssertionError(x);
        }
        out.format("%n");
        System.exit(0);
    }

    public static void run(String [] args) throws OptionException, Command.Exception {
	new Librarian().exec(args);
    }

    private void exec(String[] args) throws OptionException, Command.Exception {

        parser = new OptionParser();

        // ## Need subcommand-specific option parsing
        OptionSpec<File> libPath
            = (parser.acceptsAll(Arrays.asList("L", "library"),
                                 "Module-library location"
                                 + " (default $JAVA_MODULES)")
               .withRequiredArg()
               .describedAs("path")
               .ofType(File.class));
        OptionSpec<File> parentPath
            = (parser.acceptsAll(Arrays.asList("P", "parent-path"),
                                 "Parent module-library location")
               .withRequiredArg()
               .describedAs("path")
               .ofType(File.class));
        parser.acceptsAll(Arrays.asList("N", "no-parent"),
                          "Use no parent library when creating");
        parser.acceptsAll(Arrays.asList("v", "verbose"),
                          "Enable verbose output");
        parser.acceptsAll(Arrays.asList("h", "?", "help"),
                          "Show this help message");
        parser.acceptsAll(Arrays.asList("p", "parent"),
                          "Apply operation to parent library, if any");
        resourcePath
            = (parser.acceptsAll(Arrays.asList("r", "resources"),
                                 "Directory of resources to be processed")
               .withRequiredArg()
               .describedAs("path")
               .ofType(File.class));
        repoIndex
            = (parser.acceptsAll(Arrays.asList("i"),
                                 "Repository-list index")
               .withRequiredArg()
               .describedAs("index")
               .ofType(Integer.class));
        parser.acceptsAll(Arrays.asList("f", "force"),
                          "Force the requested operation");
        parser.acceptsAll(Arrays.asList("n", "dry-run"),
                          "Dry-run the requested operation");
        parser.acceptsAll(Arrays.asList("R", "repos"),
                          "List contents of associated repositories");

        if (args.length == 0)
            usage();

        File homeLibrary = new File(System.getProperty("java.home"),
                                    "lib/modules");

	OptionSet opts = parser.parse(args);
	if (opts.has("h"))
	    usage();
	List<String> words = opts.nonOptionArguments();
	if (words.isEmpty())
	    usage();
	String verb = words.get(0);
	Class<? extends Command<SimpleLibrary>> cmd = commands.get(verb);
	if (cmd == null)
	    throw new Command.Exception("%s: unknown command", verb);
	File lp = null;
	if (opts.has(libPath)) {
	    lp = opts.valueOf(libPath);
	} else {
	    String jm = System.getenv("JAVA_MODULES");
	    if (jm != null)
		lp = new File(jm);
	    else
		lp = homeLibrary;
	}
	File pp = null;
	if (opts.has(parentPath)) {
	    pp = opts.valueOf(parentPath);
	} else if (!opts.has("N")) {
	    pp = homeLibrary;
	}
	SimpleLibrary lib = null;
	try {
	    lib = SimpleLibrary.open(lp, verb.equals("create"), pp);
	} catch (FileNotFoundException x) {
	    String msg = null;
	    File f = new File(x.getMessage());
	    try {
		f = f.getCanonicalFile();
		if (lp.getCanonicalFile().equals(f))
		    msg = "No such library";
		else
		    msg = "Cannot open parent library " + f;
	    } catch (IOException y) {
		throw new Command.Exception(y);
	    }
	    throw new Command.Exception("%s: %s", lp, msg);
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
    }

    private Librarian() { }

    public static void main(String[] args) {
	try {
	    run(args);
	} catch (OptionException x) {
            err.println(x.getMessage());
            System.exit(1);
        } catch (Command.Exception x) {
            err.println(x.getMessage());
            System.exit(1);
        }
    }

}
