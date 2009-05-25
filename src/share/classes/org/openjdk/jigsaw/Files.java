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

package org.openjdk.jigsaw;

import java.io.*;
import java.util.*;


public final class Files {

    private Files() { }

    private static void ensureIsDirectory(File path)
        throws IOException
    {
        if (!path.exists() || !path.isDirectory())
            throw new IOException(path + ": Not a directory");
    }

    private static void ensureIsFile(File path)
        throws IOException
    {
        if (!path.exists() || !path.isFile())
            throw new IOException(path + ": Not a file");
    }

    private static String[] list(File dir)
        throws IOException
    {
        ensureIsDirectory(dir);
        String[] fs = dir.list();
        if (fs == null)
            throw new IOException(dir + ": Cannot list directory contents");
        return fs;
    }

    private static File[] listFiles(File dir)
        throws IOException
    {
        ensureIsDirectory(dir);
        File[] fs = dir.listFiles();
        if (fs == null)
            throw new IOException(dir + ": Cannot list directory contents");
        return fs;
    }

    private static void delete(File path)
        throws IOException
    {
        if (!path.delete())
            throw new IOException(path + ": Cannot delete");
    }

    public static void deleteTree(File dst)
        throws IOException
    {
        File[] fs = listFiles(dst);
        for (int i = 0; i < fs.length; i++) {
            File f = fs[i];
            if (f.isDirectory()) {
                deleteTree(f);
            } else {
                delete(f);
            }
        }
        delete(dst);
    }

    private static void copy(File src, File dst)
        throws IOException
    {
        ensureIsFile(src);
        if (dst.exists())
            ensureIsFile(dst);
        byte[] buf = new byte[8192];
        FileInputStream in = new FileInputStream(src);
        try {
            FileOutputStream out = new FileOutputStream(dst);
            try {
                int n;
                while ((n = in.read(buf)) > 0)
                    out.write(buf, 0, n);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
        dst.setLastModified(src.lastModified());
    }

    public static interface Filter<T> {
        public boolean accept(T x) throws IOException;
    }

    // src, dst are directories
    // src must exist; dst created if it does not yet exist
    // Makes dst look exactly like src, modulo filtering
    //
    public static void copyTree(File src, File dst, Filter<File> filter)
        throws IOException
    {
        ensureIsDirectory(src);
        if (dst.exists()) {
            if (dst.isDirectory())
                deleteTree(dst);
            else
                delete(dst);
        }
        if (!dst.mkdirs())
            throw new IOException(dst + ": Cannot create directory");
        String[] sls = list(src);
        for (int i = 0; i < sls.length; i++) {
            File sf = new File(src, sls[i]);
            if (filter != null && !filter.accept(sf))
                continue;
            File df = new File(dst, sls[i]);
            if (sf.isDirectory())
                copyTree(sf, df, filter);
            else
                copy(sf, df);
        }           
        dst.setLastModified(src.lastModified());
    }

    public static void copyTree(File src, File dst)
        throws IOException
    {
        copyTree(src, dst, null);
    }

    public static interface Visitor<T> {
        public void accept(T x) throws IOException;
    }

    public static void walkTree(File src, Visitor<File> visitor)
        throws IOException
    {
        ensureIsDirectory(src);
        String[] sls = list(src);
        for (int i = 0; i < sls.length; i++) {
            File sf = new File(src, sls[i]);
            if (sf.isDirectory())
                walkTree(sf, visitor);
            else
                visitor.accept(sf);
        }
    }

    public static byte[] load(File src)
        throws IOException
    {
        DataInputStream in = new DataInputStream(new FileInputStream(src));
        int n = (int)src.length();
        byte[] bs = new byte[n];
        try {
            in.readFully(bs);
            return bs;
        } finally {
            in.close();
        }
    }

    public static void store(byte[] bs, File dst)
        throws IOException
    {
        OutputStream out = new FileOutputStream(dst);
        int n = bs.length;
        try {
            int i = 0;
            while (i < n) {
                int d = Math.min(n - i, 8192);
                out.write(bs, i, d);
                i += d;
            }
        } finally {
            out.close();
        }
    }

    public static void mkdirs(File d, String what)
        throws IOException
    {
        if (!d.mkdirs())
            throw new IOException(d + ": Cannot create " + what + " directory");
    }

}
