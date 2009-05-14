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

    static class Create extends Command<SimpleLibrary> {
        protected void go(SimpleLibrary lib)
            throws Command.Exception
        {
            finishArgs();
        }
    }

    static class Dump extends Command<SimpleLibrary> {
        protected void go(SimpleLibrary lib)
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

    static class Identify extends Command<SimpleLibrary> {
        protected void go(SimpleLibrary lib)
            throws Command.Exception
        {
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

    static class Install extends Command<SimpleLibrary> {
        protected void go(SimpleLibrary lib)
            throws Command.Exception
        {
            File classes = new File(takeArg());
            java.util.List<Manifest> mfs = new ArrayList<Manifest>();
            while (hasArg())
                mfs.add(Manifest.create(takeArg(), classes));
            finishArgs();
            if (!mfs.isEmpty() && opts.has(resourcePath)) {
                // ## Single -r option only applies to first module
                // ## Should support one -r option for each module
                mfs.get(0).addResources(opts.valueOf(resourcePath));
            }
            try {
                lib.install(mfs);
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
            File classes = new File(takeArg());
            File dst = new File(takeArg());
            java.util.List<Manifest> mfs = new ArrayList<Manifest>();
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

    static class List extends Command<SimpleLibrary> {
        protected void go(SimpleLibrary lib)
            throws Command.Exception
        {
            final ModuleIdQuery midq;
            if (hasArg())
                midq = parseModuleIdQuery(takeArg());
            else
                midq = null;
            boolean parents = opts.has("p");
            finishArgs();
            int n = 0;
            try {
                for (ModuleId mid : lib.listModuleIds(parents)) {
                    if (midq != null && !midq.matches(mid))
                        continue;
                    ModuleInfo mi = lib.readModuleInfo(mid);
                    if (verbose)
                        out.format("%n");
                    out.format("%s%n", mi.id());
                    n++;
                    if (verbose) {
                        formatCommaList(out, "provides", mi.provides());
                        for (Dependence d : mi.requires())
                            out.format("  %s%n", d);
                        formatCommaList(out, "permits", mi.permits());
                    }
                }
            } catch (IOException x) {
                throw new Command.Exception(x);
            }
            if (verbose && n > 0)
                out.format("%n");
        }
    }

    static class Show extends Command<SimpleLibrary> {
        protected void go(SimpleLibrary lib)
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

    static class Config extends Command<SimpleLibrary> {
        protected void go(SimpleLibrary lib)
            throws Command.Exception
        {
            java.util.List<ModuleId> mids = new ArrayList<ModuleId>();
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


    private static Map<String,Class<? extends Command<SimpleLibrary>>> commands
        = new HashMap<String,Class<? extends Command<SimpleLibrary>>>();

    static {
        commands.put("config", Config.class);
        commands.put("create", Create.class);
        commands.put("dump", Dump.class);
        commands.put("id", Identify.class);
        commands.put("identify", Identify.class);
        commands.put("install", Install.class);
        commands.put("list", List.class);
        commands.put("ls", List.class);
        commands.put("preinstall", PreInstall.class);
        commands.put("show", Show.class);
    }

    private OptionParser parser;

    private static OptionSpec<File> resourcePath; // ##

    private void usage() {
        out.format("%n");
        out.format("usage: jmod config [<module-id> ...]%n");
        out.format("       jmod create [-L <library>] [-P <parent>]%n");
        out.format("       jmod dump <module-id> <class-name>%n");
        out.format("       jmod identify%n");
        out.format("       jmod install <classes-dir> [-r <resource-dir>] <module-name> ...%n");
        out.format("       jmod list [-v] [-p] [<module-id-query>]%n");
        out.format("       jmod preinstall <classes-dir> <dst-dir> <module-name> ...%n");
        out.format("       jmod show <module-id>%n");
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
            Class<? extends Command<SimpleLibrary>> cmd = commands.get(verb);
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
            File pp = null;
            if (opts.has(parentPath)) {
                pp = opts.valueOf(parentPath);
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
