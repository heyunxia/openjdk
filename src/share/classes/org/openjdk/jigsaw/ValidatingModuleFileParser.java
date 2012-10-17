/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import org.openjdk.jigsaw.FileConstants.ModuleFile.SectionType;

/**
 * <p> A parser of <a
 * href="http://cr.openjdk.java.net/~mr/jigsaw/notes/module-file-format/">
 * module files</a> which validates file and section hashes </p>
 *
 * <p> The computed file and section content hashes are compared against the
 * appropriate hash value from the file or section header. If they do not
 * match exactly then a {@linkplain ModuleFileParserException} is thrown. </p>
 */

public interface ValidatingModuleFileParser extends ModuleFileParser {

    /**
     * Returns the hash of the module file header.
     *
     * @return  the hash
     */
    public byte[] getHeaderHash();

    /**
     * Returns the module file section hashes.
     *
     * <p> This is a convenience method to accumulate the section hashes of each
     * section, and is populated with the value of {@linkplain
     * ModuleFileParser#getHash() getHash} at each {@linkplain
     * ModuleFileParser.Event.END_SECTION END_SECTION}. </p>
     *
     * <p> The iteration order of the map is defined as, the order in which the
     * sections appear in the module file. For example, the MODULE_INFO section
     * is always first, followed by any optional sections. </p>
     *
     * <p> The map contains entries for sections that have been parsed by the
     * parser. More specifically, it contains entries for all sections where
     * {@code END_SECTION} has been returned. An empty map is returned if no
     * sections have been parsed.
     *
     * @return  a map of section hashes
     */
    public Map<SectionType,byte[]> getHashes();

    /**
     * Returns the hash of the module file.
     *
     * @return  the hash
     *
     * @throws  ModuleFileParserException
     *          If the current event is not {@code END_FILE}
     */
    public byte[] getFileHash();

}
