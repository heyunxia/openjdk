/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
import static org.openjdk.jigsaw.Repository.ModuleMetaData;
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

    // Cache of service interface to synthesized dependences of
    // service provider modules
    private Map<String,Set<ViewDependence>> synthesizedServiceDependences = new HashMap<>();
    // FIFO queue of service interfaces corresponding to "requires service"
    // clauses of modules that have been visited by the resolver.
    // ## This queue should be sorted in a topological order
    private Deque<String> serviceInterfaceQueue = new LinkedList<>();

    private static void fail(String fmt, Object ... args) // cf. Linker.fail
        throws ConfigurationException
    {
        throw new ConfigurationException(fmt, args);
    }

    // ## Open issue: should aliases have versions?
    //
    // ## If alias has no version, a query matches an alias only if
    // ## the query does not specify a version (i.e. requires java.base;)
    // ## However, this conflicts with the current spec of the
    // ## synthesized dependence on java.base with a version constraint of
    // ## >= N version constraint is inserted in the module-info.class
    // ## at compile time if the module doesn't declare an explicit
    // ## dependence on java.base or not the java.base module itself.
    private boolean matchesQuery(ViewDependence dep, ModuleId mid) {
        boolean rv = dep.query().matches(mid);
        if (!rv) {
            // Allow this synthesized dependence for now until this issue
            // is resolved.
            String mn = dep.query().name();
            if (dep.modifiers().contains(Modifier.SYNTHESIZED) && mn.equals("java.base")) {
                return mid.name().equals(mn);
            }
        }

        return rv;
    }

    private ModuleId getModuleId(String mn, ModuleView mv) {
        if (mv.id().name().equals(mn)) {
            return mv.id();
        } else {
            for (ModuleId alias : mv.aliases()) {
                // ## if alias has version, multiple aliases matching the given name?
                if (alias.name().equals(mn)) {
                    return alias;
                }
            }
        }
        return null;
    }

    // Does the supplying module smi permit the requesting module rmi
    // to require it?
    //
    private boolean permits(ModuleInfo rmi, ViewDependence dep, ModuleView smv) {
        assert matchesQuery(dep, getModuleId(dep.query().name(), smv));
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
    private static class Choice {
        protected final ModuleInfo rmi;   // Requesting module
        protected final ViewDependence dep;   // Dependence to be satisfied
        protected final Choice next;      // Next choice in stack
        private Choice(ModuleInfo mi, ViewDependence d, Choice ch) {
            rmi = mi;
            dep = d;
            next = ch;
        }

        public String toString() {
            return (rmi != null ? rmi.id() : "ROOT") + " " + dep;
        }
    }

    private static class RootChoice extends Choice {
        private RootChoice(ViewDependence d, Choice ch) {
            super(null, d, ch);
        }
    }

    private static class ServiceProviderChoice extends Choice {
        private ServiceProviderChoice(ViewDependence d, Choice ch) {
            super(null, d, ch);
        }

        public String toString() {
            return "SERVICE PROVIDER " + dep;
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
            trace(1, depth, "resolving %s", choice);

        String mn = dep.query().name();

        // First check to see whether we've already resolved a module with
        // the given name.  If so then it must satisfy the constraints, else
        // we fail since we don't support side-by-side versioning at run time.
        //
        ModuleView mv = moduleViewForName.get(mn);
        ModuleInfo mi = mv != null ? mv.moduleInfo() : null;
        if (mi != null) {
            boolean rv = (matchesQuery(dep, getModuleId(mn, mv))
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
            if (!matchesQuery(dep, mid))
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
                    if (!matchesQuery(dep, mid))
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

        assert matchesQuery(dep, mid);

        assert moduleViewForName.get(mid.name()) == null;

        // Find and read the ModuleInfo, saving its location
        // and size data, if any
        //
        ModuleInfo mi = null;
        URI ml = null;
        ModuleMetaData mmd = null;
        for (Catalog c = cat; c != null; c = c.parent()) {
            mi = c.readLocalModuleInfo(mid);
            if (mi != null) {
                if (c != this.cat && c instanceof LocatableCatalog) {
                    ml = ((LocatableCatalog)c).location();
                    assert ml != null;
                }
                if (c instanceof RemoteRepository)
                    mmd = ((RemoteRepository)c).fetchMetaData(mid);
                break;
            }
        }
        if (mi == null)
            throw new AssertionError("No ModuleInfo for " + mid
                                     + "; initial catalog " + cat.name());

        // Find the supplying module view
        ModuleView smv = null;
        for (ModuleView mv : mi.views()) {
            if (mv.id().equals(mid) || mv.aliases().contains(mid)) {
                smv = mv;
                break;
            }
        }

        // Check this module's permits constraints
        //
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

        // add module views to the map
        for (ModuleView mv : mi.views()) {
            moduleViewForName.put(mv.id().name(), mv);
            for (ModuleId alias : mv.aliases()) {
                moduleViewForName.put(alias.name(), mv);
            }
        }

        // Save the module's location, if known
        //
        if (ml != null)
            locationForName.put(smn, ml);

        // Save the module's download and install sizes, if any
        //
        if (mmd != null) {
            modulesNeeded.add(mi.id());
            downloadRequired += mmd.getDownloadSize();
            spaceRequired += mmd.getInstallSize();
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

        // Push the service interfaces, if any, onto queue to be processed later
        Set<ServiceDependence> serviceDeps = mi.requiresServices();
        // Check point the service interface queue size so partial state can be
        // reverted if dependencies of this module cannot be resolved
        int sizeOfServiceInterfaceQueue = serviceInterfaceQueue.size();
        if (!serviceDeps.isEmpty()) {
            for (ServiceDependence sd: mi.requiresServices()) {
                final String serviceInterface = sd.service();

                // If this service interface has not been placed on the queue
                if (!serviceInterfaceQueue.contains(serviceInterface)) {
                    serviceInterfaceQueue.addLast(serviceInterface);
                    if (tracing)
                        trace(1, depth, "pushing service interface %s", serviceInterface);
                }
            }
        }

        // Recursively examine the next choice
        //
        if (!resolve(depth + 1, ch)) {
            // Revert service interface queue
            int d = serviceInterfaceQueue.size() - sizeOfServiceInterfaceQueue;
            while (d-- > 0) {
                serviceInterfaceQueue.removeLast();
            }

            // Revert maps, then fail
            modules.remove(mi);
            for (ModuleView mv : mi.views()) {
                moduleViewForName.remove(mv.id().name());
                for (ModuleId alias : mv.aliases()) {
                    moduleViewForName.remove(alias.name());
                }
            }
            if (ml != null)
                locationForName.remove(smn);
            if (mmd != null) {
                modulesNeeded.remove(mi.id());
                downloadRequired -= mmd.getDownloadSize();
                spaceRequired -= mmd.getInstallSize();
            }
            if (tracing)
                trace(1, depth, "fail: %s", mid);
            return false;

        }

        return true;
    }


    // Get the synthesized service provider module dependences for a
    // service interface.
    //
    // The catalog will be searched for service provider modules and the
    // result will be cached since such a search is potentially expensive.
    // TODO: The catalog could index service provider modules.
    //
    private Set<ViewDependence> getServiceProviderDependences(String serviceInterface,
                                                               Catalog cat)
        throws IOException
    {
        Set<ViewDependence> serviceDependences =
            synthesizedServiceDependences.get(serviceInterface);
        if (serviceDependences != null)
            return serviceDependences;

        serviceDependences = new LinkedHashSet<>();
        for (ModuleId providerId : cat.listDeclaringModuleIds()) {
            final ModuleInfo provider = cat.readModuleInfo(providerId);
            // If any view provides a service provider class then
            // add an optional view dependency for the module
            // itself
            for (ModuleView providerView : provider.views()) {
                if (providerView.services().containsKey(serviceInterface)) {
                    ModuleIdQuery q =
                        new ModuleIdQuery(provider.id().name(), null);
                    ViewDependence vd =
                        new ViewDependence(EnumSet.of(Modifier.OPTIONAL), q);
                    serviceDependences.add(vd);
                    break;
                }
            }
        }
        synthesizedServiceDependences.put(serviceInterface, serviceDependences);
        return serviceDependences;
    }

    // Phase n, n > 0: resolve services
    //
    // Resolving of service provider module dependencies may
    // result further service provider module dependencies.
    // These are treated as distinct phases
    private void resolveServices() throws ConfigurationException, IOException {
        int phase = 0;

        while (!serviceInterfaceQueue.isEmpty()) {
            phase++;
            if (tracing)
                trace(1, 0, "service provider module dependency resolving phase %d", phase);

            // Get the service provider module dependencies for the
            // service interface and create choices
            // ## Include from remote repositories?
            List<Choice> choices = new ArrayList<>();
            while (!serviceInterfaceQueue.isEmpty()) {
                String serviceInterface = serviceInterfaceQueue.removeFirst();
                trace(1, 1, "service interface %s", serviceInterface);

                Set<ViewDependence> vds = getServiceProviderDependences(serviceInterface, cat);
                if (vds.isEmpty()) {
                    trace(1, 2, "no service provider modules found");
                }

                for (ViewDependence vd: vds) {
                    if (tracing)
                        trace(1, 2, "dependency %s", vd);

                    // ## Should duplicate service provider dependencies
                    // be retained
                    choices.add(new ServiceProviderChoice(vd, null));
                }
            }

            // Resolve each service provider module dependency in this phase
            for (Choice c: choices) {
                // Result will always be true since dependency is optional
                resolve(1, c);

                // ## If the service provider module dependency has not been
                // resolved then log warning as to why
            }
        }
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
            ch = new RootChoice(dep,  ch);
        }

        // Phase 0: resolve application
        boolean resolved = resolve(0, ch);
        if (resolved) {
            // If success then resolve service provider modules, if any
            resolveServices();
            ensureServicesPresent();
        }
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
