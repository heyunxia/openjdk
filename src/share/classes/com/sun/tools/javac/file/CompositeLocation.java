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

package com.sun.tools.javac.file;

import com.sun.tools.javac.util.ListBuffer;
import java.io.IOException;
import java.util.Set;
import javax.tools.ExtendedLocation;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;

/**
 * A location composed of a list of component locations.
 */
class CompositeLocation implements ExtendedLocation {
    final JavaFileManager fileManager;
    final Iterable<? extends Location> locations;
    final String name;
    private static int count;

    CompositeLocation(Iterable<? extends Location> locations, JavaFileManager fileManager) {
        this.locations = locations;
        this.fileManager = fileManager;
        ListBuffer<String> names = new ListBuffer<String>();
        for (Location l: locations)
            names.add(l.getName());
        name = "multiLocation#" + (count++) + names.toString();
    }

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

    public String getName() {
        return name;
    }

    public boolean isOutputLocation() {
        return false;
    }

    @Override
    public String toString() {
        return getName();
    }
}
