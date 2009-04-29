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

// ## TODO: Work through security and concurrency issues

package org.openjdk.jigsaw;

import java.lang.module.*;
import java.lang.reflect.Module;
import java.io.IOException;
import java.util.*;

import static org.openjdk.jigsaw.Trace.*;


class Loader
    extends ModuleClassLoader
{

    protected final LoaderPool pool;
    private final Context context;
    private KernelLoader kernelLoader;

    private Map<String,Module> moduleForName
        = new HashMap<String,Module>();

    private Set<ModuleId> modules = new HashSet<ModuleId>();

    public Loader(LoaderPool p, Context cx) {
	super(JigsawModuleSystem.instance());
        pool = p;
	context = cx;
	kernelLoader = p.kernelLoader();
    }

    // Invoked by KernelLoader to add the kernel module
    //
    protected void addModule(Module m) {
	ModuleId mid = m.getModuleId();
	moduleForName.put(mid.name(), m);
	modules.add(mid);
    }

    // Primary entry point from VM
    //
    public Class<?> loadClass(String cn)
        throws ClassNotFoundException
    {

        // Check the loaded-class cache first.  The VM guarantees not to invoke
        // this method more than once for any given class name, but we still
        // need to check the cache manually in case this method is invoked by
        // user code.
        //
        Class<?> c = findLoadedClass(cn);
	if (c != null) {
	    if (tracing) {
		trace(0, "%s: (cache) %s", this, cn);
	    }
            return c;
	}

	// Check for a bootstrap class.  This is a temporary measure, until
	// such time as the bootstrap classes have themselves been modularized.
	//
	if (kernelLoader.isKernelClass(cn)) {
	    return kernelLoader.loadClass(cn);
	}

	// Is the requested class local or remote?  It can be one or the other,
	// but not both.
	//
	String rcxn = context.findContextForRemoteClass(cn);
	ModuleId lmid = context.findModuleForLocalClass(cn);
	if (rcxn != null && lmid != null) {
	    throw new AssertionError("Class " + cn
				     + " defined both locally and remotely");
	}

	// Find a loader, and use that to load the class
	//
	Loader ld = null;
	if (lmid != null) {
	    ld = this;
	    if (tracing)
		trace(0, "%s: load %s:%s", this, lmid, cn);
	} else if (rcxn != null) {
	    ld = pool.findLoader(rcxn);
	    if (tracing)
		trace(0, "%s: load %s:%s", this, rcxn, cn);
	}
	if (ld == null) {
	    throw new ClassNotFoundException(cn);
	}
	return ld.findClass(lmid, cn);

    }

    // Invoked by findClass, below, and (eventually) by LoaderPool.init()
    //
    Module defineModule(ModuleId mid, byte[] bs) {
        Module m = super.defineModule(mid, bs, 0, bs.length);
        moduleForName.put(mid.name(), m);
        modules.add(mid);
	return m;
    }

    private ClassNotFoundException cnf(Module m, String cn, IOException x) {
	ClassNotFoundException cnfx
	    = new ClassNotFoundException(m.getName() + ":" + cn);
	cnfx.initCause(x);
	return cnfx;
    }

    Class<?> findClass(ModuleId mid, String cn)
	throws ClassNotFoundException
    {

	if (mid == null) {
	    mid = context.findModuleForLocalClass(cn);
	    if (mid == null)
		throw new ClassNotFoundException(mid.name() + ":" + cn);
	}

        Class<?> c = findLoadedClass(cn);
        if (c != null) {
	    ModuleId cmid = c.getModule().getModuleId();
	    if (cmid == null || !cmid.equals(mid))
		throw new AssertionError(cn + " previously loaded from "
					 + cmid + "; now trying to load from "
					 + mid);
            return c;
	}

        Module m = moduleForName.get(mid.name());
        if (m != null) {
            if (!m.getModuleId().equals(mid))
                throw new AssertionError("Duplicate module in loader");
        } else {
	    try {
		byte[] bs = pool.library().readModuleInfoBytes(mid);
		m = defineModule(mid, bs);
		if (tracing)
		    trace(0, "%s: define %s", this, mid);
	    } catch (IOException x) {
		throw cnf(m, cn, x);
	    }
        }

	try {
	    byte[] bs = pool.library().readClass(mid, cn);
	    if (bs == null)
		throw new ClassNotFoundException(mid + ":" + cn);
	    c = defineClass(m, cn, bs, 0, bs.length);
	    if (tracing)
		trace(0, "%s: define %s:%s", this, mid, cn);
	    return c;
	} catch (IOException x) {
	    throw cnf(m, cn, x);
	}

    }

    public String toString() {
	return context.name();
    }

}
