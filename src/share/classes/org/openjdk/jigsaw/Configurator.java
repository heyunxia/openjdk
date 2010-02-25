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
 * app), then dynamically as part of the download process.  The configuration
 * algorithm is, in any case, <a
 * href="http://en.wikipedia.org/wiki/Offline_algorithm">offline</a>;
 * <i>i.e.</i>, it produces a complete configuration prior to application
 * startup. </p>
 *
 * <p> At most one version of a module can be present in a configuration; we do
 * not support side-by-side versioning at run time.  This vastly simplifies the
 * configuration algorithm and is no less expressive, in practical terms, than
 * the present class-path mechanism. </p>
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
 * <p> Module configuration proceeds in four phases: </p>
 *
 * <ol>
 *
 *   <li><p> Resolve versions -- Determine the version of each module which
 *   will be part of the resulting configuration. </p></li>
 * 
 *   <li><p> Construct contexts -- Assign modules to contexts, ensuring that
 *   the local dependences of a module are assigned to the same
 *   context. </p></li>
 * 
 *   <li><p> Link local suppliers -- For each class defined in a context,
 *   determine which of the context's modules will supply it. </p></li>
 * 
 *   <li><p> Link remote suppliers -- For each package imported into a context,
 *   either directly or indirectly (via "requires public"), determine the
 *   context which will supply it. </p></li>
 *
 * </ol>
 *
 * <p> The first two phases are the same at both install time and run time.
 * The linking phases, however, are different as described below. </p>
 *
 * <p> <i>For further commentary on the details of the configuration algorithm,
 * please see the <a
 * href="http://hg.openjdk.java.net/jigsaw/jigsaw/jdk/file/tip/src/share/classes/org/openjdk/jigsaw/Configurator.java">source
 * code</a>.</i> </p>
 *
 * @see Library
 * @see Resolver
 * @see ContextBuilder
 * @see Linker
 */

public final class Configurator {

    private Configurator() { }

    /**
     * <p> Compute a full install-time {@linkplain Configuration configuration}
     * for a root module in a given {@linkplain Library module library}. </p>
     *
     * <p> The configuration root is specified in the form of a {@linkplain
     * java.lang.module.ModuleIdQuery module-id query}.  If more than one
     * module in the given library satisfies the query then the most recent
     * version will be used. </p>
     *
     * <p> In a module library we store a root module's entire configuration
     * along with the module itself rather than spread the information amongst
     * the modules upon which it depends.  This allows us to support module
     * libraries in a path-like arrangement, much like LD_LIBRARY_PATH.  The
     * configurations stored in a library will need to be updated whenever
     * libraries later in the path are updated, but this should not be too
     * onerous since we expect such paths to be short, e.g., at most an
     * application library, a user library, and a system library. </p>
     *
     * @param   lib
     *          The {@link Library} against which dependences will be resolved
     *
     * @param   rootQueries
     *          A collection of {@linkplain java.lang.module.ModuleIdQuery
     *          ModuleIdQuerys} describing the desired root modules
     *
     * @throws  ConfigurationException
     *          If a valid configuration cannot be computed
     *
     * @throws  IOException
     *          If an I/O error occurs while accessing the module library
     *
     * @return  The resulting {@link Configuration}
     */
    public static Configuration<Context>
        configure(Library lib, Collection<ModuleIdQuery> rootQueries)
        throws ConfigurationException, IOException
    {

        if (tracing)
            trace(0, "Configuring %s using library %s",
                  rootQueries, lib.name());

        // 1. Resolve versions
        Resolution res = Resolver.run(lib, rootQueries);

        // 2. Construct contexts
        ContextSet<Linker.Context> cxs
            = ContextBuilder.run(res,
                                 new ContextFactory<Linker.Context>() {
                                     public Linker.Context create() {
                                         return new Linker.Context();
                                     }});

        // 3 & 4. Link local and remote suppliers
        Configuration<Context> cf = Linker.run(lib, cxs);

        if (tracing) {
            List<ModuleId> rids = new ArrayList<>();
            for (ModuleIdQuery midq : rootQueries)
                rids.add(cxs.moduleForName.get(midq.name()).id());
            trace(0, "Configured for %s", rids);
            if (traceLevel >= 3)
                cf.dump(System.out);
        }

        return cf;

    }

    public static Configuration<Context>
        configure(Library lib, ModuleIdQuery rootQuery)
        throws ConfigurationException, IOException
    {
        return configure(lib, Collections.singleton(rootQuery));
    }

    /**
     * <p> Compute a path-based {@linkplain Configuration configuration} for a
     * root module in a given {@link Catalog}. </p>
     *
     * <p> The configuration root is specified in the form of a {@linkplain
     * java.lang.module.ModuleIdQuery module-id query}.  If more than one
     * module in the given library satisfies the query then the most recent
     * version will be used. </p>
     *
     * <p> A Java compiler can't use the full library-based configuration
     * algorithm because it can't determine, at the start of an invocation,
     * exactly which classes are going to be processed, <i>i.e.</i>, either
     * compiled anew or read in from the output directory of a previous
     * invocation. </p>
     *
     * <p> A compiler can, however, determine exactly which module-info files
     * will be processed.  At compile-time we therefore use an alternative
     * version of phase 3 which uses a dominator algorithm to compute a linear
     * ordering of the modules in a context.  This ordering guarantees that if
     * there is any shadowing of a class amongst the modules then the module
     * containing the dominant definition, if there is one, will precede the
     * others.  We also use a simpler version of phase 4 which computes remote
     * contexts rather than maps from package names to contexts. </p>
     *
     * <p> The use of different algorithms at compile time <it>vs.</it>
     * configuration time does admit slightly different outcomes.  If a type
     * has multiple definitions in a context but no dominant definition then at
     * compile time an arbitrary definition will be visible but at install time
     * an error will be reported.  This is unfortunate but acceptable; it's
     * somewhat akin to the {@linkplain java.lang.LinkageError class-linkage
     * errors} which can occur when classes change incompatibly, and is likely
     * to be encountered mainly by advanced developers. </p>
     * 
     * @param   cat
     *          The {@linkplain Catalog module catalog} against which
     *          dependences will be resolved
     *
     * @param   rootQueries
     *          A collection of {@linkplain java.lang.module.ModuleIdQuery
     *          ModuleIdQuerys} describing the desired root modules
     *
     * @throws  ConfigurationException
     *          If a valid configuration cannot be computed
     *
     * @throws  IOException
     *          If an I/O error occurs while accessing the module library
     *
     * @return  The resulting {@linkplain Configuration configuration}
     */
    public static Configuration<PathContext>
        configurePaths(Catalog cat, Collection<ModuleIdQuery> rootQueries)
        throws ConfigurationException, IOException
    {

        if (tracing)
            trace(0, "Path-configuring %s using catalog %s",
                  rootQueries, cat.name());

        // 1. Resolve versions
        Resolution res = Resolver.run(cat, rootQueries);

        // 2. Construct contexts
        ContextSet<PathContext> cxs
            = ContextBuilder.run(res,
                                 new ContextFactory<PathContext>() {
                                     public PathContext create() {
                                         return new PathContext();
                                     }});

        // 3 & 4. Link local and remote suppliers
        Configuration<PathContext> cf = PathLinker.run(cxs);

        if (tracing) {
            List<ModuleId> rids = new ArrayList<>();
            for (ModuleIdQuery midq : rootQueries)
                rids.add(cxs.moduleForName.get(midq.name()).id());
            trace(0, "Configured paths for %s", rids);
            if (traceLevel >= 3)
                cf.dump(System.out);
        }

        return cf;

    }

}
