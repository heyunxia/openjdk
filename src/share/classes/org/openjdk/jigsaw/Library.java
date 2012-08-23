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
import java.security.CodeSigner;
import java.security.SignatureException;
import java.util.*;

import static org.openjdk.jigsaw.Trace.*;


/**
 * The abstract base class for module libraries
 *
 * @see SimpleLibrary
 */

public abstract class Library
    extends LocatableCatalog
{

    // ## Should use new-fangled Paths instead of old-fangled Files

    private static File systemLibraryPath = null;

    /**
     * <p> The system module library's path </p>
     */
    public static synchronized File systemLibraryPath() {
        if (systemLibraryPath == null) {
            systemLibraryPath
                = new File(new File(System.getProperty("java.home"),
                                    "lib"),
                           "modules");
        }
        return systemLibraryPath;
    }

    /**
     * <p> Open the system module library. </p>
     */
    public static Library openSystemLibrary()
        throws IOException
    {
        return SimpleLibrary.open(systemLibraryPath());
    }

    private static final JigsawModuleSystem jms
        = JigsawModuleSystem.instance();

    protected Library() { }

    /**
     * <p> Return the major version of this library. </p>
     */
    public abstract int majorVersion();

    /**
     * <p> Return the minor version of this library. </p>
     */
    public abstract int minorVersion();

    public abstract Library parent();

    /**
     * <p> Read the module-info class bytes for the module with the given
     * identifier, from this library only. </p>
     *
     * @param   mid
     *          The identifier of the module being sought
     *
     * @return  A byte array containing the content of the named module's
     *          <tt>module-info.class</tt> file, or {@code null} if no such
     *          module is present in this library
     *
     * @throws  IllegalArgumentException
     *          If the given module identifier is not a Jigsaw module
     *          identifier
     */
    protected abstract byte[] readLocalModuleInfoBytes(ModuleId mid)
        throws IOException;

    /**
     * <p> Read the raw module-info class bytes of the specified module, from
     * this library or a parent library. </p>
     */
    public byte[] readModuleInfoBytes(ModuleId mid)
        throws IOException
    {
        Library lib = this;
        while (lib != null) {
            byte[] bs = lib.readLocalModuleInfoBytes(mid);
            if (bs != null)
                return bs;
            lib = lib.parent();
        }
        return null;
    }

    public ModuleInfo readLocalModuleInfo(ModuleId mid)
        throws IOException
    {
        byte[] bs = readLocalModuleInfoBytes(mid);
        if (bs != null)
            return jms.parseModuleInfo(bs);
        return null;
    }

    /**
     * Read the class bytes of the given class within the given module in this
     * library.
     *
     * @param   mid
     *          The module's identifier
     *
     * @param   className
     *          The binary name of the requested class
     *
     * @return  The requested bytes, or {@code null} if the named module does
     *          not define such a class
     *
     * @throws  IllegalArgumentException
     *          If the given module identifier is not a Jigsaw module
     *          identifier
     */
    public abstract byte[] readLocalClass(ModuleId mid, String className)
        throws IOException;

    /**
     * Read the class bytes of the given class within the given module, in this
     * library or in a parent library.
     *
     * @param   mid
     *          The module's identifier
     *
     * @param   className
     *          The binary name of the requested class
     *
     * @return  The requested bytes, or {@code null} if the named module does
     *          not define such a class
     *
     * @throws  IllegalArgumentException
     *          If the given module identifier is not a Jigsaw module
     *          identifier
     */
    public byte[] readClass(ModuleId mid, String className)
        throws IOException
    {
        for (Library l = this; l != null; l = l.parent()) {
            byte[] bs = l.readLocalClass(mid, className);
            if (bs != null)
                return bs;
        }
        return null;
    }

    /**
     * Return a list of the public and, optionally, all other classes defined
     * by the named module in this library.
     *
     * @param   mid
     *          The module's identifier
     *
     * @param   all
     *          Whether non-public classes should be included
     *
     * @return  The requested class names, or null if the named module does not
     *          exist in this library
     *
     * @throws  IllegalArgumentException
     *          If the given module identifier is not a Jigsaw module
     *          identifier
     */
    public abstract List<String> listLocalClasses(ModuleId mid, boolean all)
        throws IOException;

    /**
     * Read the stored {@link Configuration} of the named module.
     *
     * @param   mid
     *          The module's identifier
     *
     * @return  The named module's {@link Configuration}, or null if the named
     *          module does not exist in this library
     *
     * @throws  IllegalArgumentException
     *          If the given module identifier is not a Jigsaw module
     *          identifier
     */
    public abstract Configuration<Context> readConfiguration(ModuleId mid)
        throws IOException;

    /**
     * <p> Install one or more modules into this library. </p>
     *
     * <p> The modules are first copied from the locations specified in the
     * given manifests, and then the configurations of any affected root
     * modules in the library are recomputed. </p>
     *
     * @param   mfs
     *          The manifests describing the contents of the modules to be
     *          installed
     */
    public abstract void installFromManifests(Collection<Manifest> mfs)
        throws ConfigurationException, IOException;

    /**
     * <p> Install one or more module files into this library. </p>
     *
     * @param   mfs
     *          The module files to be installed
     *
     * @param   verifySignature
     *          Perform signature verification of signed module files, if true.
     *          Otherwise treat the module files as unsigned.
     *
     * @throws  ConfigurationException
     *          If a valid configuration cannot be computed
     *
     * @throws  IOException
     *          If an I/O error occurs while accessing the module library
     *
     * @throws  SignatureException
     *          If an error occurs while validating the signature
     *
     * @throws  ModuleFileParserException
     *          If there is an error processing one the underlying module files
     */
    public abstract void install(Collection<File> mfs, boolean verifySignature)
        throws ConfigurationException, IOException, SignatureException;

    /**
     * <p> Resolve the given collection of {@linkplain
     * java.lang.module.ModuleIdQuery module-id queries} against this
     * library. </p>
     *
     * @param   midqs
     *          A non-empty collection of {@link java.lang.module.ModuleIdQuery
     *          ModuleIdQuery objects}
     *
     * @throws  ConfigurationException
     *          If a valid {@link Resolution} cannot be computed
     *
     * @throws  IOException
     *          If an I/O error occurs while accessing the module library
     */
    public abstract Resolution resolve(Collection<ModuleIdQuery> midqs)
        throws ConfigurationException, IOException;

    /**
     * <p> Install any modules required by the given {@linkplain Resolution
     * resolution}, and configure all of its root modules. </p>
     *
     * @param   res
     *          A {@link Resolution} previously computed by the
     *          {@link Library#resolve resolve} method
     *
     * @param   verifySignature
     *          Perform signature verification, if true
     *
     * @throws  ConfigurationException
     *          If a valid configuration cannot be computed
     *
     * @throws  IOException
     *          If an I/O error occurs while accessing the module library
     *
     * @throws  SignatureException
     *          If an error occurs while validating the signature
     *
     * @throws  ModuleFileParserException
     *          If there is an error processing the underlying module file
     *          required by the given resolution
     */
    public abstract void install(Resolution res, boolean verifySignature)
        throws ConfigurationException, IOException, SignatureException;

    /**
     * Remove one or more modules from this library.
     *
     * @param   mids
     *          The module identifiers
     *
     * @param   dry
     *          Perform a dry run (no changes to the module library), if true.
     *          Otherwise the modules may be removed.
     *
     * @throws  ConfigurationException
     *          If the configuration of any root modules in the library
     *          require any of the given modules
     *
     * @throws  IOException
     *          If an I/O error occurs while accessing the module library, or
     *          removing any of the module's files. Any such exceptions are
     *          caught internally. If only one is caught, then it is re-thrown.
     *          If more than one exception is caught, then the second and
     *          following exceptions are added as suppressed exceptions of the
     *          first one caught, which is then re-thrown.
     */
    public abstract void remove(List<ModuleId> mids, boolean dry)
        throws ConfigurationException, IOException;

    /**
     * Forcibly remove one or more modules from this library.
     *
     * <p> No regard is given to configuration of any root modules in the
     * library that may require any of the given modules. </p>
     *
     * @param   mids
     *          The module identifiers
     *
     * @throws  IOException
     *          If an I/O error occurs while accessing the module library, or
     *          removing any of the module's files. Any such exceptions are
     *          caught internally. If only one is caught, then it is re-thrown.
     *          If more than one exception is caught, then the second and
     *          following exceptions are added as suppressed exceptions of the
     *          first one caught, which is then re-thrown.
     */
    public abstract void removeForcibly(List<ModuleId> mids)
        throws IOException;

    /**
     * Find a resource within the given module in this library.
     *
     * @param   mid
     *          The module's identifier
     *
     * @param   rn
     *          The name of the requested resource, in the usual
     *          slash-separated form
     *
     * @return  A {@code File} object naming the location of the resource,
     *          or {@code null} if the named module does not define that
     *          resource
     */
    // ## Returning file or jar URIs here is EVIL!
    // ## Define a jmod: hierarchical URI scheme?
    public abstract URI findLocalResource(ModuleId mid, String rn)
        throws IOException;

    /**
     * Find a native library within the given module in this library.
     *
     * @param   mid
     *          The module's identifier
     *
     * @param   name
     *          The name of the requested library, in platform-specific
     *          form, <i>i.e.</i>, that returned by the {@link
     *          java.lang.System#mapLibraryName System.mapLibraryName} method
     *
     * @return  A {@code File} object naming the location of the native library,
     *          or {@code null} if the named module does not contain such a
     *          library
     */
    public abstract File findLocalNativeLibrary(ModuleId mid, String name)
        throws IOException;

    /**
     * <p> Return a file path to the given module's classes. </p>
     *
     * @param   mid
     *          The module's identifier
     *
     * @return  A {@code File} object naming the location of the module's
     *          classes, or {@code null} if the named module does not exist
     */
    public abstract File classPath(ModuleId mid)
        throws IOException;


    /**
     * <p> Return the list of {@linkplain RemoteRepository remote repositories}
     * associated with this library </p>
     */
    public abstract RemoteRepositoryList repositoryList() throws IOException;

    /**
     * <p> Read the code signers of the module with the given identifier, from
     * this library only. </p>
     *
     * @param   mid
     *          The identifier of the module being sought
     *
     * @return  An array of CodeSigners, or {@code null} if the module is not
     *          signed
     *
     * @throws  IllegalArgumentException
     *          If the given module identifier is not a Jigsaw module
     *          identifier
     *
     * @throws  IOException
     *          If an I/O error occurs while accessing the module library
     */
    public abstract CodeSigner[] readLocalCodeSigners(ModuleId mid)
        throws IOException;

}
