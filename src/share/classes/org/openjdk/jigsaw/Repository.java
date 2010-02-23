/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.*;
import java.lang.module.*;


/**
 * <p> A collection of module-info files together with associated module files,
 * suitable for download and installation </p>
 */

public abstract class Repository
    extends LocatableCatalog
{

    /**
     * <p> Size information about a yet-to-be-installed module </p>
     */
    public static class ModuleSize {

        private final long csize;

        /**
         * <p> The module's download size, in bytes </p>
         */
        public long download() { return csize; }

        private final long usize;

        /**
         * <p> The module's installed size, in bytes </p>
         *
         * <p> The number of bytes required to install a module may be less
         * than the value returned by this method, but it will never be
         * greater. </p>
         */
        public long install() { return usize; }

        ModuleSize(long cs, long us) {
            csize = cs;
            usize = us;
        }

    }

    /**
     * <p> Retrieve size information for a given module. </p>
     *
     * @param   mid
     *          The {@linkplain java.lang.module.ModuleId id} of the
     *          requested module
     *
     * @throws  IllegalArgumentException
     *          If the named module is not present in this repository
     */
    public abstract ModuleSize sizeof(ModuleId mid) throws IOException;

    public abstract InputStream fetch(ModuleId mid) throws IOException;

}
