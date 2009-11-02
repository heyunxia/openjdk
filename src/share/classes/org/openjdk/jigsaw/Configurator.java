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
 * @see ContextBuilder
 * @see Linker
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

public final class Configurator {

    private Configurator() { }

    // --
    //
    // Module configuration proceeds in four phases:
    //
    //   1. Resolve versions -- Determine the version of each module that will
    //      be part of the resulting configuration.
    //
    //   2. Construct contexts -- Assign modules to contexts, ensuring that the
    //      local dependences of a module are assigned to the same context.
    //
    //   3. Link local suppliers -- For each class defined in a context,
    //      determine which of the context's modules will supply it.
    //
    //   4. Link remote suppliers -- For each package imported into a
    //      context, either directly or indirectly (via "requires public"),
    //      determine the context that will supply it.
    //
    // --

    /**
     * <p> Compute a {@linkplain Configuration configuration} for a root module
     * in a given {@linkplain Library module library}. </p>
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
     * @throws  ConfigurationException
     *          If a valid configuration cannot be computed
     *
     * @throws  IOException
     *          If an I/O error occurs while accessing the module library
     *
     * @return  The resulting {@linkplain Configuration configuration}
     */
    public static Configuration run(Library lib, ModuleIdQuery rootQuery)
        throws ConfigurationException, IOException
    {

        if (tracing)
            trace(0, "Configuring %s using library %s",
                  rootQuery, lib.name());

        Resolution res = Resolver.run(lib, rootQuery);

        ContextSet<Linker.Context> cxs
            = ContextBuilder.run(res,
                                 new ContextFactory<Linker.Context>() {
                                     public Linker.Context create() {
                                         return new Linker.Context();
                                     }});

        Configuration cf = Linker.run(cxs);

        if (tracing) {
            ModuleInfo root = cxs.moduleForName.get(cxs.rootQuery.name());
            trace(0, "Configured %s", root.id());
            if (traceLevel >= 3)
                cf.dump(System.out);
        }

        return cf;

    }

}
