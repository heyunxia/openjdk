/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.*;
import java.nio.file.Files;
import java.nio.file.attribute.*;
import java.util.*;


/**
 * <p> Utility methods that probably belong somewhere in java.nio.file ... </p>
 */

public class FilePaths {

    private FilePaths() { }

    public static void deleteTree(Path root) throws IOException {

        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

            public FileVisitResult visitFile(Path p,
                                             BasicFileAttributes ba)
                throws IOException
            {
                p.delete();
                return FileVisitResult.CONTINUE;
            }

            public FileVisitResult postVisitDirectory(Path p,
                                                      IOException x)
                throws IOException
            {
                if (x == null) {
                    p.delete();
                }
                return FileVisitResult.CONTINUE;
            }

        });

    }

    public static Path makeTemporaryDirectory(String prefix)
        throws IOException
    {

        Path root = Paths.get(System.getProperty("java.io.tmpdir"));
        Random r = new Random();
        Path td = null;
        for (;;) {
            td = root.resolve(prefix + "."
                              + Integer.toHexString(r.nextInt()));
            if (!td.exists()) {
                try {
                    td.createDirectory();
                    break;
                } catch (FileAlreadyExistsException x) {
                }
            }
        }

        final Path ftd = td;
        Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    try {
                        deleteTree(ftd);
                    } catch (IOException e) {
                    }
                }
            });

        return td;

    }

}
