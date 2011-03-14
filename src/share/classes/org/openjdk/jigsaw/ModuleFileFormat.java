/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
import java.math.BigInteger;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import javax.security.auth.x500.X500Principal;
import sun.security.action.OpenFileInputStreamAction;
import sun.security.pkcs.*;
import sun.security.timestamp.*;
import sun.security.validator.*;
import sun.security.x509.*;

import static org.openjdk.jigsaw.FileConstants.*;

public final class ModuleFileFormat {
    /**
     * Return the subdir of a section in an extracted module file.
     */
    public static String getSubdirOfSection(ModuleFile.SectionType type) {
        switch (type) {
        case MODULE_INFO:
        case SIGNATURE:
            return ".";
        case CLASSES:
        case RESOURCES:
            return "classes";
        case NATIVE_LIBS:
            return "lib";
        case NATIVE_CMDS:
            return "bin";
        case CONFIG:
            return "etc";
        default:
            throw new AssertionError(type);
        }
    }

    public final static class Writer {
        private static final int SIGNATURE_HEADER_LENGTH = 4;
        private boolean fastestCompression;
        private File outfile;
        private RandomAccessFile file;
        private File sourcedir;
        private ModuleFile.HashType hashtype;
        private long usize;
        private boolean doSign = false;
        private ModuleFileSigner signatureMechanism = null;
        private ModuleFileSigner.Parameters signatureParameters = null;
        private List<byte[]> signatureHashes = null;

        private static class ByteArrayDataOutputStream
            extends ByteArrayOutputStream {
            public void writeTo(DataOutput out) throws IOException {
                out.write(buf, 0, count);
            }
        }

        public Writer (File outfile, File sourcedir) {
            this.outfile = outfile;
            hashtype = ModuleFile.HashType.SHA256;
            this.sourcedir = sourcedir;
        }

        /*
         * Generates a module file.
         */
        public void writeModule(File classes,
                                File resources,
                                File nativelibs,
                                File nativecmds,
                                File config)
            throws IOException, SignatureException {

            file = new RandomAccessFile(outfile, "rw");

            // Truncate the file if it already exists
            file.setLength(0);

            // Set constants that depend on the hash length
            MessageDigest md = getHashInstance(hashtype);
            int hashLength = md.getDigestLength();
            final int FILE_HEADER_LENGTH = 32 + hashLength;

            if (doSign) {
                // Record the hashes as they get generated
                signatureHashes = new ArrayList<byte[]>();
            }

            // Reset module file to right after module header
            file.seek(FILE_HEADER_LENGTH);

            // Write out the Module-Info Section
            File miclass = new File("module-info.class");
            writeSection(file, ModuleFile.SectionType.MODULE_INFO,
                         miclass, ModuleFile.Compressor.NONE);

            short sections = 1;
            int signatureSectionStart = 0;
            int signatureSectionLength = 0;

            // Signature Step 1
            // Generate a false signature (using dummy hash values).
            if (doSign) {
                // Record the file location for the Signature Section
                signatureSectionStart = (int) file.getFilePointer();
                int optionalSections =
                    countOptionalSections(classes, resources, nativelibs,
                                          nativecmds, config);
                // Determine the length of the Signature Section
                signatureSectionLength =
                    prepareSignatureSection(file, hashLength, optionalSections);
                usize += signatureSectionLength;
                sections++;
            }

            // Write out the optional file sections
            sections += writeOptionalSections(classes, resources, nativelibs,
                                              nativecmds, config);

            // Write out the module file header
            writeModuleFileHeader(md, FILE_HEADER_LENGTH, sections, hashtype,
                                  hashLength, signatureSectionStart,
                                  signatureSectionLength);

            // Signature Step 2
            // Generate the true signature (using correct hash values).
            if (doSign) {
                // Write out the Signature Section
                writeSignatureSection(file, md, signatureSectionStart);
            }
            file.close();
        }

        public void writeSection(RandomAccessFile file,
                                 ModuleFile.SectionType type,
                                 File dir,
                                 ModuleFile.Compressor compressor)
            throws IOException {

            checkFileName(type, dir);

            MessageDigest md = getHashInstance(hashtype);

            // Start of section header
            final long start = file.getFilePointer();
            // Start of section content
            final long cstart = start + 12 + md.getDigestLength();
            // Seek to start of section content
            file.seek(cstart);

            short count = writeFile(file, dir, compressor, type);
            // End of section
            final long end = file.getFilePointer();
            final int csize = (int) (end - cstart);

            // Reset module file to right after section header
            file.seek(cstart);

            // Compute hash of content
            FileChannel channel = file.getChannel();
            ByteBuffer content = channel.map(MapMode.READ_ONLY, cstart, csize);
            md.update(content);
            final byte[] hash = md.digest();

            if (doSign) {
                // section hash is needed for the signature
                signatureHashes.add(hash);
            }

            // A section type that only allows a single file
            // has a section count of 0.
            if (count > Short.MAX_VALUE)
                throw new IOException("Too many files: " + count);

            final short subsections = type.hasFiles() ? count : 0;

            // Write section header at section header start,
            // and seek to end of section.
            SectionHeader header =
                new SectionHeader(type, compressor, csize, subsections, hash);
            file.seek(start);
            header.write(file);
            file.seek(end);
        }

        public short writeFile(DataOutput out, File path,
                               ModuleFile.Compressor compressor,
                               ModuleFile.SectionType type)
            throws IOException {

            switch (compressor) {
            case NONE:
                boolean subsectionfileheader =
                    type == ModuleFile.SectionType.MODULE_INFO ? false : true;
                writeUncompressedFile(out, path, subsectionfileheader);
                usize += path.length();
                return 1;
            case GZIP:
                short count = 0;
                sourcedir = path;

                if (!path.exists())
                    throw new IOException("Path does not exist: " + path);
                if (!path.isDirectory())
                    throw new IOException("Path is not a directory: " + path);
                if (!path.canRead())
                    throw new IOException("Path can not be read: " + path);
                Queue<File> files =
                    new LinkedList(Arrays.asList(path.listFiles()));
                if (files.isEmpty())
                    throw new IOException("Path is empty: " + path);

                // System.out.println("Gzipping: " + path);
                while (!files.isEmpty()) {
                    File file = files.remove();
                    if (file.isDirectory()) {
                        files.addAll(Arrays.asList(file.listFiles()));
                        continue;
                    }
                    else {
                        writeGZIPCompressedFile(out, file);
                        ++count;
                        usize += file.length();
                    }
                }
                return count;
            case PACK200_GZIP:
                writeClasses(out, path);
                return 1;
            default:
                throw new IOException("Unsupported Compressor for files: " +
                                      compressor);
            }
        }

        public void writeClasses(DataOutput out, File dir)
            throws IOException {

            copyStream(packAndGzip(jar(dir)), out);
        }

        public void writeGZIPCompressedFile(DataOutput out,
                                            File path)
            throws IOException {

            File realpath = computeRealPath(path);
            String storedpath = computeStoredPath(realpath);
            // System.out.println("Gzipping " + realpath);
            ByteArrayDataOutputStream bados = new ByteArrayDataOutputStream();
            GZIPOutputStream gos = new GZIPOutputStream(bados);
            InputStream is = new FileInputStream(realpath);
            BufferedInputStream in = new BufferedInputStream(is);
            copyStream(in, gos);
            in.close();
            gos.finish();
            gos.close();

            final int csize = bados.size();
            SubSectionFileHeader header
                = new SubSectionFileHeader(csize, storedpath);
            header.write(out);
            bados.writeTo(out);
        }

        public void writeUncompressedFile(DataOutput out, File path,
                                          boolean writeheader)
            throws IOException {

            File realpath = computeRealPath(path);
            if (writeheader) {
                final long size = realpath.length();
                ensureValidFileSize(size, realpath);
                final int csize = (int) size;
                String storedpath = computeStoredPath(realpath);

                SubSectionFileHeader header
                    = new SubSectionFileHeader(csize, storedpath);
                header.write(out);
            }

            InputStream is = new FileInputStream(realpath);
            BufferedInputStream in = new BufferedInputStream(is);
            copyStream(in, out);
            in.close();
        }

        private static void checkFileName(ModuleFile.SectionType t,
                                          File file)
            throws IOException {

            final String MICLASS = "module-info.class";

            if (ModuleFile.SectionType.MODULE_INFO == t
                && ! file.toString().toLowerCase().endsWith(MICLASS))
                throw new IOException("Not a " + MICLASS + " file: " + file);

            if (ModuleFile.SectionType.CLASSES == t
                && ! file.isDirectory())
                throw new IOException("Not a directory: " + file);
        }

        private File computeRealPath(File path)
            throws IOException {
            String name = path.toString();
            if (name.startsWith(sourcedir.toString()))
                name = sourcedir.toPath().relativize(path.toPath()).toString();
            File realpath = resolveAndNormalize(sourcedir, name);
            if (realpath.isDirectory())
                throw new IOException("Directory instead of file: " + realpath);
            return realpath;
        }

        private String computeStoredPath(File file) throws IOException {
            // Path names are relative to an unspecifed installation directory.
            Path relativepath = (sourcedir != null?
                                 sourcedir.toPath().relativize(file.toPath())
                                 : file.toPath());
            // The '/' character separates nested directories in path names.
            String pathseparator = relativepath.getFileSystem().getSeparator();
            String stored = relativepath.toString().replace(pathseparator, "/");
            // System.out.println(sourcedir.toString() + " : " + file.toString() + "->" + stored);
            // The path names of native-code files
            // must not include more than one element.
            // ## Temporarily turn off this check until the location of
            // ## the native libraries in jdk modules are changed
            // ensureShortNativePath(file, stored);
            return stored;
        }

        private JarInputStream jar(File dir)
            throws IOException {

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            JarOutputStream jos = new JarOutputStream(baos);
            jos.setLevel(0);
            Queue<File> files = new LinkedList(Arrays.asList(dir.listFiles()));

            while (!files.isEmpty()) {
                File file = files.remove();
                if (file.isDirectory()) {
                    files.addAll(Arrays.asList(file.listFiles()));
                    continue;
                }
                else {
                    String name = file.getName().toLowerCase();
                    final String CLASS = ".class";
                    final String MICLASS = "module-info.class";
                    if (name.endsWith(CLASS) && !name.equals(MICLASS)) {
                        String path = dir.toPath()
                            .relativize(file.toPath()).toString();
                        JarEntry entry = new JarEntry(path);
                        jos.putNextEntry(entry);
                        FileInputStream cfis = new FileInputStream(file);
                        try {
                            copyStream(cfis, jos);
                        } finally {
                            cfis.close();
                        }
                        jos.closeEntry();
                    }
                    usize += file.length();
                }
            }
            jos.close();

            byte[] jar = baos.toByteArray();
            ByteArrayInputStream bain = new ByteArrayInputStream(jar);
            return new JarInputStream(bain);
        }

        private InputStream packAndGzip(JarInputStream jar)
            throws IOException {

            Pack200.Packer packer = Pack200.newPacker();
            Map p = packer.properties();
            p.put(Pack200.Packer.SEGMENT_LIMIT, "-1");
            if (fastestCompression)
                p.put(Pack200.Packer.EFFORT, "1");
            p.put(Pack200.Packer.KEEP_FILE_ORDER, Pack200.Packer.FALSE);
            p.put(Pack200.Packer.MODIFICATION_TIME, Pack200.Packer.LATEST);
            p.put(Pack200.Packer.DEFLATE_HINT, Pack200.Packer.FALSE);
            ByteArrayOutputStream gbaos = new ByteArrayOutputStream();
            GZIPOutputStream gout = new GZIPOutputStream(gbaos);
            packer.pack(jar, gout);
            jar.close();
            gout.close();

            return new ByteArrayInputStream(gbaos.toByteArray());
        }

        /**
         * Favor compression speed over size of resulting file.
         */
        public void useFastestCompression(boolean fastest) {
            fastestCompression = fastest;
        }

        /**
         * Set the module signer mechanism.
         */
        public void setSignatureMechanism(ModuleFileSigner mechanism,
                                          ModuleFileSigner.Parameters
                                              parameters)
        {
            if (mechanism == null || parameters == null) {
                throw new IllegalArgumentException();
            }
            signatureMechanism = mechanism;
            signatureParameters = parameters;
            doSign = true;
        }

        /*
         * Counts the number of optional file sections.
         */
        private int countOptionalSections(File classes,
                                          File resources,
                                          File nativelibs,
                                          File nativecmds,
                                          File config)
        {
            int count = 0;

            if (classes != null)
                count++;
            if (resources != null)
                count++;
            if (nativelibs != null)
                count++;
            if (nativecmds != null)
                count++;
            if (config != null)
                count++;

            return count;
        }

        /*
         * Check if a given directory is not empty.
         */

        private static boolean directoryIsNotEmpty(File dir)
            throws IOException {
            return dir.toPath().newDirectoryStream().iterator().hasNext();
        }

        /*
         * Processes each of the optional file sections.
         */
        private int writeOptionalSections(File classes,
                                          File resources,
                                          File nativelibs,
                                          File nativecmds,
                                          File config)
            throws IOException {

            int count = 0;

            if (classes != null && directoryIsNotEmpty(classes)) {
                writeSection(file, ModuleFile.SectionType.CLASSES,
                             classes, ModuleFile.Compressor.PACK200_GZIP);
                count++;
            }
            if (resources != null && directoryIsNotEmpty(resources)) {
                writeSection(file, ModuleFile.SectionType.RESOURCES,
                             resources, ModuleFile.Compressor.GZIP);
                count++;
            }
            if (nativelibs != null && directoryIsNotEmpty(nativelibs)) {
                writeSection(file, ModuleFile.SectionType.NATIVE_LIBS,
                             nativelibs, ModuleFile.Compressor.GZIP);
                count++;
            }
            if (nativecmds != null && directoryIsNotEmpty(nativecmds)) {
                writeSection(file, ModuleFile.SectionType.NATIVE_CMDS,
                             nativecmds, ModuleFile.Compressor.GZIP);
                count++;
            }
            if (config != null && directoryIsNotEmpty(config)) {
                writeSection(file, ModuleFile.SectionType.CONFIG,
                             config, ModuleFile.Compressor.GZIP);
                count++;
            }

            return count;
        }

        /*
         * Writes out the module file header.
         */
        private void writeModuleFileHeader(MessageDigest md,
                                           int remainderStart,
                                           short sections,
                                           ModuleFile.HashType hashtype,
                                           int hashLength,
                                           int signatureSectionStart,
                                           int signatureSectionLength)
            throws IOException {

            long csize = file.length() - remainderStart;

            // Header Step 1
            // Write out the module file header (using a dummy file hash value)
            ModuleFileHeader header =
                new ModuleFileHeader(csize, usize, sections, hashtype,
                                     new byte[hashLength]);
            file.seek(0);
            header.write(file);

            // Generate the module file hash
            byte[] fileHash = null;
            fileHash = generateFileHash(file, md, remainderStart,
                                        signatureSectionStart,
                                        signatureSectionLength);

            // Header Step 2
            // Write out the module file header (using correct file hash value)
            header = new ModuleFileHeader(csize, usize, sections, hashtype,
                                          fileHash);
            file.seek(0);
            header.write(file);

            if (doSign) {
                md.update(header.toByteArray());
                // module header hash must be first
                signatureHashes.add(0, md.digest());
                // module file hash must be last
                signatureHashes.add(fileHash);
            }
        }

        /*
         * Generates the hash value for a module file.
         * Excludes itself (the hash bytes in the module file header) and
         * the Signature Section, if present.
         */
        private byte[] generateFileHash(RandomAccessFile file,
                                        MessageDigest md,
                                        int remainderPosition,
                                        int signatureSectionPosition,
                                        int signatureSectionSize)
            throws IOException {

            long remainderSize = file.length() - remainderPosition;
            FileChannel channel = file.getChannel();

            // Module file header without the hash bytes
            ByteBuffer content = channel.map(MapMode.READ_ONLY, 0, 32);
            md.update(content);

            // Module-Info Section is handled separately when Signature Section
            // is present
            if (signatureSectionPosition > 0 && signatureSectionSize > 0) {
                int moduleInfoSectionPosition = remainderPosition;
                int moduleInfoSectionSize = signatureSectionPosition
                                            - remainderPosition;
                content = channel.map(MapMode.READ_ONLY,
                                      moduleInfoSectionPosition,
                                      moduleInfoSectionSize);
                md.update(content);

                remainderPosition = signatureSectionPosition
                                    + signatureSectionSize;
                remainderSize -= (moduleInfoSectionSize + signatureSectionSize);
            }

            // Remainder of file
            content = channel.map(MapMode.READ_ONLY, remainderPosition,
                                  remainderSize);
            md.update(content);

            return md.digest();
        }

        /*
         * Determines the length of the Signature Section. The module file is
         * extended to make space for the Signature Section.
         *
         * @see writeSignatureSection
         */
        private int prepareSignatureSection(DataOutput stream,
                                            int hashLength,
                                            int optionalSectionCount)
            throws IOException, SignatureException {

            // Each optional section has its own hash plus the header hash,
            // the Module-Info hash and the file hash.
            // Each hash is prefixed with a 2-byte length value.
            byte[] dummyToBeSigned =
                new byte[(optionalSectionCount + 3) * (2 + hashLength)];
            final int SECTION_HEADER_LENGTH = 12 + hashLength;

            // Compute the false signature using the dummy hash values
            byte[] dummySignature =
                signatureMechanism.generateSignature(dummyToBeSigned,
                                                     signatureParameters);

            // Make space for the Signature Section
            int signatureSectionPrefix =
                SECTION_HEADER_LENGTH + SIGNATURE_HEADER_LENGTH;
            int signatureSectionLength =
                signatureSectionPrefix + dummySignature.length;

            file.setLength(file.length() + signatureSectionLength); // extend
            file.write(new byte[signatureSectionPrefix]);
            file.write(dummySignature);

            return signatureSectionLength;
        }

        /*
         * Generates the module file signature and writes the Signature Section
         * into the space already prepared.
         *
         * The data to be signed is a list of hash values:
         *
         *     ToBeSignedContent {
         *         u2 moduleHeaderHashLength;
         *         b* moduleHeaderHash;
         *         u2 moduleInfoHashLength;
         *         b* moduleInfoHash;
         *         u2 sectionHashLength;
         *         b* sectionHash;
         *         ...
         *         // other section hashes (in same order as module file)
         *         ...
         *         u2 moduleFileHashLength;
         *         b* moduleFileHash;
         *     }
         *
         * @see prepareSignatureSection
         */
        private void writeSignatureSection(DataOutput stream,
                                           MessageDigest md,
                                           int signatureSectionStart)
            throws IOException, SignatureException {

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            short hashLength;
            for (byte[] hash : signatureHashes) {
                hashLength = (short) hash.length;
                baos.write((byte) ((hashLength >>> 8) & 0xFF));
                baos.write((byte) ((hashLength >>> 0) & 0xFF));
                baos.write(hash, 0, hashLength);
            }
            byte[] toBeSigned = baos.toByteArray();

            // Compute the signature
            byte[] signature =
                signatureMechanism.generateSignature(toBeSigned,
                                                     signatureParameters);

            // Generate the hash for the signature header and content
            byte[] signatureHeader = new byte[SIGNATURE_HEADER_LENGTH];
            short signatureType =
                (short) signatureMechanism.getSignatureType().value();
            // TODO: use an int rather than a short since sig may be > 32K?
            //       Also, adjust SIGNATURE_HEADER_LENGTH (from 4 to 6, if int)
            short signatureLength = (short) signature.length;
            writeShort(signatureHeader, 0, signatureType);
            writeShort(signatureHeader, 2, signatureLength);
            md.update(signatureHeader);
            md.update(signature);
            byte[] hash = md.digest();

            // Write out the Signature Section
            file.seek(signatureSectionStart);
            new SectionHeader(ModuleFile.SectionType.SIGNATURE,
                              ModuleFile.Compressor.NONE,
                              signature.length + SIGNATURE_HEADER_LENGTH,
                              (short)0, hash).write(file);
            file.write(signatureHeader);
            file.write(signature);
        }

        private void writeShort(byte[] out, int offset, short value) {
            out[offset] = (byte) ((value >>> 8) & 0xFF);
            out[++offset] = (byte) ((value >>> 0) & 0xFF);
        }
    }

    public final static class Reader {

        private DataInputStream stream;
        private File destination;
        private ModuleFile.HashType hashtype;

        private static class CountingInputStream extends FilterInputStream {
            int count;
            public CountingInputStream(InputStream stream, int count) {
                super(stream);
                this.count = count;
            }

            public int available() throws IOException {
                return count;
            }

            public boolean markSupported() {
                return false;
            }

            public int read() throws IOException {
                if (count == 0)
                    return -1;
                int read = super.read();
                if (-1 != read)
                    count--;
                return read;
            }

            public int read(byte[] b, int off, int len) throws IOException {
                if (count == 0)
                    return -1;
                len = Math.min(len, count);
                int read = super.read(b, off, len);
                if (-1 != read)
                    count-=read;
                return read;
            }

            public void reset() throws IOException {
                throw new IOException("Can't reset this stream");
            }

            public long skip(long n) throws IOException {
                if (count == 0)
                    return -1;
                n = Math.min(n, count);
                long skipped = super.skip(n);
                if (n > 0)
                    count-=skipped;
                return skipped;
            }
        }

        public Reader(DataInputStream stream) {
            hashtype = ModuleFile.HashType.SHA256;
            // Ensure that mark/reset is supported
            if (stream.markSupported()) {
                this.stream = stream;
            } else {
                this.stream =
                    new DataInputStream(new BufferedInputStream(stream));
            }
        }

        private void checkHashMatch(byte[] expected, byte[] computed)
            throws IOException {
            if (!MessageDigest.isEqual(expected, computed))
                throw new IOException("Expected hash "
                                      + hashHexString(expected)
                                      + " instead of "
                                      + hashHexString(computed));
        }

        private ModuleFileHeader fileHeader = null;
        private MessageDigest fileDigest = null;
        private MessageDigest sectionDigest = null;
        private DataInputStream fileIn = null;
        private byte[] moduleInfoBytes = null;
        private Integer moduleSignatureType = null;
        private byte[] moduleSignatureBytes = null;
        private final int MAX_SECTION_HEADER_LENGTH = 128;
        private List<byte[]> calculatedHashes = new ArrayList<>();

        /*
         * Reads the MODULE_INFO section and the Signature section, if present,
         * but does not write any files.
         */
        public byte[] readStart() throws IOException {

            try {
                // System.out.println(fileHeader.toString());
                fileDigest = getHashInstance(hashtype);
                sectionDigest = getHashInstance(hashtype);
                DigestInputStream dis =
                    new DigestInputStream(stream, fileDigest);
                fileHeader = ModuleFileHeader.read(dis);
                // calculate module header hash
                sectionDigest.update(fileHeader.toByteArray());
                calculatedHashes.add(sectionDigest.digest());

                fileIn = new DataInputStream(dis);
                if (fileHeader.getSections() < 1)
                    throw new IOException("A module file must have"
                                          + " at least one section");
                if (readSection(fileIn) != ModuleFile.SectionType.MODULE_INFO)
                    throw new IOException("First module-file section"
                                          + " is not MODULE_INFO");
                assert moduleInfoBytes != null;

                // Read the Signature Section, if present
                readSignatureSection(fileIn, dis);

                return moduleInfoBytes.clone();
            } catch (IOException x) {
                close();
                throw x;
            }
        }

        public void readRest(File dst) throws IOException {

            destination = dst;
            try {
                Files.store(moduleInfoBytes, computeRealPath("info"));
                // Module-Info and Signature, if present, have been consumed
                int consumedSections = hasSignature() ? 2 : 1;
                for (int ns = fileHeader.getSections() - consumedSections;
                     ns > 0;
                     ns--)
                    readSection(fileIn);

                // There should be no bytes left over to read.
                if (-1 != fileIn.read())
                    throw new IOException( 1 + fileIn.available()
                                           + " byte(s) left over");
                close();
                byte[] fileHeaderHash = fileHeader.getHashNoClone();
                checkHashMatch(fileHeaderHash, fileDigest.digest());
                calculatedHashes.add(fileHeaderHash);
            } finally {
                close();
            }
        }

        public byte[] getHash() throws IOException {
            if (null == fileHeader)
                readStart();
            return fileHeader.getHash();
        }

        private List<byte[]> getCalculatedHashes() {
            return calculatedHashes;
        }

        public boolean hasSignature() throws IOException {
            if (null == fileHeader)
                readStart();
            return moduleSignatureBytes != null;
        }

        public Integer getSignatureType() throws IOException {
            if (null == fileHeader)
                readStart();
            return moduleSignatureType;
        }

        public byte[] getSignature() throws IOException {
            if (null == fileHeader)
                readStart();
            return moduleSignatureBytes != null
                ? moduleSignatureBytes.clone()
                : null;
        }

        private byte[] getSignatureNoClone() {
            return moduleSignatureBytes;
        }

        private JarOutputStream contentStream = null;

        private JarOutputStream contentStream() throws IOException {
            if (contentStream == null) {
                FileOutputStream fos
                    = new FileOutputStream(computeRealPath("classes"));
                contentStream
                    = new JarOutputStream(new BufferedOutputStream(fos));
            }
            return contentStream;
        }

        public void close() throws IOException {
            try {
                if (contentStream != null) {
                    contentStream.close();
                    contentStream = null;
                }
            } finally {
                if (fileIn != null) {
                    fileIn.close();
                    fileIn = null;
                }
            }
        }

        public void readModule(File dst) throws IOException {
            readStart();
            readRest(dst);
        }

        private void readSignatureSection(DataInputStream stream,
                                          DigestInputStream dis)
            throws IOException {

            // Turn off digest computation before reading Signature Section
            dis.on(false);

            // Mark the starting position
            stream.mark(MAX_SECTION_HEADER_LENGTH);
            SectionHeader header = SectionHeader.read(stream);
            if (header != null &&
                header.getType() == ModuleFile.SectionType.SIGNATURE) {
                readSectionContent(header, stream);
            } else {
                // Revert back to the starting position
                stream.reset();
            }

            // Turn on digest computation again
            dis.on(true);
        }

        private ModuleFile.SectionType readSection(DataInputStream stream)
            throws IOException {

            SectionHeader header = SectionHeader.read(stream);
            readSectionContent(header, stream);
            return header.getType();
        }

        private void readSectionContent(SectionHeader header,
                                        DataInputStream stream)
            throws IOException {

            // System.out.println(header.toString());
            ModuleFile.SectionType type = header.getType();
            ModuleFile.Compressor compressor = header.getCompressor();
            int csize = header.getCSize();
            short subsections =
                type.hasFiles() ? header.getSubsections() : 1;

            CountingInputStream cs = new CountingInputStream(stream, csize);
            sectionDigest.reset();
            DigestInputStream dis = new DigestInputStream(cs, sectionDigest);
            DataInputStream in = new DataInputStream(dis);

            for (int subsection = 0; subsection < subsections; subsection++)
                readFile(in, compressor, type, csize);

            byte[] headerHash = header.getHashNoClone();
            checkHashMatch(headerHash, sectionDigest.digest());
            if (header.getType() != ModuleFile.SectionType.SIGNATURE) {
                calculatedHashes.add(headerHash);
            }
        }

        public void readFile(DataInputStream in,
                            ModuleFile.Compressor compressor,
                            ModuleFile.SectionType type,
                            int csize)
            throws IOException {

            switch (compressor) {
            case NONE:
                if (type == ModuleFile.SectionType.MODULE_INFO) {
                    moduleInfoBytes = readModuleInfo(in, csize);

                } else if (type == ModuleFile.SectionType.SIGNATURE) {
                    // Examine the Signature header
                    moduleSignatureType = (int) in.readShort();
                    short length = in.readShort();
                    moduleSignatureBytes = readModuleSignature(in, csize - 4);
                    if (length != moduleSignatureBytes.length) {
                        throw new IOException("Invalid Signature length");
                    }
                } else {
                    readUncompressedFile(in, type, csize);
                }
                break;
            case GZIP:
                readGZIPCompressedFile(in, type);
                break;
            case PACK200_GZIP:
                readClasses(
                    new DataInputStream(new CountingInputStream(in, csize)));
                break;
            default:
                throw new IOException("Unsupported Compressor for files: " +
                                      compressor);
            }
        }

        public void readClasses(DataInputStream in) throws IOException {
            unpack200gzip(in);
        }

        private File currentPath = null;

        private OutputStream openOutputStream(ModuleFile.SectionType type,
                                              String path)
            throws IOException
        {
            currentPath = null;
            assert type != ModuleFile.SectionType.CLASSES;
            if (type == ModuleFile.SectionType.RESOURCES)
                return Files.newOutputStream(contentStream(), path);
            currentPath = computeRealPath(type, path);
            File parent = currentPath.getParentFile();
            if (!parent.exists())
                Files.mkdirs(parent, currentPath.getName());
            return new BufferedOutputStream(new FileOutputStream(currentPath));
        }

        public void readGZIPCompressedFile(DataInputStream in,
                                           ModuleFile.SectionType type)
            throws IOException {

            SubSectionFileHeader header = SubSectionFileHeader.read(in);
            int csize = header.getCSize();

            // Splice off the compressed file from input stream
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            copyStream(new CountingInputStream(in, csize), baos, csize);

            byte[] compressedfile = baos.toByteArray();
            ByteArrayInputStream bain
                = new ByteArrayInputStream(compressedfile);
            GZIPInputStream gin = new GZIPInputStream(bain);

            OutputStream out = openOutputStream(type, header.getPath());
            copyStream(gin, out);
            gin.close();
            out.close();

            markNativeCodeExecutable(type, currentPath);
        }

        public void readUncompressedFile(DataInputStream in,
                                         ModuleFile.SectionType type,
                                         int csize)
            throws IOException
        {
            assert type != ModuleFile.SectionType.MODULE_INFO;
            SubSectionFileHeader header = SubSectionFileHeader.read(in);
            csize = header.getCSize();
            OutputStream out = openOutputStream(type, header.getPath());
            CountingInputStream cin = new CountingInputStream(in, csize);
            byte[] buf = new byte[8192];
            int n;
            while ((n = cin.read(buf)) >= 0)
                out.write(buf, 0, n);
            out.close();
            markNativeCodeExecutable(type, currentPath);
         }

        public byte[] readModuleInfo(DataInputStream in, int csize)
            throws IOException
        {
            CountingInputStream cin = new CountingInputStream(in, csize);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = cin.read(buf)) >= 0)
                out.write(buf, 0, n);
            return out.toByteArray();
        }

        public byte[] readModuleSignature(DataInputStream in, int csize)
            throws IOException
        {
            return readModuleInfo(in, csize); // signature has the same format
        }

        private File computeRealPath(String storedpath) throws IOException {

            String convertedpath = storedpath.replace('/', File.separatorChar);
            File path = new File(convertedpath);

            // Absolute path names are not permitted.
            ensureNonAbsolute(path);
            path = resolveAndNormalize(destination, convertedpath);
            // Create the parent directories if necessary
            File parent = path.getParentFile();
            if (!parent.exists())
                Files.mkdirs(parent, path.getName());

            return path;
        }

        private File computeRealPath(ModuleFile.SectionType type,
                                     String storedpath)
            throws IOException
        {
            String dir = getSubdirOfSection(type);
            return computeRealPath(dir + File.separatorChar + storedpath);
        }

        private static void markNativeCodeExecutable(ModuleFile.SectionType type,
                                                     File file)
        {
            if (type == ModuleFile.SectionType.NATIVE_CMDS
                || (type == ModuleFile.SectionType.NATIVE_LIBS
                    && System.getProperty("os.name").startsWith("Windows")))
                {
                    file.setExecutable(true);
                }
        }

        private void unpack200gzip(DataInputStream in) throws IOException {
            GZIPInputStream gis = new GZIPInputStream(in) {
                    public void close() throws IOException {}
                };
            Pack200.Unpacker unpacker = Pack200.newUnpacker();
            unpacker.unpack(gis, contentStream());
        }

    }

    private static void checkCompressor(ModuleFile.SectionType type,
                                        ModuleFile.Compressor compressor)
        throws IllegalArgumentException {

        if ((ModuleFile.SectionType.MODULE_INFO == type &&
             ModuleFile.Compressor.NONE != compressor)
            || (ModuleFile.SectionType.CLASSES == type &&
                ModuleFile.Compressor.PACK200_GZIP != compressor))
            throw new IllegalArgumentException(type
                                               + " may not use compressor "
                                               + compressor);
    }

    private static void checkSubsectionCount(ModuleFile.SectionType type,
                                             short subsections)
        throws IllegalArgumentException {

        if (!type.hasFiles() && subsections != 0)
            throw new IllegalArgumentException(type
                                               + " subsection count not 0: "
                                               + subsections);
        else if (type.hasFiles() && subsections == 0)
            throw new IllegalArgumentException(type + " subsection count is 0");
    }

    private static void copyStream(InputStream in, DataOutput out)
        throws IOException {

        byte[] buffer = new byte[1024 * 8];
        for (int b_read = in.read(buffer);
             -1 != b_read;
             b_read = in.read(buffer))
            out.write(buffer, 0, b_read);
    }

    private static void copyStream(InputStream in, OutputStream out)
        throws IOException {
        copyStream(in, (DataOutput) new DataOutputStream(out));
    }

    private static void copyStream(InputStream in, DataOutput out,
                                   int count)
        throws IOException {

        byte[] buffer = new byte[1024 * 8];

        while(count > 0) {
            int b_read = in.read(buffer, 0, Math.min(count, buffer.length));
            if (-1 == b_read)
                return;
            out.write(buffer, 0, b_read);
            count-=b_read;
        }
    }

    private static void copyStream(InputStream in, OutputStream out,
                                   int count)
        throws IOException{
        copyStream(in, (DataOutput) new DataOutputStream(out), count);
    }

    private static boolean directoryIsNotEmpty(File dir)
        throws IOException {
        return dir.toPath().newDirectoryStream().iterator().hasNext();
    }

    private static void ensureNonAbsolute(File path) throws IOException {
        if (path.isAbsolute())
            throw new IOException("Abolute path instead of relative: " + path);
    }

    private static void ensureNonNegativity(long size, String parameter)
        throws IllegalArgumentException {

        if (size < 0)
            throw new IllegalArgumentException(parameter + "<0: " + size);
    }

    private static void ensureNonNull(Object reference, String parameter)
        throws IllegalArgumentException {

        if (null == reference)
            throw new IllegalArgumentException(parameter + " == null");
    }

    private static void ensureMatch(int found, int expected, String field)
        throws IOException {

        if (found != expected)
            throw new IOException(field + " expected : "
                + Integer.toHexString(expected) + " found: "
                + Integer.toHexString(found));
    }

    private static void ensureShortNativePath(File path, String name)
        throws IOException {
            // TODO: check for native code file in a stricter way
            if (path.canExecute()
                && name.indexOf('/') != -1)
                throw new IOException("Native code path too long: " + path);
    }

    private static void ensureValidFileSize(long size, File path)
        throws IOException {

        if (size < 0 || size > Integer.MAX_VALUE)
            throw new IOException("File " + path + " too large: " + size);
    }

    private static MessageDigest getHashInstance(ModuleFile.HashType hashtype)
        throws IOException {

        try {
            switch(hashtype) {
            case SHA256:
                return MessageDigest.getInstance("SHA-256");
            default:
                throw new IOException("Unknown hash type: " + hashtype);
            }
        }
        catch (NoSuchAlgorithmException ex) {
            throw (IOException) (new IOException(hashtype + " not found"))
                .initCause(ex);
        }
    }

    private static short getMUTF8Length(String name) {
        short size = 2;

        for (int i = name.length()-1; i >= 0; i--) {
            char ch = name.charAt(i);

            if ('\u0001' <= ch && ch <= '\u007F')
                size += 1;
            else if ('\u0000' == ch
                     || '\u0080' <= ch && ch <= '\u07FF')
                size += 2;
            else
                size += 3;
        }

        return size;
    }

    private static String hashHexString(byte[] hash) {
        StringBuilder hex = new StringBuilder("0x");
        for (int i = 0; i < hash.length; i++) {
            int val = (hash[i] & 0xFF);
            if (val <= 16)
                hex.append("0");
            hex.append(Integer.toHexString(val));
        }
        return hex.toString();
    }

    private static File resolveAndNormalize(File directory, String path)
        throws IOException {

        File realpath = new File(directory, path);
        if (directory != null &&
            ! realpath.toPath().startsWith(directory.toPath()))
            throw new IOException("Bogus relative path: " + path);

        return realpath;
    }

    private static void writeHash(DataOutput out, byte[] hash)
        throws IOException {

        out.writeShort(hash.length);
        out.write(hash);
    }

    private static short readHashLength(DataInputStream in) throws IOException {
        final short hashLength = in.readShort();
        ensureNonNegativity(hashLength, "hashLength");

        return hashLength;
    }

    private static byte[] readHashBytes(DataInputStream in, short hashLength)
        throws IOException {

        final byte[] hash = new byte[hashLength];
        in.readFully(hash);

        return hash;
    }

    private static byte[] readHash(DataInputStream in) throws IOException {
        return readHashBytes(in, readHashLength(in));
    }

    private static byte[] readFileHash(DigestInputStream dis)
        throws IOException {

        DataInputStream in = new DataInputStream(dis);

        final short hashLength = readHashLength(in);

        // Turn digest computation off before reading the file hash
        dis.on(false);
        byte[] hash = readHashBytes(in, hashLength);
        // Turn digest computation on again afterwards.
        dis.on(true);

        return hash;
    }

    public final static class ModuleFileHeader {
        // Fields are specified as unsigned. Treat signed values as bugs.
        int magic;                  // MAGIC
        Type type;                  // Type.MODULE_FILE
        short major;                // ModuleFile.MAJOR_VERSION
        short minor;                // ModuleFile.MINOR_VERSION
        long csize;                 // Size of entire file, compressed
        long usize;                 // Space required for uncompressed contents
                                    //   (upper bound; need not be exact)
        short sections;             // Number of following sections
        ModuleFile.HashType hashType;// One of ModuleFile.HashType
                                    //   (applies to all hashes in this file)
        byte[] hash;                // Hash of entire file (except this hash
                                    // and the Signature section, if present)

        public String toString() {
            return "MODULE{csize=" + csize
                + ", sections=" + sections
                + ", hash=" + hashHexString(hash) + "}";
        }

        public short getSections() {
            return sections;
        }

        public byte[] getHash() {
            return (byte[]) hash.clone();
        }

        private byte[] getHashNoClone() {
            return hash;
        }

        public ModuleFileHeader (long csize, long usize, short sections,
                                 ModuleFile.HashType hashType, byte[] hash)
            throws IllegalArgumentException {

            ensureNonNegativity(csize, "csize");
            ensureNonNegativity(usize, "usize");
            ensureNonNegativity(sections, "sections");

            magic = MAGIC;
            type = Type.MODULE_FILE;
            major = ModuleFile.MAJOR_VERSION;
            minor = ModuleFile.MINOR_VERSION;

            this.csize = csize;
            this.usize = usize;
            this.sections = sections;
            this.hashType = hashType;
            this.hash = (byte[]) hash.clone();
        }

        public void write(final DataOutput out) throws IOException {

            out.writeInt(magic);
            out.writeShort(type.value());
            out.writeShort(major);
            out.writeShort(minor);
            out.writeLong(csize);
            out.writeLong(usize);
            out.writeShort(sections);
            out.writeShort(hashType.value());
            writeHash(out, hash);
        }

        private static ModuleFile.HashType lookupHashType(short value)
            throws IllegalArgumentException {

            for (ModuleFile.HashType i :
                    ModuleFile.HashType.class.getEnumConstants())
                if (i.value() == value) return i;

            throw new IllegalArgumentException("No HashType exists with value "
                                               + value);
        }

        public static ModuleFileHeader read(final DigestInputStream dis)
            throws IOException {
            DataInputStream in = new DataInputStream(dis);

            final int magic = in.readInt();
            ensureMatch(magic, MAGIC, "MAGIC");

            final short type = in.readShort();
            ensureMatch(type, Type.MODULE_FILE.value(),
                        "Type.MODULE_FILE");

            final short major = in.readShort();
            ensureMatch(major, ModuleFile.MAJOR_VERSION,
                        "ModuleFile.MAJOR_VERSION");

            final short minor = in.readShort();
            ensureMatch(minor, ModuleFile.MINOR_VERSION,
                        "ModuleFile.MINOR_VERSION");

            final long csize = in.readLong();
            final long usize = in.readLong();
            final short sections = in.readShort();
            final short hashTypeValue = in.readShort();
            ModuleFile.HashType hashType = lookupHashType(hashTypeValue);
            final byte[] hash = readFileHash(dis);

            return new ModuleFileHeader(csize, usize, sections, hashType, hash);
        }

        private byte[] toByteArray() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            write(new DataOutputStream(baos));

            return baos.toByteArray();
        }
        }

        public final static class SectionHeader {
            // Fields are specified as unsigned. Treat signed values as bugs.
            private ModuleFile.SectionType type;
            private ModuleFile.Compressor compressor;
            private int csize;          // Size of section content, compressed
            private short subsections;  // Number of following subsections
            private byte[] hash;       // Hash of section content

            public SectionHeader(ModuleFile.SectionType type,
                                 ModuleFile.Compressor compressor,
                                 int csize, short subsections, byte[] hash) {
                ensureNonNull(type, "type");
                ensureNonNull(compressor, "compressor");
                ensureNonNegativity(csize, "csize");
                ensureNonNegativity(subsections, "subsections");
                ensureNonNull(hash, "hash");
                checkSubsectionCount(type, subsections);
                checkCompressor(type, compressor);


                this.type = type;
                this.compressor = compressor;
                this.csize = csize;
                this.subsections = subsections;
                this.hash = (byte[]) hash.clone();
            }

            public void write(DataOutput out) throws IOException {
                out.writeShort(type.value());
                out.writeShort(compressor.value());
                out.writeInt(csize);
                out.writeShort(subsections);
                writeHash(out, hash);
            }

            private static ModuleFile.SectionType lookupSectionType(short value)
                throws IllegalArgumentException {

                for (ModuleFile.SectionType i :
                         ModuleFile.SectionType.class.getEnumConstants())
                    if (i.value() == value) return i;

                throw new
                    IllegalArgumentException("No SectionType exists with value "
                                             + value);
            }

            private static ModuleFile.Compressor lookupCompressor(short value)
                throws IllegalArgumentException {

                for (ModuleFile.Compressor i :
                         ModuleFile.Compressor.class.getEnumConstants())
                    if (i.value() == value) return i;

                throw new
                    IllegalArgumentException("No Compressor exists with value "
                                             + value);
            }

            public static SectionHeader read(DataInputStream in)
                throws IOException {

                short tvalue = in.readShort();
                final ModuleFile.SectionType type = lookupSectionType(tvalue);
                short cvalue = in.readShort();
                final ModuleFile.Compressor compressor =
                    lookupCompressor(cvalue);
                final int csize = in.readInt();
                final short sections = in.readShort();
                final byte[] hash = readHash(in);

                return new SectionHeader(type, compressor, csize,
                                         sections, hash);
            }

            public ModuleFile.SectionType getType() {
                return type;
            }

            public ModuleFile.Compressor getCompressor() {
                return compressor;
            }

            public int getCSize() {
                return csize;
            }

            public short getSubsections() {
                return subsections;
            }

            public byte[] getHash() {
                return (byte[]) hash.clone();
            }

            private byte[] getHashNoClone() {
                return hash;
            }

            public String toString() {
                return "SectionHeader{type= " + getType()
                    + ", compressor=" + getCompressor()
                    + ", csize=" + getCSize()
                    + ", subsections=" + getSubsections()
                    + ", hash=" + hashHexString(hash) + "}";
            }
        }

        public final static class SubSectionFileHeader {
            private int csize;              // Size of file, compressed
            private String path;            // Path name, in Java-modified UTF-8

            public int getCSize() {
                return csize;
            }

            public String getPath() {
                return path;
            }

            public SubSectionFileHeader(int csize, String path) {
                ensureNonNegativity(csize, "csize");
                ensureNonNull(path, "path");

                this.csize = csize;
                this.path = path;
            }

            public void write(DataOutput out) throws IOException {
                out.writeShort(ModuleFile.SubSectionType.FILE.value());
                out.writeInt(csize);
                out.writeUTF(path);
            }

            public static SubSectionFileHeader read(DataInputStream in)
                throws IOException {

                final short type = in.readShort();
                ensureMatch(type, ModuleFile.SubSectionType.FILE.value(),
                            "ModuleFile.SubSectionType.FILE");
                final int csize = in.readInt();
                final String path = in.readUTF();

                return new SubSectionFileHeader(csize, path);
            }
        }

        public final static class PKCS7Signer implements ModuleFileSigner {

            public ModuleFile.SignatureType getSignatureType()
            {
                return ModuleFile.SignatureType.PKCS7;
            }

            public byte[] generateSignature(byte[] toBeSigned,
                                            ModuleFileSigner.Parameters
                                                parameters)
                throws SignatureException
            {
                // Compute the signature
    
                Signature signatureAlgorithm =
                    parameters.getSignatureAlgorithm();
                signatureAlgorithm.update(toBeSigned);
                byte[] signature = signatureAlgorithm.sign();
    
                // Create the PKCS #7 signed data message

                AlgorithmId keyAlgorithmId = null;
                AlgorithmId digestAlgorithmId = null;
                String signatureAlgorithmName =
                    signatureAlgorithm.getAlgorithm();

                try {
                    keyAlgorithmId =
                        AlgorithmId.get(AlgorithmId.getEncAlgFromSigAlg(
                            signatureAlgorithmName));
                    digestAlgorithmId =
                        AlgorithmId.get(AlgorithmId.getDigAlgFromSigAlg(
                            signatureAlgorithmName));

                } catch (NoSuchAlgorithmException nsae) {
                    throw new SignatureException(nsae);
                }

                AlgorithmId[] algorithms = {digestAlgorithmId};
                ContentInfo contentInfo = new ContentInfo(toBeSigned);
                Certificate[] signerCertificateChain =
                    parameters.getSignerCertificateChain();
                X509Certificate[] signerX509CertificateChain = null;
                X500Principal issuerName = null;
                BigInteger serialNumber = null;

                List<Certificate> certs = Arrays.asList(signerCertificateChain);
                signerX509CertificateChain =
                    certs.toArray(new X509Certificate[0]);
                issuerName =
                    signerX509CertificateChain[0].getIssuerX500Principal();
                serialNumber = signerX509CertificateChain[0].getSerialNumber();

                SignerInfo signerInfo = new SignerInfo(
                    X500Name.asX500Name(issuerName), serialNumber,
                    digestAlgorithmId, keyAlgorithmId, signature);
                SignerInfo[] signerInfos = {signerInfo};

                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                PKCS7 pkcs7 = new PKCS7(algorithms, contentInfo,
                                        signerX509CertificateChain,
                                        signerInfos);

                try {
                    pkcs7.encodeSignedData(outStream);

                } catch (IOException ioe) {
                    throw new SignatureException(ioe);
                }

                return outStream.toByteArray();
            }
        }

        public final static class PKCS7SignerParameters
            implements ModuleFileSigner.Parameters {

            private Signature signatureAlgorithm;
            private Certificate[] signerCertificateChain;

            public PKCS7SignerParameters(Signature signature,
                                         Certificate[] certificateChain)
            {
                signatureAlgorithm = signature;
                signerCertificateChain = certificateChain;
            }

            public Signature getSignatureAlgorithm()
            {
                return signatureAlgorithm;
            }

            public Certificate[] getSignerCertificateChain()
            {
                return signerCertificateChain;
            }
        }

        public final static class PKCS7Verifier implements ModuleFileVerifier {

            private final PKCS7 pkcs7;
            private final CertificateFactory cf;
            private final List<byte[]> calculatedHashes;
            private final List<byte[]> expectedHashes;

            public PKCS7Verifier(Reader reader) throws SignatureException
            {
                try {
                    pkcs7 = new PKCS7(reader.getSignatureNoClone());
                    expectedHashes =
                        parseSignedData(pkcs7.getContentInfo().getData());
                    cf = CertificateFactory.getInstance("X.509");
                } catch (final IOException|CertificateException e) {
                    throw new SignatureException(e);
                }
                this.calculatedHashes = reader.getCalculatedHashes();
            }

            public ModuleFile.SignatureType getSignatureType() {
                return ModuleFile.SignatureType.PKCS7;
            }

            public Set<CodeSigner> verifySignature(ModuleFileVerifier.Parameters
                                                   parameters)
                throws SignatureException
            {
                try {
                    PKCS7VerifierParameters params =
                        (PKCS7VerifierParameters)parameters;
                    Validator validator = params.getValidator();

                    Set<CodeSigner> signers = new HashSet<>();
                    X509Certificate[] arrayType = new X509Certificate[0];

                    // Verify signature. This will return null if the signature
                    // cannot be verified successfully.
                    SignerInfo[] signerInfos = pkcs7.verify();
                    if (signerInfos == null)
                        throw new SignatureException("Cannot verify module "
                                                     + "signature");

                    // Performs certificate path validation for each signer.
                    // A validation failure results in an exception.
                    for (SignerInfo signerInfo : signerInfos) {
                        List<X509Certificate> certChain =
                            signerInfo.getCertificateChain(pkcs7);

                        validator.validate(certChain.toArray(arrayType));
                        CertPath certPath = cf.generateCertPath(certChain);
                        signers.add(new CodeSigner(certPath,
                                                   getTimestamp(signerInfo)));
                    }
                    return signers;

                } catch (IOException ioe) {
                    throw new SignatureException(ioe);

                } catch (GeneralSecurityException gse) {
                    throw new SignatureException(gse);
                }
            }

            private List<byte[]> parseSignedData(byte[] signedData)
                throws IOException
            {
                List<byte[]> hashes = new ArrayList<>();
                DataInputStream dis = new DataInputStream(
                    new ByteArrayInputStream(signedData));
                do {
                    short hashLength = dis.readShort();
                    byte[] hash = new byte[hashLength];
                    if (dis.read(hash) != hashLength)
                        throw new IOException("invalid hash length in "
                                              + "signed data");
                    hashes.add(hash);
                } while (dis.available() > 0);

                if (dis.available() != 0)
                    throw new IOException("extra data at end of signed data");

                // must be at least 3 hashes (header, module info, & whole file)
                if (hashes.size() < 3)
                    throw new IOException("too few hashes in signed data");
                return hashes;
            }
    
            public void verifyHashes(ModuleFileVerifier.Parameters parameters)
                throws SignatureException
            {
                verifyHashesStart(parameters);
                verifyHashesRest(parameters);
            }

            public void verifyHashesStart(ModuleFileVerifier.Parameters params)
                throws SignatureException {
                if (calculatedHashes.size() < 2)
                    throw new SignatureException("Unexpected number of hashes");
                // check module header hash
                checkHashMatch(expectedHashes.get(0), calculatedHashes.get(0));
                // check module info hash
                checkHashMatch(expectedHashes.get(1), calculatedHashes.get(1));
            }

            public void verifyHashesRest(ModuleFileVerifier.Parameters params)
                throws SignatureException {
                if (calculatedHashes.size() != expectedHashes.size())
                    throw new SignatureException("Unexpected number of hashes");

                for (int i = 2; i < expectedHashes.size(); i++) {
                    checkHashMatch(expectedHashes.get(i),
                                   calculatedHashes.get(i));
                }
            }

            private void checkHashMatch(byte[] expected, byte[] computed)
                throws SignatureException {
                if (!MessageDigest.isEqual(expected, computed))
                    throw new SignatureException("Expected hash "
                                  + hashHexString(expected)
                                  + " instead of "
                                  + hashHexString(computed));
            }

            private Timestamp getTimestamp(SignerInfo signerInfo)
                throws IOException, GeneralSecurityException
            {
                Timestamp timestamp = null;

                // Extract the signer's unsigned attributes
                PKCS9Attributes unsignedAttrs =
                    signerInfo.getUnauthenticatedAttributes();
                if (unsignedAttrs != null) {
                    PKCS9Attribute timestampTokenAttr =
                        unsignedAttrs.getAttribute("signatureTimestampToken");
                    if (timestampTokenAttr != null) {
                        PKCS7 timestampToken =
                            new PKCS7((byte[])timestampTokenAttr.getValue());
                        // Extract the content (an encoded timestamp token info)
                        byte[] encodedTimestampTokenInfo =
                            timestampToken.getContentInfo().getData();
                        // Extract the signer (the Timestamping Authority)
                        // while verifying the content
                        SignerInfo[] tsaSigner =
                            timestampToken.verify(encodedTimestampTokenInfo);
                        // Expect only one signer
                        List<X509Certificate> tsaCertChain =
                            tsaSigner[0].getCertificateChain(timestampToken);
                        CertPath tsaCertPath =
                            cf.generateCertPath(tsaCertChain);
                        // Create a timestamp token info object
                        TimestampToken timestampTokenInfo =
                            new TimestampToken(encodedTimestampTokenInfo);
                        // Create a timestamp object
                        timestamp = new Timestamp(timestampTokenInfo.getDate(),
                                                  tsaCertPath);
                    }
                }
                return timestamp;
            }
        }

        public final static class PKCS7VerifierParameters
            implements ModuleFileVerifier.Parameters
        {
            private final Validator validator;

            public PKCS7VerifierParameters() throws IOException
            {
                validator = Validator.getInstance(Validator.TYPE_PKIX,
                                                  Validator.VAR_CODE_SIGNING,
                                                  loadCACertsStore());
            }

            public Collection<X509Certificate> getTrustedCerts()
            {
                return validator.getTrustedCertificates();
            }

            public Validator getValidator() 
            {
                return validator;
            }

            /*
             * Loads the default system-level trusted CA certs store at
             * '${java.home}/lib/security/cacerts' unless overridden by the
             * 'org.openjdk.system.security.cacerts' system property.
             * The cert store must be in JKS format.
             */
            private static KeyStore loadCACertsStore() throws IOException
            {
                KeyStore trustedCertStore = null;
                FileInputStream inStream = null;
                String cacerts =
                    System.getProperty("org.openjdk.system.security.cacerts",
                                       System.getProperty("java.home")
                                       + "/lib/security/cacerts");
                try {
                    trustedCertStore = KeyStore.getInstance("JKS");
                    inStream = AccessController.doPrivileged
                                  (new OpenFileInputStreamAction(cacerts));
                    trustedCertStore.load(inStream, null);
                } catch (PrivilegedActionException pae) {
                    throw (IOException)pae.getCause();
                } catch (GeneralSecurityException gse) {
                    throw new IOException(gse);
                } finally {
                    if (inStream != null)
                        inStream.close();
                }
                return trustedCertStore;
            }
        }
}
