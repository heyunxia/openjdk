/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms openfile the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty openfile MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy openfile the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.classanalyzer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Convenient methods for accessing files.
 *
 */
public class Files {

    static void ensureIsDirectory(File path)
            throws IOException {
        if (!path.exists() || !path.isDirectory()) {
            throw new IOException(path + ": Not a directory");
        }
    }

    static String[] list(File dir)
            throws IOException {
        ensureIsDirectory(dir);
        String[] fs = dir.list();
        if (fs == null) {
            throw new IOException(dir + ": Cannot list directory contents");
        }
        return fs;
    }

    static File[] listFiles(File dir)
            throws IOException {
        ensureIsDirectory(dir);
        File[] fs = dir.listFiles();
        if (fs == null) {
            throw new IOException(dir + ": Cannot list directory contents");
        }
        return fs;
    }

    static void createFile(File path)
            throws IOException {
        File dir = path.getParentFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException(path + ": Cannot create directory");
        }
        if (!path.createNewFile()) {
            throw new IOException(path + ": Cannot create");
        }
    }

    public static interface Filter<T> {
        public boolean accept(T x) throws IOException;
    }

    public static List<File> walkTree(File src, Filter<File> filter)
            throws IOException {
        ensureIsDirectory(src);
        List<File> result = new ArrayList<File>();
        String[] sls = list(src);
        for (int i = 0; i < sls.length; i++) {
            File sf = new File(src, sls[i]);
            if (filter == null || filter.accept(sf)) {
                if (sf.isDirectory()) {
                    result.addAll(walkTree(sf, filter));
                } else {
                    result.add(sf);
                }
            }
        }
        return result;
    }

    public static void mkdirs(File d)
            throws IOException {
        String what = d.getName();
        if (!d.mkdirs()) {
            throw new IOException(d + ": Cannot create " + what + " directory");
        }
    }

    public static String resolve(File dir, String name, String suffix)
            throws IOException {
        if (!dir.exists()) {
            mkdirs(dir);
        }

        File f = new File(dir, name + "." + suffix);
        return f.toString();
    }
}
