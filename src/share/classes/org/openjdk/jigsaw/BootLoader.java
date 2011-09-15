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
import java.util.*;

import static org.openjdk.jigsaw.Trace.*;


// A specialized loader for "bootstrap" classes.  In Jigsaw these are the
// classes in the java.* package hierarchy and some related sun.*/com.sun.*
// packages.  We load them using the VM's built-in bootstrap class loader,
// thus preserving current behavior, in particular the constraints that
// java.* classes are only loaded by the built-in class loader and that
// Class.getClassLoader() returns null for java.* classes.

public final class BootLoader    // ## TEMPORARY should be package-private
    extends Loader
{

    private static native void extendBootPath0(String path);

    // ## TEMPORARY should be private; used by j.l.ClassLoader
    // ## to make the legacy application class loader work
    public static void extendBootPath(File path) {
        extendBootPath0(path.getPath());
    }

    private BootLoader(LoaderPool lp, Context cx) {
        super(lp, cx);

        // Add the rest of the boot context's modules
        // to the VM's boot class path
        //
        for (ModuleId mid : cx.modules()) {
            if (mid.equals(Platform.bootModule()))
                continue;
            try {
                File p = pool.library().classPath(mid);
                extendBootPath(p);
            } catch (IOException x) {
                throw new Error(x);
            }
        }

    }

    public static void main(String[] args) throws Exception {
        extendBootPath(new File("/tmp/foo/bar"));
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

    private static BootLoader bootLoader;
    static BootLoader newLoader(LoaderPool p, Context cx) {
        if (bootLoader != null)
            throw new AssertionError("Not supporting multiple LoaderPool yet");

        bootLoader = new BootLoader(p, cx);
        return bootLoader;
    }

    public static BootLoader getLoader() {
        if (bootLoader == null)
            throw new AssertionError("BootLoader not initialized: booted=" +
                sun.misc.VM.isBooted());
        return bootLoader;
    }

    /**
     * Returns the Module for the given class loaded by the VM
     * bootstrap class loader. 
     */
    public Module findModule(Class<?> c) throws IOException {
        Context cx = context;
        ModuleId mid = cx.findModuleForLocalClass(c.getName());
        if (mid == null)
            return null;

        // Find the library from which we'll load the class
        //
        Library lib = bootLoader.pool.library(cx, mid);
        return findModule(lib, mid);
    }

}
