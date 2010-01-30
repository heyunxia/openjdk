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
     * <p> Open the system module library </p>
     */
    public static Library openSystemLibrary()
        throws IOException
    {
        return SimpleLibrary.open(systemLibraryPath());
    }

    private static final JigsawModuleSystem jms
        = JigsawModuleSystem.instance();

    protected Library() { }

    public abstract int majorVersion();
    public abstract int minorVersion();

    public abstract Library parent();

    /**
     * <p> List all of the root modules installed in this library.  A root
     * module is any module that declares a main class. </p>
     *
     * <p> This method does not include root modules installed in this
     * library's parent, if any. </p>
     *
     * @return  An unsorted list of module-info objects
     */
    public List<ModuleInfo> listLocalRootModuleInfos()
        throws IOException
    {
        final List<ModuleInfo> mis = new ArrayList<ModuleInfo>();
        for (ModuleId mid : listLocalModuleIds()) {
            ModuleInfo mi = readModuleInfo(mid);
            if (mi.mainClass() != null)
                mis.add(mi);
        }
        return mis;
    }

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
            byte [] bs = l.readLocalClass(mid, className);
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
     */
    public abstract void install(Collection<File> mfs)
        throws ConfigurationException, IOException;

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
    // ## Returning file paths here is EVIL!
    public abstract File findLocalResource(ModuleId mid, String rn)
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


    public abstract RemoteRepositoryList repositoryList() throws IOException;

    /*

    InstallState install(ModuleIdQuery midq) throws InstallException;

    */

}
