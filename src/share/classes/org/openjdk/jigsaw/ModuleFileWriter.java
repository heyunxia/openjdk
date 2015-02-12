/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.attribute.BasicFileAttributes;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import static org.openjdk.jigsaw.FileConstants.ModuleFile.*;
import static org.openjdk.jigsaw.ModuleFile.*;


/**
 * <p> A writer of module files </p>
 */

public class ModuleFileWriter {

    private final File outfile;
    private final HashType hashtype;
    private final boolean fastestCompression;
    private long usize;

    public ModuleFileWriter(File outfile) {
        this(outfile, false);
    }

    public ModuleFileWriter(File outfile, boolean useFastestCompression) {
        this.outfile = outfile;
        this.hashtype = HashType.SHA256;
        this.fastestCompression = useFastestCompression;
    }

    /**
     * Generates an unsigned module file.
     */
    public void writeModule(File mdir,
                            File nativelibs,
                            File nativecmds,
                            File config)
            throws IOException
    {
        if (!mdir.isDirectory()) {
            throw new IOException("Not a directory: " + mdir);
        }

        try (RandomAccessFile file = new RandomAccessFile(outfile, "rw")) {
            // Truncate the file if it already exists
            file.setLength(0);

            // Reset module file to right after module header
            file.seek(ModuleFileHeader.LENGTH);

            // TODO: Why was this after the module info???
            long remainderStart = file.getFilePointer();

            // Write out the Module-Info Section
            File miclass = new File(mdir, "module-info.class");
            if (!miclass.exists()) {
                throw new IOException(miclass + " does not exist");
            }
            writeSection(file,
                         SectionType.MODULE_INFO,
                         mdir,
                         Collections.singletonList(miclass.toPath()),
                         Compressor.NONE);

            // Write out the optional file sections
            writeOptionalSections(file, mdir, nativelibs, nativecmds, config);

            // Write out the module file header
            writeModuleFileHeader(file, remainderStart);
        }
    }

    /*
     * Write a section to the given module file.
     *
     * @params file RandomAccessFile for the resulting jmod file
     * @params type section type
     * @params sourcedir the directory containing the files to be written
     * @params files list of files to be written
     * @params compressor compression type
     */
    private void writeSection(RandomAccessFile file,
                              SectionType type,
                              File sourcedir,
                              List<Path> files,
                              Compressor compressor) throws IOException {
        // Start of section header
        final long start = file.getFilePointer();

        // Start of section content
        final long cstart = start + SectionHeader.LENGTH;
        // Seek to start of section content
        file.seek(cstart);

        writeSectionContent(file, type, sourcedir, files, compressor);

        // End of section
        final long end = file.getFilePointer();
        final int csize = (int) (end - cstart);

        // A section type that has no files has a section count of 0.
        int count = type.hasFiles() ? files.size() : 0;
        if (count > Short.MAX_VALUE) {
            throw new IOException("Too many files: " + count);
        }
        writeSectionHeader(file, type, compressor, start, csize, (short) count);
    }

    private void writeSectionContent(RandomAccessFile file,
                                     SectionType type,
                                     File sourcedir,
                                     List<Path> files,
                                     Compressor compressor) throws IOException {

        if (type.hasFiles()) {
            for (Path p : files) {
                writeSubSection(file, sourcedir, p, compressor);
            }
        } else if (type == SectionType.CLASSES) {
            writeClassesContent(file, sourcedir, files, compressor);
        } else if (files.size() == 1) {
            writeFileContent(file, files.get(0), compressor);
        } else {
            throw new IllegalArgumentException("Section type: " + type
                    + " can only have one single file but given " + files);
        }
    }

    private void writeSectionHeader(RandomAccessFile file,
                                    SectionType type,
                                    Compressor compressor,
                                    long start, int csize,
                                    short subsections) throws IOException {

        // Compute hash of content
        MessageDigest md = getHashInstance(hashtype);
        FileChannel channel = file.getChannel();
        ByteBuffer content = ByteBuffer.allocate(csize);

        final long cstart = start + SectionHeader.LENGTH;
        int n = channel.read(content, cstart);
        if (n != csize) {
            throw new IOException("too few bytes read");
        }
        content.position(0);
        md.update(content);
        final byte[] hash = md.digest();

        // Write section header at section header start,
        // and seek to end of section.
        SectionHeader header =
                new SectionHeader(type, compressor, csize, subsections, hash);
        file.seek(start);
        header.write(file);
        file.seek(cstart + csize);
    }

    private void writeClassesContent(DataOutput out,
                                     File sourcedir,
                                     List<Path> files,
                                     Compressor compressor) throws IOException {
        CompressedClassOutputStream cos =
            CompressedClassOutputStream.newInstance(sourcedir.toPath(),
                                                    files,
                                                    compressor,
                                                    fastestCompression);
        usize += cos.getUSize();
        cos.writeTo(out);
    }

    private void writeFileContent(DataOutput out,
                                  Path p,
                                  Compressor compressor) throws IOException {
        CompressedOutputStream cos = CompressedOutputStream.newInstance(p, compressor);
        usize += cos.getUSize();
        cos.writeTo(out);
    }

    /*
     * Write a subsection to the given module file.
     *
     * @params file RandomAccessFile for the resulting jmod file
     * @params sourcedir the directory containing the file to be written
     * @params p Path of a file to be written
     * @params compressor compression type
     */
    private void writeSubSection(RandomAccessFile file,
                                 File sourcedir,
                                 Path p,
                                 Compressor compressor) throws IOException {
        CompressedOutputStream cos = CompressedOutputStream.newInstance(p, compressor);
        usize += cos.getUSize();

        String storedpath = relativePath(sourcedir.toPath(), p);
        SubSectionFileHeader header = new SubSectionFileHeader((int)cos.getCSize(), storedpath);
        header.write(file);
        cos.writeTo(file);
    }


    /*
     * Processes each of the optional file sections.
     */
    private void writeOptionalSections(RandomAccessFile file,
                                       File mdir,
                                       File nativelibs,
                                       File nativecmds,
                                       File config)
            throws IOException
   {
        List<Path> classes = new ArrayList<>();
        List<Path> resources = new ArrayList<>();
        listClassesResources(mdir.toPath(), classes, resources);

        if (!classes.isEmpty()) {
            writeSection(file,
                         SectionType.CLASSES,
                         mdir,
                         classes,
                         Compressor.PACK200_GZIP);
        }
        if (!resources.isEmpty()) {
            writeSection(file,
                         SectionType.RESOURCES,
                         mdir,
                         resources,
                         Compressor.GZIP);
        }

        if (nativelibs != null && directoryIsNotEmpty(nativelibs)) {
            writeSection(file,
                         SectionType.NATIVE_LIBS,
                         nativelibs,
                         listFiles(nativelibs.toPath()),
                         Compressor.GZIP);
        }
        if (nativecmds != null && directoryIsNotEmpty(nativecmds)) {
            writeSection(file,
                         SectionType.NATIVE_CMDS,
                         nativecmds,
                         listFiles(nativecmds.toPath()),
                         Compressor.GZIP);
        }
        if (config != null && directoryIsNotEmpty(config)) {
            writeSection(file,
                         SectionType.CONFIG,
                         config,
                         listFiles(config.toPath()),
                         Compressor.GZIP);
        }
    }

    /*
     * Writes out the module file header.
     */
    private void writeModuleFileHeader(RandomAccessFile file,
                                       long remainderStart)
            throws IOException
    {

        long csize = file.length() - remainderStart;

        // Header Step 1
        // Write out the module file header (using a dummy file hash value)
        ModuleFileHeader header =
                new ModuleFileHeader(csize, usize, hashtype,
                                     new byte[hashtype.length()]);
        file.seek(0);
        header.write(file);

        // Generate the module file hash
        byte[] fileHash = generateFileHash(file);

        // Header Step 2
        // Write out the module file header (using correct file hash value)
        header = new ModuleFileHeader(csize, usize, hashtype, fileHash);
        file.seek(0);
        header.write(file);
    }

    private void listClassesResources(Path dir,
                                      final List<Path> classes,
                                      final List<Path> resources)
            throws IOException
    {

        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (!file.endsWith("module-info.class")) {
                    if (file.toFile().getName().endsWith(".class")) {
                        classes.add(file);
                    } else {
                        resources.add(file);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // CompressedOutputStream and CompressedClassOutputStream are
    // subclass of ByteArrayOutputStream to avoid the array copy
    // from the compressed bytes and write buf directly to DataOutput
    static class CompressedOutputStream extends ByteArrayOutputStream {
        protected long size = 0;   // uncompressed size
        protected CompressedOutputStream() {
        }

        private CompressedOutputStream(Path p) throws IOException {
            // no compression
            size += Files.copy(p, this);
        }

        long getUSize() {
            return size;
        }

        long getCSize() {
            return count;
        }

        void writeTo(DataOutput out) throws IOException {
            out.write(buf, 0, count);
        }

        static CompressedOutputStream newInstance(Path p, Compressor type)
                throws IOException
        {
            switch (type) {
                case NONE:
                    return new CompressedOutputStream(p);
                case GZIP:
                    return new GZIPCompressedOutputStream(p);
                case PACK200_GZIP:
                default:
                    throw new IllegalArgumentException("Unsupported type: " + type);
            }
        }

        static class GZIPCompressedOutputStream extends CompressedOutputStream {
            GZIPCompressedOutputStream(Path p) throws IOException {
                super();
                size += p.toFile().length();
                try (GZIPOutputStream gos = new GZIPOutputStream(this)) {
                    Files.copy(p, gos);
                    gos.finish();
                }
            }
        }
    }

    static abstract class CompressedClassOutputStream extends ByteArrayOutputStream {
        protected long size = 0;
        long getUSize() {
            return size;
        }

        long getCSize() {
            return count;
        }

        void writeTo(DataOutput out) throws IOException {
            out.write(buf, 0, count);
        }

        static CompressedClassOutputStream newInstance(Path sourcepath,
                                                       List<Path> classes,
                                                       Compressor type,
                                                       boolean fastestCompression)
                throws IOException
        {
            switch (type) {
                case PACK200_GZIP:
                    Pack200GZipOutputStream pos =
                            new Pack200GZipOutputStream(fastestCompression);
                    pos.compress(sourcepath, classes);
                    return pos;
                default:
                    throw new IllegalArgumentException("Unsupported type: " + type);
            }
        }

        static class Pack200GZipOutputStream extends CompressedClassOutputStream {
            final Pack200.Packer packer = Pack200.newPacker();
            Pack200GZipOutputStream(boolean fastestCompression) {
                Map<String, String> p = packer.properties();
                p.put(Pack200.Packer.SEGMENT_LIMIT, "-1");
                if (fastestCompression) {
                    p.put(Pack200.Packer.EFFORT, "1");
                }
                p.put(Pack200.Packer.KEEP_FILE_ORDER, Pack200.Packer.FALSE);
                p.put(Pack200.Packer.MODIFICATION_TIME, Pack200.Packer.LATEST);
                p.put(Pack200.Packer.DEFLATE_HINT, Pack200.Packer.FALSE);
            }

            void compress(Path sourcepath, List<Path> classes) throws IOException {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                try (JarOutputStream jos = new JarOutputStream(os)) {
                    jos.setLevel(0);
                    for (Path file : classes) {
                        // write to the JarInputStream for later pack200 compression
                        String name = file.toFile().getName().toLowerCase();
                        String p = sourcepath.relativize(file).toString();
                        if (!p.equals("module-info.class")) {
                            JarEntry entry = new JarEntry(p);
                            jos.putNextEntry(entry);
                            Files.copy(file, jos);
                            jos.closeEntry();
                        }
                        size += file.toFile().length();
                    }
                }

                byte[] data = os.toByteArray();
                try (JarInputStream jin =
                        new JarInputStream(new ByteArrayInputStream(data));
                     GZIPOutputStream gout = new GZIPOutputStream(this)) {
                    packer.pack(jin, gout);
                }
            }
        }
    }

    private String relativePath(Path sourcepath, Path path) throws IOException {
        Path relativepath = sourcepath.relativize(path);

        // The '/' character separates nested directories in path names.
        String pathseparator = relativepath.getFileSystem().getSeparator();
        String stored = relativepath.toString().replace(pathseparator, "/");

        // The path names of native-code files
        // must not include more than one element.
        // ## Temporarily turn off this check until the location of
        // ## the native libraries in jdk modules are changed
        // ensureShortNativePath(realpath, stored);
        return stored;
    }

    private List<Path> listFiles(Path path) throws IOException {
        final List<Path> list = new ArrayList<>();
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                list.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        return list;
    }

    /*
     * Generates the hash value for a module file.
     * Excludes itself (the hash bytes in the module file header).
     */
    private byte[] generateFileHash(RandomAccessFile file)
            throws IOException
    {
        MessageDigest md = getHashInstance(hashtype);

        long remainderSize = file.length() - ModuleFileHeader.LENGTH;
        FileChannel channel = file.getChannel();

        // Module file header without the hash bytes
        ByteBuffer content = ByteBuffer.allocate(ModuleFileHeader.LENGTH_WITHOUT_HASH);
        int n = channel.read(content, 0);
        if (n != ModuleFileHeader.LENGTH_WITHOUT_HASH) {
            throw new IOException("too few bytes read");
        }
        content.position(0);
        md.update(content);

        // Remainder of file (read in chunks)
        content = ByteBuffer.allocate(8192);
        channel.position(ModuleFileHeader.LENGTH);
        n = channel.read(content);
        while (n != -1) {
            content.limit(n);
            content.position(0);
            md.update(content);
            content = ByteBuffer.allocate(8192);
            n = channel.read(content);
        }

        return md.digest();
    }

    /*
     * Check if a given directory is not empty.
     */
    private static boolean directoryIsNotEmpty(File dir)
            throws IOException {
        try (DirectoryStream<Path> ds =
                        Files.newDirectoryStream(dir.toPath())) {
            return ds.iterator().hasNext();
        }
    }

}
