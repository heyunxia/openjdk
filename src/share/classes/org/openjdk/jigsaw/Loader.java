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

// Some interaction traces can be found at the end of this file
//
// TODO
//  - Work through local, private, permits cases
//  - Work through security and concurrency issues

package org.openjdk.jigsaw;

import java.lang.module.*;
import java.lang.reflect.Module;
import java.io.IOException;
import java.util.*;
import sun.reflect.Reflection;

import static org.openjdk.jigsaw.Trace.*;


class Loader
    extends ModuleClassLoader
{

    private LoaderPool pool;
    private KernelLoader kernelLoader;

    private Map<String,Module> moduleForName
        = new HashMap<String,Module>();

    private Set<ModuleId> modules = new HashSet<ModuleId>();

    public Loader(LoaderPool p) {
	super(JigsawModuleSystem.instance());
        pool = p;
	kernelLoader = p.kernelLoader();
    }

    protected void traceLoad(String name, Module requestor, String supplier) {
	trace(0, "%s %s %s:%s",
	      this,
	      requestor != null ? requestor.getModuleId() : "unnamed",
	      supplier, name);
    }

    private static Module getCallerModule() {
	Class c = Reflection.getCallerClass(3);
	if (c == null)
	    return null;
	return c.getModule();
    }

    /**
     * Primary entry point from VM
     */
    public Class<?> loadClass(String name)
        throws ClassNotFoundException
    {
	Module m = getCallerModule();
	return loadClass(name, m);
    }

    public Class<?> loadClass(String name, Module requestor)
        throws ClassNotFoundException
    {
        Class<?> c = null;

        // Check the loaded-class cache first.  The VM guarantees not to invoke
        // this method more than once for any given class name, but we still
        // need to check the cache manually in case this method is invoked by
        // user code.
        //
        if ((c = findLoadedClass(name)) != null) {
	    if (tracing)
		traceLoad(name, requestor, "(cache)");
            return c;
	}

	// Check for a bootstrap class.  This is a temporary measure, until
	// such time as the bootstrap classes have themselves been modularized.
	//
	if (kernelLoader.isKernelClass(name)) {
	    return kernelLoader.loadClass(name, requestor);
	}

	// Find a supplier of the requested class
	//
        ModuleId supplier
	    = pool.library().findModuleForClass(name,
						requestor.getModuleId());
        if (supplier == null)
            throw new ClassNotFoundException("unknown:" + name);

	if (tracing)
	    traceLoad(name, requestor, supplier.toString());
	return loadClassFromSupplier(supplier, name);
    }

    // Also invoked by LoaderPool.findClass
    //
    Class<?> loadClassFromSupplier(ModuleId supplier, String name)
	throws ClassNotFoundException
    {
	// Find a loader for the supplier, and use that to load the class
	Loader ld = null;
	if (modules.contains(supplier)) {
	    // If supplier is already in this loader, then use this loader
	    ld = this;
	} else {
	    // Otherwise, ask our module-loader pool to find a loader for
	    // the supplier, and then ask that loader to load the class
	    ld = pool.findLoader(supplier);
	    if (ld == null)
		throw new ClassNotFoundException(supplier + ":" + name);
	}
	return ld.findClass(supplier, name);
    }

    // Invoked by KernelLoader to add the kernel module
    //
    protected void addModule(Module m) {
	ModuleId mid = m.getModuleId();
	moduleForName.put(mid.name(), m);
	modules.add(mid);
    }

    // Invoked by findClass, below, and (eventually) by LoaderPool.init()
    //
    Module defineModule(ModuleId mid, byte[] bs) {
        Module m = super.defineModule(mid, bs, 0, bs.length);
        moduleForName.put(mid.name(), m);
        modules.add(mid);
	return m;
    }

    private ClassNotFoundException cnf(Module m, String name, IOException x) {
	ClassNotFoundException cnfx
	    = new ClassNotFoundException(m.getName() + ":" + name);
	cnfx.initCause(x);
	return cnfx;
    }

    Class<?> findClass(ModuleId mid, String name)
	throws ClassNotFoundException
    {

        Class<?> c = findLoadedClass(name);
        if (c != null)
            return c;

        Module m = moduleForName.get(mid.name());
        if (m != null) {
            if (!m.getModuleId().equals(mid)) {
                throw new ClassNotFoundException("Duplicate module in loader");
		// ## DuplicateModuleInLoaderException();
	    }
        } else {
	    try {
		byte[] bs = pool.library().findModuleInfoBytes(mid);
		m = defineModule(mid, bs);
	    } catch (IOException x) {
		throw cnf(m, name, x);
	    }
        }

        // ## Could check m's permits clause here (though we'd need the
        // ## requestor's name to be passed in), but the VM will do that
        // ## during linking (right?)

	try {
	    byte[] bs = pool.library().findClass(mid, name);
	    if (bs == null)
		throw new ClassNotFoundException(mid + ":" + name);
	    return defineClass(m, name, bs, 0, bs.length);
	} catch (IOException x) {
	    throw cnf(m, name, x);
	}

    }

    public String toString() {
	StringBuilder sb = new StringBuilder();
	sb.append("((").append(pool.library().path()).append("))");
	sb.append(modules.toString());
	return sb.toString();
    }

}


/* -- VM/Jigsaw interaction traces --

   Case 1: Load a class from a module not yet loaded

     Class C1 in module M1 refers to class C2
       Let L1 = C1.class.getClassLoader()
       VM invokes L1.loadClass("C2", M1)
         Let P = L1's JigsawModuleSystem
         Let ML = P's module library
         L1 invokes ML.findModuleForClass("C2", M1)
           ML searches M1's dependences for a module that contains C2
           ML returns MI2 = Module id of supplying module
         L1 invokes P.findLoader(MI2)
           P creates a new loader L2
           P adds MI2 -> L2 entry to its module-id-to-loader map
         L1 invokes L2.findClass(MI2, "C2")
           L2 reads M2's module-info bytes via ML
           L2 invokes defineModule(MI2, module-info bytes)
             VM creates M2 = new java.lang.reflect.Module(bytes, L2)
           L2 adds M2 to the set of L2's modules
           L2 adds MI2.name -> M2 entry to L2's name-to-module map
           L2 reads C2's class bytes via ML
           L2 invokes defineClass(MI2, "C2", class bytes), obtaining C2
         L1 returns C2
       VM links C2
     C1 proceeds to use C2

   Case 2: JRE launch sequence (very rough idea)

     Launcher initializes VM
       VM finds JRE kernel-module class files in library
       VM uses bootstrap loader to invoke JigsawModuleSystem.init
         This will create the kernel module, KM, and its loader, KL
       VM modifies all loaded classes so that:
         C.getClassLoader() == KL
         C.getModule() == KM
       (From this point forward the bootstrap loader is not used)
     Launcher invokes JigsawModuleSystem.findClass to find the main class, M
     Launcher invokes M.main()

 */
