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

/**
 * The abstract base class for module libraries
 *
 * @see SimpleLibrary
 */

public abstract class Library {

    private static final JigsawModuleSystem jms
	= JigsawModuleSystem.instance();

    /**
     * This library's name, not guaranteed to be unique.
     */
    public abstract String name();

    public abstract int majorVersion();
    public abstract int minorVersion();

    /**
     * Visitor interface for {@link java.lang.module.ModuleInfo ModuleInfo}
     * objects.
     *
     * @see Library#visitModules
     */
    public interface ModuleInfoVisitor {

	/**
	 * Visit a single module-info object.
	 */
	public void accept(ModuleInfo mi);

    }

    /**
     * Visit all of the modules installed in this library.
     *
     * @param   visitor
     *          The visitor to be applied to each {@link
     *          java.lang.module.ModuleInfo ModuleInfo}
     */

    public abstract void visitModules(ModuleInfoVisitor visitor)
	throws IOException;

    /**
     * List all of the root modules installed in this library.  A root module
     * is any module that declares a main class.
     *
     * @return  An unsorted list of module-info objects
     */
    public List<ModuleInfo> listRootModuleInfos()
	throws IOException
    {
	final List<ModuleInfo> mis = new ArrayList<ModuleInfo>();
	visitModules(new ModuleInfoVisitor() {
		public void accept(ModuleInfo mi) {
		    if (mi.mainClass() != null)
			mis.add(mi);
		}
	    });
	return mis;
    }

    /**
     * Find all modules with the given name in this library.
     *
     * @param   moduleName
     *          The name of the modules being sought
     *
     * @return  An unsorted list containing the module identifiers of the
     *          found modules; if no modules were found then the list will
     *          be empty
     */
    public abstract List<ModuleId> findModuleIds(String moduleName)
	throws IOException;

    /**
     * Find all modules matching the given query in this library.
     *
     * @param   midq
     *          The query to match against
     *
     * @return  An unsorted list containing the module identifiers of the
     *          found modules; if no modules were found then the list will
     *          be empty
     *
     * @throws  IllegalArgumentException
     *          If the given module-identifier query is not a Jigsaw
     *          module-identifier query
     */
    public List<ModuleId> findModuleIds(ModuleIdQuery midq)
	throws IOException
    {
	List<ModuleId> ans = findModuleIds(midq.name());
	if (ans.isEmpty() || midq.versionQuery() == null)
	    return ans;
	for (Iterator<ModuleId> i = ans.iterator(); i.hasNext();) {
	    ModuleId mid = i.next();
	    if (!midq.matches(mid))
		i.remove();
	}
	return ans;
    }

    /**
     * Find the most recently-versioned module matching the given query in this
     * library.
     *
     * @param   midq
     *          The query to match against
     *
     * @return  The identification of the latest module matching the given
     *          query, or {@code null} if none is found
     *
     * @throws  IllegalArgumentException
     *          If the given module-identifier query is not a Jigsaw
     *          module-identifier query
     */
    public ModuleId findLatestModuleId(ModuleIdQuery midq)
	throws IOException
    {
	List<ModuleId> mids = findModuleIds(midq);
	if (mids.isEmpty())
	    return null;
	if (mids.size() == 1)
	    return mids.get(0);
	Collections.sort(mids);
	return mids.get(0);
    }

    /**
     * Read the module-info class bytes for the module with the given
     * identifier.
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
    public abstract byte[] readModuleInfoBytes(ModuleId mid)
	throws IOException;

    /**
     * Find the {@link java.lang.module.ModuleInfo ModuleInfo} object for the
     * module with the given identifier.
     *
     * @param   mid
     *          The identifier of the module being sought
     *
     * @return  The requested {@link java.lang.module.ModuleInfo ModuleInfo},
     *          or {@code null} if no such module is present in this library
     *
     * @throws  IllegalArgumentException
     *          If the given module identifier is not a Jigsaw module
     *          identifier
     */
    public ModuleInfo readModuleInfo(ModuleId mid)
	throws IOException
    {
	byte[] bs = readModuleInfoBytes(mid);
	if (bs == null)
	    return null;
	return jms.parseModuleInfo(bs);
    }

    /**
     * Read the class bytes of the given class within the given module.
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
    public abstract byte[] readClass(ModuleId mid, String className)
	throws IOException;

    /**
     * Return a list of the public and, optionally, all other classes defined
     * by the named module.
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
    public abstract List<String> listClasses(ModuleId mid, boolean all)
	throws IOException;

    /**
     * Read the {@link Configuration} of the named module.
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
    public abstract Configuration readConfiguration(ModuleId mid)
	throws IOException;

    /**
     * Install one or more modules into this library.
     *
     * The modules are first copied from the given classes directory, and then
     * the configuration of any affected root modules in the library are
     * recomputed.
     *
     * @param   classes
     *          The directory of classes containing the modules to be installed
     *
     * @param   moduleNames
     *          The names of the modules to be installed from the given classes
     *          directory
     */
    public abstract void install(File classes, final List<String> moduleNames)
	throws ConfigurationException, IOException;

}
