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
import java.lang.reflect.Module;
import java.io.*;

import static org.openjdk.jigsaw.Trace.*;


// A specialized loader for "bootstrap" classes.  In Jigsaw these are the
// classes in the java.* package hierarchy and some related sun.*/com.sun.*
// packages.  We load them using the VM's built-in bootstrap class loader,
// thus preserving current behavior, in particular the constraints that
// java.* classes are only loaded by the built-in class loader and that
// Class.getClassLoader() returns null for java.* classes.

final class BootLoader
    extends Loader
{
    private static LoaderPool systemLoaderPool = null;
    private static Context baseContext = null;

    // Entry point invoked by the VM
    static BootLoader getBaseModuleLoader() {
        if (baseContext == null)
            // classpath mode
            return null;

        return (BootLoader)systemLoaderPool.findLoader(baseContext);
    }

    // Called only once during the system class loader initialization
    static void setSystemLoaderPool(LoaderPool lp) {
        systemLoaderPool = lp;
        baseContext = lp.config().getContextForModuleName(Platform.BASE_MODULE_NAME);
    }

    BootLoader(LoaderPool lp, Context cx) {
        super(lp, cx);
    }

    @Override
    Class<?> finishFindingClass(Library lib, ModuleId mid, Module m, String cn)
        throws ClassNotFoundException
    {
        Class<?> c = findBootClass(cn);
        if (tracing)
            trace(0, "%s: found %s:%s (boot)", this, mid, cn);

        sun.misc.SharedSecrets.getJavaLangAccess().setModule(c, m);
        return c;
    }
}
