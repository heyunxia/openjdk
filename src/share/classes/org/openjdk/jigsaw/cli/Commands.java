/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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


/**
 * Commands shared by multiple CLIs
 */

class Commands {

    private static JigsawModuleSystem jms
        = JigsawModuleSystem.instance();

    private static void formatCommaList(PrintStream out,
                                        String prefix,
                                        Collection<?> list)
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

    private static void formatModuleView(PrintStream out,
                                         ModuleView view,
                                         String indent) {
        formatCommaList(out, indent + "provides",
                        view.aliases());
        formatCommaList(out, indent + "permits",
                        view.permits());
        Map<String,Set<String>> services = view.services();
        for (Map.Entry<String,Set<String>> entry: services.entrySet()) {
            String sn = entry.getKey();
            for (String impl: entry.getValue()) {
                out.format("%s  provides service %s with %s%n", indent, sn, impl);
            }
        }

        if (!view.exports().isEmpty()) {
            out.format("  %sexports%n", indent);
            Set<String> exports = new TreeSet<>(view.exports());
            for (String pn : exports) {
                out.format("  %s  %s%n", indent, pn);
            }
        }
    }

    private static void listCommand(Catalog cat, ModuleIdQuery midq,
                                    boolean parents, boolean verbose)
        throws Command.Exception
    {
        int n = 0;
        try {
            List<ModuleId> mids;
            if (midq == null) {
                mids = parents ? cat.listDeclaringModuleIds() : cat.listLocalDeclaringModuleIds();
            } else {
                List<ModuleId> list = parents ? cat.listModuleIds()
                                              : cat.listLocalModuleIds();
                mids = new ArrayList<>();
                for (ModuleId mid : list) {
                    if (midq.matches(mid))
                        mids.add(mid);
                }
            }
            for (ModuleId mid : mids) {
                ModuleInfo mi = cat.readModuleInfo(mid);
                if (verbose) {
                    out.format("%n");
                }

                // print module and its views
                out.format("%s%n", mi.id());
                n++;
                if (verbose) {
                    for (ViewDependence d : mi.requiresModules()) {
                        out.format("  %s%n", d);
                    }
                    for (ServiceDependence sd: mi.requiresServices()) {
                        out.format("  %s%n", sd);
                    }
                    formatModuleView(out, mi.defaultView(), "");

                    for (ModuleView mv : mi.views()) {
                        if (mv == mi.defaultView())
                            continue;

                        out.format("  view %s%n", mv.id().name());
                        formatModuleView(out, mv, "  ");
                    }
                }
            }
        } catch (IOException x) {
            throw new Command.Exception(x);
        }
        if (verbose && n > 0)
            out.format("%n");
    }

    private static Catalog compose(ModuleIdQuery midq,
                                   List<? extends Catalog> cats)
        throws IOException
    {

        final Set<ModuleId> modules = new HashSet<>();
        final Map<ModuleId,ModuleInfo> mods = new HashMap<>();
        for (Catalog c : cats) {
            List<ModuleId> mids = c.listDeclaringModuleIds();
            modules.addAll(mids);
            for (ModuleId mid : mids) {
                if (mods.containsKey(mid))
                    continue;

                ModuleInfo mi = c.readModuleInfo(mid);
                for (ModuleView mv : mi.views()) {
                    mods.put(mid, mi);
                    for (ModuleId alias : mv.aliases()) {
                        mods.put(alias, mi);
                    }
                }
            }
        }

        return new Catalog() {

            public String name() { return "composite"; }

            public Catalog parent() { return null; }

            protected void gatherLocalModuleIds(String moduleName,
                                                Set<ModuleId> ids)
                throws IOException
            {
                ids.addAll(mods.keySet());
            }

            protected void gatherLocalDeclaringModuleIds(Set<ModuleId> mids)
                throws IOException
            {
                mids.addAll(modules);
            }

            protected ModuleInfo readLocalModuleInfo(ModuleId mid)
                throws IOException
            {
                return mods.get(mid);
            }

        };

    }

    // ## There must be a better way to do this!

    static class ListLibrary extends Command<SimpleLibrary> {
        protected void go(SimpleLibrary lib)
            throws Command.Exception
        {
            ModuleIdQuery midq;
            if (hasArg())
                midq = jms.parseModuleIdQuery(takeArg());
            else
                midq = null;
            finishArgs();
            boolean parents = opts.has("p");
            Catalog cat = lib;
            try {
                if (opts.has("R"))
                    cat = compose(midq, lib.repositoryList().repositories());
            } catch (IOException x) {
                throw new Command.Exception(x);
            }
            listCommand(cat, midq, parents, verbose);
        }
    }

    static class ListRepository extends Command<Repository> {
        protected void go(Repository repo)
            throws Command.Exception
        {
            ModuleIdQuery midq;
            if (hasArg())
                midq = jms.parseModuleIdQuery(takeArg());
            else
                midq = null;
            finishArgs();
            listCommand(repo, midq, false, verbose);
        }
    }

}
