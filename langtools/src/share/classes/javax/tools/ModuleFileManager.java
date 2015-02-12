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

package javax.tools;

public interface ModuleFileManager extends JavaFileManager {
    class InvalidLocationException extends IllegalArgumentException {
        private static final long serialVersionUID = 2115919242510692026L;
    }

    class InvalidFileObjectException extends IllegalArgumentException {
        private static final long serialVersionUID = 1234906668846471087L;
    }

    enum ModuleMode { SINGLE, MULTIPLE };

    /**
     * Determine if the file manager is running in "single module mode"
     * or "multiple module mode". This affects how files are written to
     * the class output directory.
     * Multiple module mode is active if a module path has been set,
     * but not a class path.
     */
    ModuleMode getModuleMode();

    /**
     * Get a location representing the "container" for a file object
     * for a compilation unit in a given package.
     */
    Location getModuleLocation(Location location, JavaFileObject fo, String packageName)
            throws IllegalArgumentException;

    /**
     * Get the set of "module locations" available on a "module path",
     * where each "module location" is determined by the existence of
     * a subdirectory on the path containing a module-info file.
     */
    Iterable<? extends Location> getModuleLocations(Location location);

    /**
     * Join a set of locations into a "search path".
     */
    Location join(Iterable<? extends Location> locations);
}
