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
 * questions.30
 */

package org.openjdk.jigsaw;

import java.lang.module.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

import static java.lang.module.Dependence.Modifier;
import static org.openjdk.jigsaw.Trace.*;


// ## TODO: Implement intra-context dominant-shadow algorithm

final class Linker {

    // We extend the plain Context class with additional state for use
    // during the linking process
    //
    static class Context
        extends org.openjdk.jigsaw.Context
        implements LinkingContext
    {

        Context() { }                   // Needed by Configurator

        // A context-for-package map that returns actual contexts,
        // rather than context names as in the superclass
        //
        private Map<String,Context> contextForPackage
            = new HashMap<String,Context>();

        // The ModuleInfos of the modules in this context
        //
        Set<ModuleInfo> moduleInfos = new HashSet<ModuleInfo>(); // ## private?

        public Set<ModuleInfo> moduleInfos() { return moduleInfos; }

        // This context's supplying contexts
        //
        private Set<Context> suppliers = new IdentityHashSet<>();

        // This context's re-exported supplying contexts
        //
        private Set<Context> reExportedSuppliers = new IdentityHashSet<>();

        // The set of packages defined by this context
        //
        private Set<String> packages = new HashSet<String>();

        // The set of packages exported by this context,
        // either directly or indirectly
        //
        private Set<String> exports = new HashSet<String>();

    }

    private final Library lib;
    private final ContextSet<Context> cxs;
    private final LibraryPool libPool;

    private Linker(Library l, ContextSet<Context> c) {
        lib = l;
        libPool = new LibraryPool(lib);
        cxs = c;
     }


    // -- 3. Resolve local suppliers --
    //
    // For a context containing just one module, resolving local suppliers is
    // trivial because there is only one supplier.
    //
    // For contexts with multiple modules, if a class is defined in more than
    // one module then we use the definition in the module that dominates the
    // local dependence graph.  If a dominant definition does not exist then
    // we fail. ## Not yet implemented
    //
    // --

    private String packageName(String cn) {
        int i = cn.lastIndexOf('.');
        if (i < 0)
            throw new IllegalArgumentException(cn + ": No package name");
        return cn.substring(0, i);
    }

    private void fail(String fmt, Object ... args)
        throws ConfigurationException
    {
        throw new ConfigurationException(fmt, args);
    }

    private void resolveLocalSuppliers()
        throws ConfigurationException, IOException
    {
        for (Context cx : cxs.contexts) {
            for (ModuleInfo mi : cx.moduleInfos) {
                Library l = libPool.get(cx, mi.id());
                for (String cn : l.listLocalClasses(mi.id(), true)) {
                    ModuleId smid = cx.findModuleForLocalClass(cn);
                    if (smid != null) {
                        // ## Do something more clever here: It should be possible
                        // ## to shadow definitions within a context when there is
                        // ## a dominant definition.
                        fail("Class %s: Multiple definitions in modules %s and %s",
                             cn, mi.id(), smid);
                    }
                    cx.putModuleForLocalClass(cn, mi.id());
                }
            }
        }
    }


    // -- 4. Resolve remote suppliers --
    //
    // To resolve remote suppliers we first compute, for each context, the
    // set of packages that it exports directly and the set of contexts whose
    // public classes it re-exports.  We then run a simple data-flow algorithm
    // to propagate re-exported packages throughout the context graph.
    //
    // --

    private boolean propagatePackage(boolean changed,
                                     Context cx, Context scx, String pn)
        throws ConfigurationException
    {
        if (cx.packages.contains(pn)) {
            fail("Package %s defined in %s but exported by supplier %s",
                 pn, cx, scx);
        }
        Context dcx = cx.contextForPackage.get(pn);
        if (dcx == null) {
            if (scx.packages.contains(pn))
                dcx = scx;
            else
                dcx = scx.contextForPackage.get(pn);
            cx.contextForPackage.put(pn, dcx);
            if (tracing && !Platform.isPlatformContext(dcx))
                trace(1, 1, "adding %s:%s to %s", dcx, pn, cx);
            if (cx.reExportedSuppliers.contains(scx))
                cx.exports.add(pn);
            changed = true;
        } else if (dcx != scx) {
            if (dcx != scx.contextForPackage.get(pn))
                fail("Package %s defined in both %s and %s", pn, scx, dcx);
        }
        return changed;
    }

    private void propagateExports()
        throws ConfigurationException
    {
        int n = 0;
        for (;;) {
            n++;
            if (tracing)
                trace(1, "propagating suppliers (pass %d)", n);
            boolean changed = false;
            for (Context cx : cxs.contexts) {
                for (Context scx : cx.suppliers) {
                    for (String pn : scx.exports)
                        changed = propagatePackage(changed, cx, scx, pn);
                }
            }
            if (!changed)
                return;
        }
    }

    private void resolveRemoteSuppliers()
        throws ConfigurationException, IOException
    {

        // Prepare export and supplier sets
        for (Context cx : cxs.contexts) {
            for (ModuleInfo mi : cx.moduleInfos) {
                Library l = libPool.get(cx, mi.id());
                for (String cn : l.listLocalClasses(mi.id(), false)) {
                    String pn = packageName(cn);
                    cx.packages.add(pn);
                    cx.exports.add(pn);
                }
                for (Dependence d : mi.requires()) {
                    Context scx = cxs.contextForModule.get(d.query().name());
                    if (scx == null) {
                        // Unsatisfied optional dependence
                        assert d.modifiers().contains(Modifier.OPTIONAL);
                        continue;
                    }
                    if (scx == cx) {
                        // Same context
                        continue;
                    }

                    if (!d.modifiers().contains(Modifier.LOCAL)) {
                        // Dependence upon some other context
                        cx.suppliers.add(scx);
                    }
                    if (d.modifiers().contains(Modifier.PUBLIC)) {
                        // Required publicly, so re-export it
                        cx.reExportedSuppliers.add(scx);
                    }
                }
            }
        }

        // Flow
        propagateExports();

    }

    private void run()
        throws ConfigurationException, IOException
    {

        // Compute context import/export/supplier maps
        resolveLocalSuppliers();
        resolveRemoteSuppliers();

        // Synchronize the context-for-package maps
        for (Context cx : cxs.contexts) {
            for (Map.Entry<String,Context> me
                     : cx.contextForPackage.entrySet())
            {
                cx.putContextForRemotePackage(me.getKey(),
                                              me.getValue().name());
            }
        }

    }

    // Entry point
    //
    static Configuration<org.openjdk.jigsaw.Context>
        run(Library lib, ContextSet<Linker.Context> cxs)
        throws ConfigurationException, IOException
    {
        new Linker(lib, cxs).run();
        List<ModuleId> rids = new ArrayList<>();
        for (ModuleIdQuery rq : cxs.rootQueries)
            rids.add(cxs.moduleForName.get(rq.name()).id());
        return new Configuration<org.openjdk.jigsaw.Context>(rids,
                                   cxs.contexts,
                                   cxs.contextForModule);
    }

}
