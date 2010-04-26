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

package javax.tools;

import java.io.IOException;
import java.util.Set;

/**
 * A location that can list its contents.
 * @author jjg
 */
public interface ExtendedLocation extends JavaFileManager.Location {


    /**
     * List the contents of this location.
     * @param packageName  a package name
     * @param kinds        return objects only of these kinds
     * @param recurse      if true include "subpackages"
     * @return an Iterable of file objects matching the given criteria
     * @throws IOException if an I/O error occurred
     * @see JavaFileManager.list
     */
    Iterable<JavaFileObject> list(String packageName,
                                  Set<JavaFileObject.Kind> kinds,
                                  boolean recurse)
        throws IOException;

    /**
     * Infers a binary name of a file object returned from this location.
     *
     * @param location a location
     * @param file a file object
     * @return a binary name or {@code null} if the file object is not
     * found in this location
     * @see JavaFileManager.inferBinaryName
     */
    String inferBinaryName(JavaFileObject file);
}
