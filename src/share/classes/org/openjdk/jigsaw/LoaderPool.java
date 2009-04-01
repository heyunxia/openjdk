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
import java.lang.reflect.Method;
import java.lang.reflect.Module;
import java.io.*;
import java.util.*;

import static org.openjdk.jigsaw.Trace.*;


public final class LoaderPool {

    private Library lib;

    LoaderPool(Library lib) {
	if (lib == null)
	    throw new IllegalArgumentException();
	this.lib = lib;
    }

    Library library() {
	return lib;
    }

    // Our pool of module class loaders.  We use a weak set, so that
    // loaders can be garbage-collected when no longer in use.
    //
    private Set<Loader> loaders
	= new HashSet<Loader>();
        // ## = new WeakSet<Loader>();

    // Map from module ids to module class loaders.  References to loaders
    // are weak, as above.
    //
    private Map<ModuleId,Loader> loaderForModule
	= new HashMap<ModuleId,Loader>();
        // ## = new WeakValueHashMap<ModuleId,Loader>();

    // Find a loader for the given module, or else create one
    //
    Loader findLoader(ModuleId mid) {
        Loader ld = loaderForModule.get(mid);
        if (ld == null) {
            ld = new Loader(this);
            loaders.add(ld);
            loaderForModule.put(mid, ld);
        }
        return ld;
    }

    // The "kernel" module
    //
    private Module kernelModule;

    Module kernelModule() { return kernelModule; }

    // The "kernel" loader -- we retain a separate reference to it here since
    // kernelModule.getClassLoader() is just a ModuleClassLoader, and we need
    // the Jigsaw view of it
    //
    private KernelLoader kernelLoader;

    KernelLoader kernelLoader() { return kernelLoader; }

    /* ## not yet
    // Invoked by the VM to initialize the module system
    // Returns the kernel module
    //
    private Module init(ModuleId mid, byte[] bs) {
        kernelLoader = findLoader(mid);
        kernelModule = kernelLoader.defineModule(mid, bs);
        return kernelModule;
    }
    */

    // Invoked by the launcher to find the main class
    //
    public static Class<?> findClass(File libPath,
				     ModuleIdQuery midq, String name)
        throws IOException, ClassNotFoundException
    {
	try {
	    Library lb = Library.open(libPath, false);
	    LoaderPool lp = new LoaderPool(lb);
	    KernelLoader kl = new KernelLoader(lp);
	    lp.kernelLoader = kl;
	    lp.kernelModule = kl.module();
	    ModuleId mid = lb.findLatestModuleId(midq);
	    String cn = name;
	    if (cn == null) {
		// Use the module's declared main class, if any
		ModuleInfo mi = lb.findModuleInfo(mid);
		if (mi != null)
		    cn = mi.mainClass();
		else
		    throw new Error(mid + ": No main class specified");
	    }
	    return kl.loadClassFromSupplier(mid, cn);
	} catch (IOException x) {
	    // ## refactor; see Loader.cnf
	    ClassNotFoundException cnfx
		= new ClassNotFoundException(midq.name()
					     + ":" + name);
	    cnfx.initCause(x);
	    throw cnfx;
	}
    }

}
