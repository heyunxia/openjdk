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

package jdk.internal.jrtfs;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;

final class SystemImages {
    private SystemImages() {}

    static final Path bootImagePath;
    static final Path extImagePath;
    static final Path appImagePath;
    static {
        String javaHome = home();
        FileSystem fs = FileSystems.getDefault();
        bootImagePath = fs.getPath(javaHome, "lib", "modules", "bootmodules.jimage");
        extImagePath = fs.getPath(javaHome, "lib", "modules", "extmodules.jimage");
        appImagePath = fs.getPath(javaHome, "lib", "modules", "appmodules.jimage");
    }

    /**
     * Returns the appropriate JDK home for this usage of the FileSystemProvider.
     * When the CodeSource is null (null loader) then jrt:/ is the current runtime,
     * otherwise the JDK home is located relative to jrt-fs.jar.
     */
    private static String home() {
        CodeSource cs = SystemImages.class.getProtectionDomain().getCodeSource();
        if (cs == null)
            return System.getProperty("java.home");
        URL url = cs.getLocation();
        if (!url.getProtocol().equalsIgnoreCase("file"))
            throw new RuntimeException(url + " loaded in unexpected way");
        try {
            return Paths.get(url.toURI()).getParent().toString();
        } catch (URISyntaxException e) {
            throw new InternalError(e);
        }
    }
}
