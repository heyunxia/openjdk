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
     * The name of the module to be installed from the module-classes
     * directories
     */
    public String module() {
        return module;
    }

    private List<File> classes = new ArrayList<File>();
    private List<File> resources = new ArrayList<File>();

    /* ## Eventually
    private List<File> libs = new ArrayList<File>();
    private List<File> cmds = new ArrayList<File>();
    */

    /**
     * <p> Add a module-class directory to this manifest. </p>
     *
     * <p> The name of each child of such a directory should be that of a
     * specific module, without a version number.  Each child should, in turn,
     * contain a {@code module-info.class} file as well as classes structured
     * in the usual fashion, with a subdirectory for each package-name
     * component. </p>
     */
    public Manifest addClasses(File f) {
        if (!f.isDirectory())
            throw new IllegalArgumentException(f + ": Not a directory");
        classes.add(f);
        return this;
    }

    /**
     * <p> The module-classes directories to be scanned for the requested
     * modules. </p>
     */
    public List<File> classes() {
        return classes;
    }


    /**
     * <p> Add a resource directory to this manifest. </p>
     *
     * <p> The content of this directory will be copied, without change, into
     * the installed module. </p>
     */
    public Manifest addResources(File f) {
        if (!f.isDirectory())
            throw new IllegalArgumentException(f + ": Not a directory");
        resources.add(f);
        return this;
    }

    /**
     * <p> The resource directories whose contents will be installed </p>
     */
    public List<File> resources() {
        return resources;
    }

    public static Manifest create(String mn) {
        return new Manifest(mn);
    }

    public static Manifest create(String mn, File classes) {
        return new Manifest(mn).addClasses(classes);
    }

    public static Manifest create(String mn, File classes, File resources) {
        return new Manifest(mn).addClasses(classes).addResources(resources);
    }

}
