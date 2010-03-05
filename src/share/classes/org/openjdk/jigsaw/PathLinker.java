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
 * have any questions.30
 */

package org.openjdk.jigsaw;

import java.lang.module.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

import static java.lang.module.Dependence.Modifier;
import static org.openjdk.jigsaw.Trace.*;


// ## TODO: Implement intra-context dominant-shadow algorithm

final class PathLinker {

    private final ContextSet<PathContext> cxs;

    private PathLinker(ContextSet<PathContext> c) {
        cxs = c;
     }

    private void fail(String fmt, Object ... args)
        throws ConfigurationException
    {
        throw new ConfigurationException(fmt, args);
    }

    // Link local suppliers
    //
    // For a context containing just one module, linking local suppliers is
    // trivial because there is only one supplier.
    //
    // For contexts with multiple modules we linearize the list of local
    // suppliers by computing ... ## Not yet implemented
    //
    private void linkLocalSuppliers()
        throws ConfigurationException, IOException
    {
        for (PathContext cx : cxs.contexts) {
            if (cx.modules.size() == 1) {
                cx.localPath.addAll(cx.modules);
                continue;
            }
            // Order suppliers according to dominance ## Not yet implemented
            cx.localPath.addAll(cx.modules());
            Collections.sort(cx.localPath);
        }
    }

    // Link remote suppliers
    //
    // To link remote suppliers we first compute, for each context, its
    // supplying and re-exported contexts.  We then run a simple data-flow
    // algorithm to propagate the re-exported contexts throughout the
    // context graph.

    private boolean propagate(boolean changed, PathContext cx) {

        // Every supplier re-exported by a supplying context
        // must also be a supplier to this context
        //
        for (PathContext scx
                 : new ArrayList<PathContext>(cx.suppliers))
        {
            for (PathContext rscx : scx.reExportedSuppliers) {
                if (!cx.suppliers.contains(rscx)) {
                    if (tracing && !Platform.isPlatformContext(rscx))
                        trace(1, 1, "adding %s to %s", rscx, cx);
                    cx.suppliers.add(rscx);
                    changed = true;
                }
            }
        }

        // If this context re-exports one of its suppliers
        // then it must also re-export all of that supplier's
        // re-exported suppliers
        //
        for (PathContext rscx
                 : new ArrayList<PathContext>(cx.reExportedSuppliers))
        {
            for (PathContext rrscx : rscx.reExportedSuppliers) {
                if (!cx.reExportedSuppliers.contains(rrscx)) {
                    if (tracing && !Platform.isPlatformContext(rrscx))
                        trace(1, 1, "adding %s as a re-export from %s",
                              rrscx, cx);
                    cx.reExportedSuppliers.add(rrscx);
                    changed = true;
                }
            }
        }

        return changed;

    }

    private void propagate()
        throws ConfigurationException
    {
        int n = 0;
        for (;;) {
            n++;
            if (tracing)
                trace(1, "propagating suppliers (pass %d)", n);
            boolean changed = false;
            for (PathContext cx : cxs.contexts) {
                changed = propagate(changed, cx);
            }
            if (!changed)
                return;
        }
    }

    private void linkRemoteSuppliers()
        throws ConfigurationException, IOException
    {

        // Prepare export and supplier sets
        if (tracing)
            trace(1, "preparing export and supplier sets");
        for (PathContext cx : cxs.contexts) {
            for (ModuleInfo mi : cx.moduleInfos) {
                for (Dependence d : mi.requires()) {
                    trace(1, 3, "dep %s", d);
                    PathContext scx = cxs.contextForModule.get(d.query().name());
                    if (scx == null) {
                        // Unsatisfied optional dependence
                        assert d.modifiers().contains(Modifier.OPTIONAL);
                        continue;
                    }
                    if (!d.modifiers().contains(Modifier.LOCAL)) {
                        // Dependence upon some other context
                        if (tracing)
                            trace(1, 1, "adding %s as supplier to %s",
                                  scx, cx);
                        cx.suppliers.add(scx);
                    }
                    if (d.modifiers().contains(Modifier.PUBLIC)) {
                        // Required publicly, so re-export it
                        if (tracing)
                            trace(1, 1, "re-exporting %s from %s",
                                  scx, cx);
                        cx.reExportedSuppliers.add(scx);
                    }
                }
            }
        }

        // Flow
        propagate();

    }

    private void run()
        throws ConfigurationException, IOException
    {
        linkLocalSuppliers();
        linkRemoteSuppliers();
    }

    // Entry point
    //
    static Configuration<PathContext> run(ContextSet<PathContext> cxs)
        throws ConfigurationException, IOException
    {

        // Link
        new PathLinker(cxs).run();

        // Lock down results
        for (PathContext cx : cxs.contexts) {
            cx.localPath = Collections.unmodifiableList(cx.localPath);
            cx.suppliers = Collections.unmodifiableSet(new HashSet<PathContext>(cx.suppliers));
        }

        List<ModuleId> rids = new ArrayList<>();
        for (ModuleIdQuery rq : cxs.rootQueries)
            rids.add(cxs.moduleForName.get(rq.name()).id());
        return new Configuration<>(rids,
                                   cxs.contexts,
                                   cxs.contextForModule);

    }

}
