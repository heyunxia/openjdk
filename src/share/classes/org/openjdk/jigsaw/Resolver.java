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


/**
 * <p> Compute a {@linkplain Configuration configuration} for a root module in
 * a given {@linkplain Library module library}. </p>
 *
 * <p> Each root module (<i>i.e.</i>, a module with a main class) induces a
 * unique configuration.  We can compute a configuration at install time or, if
 * a module is downloaded at run time (<i>e.g.</i>, an applet or web-start
 * app), then dynamically as part of the download process.  The resolution
 * algorithm is, in any case, <a
 * href="http://en.wikipedia.org/wiki/Offline_algorithm">offline</a>;
 * <i>i.e.</i>, it produces a complete configuration prior to application
 * startup. </p>
 *
 * <p> At most one version of a module can be present in a configuration; we do
 * not support side-by-side versioning at run time.  This vastly simplifies the
 * resolution algorithm and is no less expressive, in practical terms, than the
 * present class-path mechanism. </p>
 *
 * <p> Multiple modules defining types in the same package must all be assigned
 * to the same context, so that they all wind up in the same class loader at
 * run time.  This allows us to reason about, and store, cross-context
 * dependences in terms of package names rather than individual type names,
 * saving both time and space. </p>
 *
 * <p> A type definition in a module may be shadowed only by some other module
 * in the same context, and the shadowing definition must dominate all other
 * definitions. </p>
 *
 * <p> In a module library we store a root module's entire configuration along
 * with the module itself rather than spread the information amongst the
 * modules upon which it depends.  This allows us to support module libraries
 * in a path-like arrangement, much like LD_LIBRARY_PATH.  The configurations
 * stored in a library will need to be updated whenever libraries later in the
 * path are updated, but this should not be too onerous since we expect such
 * paths to be short, e.g., at most an application library, a user library, and
 * a system library. </p>
 *
 * <p> <i>For further commentary on the details of the resolution algorithm,
 * please see the <a
 * href="http://hg.openjdk.java.net/jigsaw/jigsaw/jdk/file/tip/src/share/classes/org/openjdk/jigsaw/Resolver.java">source
 * code</a>.</i> </p>
 *
 * @see Library
 * @see Resolver
 */

// ## TODO: Implement intra-context dominant-shadow algorithm
// ## TODO: Implement optional dependences
// ## TODO: Implement provides
// ## TODO: Improve error messages

// ## TODO: Create an alternate version of phase 3, for use by compilers
//
// A Java compiler can't use the full resolution algorithm because it can't
// determine, at the start of an invocation, exactly which classes are going to
// be processed, i.e., either compiled anew or read in from the output
// directory of a previous invocation.
//
// A compiler can, however, determine exactly which module-info files will be
// processed.  As long as all the relevant module-info files are available then
// we can implement an alternative version of phase 3 which uses a dominator
// algorithm to compute a linear ordering of the modules in a context.  This
// ordering will guarantee that if there is any shadowing of a class amongst
// the modules then the module containing the dominant definition, if there is
// one, will precede the others.  The result of this algorithm can be reported
// in, say, a CompileTimeConfiguration object which has a visibleModules()
// property of type Map<ModuleId,List<ModuleId>>.
//
// The use of different algorithms at compile time vs. configuration time does
// admit slightly different outcomes.  If a class has multiple definitions in
// a context but no dominant definition then at compile time an arbitrary
// definition will be visible but at configuration time an error will be
// reported.  This is unfortunate but acceptable; it's somewhat akin to the
// {@linkplain java.lang.LinkageError class-linkage errors} which can occur
// when classes change incompatibly, and is likely to be encountered mainly
// by advanced developers.

public final class Resolver {

    // --
    //
    // Module resolution proceeds in four phases:
    //
    //   1. Resolve versions -- Determine the version of each module that will
    //      be part of the resulting configuration.
    //
    //   2. Construct contexts -- Assign modules to contexts, ensuring that the
    //      local dependences of a module are assigned to the same context.
    //
    //   3. Resolve local suppliers -- For each class defined in a context,
    //      determine which of the context's modules will supply it.
    //
    //   4. Resolve remote suppliers -- For each package imported into a
    //      context, either directly or indirectly (via "requires public"),
    //      determine the context that will supply it.
    //
    // --

    // Variable-name conventions
    //
    // mi = ModuleInfo
    // mid = ModuleId
    // nm = module name
    // pn = package name
    // cn = class name
    //
    // Prefixes: 'r' for a requesting module, 's' for a supplying module

    private final Library lib;
    private ModuleIdQuery rootQuery;

    private Resolver(Library l, ModuleIdQuery rq) {
        lib = l;
        rootQuery = rq;
    }

    private Set<ModuleInfo> modules = new HashSet<ModuleInfo>();

    private Map<String,ModuleInfo> moduleForName
        = new HashMap<String,ModuleInfo>();


    // -- 1. Resolve versions --
    //
    // We resolve module versions by doing a recursive depth-first search of
    // the space of possible choices.
    //
    // This algorithm will find the optimal set of module versions, if one
    // exists, in the sense that each version chosen will be the newest version
    // that satisfies all dependences upon it.
    //
    // Naively one might expect that we could just walk the dependence graph,
    // but that doesn't work.  Each choice that we make can change the shape
    // of the dependence graph since different versions of the same module can
    // have completely different dependences.
    //
    // --

    // Does the supplying module smi permit the requesting module rmi
    // to require it?
    //
    private boolean permits(ModuleInfo rmi, Dependence dep, ModuleInfo smi) {
        assert dep.query().matches(smi.id());
        if (rmi == null) {
            // Special case: Synthetic root dependence
            return true;
        }
        Set<String> ps = smi.permits();
        if (ps.isEmpty() && !dep.modifiers().contains(Modifier.LOCAL)) {
            // Non-local dependences are implicitly permitted
            // when the permit set is empty
            return true;
        }
        return ps.contains(rmi.id().name());
    }

    // A choice that remains to be made.  Choices are arranged in a stack,
    // using the next field.  Initially the stack is primed with the choice of
    // which module to assign as the root module.  When a candidate module is
    // identified for a particular choice then that module's dependences are
    // pushed onto the stack as further choices to be made.  If the stack ever
    // becomes empty then the algorithm terminates successfully.  If the search
    // completes without emptying the stack then it fails.
    //
    private static final class Choice {
        private final ModuleInfo rmi;   // Requesting module
        private final Dependence dep;   // Dependence to be satisfied
        private final Choice next;      // Next choice in stack
        private Choice(ModuleInfo mi, Dependence d, Choice ch) {
            rmi = mi;
            dep = d;
            next = ch;
        }
    }

    // Resolve the given choice
    //
    private boolean resolve(int depth, Choice choice)
        throws IOException
    {

        if (choice == null) {
            // Success!
            return true;
        }

        ModuleInfo rmi = choice.rmi;
        Dependence dep = choice.dep;

        if (tracing)
            trace(1, depth, "resolving %s %s",
                  rmi != null ? rmi.id() : "ROOT", dep);

        if (dep.modifiers().contains(Modifier.OPTIONAL)) {
            // Skip for now; we'll handle these later
            return resolve(depth + 1, choice.next);
        }

        String mn = dep.query().name();

        // First check to see whether we've already resolved a module with
        // the given name.  If so then it must satisfy the constraints, else
        // we fail since we don't support side-by-side versioning at run time.
        //
        ModuleInfo mi = moduleForName.get(mn);
        if (mi != null) {
            boolean rv = (dep.query().matches(mi.id())
                          && permits(rmi, dep, mi));
            if (!rv) {
                if (tracing)
                    trace(1, depth, "fail: previously-resolved %s unacceptable",
                          mi.id());
                return false;
            }
            return resolve(depth + 1, choice.next);
        }

        // No resolved module of this name yet, so go try to find one.
        // We prefer newer versions to older versions.
        //
        List<ModuleId> candidates = lib.findModuleIds(mn);
        Collections.sort(candidates, Collections.reverseOrder());
        for (ModuleId mid : candidates) {
            if (!dep.query().matches(mid))
                continue;
            if (resolve(depth + 1, choice.next, rmi, dep, mid))
                return true;
        }

        if (tracing)
            trace(1, depth, "fail: %s", dep);
        return false;

    }

    // Consider a candidate module for the given requesting module and
    // dependence
    //
    private boolean resolve(int depth, Choice nextChoice,
                            ModuleInfo rmi, Dependence dep, ModuleId mid)
        throws IOException
    {

        if (tracing)
            trace(1, depth, "trying %s", mid);

        assert dep.query().matches(mid);
        assert moduleForName.get(mid.name()) == null;

        ModuleInfo mi = lib.readModuleInfo(mid);
        Platform.adjustPlatformDependences(mi);

        // Check this module's permits constraints
        //
        if (!permits(rmi, dep, mi)) {
            if (tracing)
                trace(1, depth, "fail: permits %s", mi.permits());
            return false;
        }

        // Save the ModuleInfo in the moduleForName map,
        // which also serves as our visited-node set
        //
        modules.add(mi);
        moduleForName.put(mid.name(), mi);

        // Push this module's dependences onto the choice stack,
        // in reverse order so that the choices are examined in
        // forward order
        //
        Choice ch = nextChoice;
        // ## ModuleInfo.requires() should be a list, not a set
        List<Dependence> dl = new ArrayList<Dependence>(mi.requires());
        Collections.reverse(dl);
        for (Dependence d : dl)
            ch = new Choice(mi, d, ch);

        // Recursively examine the next choice
        //
        if (!resolve(depth + 1, ch)) {
            // Revert map, then fail
            modules.remove(mi);
            moduleForName.remove(mid.name());
            if (tracing)
                trace(1, depth, "fail: %s", mid);
            return false;
        }

        return true;

    }

    // Entry point
    //
    private boolean resolve(ModuleIdQuery rq)
        throws IOException
    {
        Dependence dep = new Dependence(EnumSet.noneOf(Modifier.class), rq);
        return resolve(0, new Choice(null, dep,  null));
    }


    // -- 2. Construct contexts --
    //
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
    //
    // --

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
        for (ModuleInfo mi : modules) {
            for (Dependence d : mi.requires()) {
                if (d.modifiers().contains(Modifier.LOCAL)) {
                    ModuleInfo smi = moduleForName.get(d.query().name());
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

    // We extend the plain Context class with additional state for use
    // during the resolution process
    //
    private static class Context
        extends org.openjdk.jigsaw.Context
    {

        // A context-for-package map that returns actual contexts,
        // rather than context names as in the superclass
        //
        private Map<String,Context> contextForPackage
            = new HashMap<String,Context>();

        // The ModuleInfos of the modules in this context
        //
        private Set<ModuleInfo> moduleInfos = new HashSet<ModuleInfo>();

        // This context's supplying contexts
        //
        private Set<Context> suppliers = new HashSet<Context>();

        // This context's re-exported supplying contexts
        //
        private Set<Context> reExportedSuppliers = new HashSet<Context>();

        // The set of packages defined by this context
        //
        private Set<String> packages = new HashSet<String>();

        // The set of packages exported by this context,
        // either directly or indirectly
        //
        private Set<String> exports = new HashSet<String>();

    }

    // All of our contexts
    //
    private Set<Context> contexts = new LinkedHashSet<Context>();

    // For each module, its assigned context; this also serves
    // as the visited-node set during context construction
    //
    private Map<String,Context> contextForModule
        = new HashMap<String,Context>();

    // Add the given module to the given context, or create a new context for
    // that module if none is given, and then add all the other modules in the
    // module's locally-connected component to the same context
    //
    private void build(Context pcx, ModuleInfo mi)
        throws ConfigurationException
    {

        assert !contextForModule.containsKey(mi.id().name());

        Context cx = pcx;
        if (cx == null) {
            cx = new Context();
            contexts.add(cx);
        }
        cx.add(mi.id());
        cx.moduleInfos.add(mi);
        contextForModule.put(mi.id().name(), cx);

        // Forward edges
        for (Dependence d : mi.requires()) {
            if (d.modifiers().contains(Modifier.LOCAL)) {
                Context scx = contextForModule.get(d.query().name());
                if (scx != null) {
                    assert cx == scx;
                    continue;
                }
                ModuleInfo smi = moduleForName.get(d.query().name());
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
                Context rcx = contextForModule.get(rmn);
                if (rcx != null) {
                    assert cx == rcx;
                    continue;
                }
                ModuleInfo rmi = moduleForName.get(rmn);
                assert rmi != null;
                build(cx, rmi);
            }
        }

    }

    // Entry point
    //
    private void build()
        throws ConfigurationException
    {
        findLocalRequestors();
        for (ModuleInfo mi : modules) {
            if (contextForModule.containsKey(mi.id().name()))
                continue;
            build(null, mi);
        }
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
        for (Context cx : contexts) {
            for (ModuleInfo mi : cx.moduleInfos) {
                for (String cn : lib.listClasses(mi.id(), true)) {
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
            for (Context cx : contexts) {
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
        for (Context cx : contexts) {
            for (ModuleInfo mi : cx.moduleInfos) {
                for (String cn : lib.listClasses(mi.id(), false)) {
                    String pn = packageName(cn);
                    cx.packages.add(pn);
                    cx.exports.add(pn);
                }
                for (Dependence d : mi.requires()) {
                    Context scx = contextForModule.get(d.query().name());
                    if (scx == null) {
                        // Unsatisfied optional dependence
                        assert d.modifiers().contains(Modifier.OPTIONAL);
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


    // -- Top level --

    /**
     * <p> Create, but do not run, a new resolver. </p>
     *
     * <p> The configuration root is specified in the form of a {@linkplain
     * java.lang.module.ModuleIdQuery module-id query}.  If more than one
     * module in the given library satisfies the query then the most recent
     * version will be used. </p>
     *
     * @param   lib
     *          The {@linkplain Library module library} against which
     *          dependences will be resolved
     *
     * @param   rootQuery
     *          A {@linkplain java.lang.module.ModuleIdQuery module-id query}
     *          describing the desired root module
     *
     * @return  A newly-initialized resolver
     */
    public static Resolver create(Library lib,
                                  ModuleIdQuery rootQuery)
    {
        return new Resolver(lib, rootQuery);
    }

    /**
     * <p> Run this resolver. </p>
     *
     * @throws  IllegalStateException
     *          If this resolver has already been run
     *
     * @throws  ConfigurationException
     *          If a valid configuration cannot be computed
     *
     * @throws  IOException
     *          If an I/O error occurs while accessing the module library
     *
     * @return  The resulting {@linkplain Configuration configuration}
     */
    public Configuration run()
        throws ConfigurationException, IOException
    {

        if (!modules.isEmpty()) {
            // Been here, done that
            throw new IllegalStateException();
        }

        if (tracing)
            trace(0, "Configuring %s using library %s",
                  rootQuery, lib.name());

        // Resolve dependences
        if (!resolve(rootQuery))
            fail("%s: Cannot resolve", rootQuery);

        // Build context graph
        ModuleInfo root = moduleForName.get(rootQuery.name());
        assert root != null;
        build();
        Context rcx = contextForModule.get(rootQuery.name());
        assert rcx != null;

        // Compute context import/export/supplier maps
        resolveLocalSuppliers();
        resolveRemoteSuppliers();

        // Freeze context names
        for (Context cx : contexts) {
            cx.freeze();
        }

        // Synchronize the context-for-package maps
        for (Context cx : contexts) {
            for (Map.Entry<String,Context> me
                     : cx.contextForPackage.entrySet())
            {
                cx.putContextForRemotePackage(me.getKey(),
                                              me.getValue().name());
            }
        }

        // We're done!
        Configuration cf
            = new Configuration(root.id(), contexts, contextForModule);
        if (tracing) {
            trace(0, "Configured %s", root.id());
            if (traceLevel >= 3)
                cf.dump(System.out);
        }
        return cf;

    }

}
