/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.jigsaw;

import java.lang.module.*;
import java.io.*;
import java.net.URI;
import java.util.*;

import static java.lang.module.ViewDependence.Modifier;
import static org.openjdk.jigsaw.Trace.*;


// A module with no outbound or inbound local dependences is always
// assigned its own unique context.
//
// A module with local dependences, either outbound, inbound, or both,
// must be assigned to the same context as the modules upon which it
// locally depends (outbound) and the modules that depend locally upon
// it (inbound).
//
// Put another way, local dependences are bidirectional.  A requestor
// can see all of the public and package-private class definitions in
// each of its local suppliers; a supplier, likewise, can see all such
// definitions in its local requestors.  Local visibility is, in fact,
// transitive: A module can see all public and package-private classes
// in all of the modules assigned to the same context.
//
// We build the context graph by creating a unique context for each
// locally-connected component in the undirected view of the
// resolved-module graph.  This requires first adding a back edge for
// each local dependence, i.e., from supplier to requestor.

final class ContextBuilder<Cx extends BaseContext> {

    private Resolution res;
    private ContextFactory<Cx> cxf;

    private ContextBuilder(Resolution r, ContextFactory<Cx> c) {
        res = r;
        cxf = c;
    }

    private Map<String,List<String>> localRequestorsOfName    // Back edges
        = new HashMap<String,List<String>>();

    private void addLocalRequestor(String rmn, String smn) {
        List<String> ls = localRequestorsOfName.get(smn);
        if (ls == null) {
            ls = new ArrayList<String>();
            localRequestorsOfName.put(smn, ls);
        }
        ls.add(rmn);
    }

    // Find local-dependence back edges
    //
    private void findLocalRequestors() {
        for (ModuleInfo mi : res.modules) {
            for (ViewDependence d : mi.requiresModules()) {
                if (d.modifiers().contains(Modifier.LOCAL)) {
                    ModuleView smv = res.moduleViewForName.get(d.query().name());
                    if (smv == null) {
                        // smi can be null if dependence is optional
                        assert d.modifiers().contains(Modifier.OPTIONAL);
                        continue;
                    }
                    addLocalRequestor(mi.id().name(), smv.id().name());
                }
            }
        }
    }

    // All of our contexts
    //
    private Set<Cx> contexts = new IdentityHashSet<>();

    // For each module view, its assigned context; this also serves
    // as the visited-node set during context construction
    //
    private Map<String,Cx> contextForModuleView
        = new HashMap<>();

    private void addContextForModuleView(Cx cx, ModuleView mv) {
        contextForModuleView.put(mv.id().name(), cx);
        for (ModuleId alias : mv.aliases()) {
            contextForModuleView.put(alias.name(), cx);
        }
    }

    // Add the given module view to the given context, or create a new context for
    // that module view if none is given, and then add all the other modules in the
    // module's locally-connected component to the same context
    //
    private void build(Cx pcx, ModuleView mv, ModuleInfo mi) {
        assert !contextForModuleView.containsKey(mv.id().name());

        Cx cx = pcx;
        if (cx == null) {
            cx = cxf.create();
            contexts.add(cx);
        }
        if (!cx.modules.containsKey(mi.id())) {
            Set<ModuleId> views = new HashSet<>();
            for (ModuleView v : mi.views()) {
                views.add(v.id());
            }
            cx.add(mi.id(), views);
            if (cx instanceof LinkingContext) {
                ((LinkingContext) cx).addModule(mi);
            }
            if (cx instanceof Context) {
                URI lp = res.locationForName.get(mi.id().name());
                if (lp != null) {
                    String s = lp.getScheme();
                    if (s == null || !s.equals("file")) {
                        throw new AssertionError(s);
                    }
                    ((Context) cx).putLibraryPathForModule(mi.id(), new File(lp));
                }
            }
            addContextForModuleView(cx, mi.defaultView());
        }
        addContextForModuleView(cx, mv);

        // Forward edges
        for (ViewDependence d : mi.requiresModules()) {
            if (d.modifiers().contains(Modifier.LOCAL)) {
                ModuleView smv = res.moduleViewForName.get(d.query().name());
                if (smv == null) {
                    // Unsatisfied optional dependence
                    assert d.modifiers().contains(Modifier.OPTIONAL);
                    continue;
                }
                Cx scx = contextForModuleView.get(smv.id().name());
                ModuleInfo smi = smv.moduleInfo();
                if (scx != null) {
                    assert cx == scx;
                    continue;
                }

                build(cx, smv, smi);
            }
        }

        // Back edges
        List<String> localRequestors
            = localRequestorsOfName.get(mv.id().name());
        if (localRequestors != null) {
            for (String rmn : localRequestors) {
                Cx rcx = contextForModuleView.get(rmn);
                if (rcx != null) {
                    assert cx == rcx;
                    continue;
                }
                // requestor must be a module name
                ModuleView rmv = res.moduleViewForName.get(rmn);
                assert rmv != null;
                build(cx, rmv, rmv.moduleInfo());
            }
        }

    }

    private void run() {
        findLocalRequestors();
        for (ModuleInfo mi : res.modules) {
            for (ModuleView mv : mi.views()) {
                if (contextForModuleView.containsKey(mv.id().name()))
                    continue;
                Cx cx = contextForModuleView.get(mi.id().name());
                build(cx, mv, mi);
            }
        }
        for (Cx cx : contexts)
            cx.freeze();
    }

    // Entry point
    //
    static <Cx extends BaseContext> ContextSet<Cx>
        run(Resolution res, ContextFactory<Cx> cxf)
    {

        for (ModuleIdQuery rq : res.rootQueries)
            assert res.moduleViewForName.get(rq.name()) != null : rq;

        ContextBuilder<Cx> cb = new ContextBuilder<Cx>(res, cxf);
        cb.run();
        for (ModuleIdQuery rq : res.rootQueries)
            assert cb.contextForModuleView.get(rq.name()) != null : rq;

        // Rehash the contexts so that the resulting ContextSet
        // doesn't contain an IdentityHashSet
        Set<Cx> rehashedContexts = new HashSet<>(cb.contexts);
        return new ContextSet<Cx>(res, rehashedContexts,
                                  cb.contextForModuleView);
    }

}
