/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.jimage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Utility to read module info from .jimage file.

final class PackageModuleMap {
    private PackageModuleMap() {}

    static final String MODULES_ENTRY = "module/modules.offsets";
    static final String PACKAGES_ENTRY = "packages.offsets";

    /*
     * Returns a package-to-module map.
     *
     * The package name is in binary name format.
     */
    static Map<String,String> readFrom(ImageReader reader) throws IOException {
        Map<String,String> result = new HashMap<>();
        ImageLocation loc = reader.findLocation(MODULES_ENTRY);
        byte[] bytes = reader.getResource(loc.getContentOffset(), loc.getUncompressedSize());
        IntBuffer mbufs = ByteBuffer.wrap(bytes).asIntBuffer();
        List<String> mnames = new ArrayList<>();
        while (mbufs.hasRemaining()) {
            int moffset = mbufs.get();
            mnames.add(reader.getString(moffset));
        }

        for (String mn : mnames) {
            ImageLocation mindex = reader.findLocation(mn + "/" + PACKAGES_ENTRY);
            byte[] poffsets = reader.getResource(mindex.getContentOffset(), mindex.getUncompressedSize());
            IntBuffer pbufs = ByteBuffer.wrap(poffsets).asIntBuffer();
            while (pbufs.hasRemaining()) {
                int poffset = pbufs.get();
                String pn = reader.getString(poffset);
                result.put(pn, mn);
            }
        }
        return result;
    }
}
