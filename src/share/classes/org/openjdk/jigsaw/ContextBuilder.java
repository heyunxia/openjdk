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

package org.openjdk.jigsaw;

import java.lang.module.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

import static java.lang.module.Dependence.Modifier;
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
            for (Dependence d : mi.requires()) {
                if (d.modifiers().contains(Modifier.LOCAL)) {
                    ModuleInfo smi = res.moduleForName.get(d.query().name());
                    if (smi == null) {
                        // smi can be null if dependence is optional
                        assert d.modifiers().contains(Modifier.OPTIONAL);
                        continue;
                    }
                    addLocalRequestor(mi.id().name(), smi.id().name());
                }
            }
        }
    }

    // All of our contexts
    //
    private Set<Cx> contexts = new IdentityHashSet<>();

    // For each module, its assigned context; this also serves
    // as the visited-node set during context construction
    //
    private Map<String,Cx> contextForModule
        = new HashMap<String,Cx>();

    // Add the given module to the given context, or create a new context for
    // that module if none is given, and then add all the other modules in the
    // module's locally-connected component to the same context
    //
    private void build(Cx pcx, ModuleInfo mi) {

        assert !contextForModule.containsKey(mi.id().name());

        Cx cx = pcx;
        if (cx == null) {
            cx = cxf.create();
            contexts.add(cx);
        }
        cx.add(mi.id());
        if (cx instanceof LinkingContext)
            ((LinkingContext)cx).moduleInfos().add(mi);
        contextForModule.put(mi.id().name(), cx);

        // Forward edges
        for (Dependence d : mi.requires()) {
            if (d.modifiers().contains(Modifier.LOCAL)) {
                Cx scx = contextForModule.get(d.query().name());
                if (scx != null) {
                    assert cx == scx;
                    continue;
                }
                ModuleInfo smi = res.moduleForName.get(d.query().name());
                assert smi != null;
                if (smi == null) {
                    // Unsatisfied optional dependence
                    assert d.modifiers().contains(Modifier.OPTIONAL);
                    continue;
                }
                build(cx, smi);
            }
        }

        // Back edges
        List<String> localRequestors
            = localRequestorsOfName.get(mi.id().name());
        if (localRequestors != null) {
            for (String rmn : localRequestors) {
                Cx rcx = contextForModule.get(rmn);
                if (rcx != null) {
                    assert cx == rcx;
                    continue;
                }
                ModuleInfo rmi = res.moduleForName.get(rmn);
                assert rmi != null;
                build(cx, rmi);
            }
        }

    }

    private void run() {
        findLocalRequestors();
        for (ModuleInfo mi : res.modules) {
            if (contextForModule.containsKey(mi.id().name()))
                continue;
            build(null, mi);
        }
        for (Cx cx : contexts)
            cx.freeze();
    }

    // Entry point
    //
    static <Cx extends BaseContext> ContextSet<Cx>
        run(Resolution res, ContextFactory<Cx> cxf)
    {

        assert res.moduleForName.get(res.rootQuery.name()) != null;
        ContextBuilder<Cx> cb = new ContextBuilder<Cx>(res, cxf);
        cb.run();
        assert cb.contextForModule.get(res.rootQuery.name()) != null;

        // Rehash the contexts so that the resulting ContextSet
        // doesn't contain an IdentityHashSet
        Set<Cx> rehashedContexts = new HashSet<>(cb.contexts);

        return new ContextSet<Cx>(res, rehashedContexts, cb.contextForModule);

    }

}
