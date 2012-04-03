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

    private Set<ModuleInfo> modules = new HashSet<>();

    private Map<String,ModuleView> moduleViewForName
        = new HashMap<>();
    private Map<String,URI> locationForName = new HashMap<>();
    
    // modules needs to be downloaded from remote repository
    private Set<ModuleId> modulesNeeded = new HashSet<>();

    private long spaceRequired = 0;
    private long downloadRequired = 0;
    
    // cache of dependencies synthesized for services
    private Map<ModuleId,List<ViewDependence>> synthesizedDeps = new HashMap<>();
    

    private static void fail(String fmt, Object ... args) // cf. Linker.fail
        throws ConfigurationException
    {
        throw new ConfigurationException(fmt, args);
    }

    // Does the supplying module smi permit the requesting module rmi
    // to require it?
    //
    private boolean permits(ModuleInfo rmi, ViewDependence dep, ModuleView smv) {
        assert dep.query().matches(smv.id());
        if (rmi == null) {
            // Special case: Synthetic root dependence
            return true;
        }
        Set<String> ps = smv.permits();
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
        private final ViewDependence dep;   // Dependence to be satisfied
        private final Choice next;      // Next choice in stack
        private Choice(ModuleInfo mi, ViewDependence d, Choice ch) {
            rmi = mi;
            dep = d;
            next = ch;
        }
    }
    
    // Resolve the given choice
    //
    private boolean resolve(int depth, Choice choice)
        throws ConfigurationException, IOException
    {

        if (choice == null) {
            // Success!
            return true;
        }

        ModuleInfo rmi = choice.rmi;
        ViewDependence dep = choice.dep;

        if (tracing)
            trace(1, depth, "resolving %s %s",
                  rmi != null ? rmi.id() : "ROOT", dep);
        
        String mn = dep.query().name();

        // First check to see whether we've already resolved a module with
        // the given name.  If so then it must satisfy the constraints, else
        // we fail since we don't support side-by-side versioning at run time.
        //
        ModuleView mv = moduleViewForName.get(mn);
        ModuleInfo mi = mv != null ? mv.moduleInfo() : null;
        if (mi != null) {
            boolean rv = (dep.query().matches(mv.id())
                          && permits(rmi, dep, mv));
            if (!rv) {
                if (tracing)
                    trace(1, depth, "fail: previously-resolved %s (module %s) unacceptable",
                          mv.id(), mi.id());
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
                            ModuleInfo rmi, ViewDependence dep,
                            Catalog cat, ModuleId mid)
        throws ConfigurationException, IOException
    {

        if (tracing) {
            String loc = "";
            if (cat instanceof RemoteRepository)
                loc = " " + ((LocatableCatalog)cat).location();
            trace(1, depth, "trying %s%s", mid, loc);
        }

        assert dep.query().matches(mid);
        
        assert moduleViewForName.get(mid.name()) == null;

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

        // Check this module's permits constraints
        //
        ModuleView smv = null;
        for (ModuleView mv : mi.views()) {
            if (mv.id().equals(mid)) {
                smv = mv;
                break;
            }
        }
        if (!permits(rmi, dep, smv)) {
            if (tracing)
                trace(1, depth, "fail: permits %s", smv.permits());
            return false;
        }

        // Save the ModuleView in the moduleViewForName map,
        // which also serves as our visited-node set
        //
        String smn = mi.id().name();
        modules.add(mi);
        
        // add module views
        for (ModuleView mv : mi.views()) {
            moduleViewForName.put(mv.id().name(), mv);
        }

        // Save the module's location, if known
        //
        if (ml != null)
            locationForName.put(smn, ml);

        // Save the module's download and install sizes, if any
        //
        if (ms != null) {
            modulesNeeded.add(mi.id());
            downloadRequired += ms.download();
            spaceRequired += ms.install();
        }

        // Push this module's dependences onto the choice stack,
        // in reverse order so that the choices are examined in
        // forward order
        //
        Choice ch = nextChoice;
        // ## ModuleInfo.requires() should be a list, not a set
        List<ViewDependence> dl = new ArrayList<>(mi.requiresModules());
        Collections.reverse(dl);
        for (ViewDependence d : dl)
            ch = new Choice(mi, d, ch);
        
        // Push an optional dependency onto the choice stack for each module
        // that is a potential supplier to this module.
        // ## Include service implementations in remote repositories? 
        //
        Set<ServiceDependence> serviceDeps = mi.requiresServices();
        if (!serviceDeps.isEmpty()) {
            // use synthesized dependencies if previously generated
            List<ViewDependence> viewDeps = synthesizedDeps.get(mi.id());
            if (viewDeps == null) {
                viewDeps = new ArrayList<>();
                for (ServiceDependence sd: mi.requiresServices()) {
                    String sn = sd.service();
                    for (ModuleId other: cat.listModuleIds()) { 
                        for (ModuleView view: cat.readModuleInfo(other).views()) {
                            Set<String> providers = view.services().get(sn); 
                            if (providers != null) {
                                ModuleIdQuery q = 
                                    new ModuleIdQuery(view.id().name(), null);
                                ViewDependence vd =
                                    new ViewDependence(EnumSet.of(Modifier.OPTIONAL), q);
                                viewDeps.add(vd);
                            }
                        }
                    }
                }
                Collections.reverse(viewDeps);
                synthesizedDeps.put(mi.id(), viewDeps);
            }                
            for (ViewDependence vd: viewDeps)
                ch = new Choice(mi, vd, ch);   
        }
        
        // Recursively examine the next choice
        //
        if (!resolve(depth + 1, ch)) {

            // Revert maps, then fail
            modules.remove(mi);
            for (ModuleView mv : mi.views()) {
                moduleViewForName.remove(mv.id().name());
            }
            if (ml != null)
                locationForName.remove(smn);
            if (ms != null) {
                modulesNeeded.remove(mi.id());
                downloadRequired -= ms.download();
                spaceRequired -= ms.install();
            }
            if (tracing)
                trace(1, depth, "fail: %s", mid);
            return false;

        }

        return true;
    }

    // Checks that chosen modules that require a service have at least one
    // implementation in the chosen set.
    //
    private void ensureServicesPresent() throws ConfigurationException {
        Set<String> serviceTypes = new HashSet<>();
        for (ModuleInfo mi: modules) {
            for (ModuleView view: mi.views()) {
                Map<String,Set<String>> services = view.services();
                serviceTypes.addAll(services.keySet());
            }   
        }
        for (ModuleInfo mi: modules) {
            for (ServiceDependence sd: mi.requiresServices()) {
                if (!sd.modifiers().contains((Modifier.OPTIONAL))) {
                    String sn = sd.service();
                    if (!serviceTypes.contains(sn)) {
                        fail("No implementations of service %s, required by %s", 
                             sn, mi.id());
                    }

                }
            }
        }    
    }

    private boolean run()
        throws ConfigurationException, IOException
    {
        Choice ch = null;
        for (ModuleIdQuery midq : rootQueries) {
            ViewDependence dep = new ViewDependence(EnumSet.noneOf(Modifier.class),
                                                    midq);
            ch = new Choice(null, dep,  ch);
        }
        boolean resolved = resolve(0, ch);
        if (resolved)
            ensureServicesPresent();
        return resolved;
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
                              r.moduleViewForName,
                              r.locationForName,
                              r.modulesNeeded,
                              r.downloadRequired, r.spaceRequired);
    }

}
