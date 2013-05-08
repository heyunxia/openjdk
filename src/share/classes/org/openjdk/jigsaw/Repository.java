/*
 * Copyright (c) 2010, 2012 Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.lang.module.*;
import java.util.Objects;


/**
 * <p> A collection of module-info files together with associated module files,
 * suitable for download and installation. </p>
 */

public abstract class Repository
    extends LocatableCatalog
{

    /**
     * <p> The type of a module file <p>
     */
    public static enum ModuleType {     // ## Should be ModuleFileType
        /**
         * A module type that is a java module file
         */
        JMOD("jmod"),

        /**
         * A module type that is a modular jar file
         */
        JAR("jar");

        private final String extension;

        ModuleType(String suffix) {
            this.extension = suffix;
        }

        public String getFileNameExtension() {
            return extension;
        }

        public String getFileNameSuffix() {
            return "." + getFileNameExtension();
        }

        /**
         * Get the module type from the file name extension.
         *
         * @param extension the file name extension.
         * @return the module type.
         * @throws IllegalArgumentException if {@code extension}
         *         has no corresponding module type.
         * @throws NullPointerException if {@code extension} is null
         */
        public static ModuleType fromFileNameExtension(String extension) {
            Objects.requireNonNull(extension, "Extension is null");
            for (ModuleType type: values()) {
                if (type.extension.equals(extension)) {
                    return type;
                }
            }

            throw new IllegalArgumentException(
                    "No module type for the file name extension " + extension);
        }

    }

    /**
     * <p> Size and type information about a yet-to-be-installed module </p>
     */
    public static class ModuleMetaData { // ## Should be ModuleFileMetaData

        private final ModuleType type;

        /**
         * The type of the module.
         */
        public ModuleType getType() {
            return type;
        }

        private final long csize;

        /**
         * The module's download size, in bytes.
         */
        public long getDownloadSize() { return csize; }

        private final long usize;

        /**
         * The module's installed size, in bytes.
         *
         * <p> The number of bytes required to install a module may be less
         * than the value returned by this method, but it will never be
         * greater. </p>
         */
        public long getInstallSize() { return usize; }

        ModuleMetaData(ModuleType t, long cs, long us) {
            type = t;
            csize = cs;
            usize = us;
        }

    }

    /**
     * Fetch the meta data for a given module. Such meta data will consist of
     * of the module type and size information.
     *
     * @param   mid
     *          The {@linkplain java.lang.module.ModuleId id} of the
     *          requested module
     *
     * @throws  IllegalArgumentException
     *          If the named module is not present in this repository
     */
    public abstract ModuleMetaData fetchMetaData(ModuleId mid) throws IOException;

    /**
     * Fetch the bytes for a given module.
     *
     * @param   mid
     *          The {@linkplain java.lang.module.ModuleId id} of the
     *          requested module
     *
     * @throws  IllegalArgumentException
     *          If the named module is not present in this repository
     * @throws  IOException
     *          If there is an error fetching the module.
     */
    public abstract InputStream fetch(ModuleId mid) throws IOException;

}
