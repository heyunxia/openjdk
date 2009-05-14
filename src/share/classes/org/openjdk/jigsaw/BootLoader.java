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
import java.lang.reflect.Module;
import java.util.*;

import static org.openjdk.jigsaw.Trace.*;


// A specialized loader for "bootstrap" classes.  In Jigsaw these are the
// classes in the java.* package hierarchy and some related sun.*/com.sun.*
// packages.  We load them using the VM's built-in bootstrap class loader,
// thus preserving current behavior, in particular the constraints that
// java.* classes are only loaded by the built-in class loader and that
// Class.getClassLoader() returns null for java.* classes.

// ## TODO: Add additional modules to boot class path on demand
//
// Right now we have just one big JDK module.  When we start splitting it
// up we'll need to create a new VM interface so that this class can append
// additional modules to the VM's boot class path.  This should not be hard;
// we just need to expose ClassLoader::update_class_path_entry_list.

final class BootLoader
    extends Loader
{

    @Override
    Class<?> finishFindingClass(ModuleId mid, Module m, String cn)
        throws ClassNotFoundException
    {
        Class<?> c = findBootClass(cn);
        if (tracing)
            trace(0, "%s: found %s:%s (boot)", this, mid, cn);
        return c;
    }

    BootLoader(LoaderPool lp, Context cx) {
        super(lp, cx);
    }

}
