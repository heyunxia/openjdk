/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.FileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.*;
import java.util.zip.*;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;


/**
 * <p> Assorted file utilities </p>
 */

public final class Files {

    private Files() { }

    // paths are stored with a platform agnostic separator, '/'
    static String convertSeparator(String path) {
        return path.replace(File.separatorChar, '/');
    }

    static String platformSeparator(String path) {
        return path.replace('/', File.separatorChar);
    }

    static void ensureWriteable(File path) throws IOException {
        if (!path.canWrite())
            throw new IOException(path + ": is not writeable.");
    }

    static String ensureNonAbsolute(String path) throws IOException {
        if ((new File(path)).isAbsolute())
            throw new IOException("Abolute path instead of relative: " + path);
        return path;
    }

    static void ensureIsDirectory(File path) throws IOException {
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

    public static void delete(File path)
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

    static List<IOException> deleteTreeUnchecked(Path dir) {
        final List<IOException> excs = new ArrayList<>();
        try {
            java.nio.file.Files.walkFileTree(dir, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        java.nio.file.Files.delete(file);
                    } catch (IOException x) {
                        excs.add(x);
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    try {
                        java.nio.file.Files.delete(dir);
                    } catch (IOException x) {
                        excs.add(x);
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    excs.add(exc);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException x) {
            excs.add(x);
        }
        return excs;
    }

    private static void copy(File src, File dst)
        throws IOException
    {
        java.nio.file.Files.copy(src.toPath(), dst.toPath(),
                                 COPY_ATTRIBUTES, REPLACE_EXISTING);
    }

    /**
     * A predicate for filtering files
     */
    public static interface Filter<T> {
        public boolean accept(T x) throws IOException;
    }

    // src, dst are directories
    // src must exist; dst created if it does not yet exist
    // Copy files from src to dst, modulo filtering
    //
    public static void copyTree(File src, File dst, Filter<File> filter)
        throws IOException
    {
        ensureIsDirectory(src);
        if (dst.exists()) {
            if (!dst.isDirectory())
                delete(dst);
        } else if (!dst.mkdirs())
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

    private static void storeTree(File src, JarOutputStream dst, boolean deflate,
                                  Filter<File> filter, String dstPath)
        throws IOException
    {
        ensureIsDirectory(src);
        String[] sls = list(src);
        for (int i = 0; i < sls.length; i++) {
            File sf = new File(src, sls[i]);
            if (filter != null && !filter.accept(sf))
                continue;
            String dp = (dstPath == null) ? sls[i] : dstPath + "/" + sls[i];
            if (sf.isDirectory()) {
                storeTree(sf, dst, deflate, filter, dp);
            } else {
                ensureIsFile(sf);
                try (OutputStream out = newOutputStream(dst, deflate, dp)) {
                    java.nio.file.Files.copy(sf.toPath(), out);
                }
            }
        }
    }

    public static void storeTree(File src, JarOutputStream dst, boolean deflate,
                                 Filter<File> filter)
        throws IOException
    {
        storeTree(src, dst, deflate, filter, null);
    }

    public static void storeTree(File src, JarOutputStream dst, boolean deflate)
        throws IOException
    {
        storeTree(src, dst, deflate, null, null);
    }

    /**
     * A file visitor
     */
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

    public static byte[] load(InputStream is, int n)
        throws IOException
    {
        DataInputStream in = new DataInputStream(is);
        byte[] bs = new byte[n];
        try {
            in.readFully(bs);
            return bs;
        } finally {
            in.close();
        }
    }

    public static byte[] load(File src)
        throws IOException
    {
        FileInputStream fis = new FileInputStream(src);
        return load(fis, (int)src.length());
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

    private static class NonClosingInputStream
        extends FilterInputStream
    {

        private NonClosingInputStream(InputStream out) {
            super(out);
        }

        public void close() { }

    }

    public static InputStream nonClosingStream(InputStream out) {
        return new NonClosingInputStream(out);
    }

    private static class JarEntryOutputStream
        extends FilterOutputStream
    {

        CRC32 crc;
        ByteArrayOutputStream baos;
        CheckedOutputStream cos;
        JarOutputStream jos;
        boolean deflate;
        String path;

        private JarEntryOutputStream(JarOutputStream jos,
                                     boolean deflate,
                                     CRC32 crc,
                                     ByteArrayOutputStream baos,
                                     CheckedOutputStream cos,
                                     String path)
        {
            super(cos);
            this.jos = jos;
            this.deflate = deflate;
            this.crc = crc;
            this.baos = baos;
            this.cos = cos;
            this.path = path;
        }

        public void close() throws IOException {
            cos.close();
            JarEntry je = new JarEntry(path);
            if (deflate) {
                je.setMethod(JarEntry.DEFLATED);
            } else {
                je.setMethod(JarEntry.STORED);
                je.setCrc(crc.getValue());
                je.setSize(baos.size());
                je.setCompressedSize(baos.size());
            }
            jos.putNextEntry(je);
            baos.writeTo(jos);
            jos.closeEntry();
        }

    }

    public static JarEntryOutputStream
        newOutputStream(JarOutputStream jos, boolean deflate, String path)
    {
        // Gee, dac, that zip API sure is broken, isn't it?
        CRC32 crc = new CRC32();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CheckedOutputStream cos = new CheckedOutputStream(baos, crc);
        return new JarEntryOutputStream(jos, deflate, crc, baos, cos, path);
    }

    public static JarEntryOutputStream
        newOutputStream(JarOutputStream jos, String path)
    {
        return newOutputStream(jos, false, path);
    }
}
