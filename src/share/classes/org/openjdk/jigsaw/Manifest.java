/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
import java.util.*;

import static org.openjdk.jigsaw.Trace.*;


/**
 * A manifest of the files to be installed or packaged as a module
 *
 * @see Library
 * @see SimpleLibrary
 */

public final class Manifest {

    private String module;

    private Manifest(String mn) {
        module = mn;
    }

    /**
     * <p> The name of the module for which the files in this manifest are to
     * be packaged </p>
     */
    public String module() {
        return module;
    }

    // list of directories, each of which contains
    // classes and resources
    private List<File> classes = new ArrayList<File>();

    /* ## Eventually
    private List<File> libs = new ArrayList<File>();
    private List<File> cmds = new ArrayList<File>();
    */

    /**
     * <p> Add a classes directory to this manifest. </p>
     *
     * <p> Two types of classes directories are supported. </p>
     *
     * <ul>
     *
     *   <li><p> A <i>single-module</i> classes directory is just like a
     *   regular classes directory, with a subdirectory for each initial
     *   package-name component, except that it also contains a
     *   <tt>module-info.class</tt> file. </p></li>
     *
     *   <li><p> A <i>multi-module</i> classes directory contains one
     *   subdirectory for each module; the name of that subdirectory is the
     *   module's name, without a version number.  Each such subdirectory is
     *   itself structured as in the single-module case </p></li>
     *
     * </ul>
     *
     * <p> If a classes directory contains a <tt>module-info.class</tt> then
     * the single-module case is assumed. </p>
     */
    public Manifest addClasses(File f) {
        if (!f.isDirectory())
            throw new IllegalArgumentException(f + ": Not a directory");
        classes.add(f);
        return this;
    }

    /**
     * <p> The module-classes-resources directories to be scanned for the
     * requested modules. </p>
     *
     * <p> The resource files will be copied, without change, into the
     * installed module. </p>
     */
    public List<File> classes() {
        return classes;
    }

    /**
     * <p> Create an empty manifest for the given module name </p>
     */
    public static Manifest create(String mn) {
        return new Manifest(mn);
    }

    /**
     * <p> Create a manifest for the given module name and class directory </p>
     */
    public static Manifest create(String mn, File classes) {
        return new Manifest(mn).addClasses(classes);
    }

}
