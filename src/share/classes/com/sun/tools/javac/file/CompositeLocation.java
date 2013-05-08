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

package com.sun.tools.javac.file;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import javax.tools.ExtendedLocation;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardJavaFileManager;

import com.sun.tools.javac.util.ListBuffer;

/**
 * A location composed of a list of component locations.
 */
class CompositeLocation implements ExtendedLocation {
    final JavaFileManager fileManager;
    final Iterable<? extends Location> locations;
    final String name;
    private static int count; // FIXME, move count/name to creator, or move CompositeLocation to Locations

    CompositeLocation(Iterable<? extends Location> locations, JavaFileManager fileManager) {
        this.locations = locations;
        this.fileManager = fileManager;
        ListBuffer<String> names = new ListBuffer<String>();
        for (Location l: locations)
            names.add(l.getName());
        name = "multiLocation#" + (count++) + names.toString();
    }

    @Override // javax.tools.ExtendedLocation
    public Iterable<JavaFileObject> list(String packageName, Set<Kind> kinds, boolean recurse) throws IOException {
        ListBuffer<JavaFileObject> results = new ListBuffer<JavaFileObject>();
        for (Location l: locations) {
            Iterable<JavaFileObject> fileObjects;
            if (l instanceof ExtendedLocation)
                fileObjects = ((ExtendedLocation) l).list(packageName, kinds, recurse);
            else
                fileObjects = fileManager.list(l, packageName, kinds, recurse);
            for (JavaFileObject fo: fileObjects)
                results.add(fo);
        }
        return results.toList();
    }

    @Override // javax.tools.ExtendedLocation
    public String inferBinaryName(JavaFileObject file) {
        for (Location l: locations) {
            String binaryName;
            if (l instanceof ExtendedLocation)
                binaryName = ((ExtendedLocation) l).inferBinaryName(file);
            else
                binaryName = fileManager.inferBinaryName(l, file);
            if (binaryName != null)
                return binaryName;
        }
        return null;
    }

    @Override // javax.tools.JavaFileManager.Location
    public String getName() {
        return name;
    }

    @Override // javax.tools.JavaFileManager.Location
    public boolean isOutputLocation() {
        return false;
    }

    @Override
    public String toString() {
        return getName();
    }

    Collection<File> getLocation() {
        if (!(fileManager instanceof StandardJavaFileManager))
            throw new IllegalStateException();
        StandardJavaFileManager fm = (StandardJavaFileManager) fileManager;
        ListBuffer<File> files = new ListBuffer<File>();
        for (Location l: locations) {
            Iterable<? extends File> iter = fm.getLocation(l);
            // FIXME: need a way to distinguish between empty locations and
            // non-standard locations
//            if (iter == null)
//                throw new IllegalStateException();
            if (iter != null)
                for (File f: iter) files.add(f);
        }
        return files.toList();
    }
}
