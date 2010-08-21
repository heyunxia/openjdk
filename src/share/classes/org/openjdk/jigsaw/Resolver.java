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

package org.openjdk.jigsaw;

import java.lang.module.*;
import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.regex.*;

import static java.lang.module.Dependence.Modifier;
import static org.openjdk.jigsaw.Repository.ModuleSize;
import static org.openjdk.jigsaw.Trace.*;


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

// ## TODO: Implement provides
// ## TODO: Improve error messages

final class Resolver {

    // Variable-name conventions
    //
    // mi = ModuleInfo
    // mid = ModuleId
    // nm = module name
    // pn = package name
    // cn = class name
    //
    // Prefixes: 'r' for a requesting module, 's' for a supplying module

    private final Catalog cat;
    private Collection<ModuleIdQuery> rootQueries;

    private Resolver(Catalog c, Collection<ModuleIdQuery> rqs) {
        cat = c;
        rootQueries = rqs;
    }

    private Set<ModuleInfo> modules = new HashSet<ModuleInfo>();

    private Map<String,ModuleInfo> moduleForName
        = new HashMap<String,ModuleInfo>();

    private Map<String,URI> locationForName = new HashMap<>();

    private long spaceRequired = 0;
    private long downloadRequired = 0;

    private static void fail(String fmt, Object ... args) // cf. Linker.fail
        throws ConfigurationException
    {
        throw new ConfigurationException(fmt, args);
    }

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

    // A choice which remains to be made.  Choices are arranged in a stack,
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
        // We prefer newer versions to older versions, and we consider
        // all modules of the given name in our catalog and its parent
        // catalog(s), if any.
        //
        List<ModuleId> candidates = cat.findModuleIds(mn);
        Collections.sort(candidates, Collections.reverseOrder());
        for (ModuleId mid : candidates) {
            if (!dep.query().matches(mid))
                continue;
            if (resolve(depth + 1, choice.next, rmi, dep, cat, mid))
                return true;
        }

        if (dep.modifiers().contains(Modifier.OPTIONAL)) {
            // Don't fail; it's just an optional dependence
            return resolve(depth + 1, choice.next);
        }

        // No local module found, so if this catalog is a library then
        // consider its remote repositories, if any
        //
        // ## Policy issues: Anywhere vs. local, child vs. parent, ...
        //
        if (cat instanceof Library) {
            Library lib = (Library)cat;
            RemoteRepositoryList rrl = lib.repositoryList();
            RemoteRepository rr = rrl.firstRepository();
            if (rr != null) {
                candidates = rr.findModuleIds(mn);
                Collections.sort(candidates, Collections.reverseOrder());
                if (tracing)
                    trace(1, depth,
                          "considering candidates from repos of %s: %s",
                          lib.name(), candidates);
                for (ModuleId mid : candidates) {
                    if (!dep.query().matches(mid))
                        continue;
                    if (resolve(depth + 1, choice.next, rmi, dep, rr, mid))
                        return true;
                }
            }
        }

        if (tracing)
            trace(1, depth, "fail: %s", dep);
        return false;

    }

    // Consider a candidate module for the given requesting module and
    // dependence
    //
    private boolean resolve(int depth, Choice nextChoice,
                            ModuleInfo rmi, Dependence dep,
                            Catalog cat, ModuleId mid)
        throws IOException
    {

        if (tracing) {
            String loc = "";
            if (cat instanceof RemoteRepository)
                loc = " " + ((LocatableCatalog)cat).location();
            trace(1, depth, "trying %s%s", mid, loc);
        }

        assert dep.query().matches(mid);
        assert moduleForName.get(mid.name()) == null;

        // Find and read the ModuleInfo, saving its location
        // and size data, if any
        //
        ModuleInfo mi = null;
        URI ml = null;
        ModuleSize ms = null;
        for (Catalog c = cat; c != null; c = c.parent()) {
            mi = c.readLocalModuleInfo(mid);
            if (mi != null) {
                if (c != this.cat && c instanceof LocatableCatalog) {
                    ml = ((LocatableCatalog)c).location();
                    assert ml != null;
                }
                if (c instanceof RemoteRepository)
                    ms = ((RemoteRepository)c).sizeof(mid);
                break;
            }
        }
        if (mi == null)
            throw new AssertionError("No ModuleInfo for " + mid
                                     + "; initial catalog " + cat.name());

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

        // Save the module's location, if known
        //
        if (ml != null)
            locationForName.put(mid.name(), ml);

        // Save the module's download and install sizes, if any
        //
        if (ms != null) {
            downloadRequired += ms.download();
            spaceRequired += ms.install();
        }

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

            // Revert maps, then fail
            modules.remove(mi);
            moduleForName.remove(mid.name());
            if (ml != null)
                locationForName.remove(mid.name());
            if (ms != null) {
                downloadRequired -= ms.download();
                spaceRequired -= ms.install();
            }
            if (tracing)
                trace(1, depth, "fail: %s", mid);
            return false;

        }

        return true;

    }

    private boolean run()
        throws IOException
    {
        Choice ch = null;
        for (ModuleIdQuery midq : rootQueries) {
            Dependence dep = new Dependence(EnumSet.noneOf(Modifier.class),
                                            midq);
            ch = new Choice(null, dep,  ch);
        }
        return resolve(0, ch);
    }

    // Entry point
    //
    static Resolution run(Catalog cat, Collection<ModuleIdQuery> rootQueries)
        throws ConfigurationException, IOException
    {
        Resolver r = new Resolver(cat, rootQueries);
        if (!r.run())
            fail("%s: Cannot resolve",
                 (rootQueries.size() == 1
                  ? rootQueries.iterator().next()
                  : rootQueries));
        return new Resolution(rootQueries, r.modules,
                              r.moduleForName, r.locationForName,
                              r.downloadRequired, r.spaceRequired);
    }

}
