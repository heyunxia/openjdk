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

import java.io.File;
import java.lang.module.*;
import java.lang.reflect.*;

import static org.openjdk.jigsaw.Trace.*;


public final class Launcher {

    public static void main(String[] args)
	throws Exception
    {
	JigsawModuleSystem jms = JigsawModuleSystem.instance();
	File lib = new File(args[0]);
	ModuleIdQuery midq = new ModuleIdQuery(args[1], null);
	String main = (args.length == 3) ? args[2] : null;
	Class<?> c = LoaderPool.loadClass(lib, midq, main);
	if (tracing)
	    trace(0, "launch: loader %s, module %s, class %s",
		  c.getClassLoader(),
		  c.getModule().getModuleInfo().id(),
		  c.getName());
	Method m = c.getDeclaredMethod("main",
				       Class.forName("[Ljava.lang.String;"));
	try {
	    m.invoke(null, (Object)new String[] { });
	} catch (InvocationTargetException x) {
	    Throwable y = x.getCause();
	    if (y == null)
		throw x;
	    if (y instanceof RuntimeException)
		throw (RuntimeException)y;
	    if (y instanceof Exception)
		throw (Exception)y;
	    if (y instanceof Error)
		throw (Error)y;
	    throw new AssertionError(y);
	}
    }

}
