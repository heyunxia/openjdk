/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

import org.openjdk.jigsaw.*;
import org.openjdk.internal.joptsimple.*;

import static java.lang.System.out;
import static java.lang.System.err;


public class Repositor {

    private static JigsawModuleSystem jms
        = JigsawModuleSystem.instance();

    private static boolean debug = false;

    static class Create extends Command<PublishedRepository> {
        protected void go(PublishedRepository rp)
            throws Command.Exception
        {
            finishArgs();
        }
    }

    static class Add extends Command<PublishedRepository> {
        protected void go(PublishedRepository rp)
            throws Command.Exception
        {
            while (hasArg()) {
                Path mp = Paths.get(takeArg());
                try {
                    rp.publish(mp);
                } catch (IOException x) {
                    throw new Command.Exception(mp.toString(), x);
                }
            }
        }
    }

    static class Del extends Command<PublishedRepository> {
        protected void go(PublishedRepository rp)
            throws Command.Exception
        {
            while (hasArg()) {
                ModuleId mid = jms.parseModuleId(takeArg());
                try {
                    rp.remove(mid);
                } catch (IOException x) {
                    throw new Command.Exception(mid.toString(), x);
                }
            }
        }
    }

    static class Dump extends Command<Repository> {
        protected void go(Repository rp)
            throws Command.Exception
        {
            ModuleId mid = jms.parseModuleId(takeArg());
            String ops = takeArg();
            finishArgs();
            InputStream in = null;
            try {
                try {
                    in = rp.fetch(mid);
                    OutputStream fout = null;
                    try {
                        if (ops.equals("-"))
                            fout = System.out;
                        else
                            fout = new FileOutputStream(ops);
                        byte[] bb = new byte[8192];
                        int n;
                        while ((n = in.read(bb)) > 0)
                            fout.write(bb, 0, n);
                    } finally {
                        if (fout != null)
                            fout.close();
                    }
                } finally {
                    if (in != null)
                        in.close();
                }
            } catch (IOException x) {
                throw new Command.Exception(mid.toString(), x);
            }
        }
    }

    static class ReCatalog extends Command<PublishedRepository> {
        protected void go(PublishedRepository rp)
            throws Command.Exception
        {
            finishArgs();
            try {
                rp.reCatalog();
            } catch (IOException x) {
                throw new Command.Exception(x);
            }
        }
    }

    static class Validate extends Command<PublishedRepository> {
        protected void go(PublishedRepository rp)
            throws Command.Exception
        {
            finishArgs();
            try {
                if (!rp.validate(null))
                    System.exit(1);
            } catch (IOException x) {
                throw new Command.Exception(x);
            }
        }
    }

    private static Map<String,Class<? extends Command<PublishedRepository>>>
        pubCommands = new HashMap<>();

    static {
        pubCommands.put("add", Add.class);
        pubCommands.put("create", Create.class);
        pubCommands.put("del", Del.class);
        pubCommands.put("recat", ReCatalog.class);
        pubCommands.put("val", Validate.class);
        pubCommands.put("validate", Validate.class);
    }

    private static Map<String,Class<? extends Command<Repository>>>
        commands = new HashMap<>();

    static {
        commands.put("dump", Dump.class);
        commands.put("list", Commands.ListRepository.class);
        commands.put("ls", Commands.ListRepository.class);
    }

    private OptionParser parser;

    private void usage() {
        out.format("%n");
        out.format("usage: jrepo <repo> add <module-file> ...%n");
        out.format("       jrepo <repo> create%n");
        out.format("       jrepo <repo> del <module-id> ...%n");
        out.format("       jrepo <repo> dump <module-id> <output-file>%n");
        out.format("       jrepo <repo> list [-v]%n");
        out.format("       jrepo <repo> recat%n");
        out.format("       jrepo <repo> validate%n");
        out.format("%n");
        out.format("  where <repo> is a repo directory path or http URL%n");
        out.format("  (http repos support only the dump and list commands)%n");
        out.format("%n");
        try {
            parser.printHelpOn(out);
        } catch (IOException x) {
            throw new AssertionError(x);
        }
        out.format("%n");
        System.exit(0);
    }

    private Repository open(String rn, boolean create)
        throws Command.Exception
    {
        try {
            if (rn.matches("https?://.*")) {
                if (create)
                    throw new Command.Exception("%s: Cannot create a remote repository",
                                                rn);
                Path rdir = FilePaths.makeTemporaryDirectory("jrepo");
                if (debug)
                    out.format("temporary directory: %s%n", rdir);
                RemoteRepository rr
                    = RemoteRepository.create(new File(rdir.toString(), "repo"),
                                              URI.create(rn));
                rr.updateCatalog(false);
                return rr;
            }
            return PublishedRepository.open(Paths.get(rn), create);
        } catch (IOException x) {
            throw new Command.Exception(x);
        }
    }

    private void run(String[] args) {

        parser = new OptionParser();
        parser.acceptsAll(Arrays.asList("v", "verbose"),
                          "Enable verbose output");
        parser.acceptsAll(Arrays.asList("h", "?", "help"),
                          "Show this help message");
        parser.acceptsAll(Arrays.asList("d", "debug"),
                          "Enable debug output");

        if (args.length == 0)
            usage();

        try {

            if (args.length < 2)
                usage();
            String rn = args[0];
            args = Arrays.copyOfRange(args, 1, args.length);
            OptionSet opts = parser.parse(args);
            if (opts.has("h"))
                usage();
            debug = opts.has("d");
            List<String> words = opts.nonOptionArguments();
            String verb = words.get(0);

            Repository r = open(rn, verb.equals("create"));
            if (r instanceof PublishedRepository) {
                Class<? extends Command<PublishedRepository>> cmd
                    = pubCommands.get(verb);
                if (cmd != null) {
                    try {
                        cmd.newInstance().run((PublishedRepository)r, opts);
                    } catch (InstantiationException x) {
                        throw new AssertionError(x);
                    } catch (IllegalAccessException x) {
                        throw new AssertionError(x);
                    }
                    return;
                }
            }

            Class<? extends Command<Repository>> cmd = commands.get(verb);
            if (cmd == null)
                throw new Command.Exception("%s: unknown command", verb);
            try {
                cmd.newInstance().run(r, opts);
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
            if (debug)
                x.printStackTrace();
            System.exit(1);
        }

    }

    private Repositor() { }

    public static void main(String[] args) throws Exception {
        new Repositor().run(args);
    }

}
