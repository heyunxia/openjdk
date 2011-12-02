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
import java.security.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

import static org.openjdk.jigsaw.FileConstants.ModuleFile.*;

public final class ModuleFile {
    /**
     * Return the subdir of a section in an extracted module file.
     */
    public static String getSubdirOfSection(SectionType type) {
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

    public final static class Reader implements Closeable {

        private DataInputStream stream;
        private File destination;
        private boolean deflate;
        private HashType hashtype;

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
            hashtype = HashType.SHA256;
            // Ensure that mark/reset is supported
            if (stream.markSupported()) {
                this.stream = stream;
            } else {
                this.stream =
                    new DataInputStream(new BufferedInputStream(stream));
            }
        }

        private void checkHashMatch(byte[] expected, byte[] computed)
            throws IOException
        {
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
        private boolean extract = true;

        /*
         * Reads the MODULE_INFO section and the Signature section, if present,
         * but does not write any files.
         */
        public byte[] readStart() throws IOException {

            try {
                fileDigest = getHashInstance(hashtype);
                sectionDigest = getHashInstance(hashtype);
                DigestInputStream dis =
                    new DigestInputStream(stream, fileDigest);
                fileHeader = ModuleFileHeader.read(dis);
                // calculate module header hash
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                fileHeader.write(new DataOutputStream(baos));
                sectionDigest.update(baos.toByteArray());
                calculatedHashes.add(sectionDigest.digest());

                fileIn = new DataInputStream(dis);
                if (readSection(fileIn) != SectionType.MODULE_INFO)
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

        public void readRest() throws IOException {
            extract = false;
            readRest(null, false);
        }

        public void readRest(File dst, boolean deflate) throws IOException {
            this.destination = dst;
            this.deflate = deflate;
            try {
                if (extract)
                    Files.store(moduleInfoBytes, computeRealPath("info"));
                // Module-Info and Signature, if present, have been consumed

                // Read rest of file until all sections have been read
                stream.mark(1);
                while (-1 != stream.read()) {
                    stream.reset();
                    readSection(fileIn);
                    stream.mark(1);
                }

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

        public List<byte[]> getCalculatedHashes() {
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

        byte[] getSignatureNoClone() {
            return moduleSignatureBytes;
        }

        private JarOutputStream contentStream = null;

        private JarOutputStream contentStream() throws IOException {
            if (contentStream == null) {
                if (extract) {
                    FileOutputStream fos
                        = new FileOutputStream(computeRealPath("classes"));
                    contentStream
                        = new JarOutputStream(new BufferedOutputStream(fos));
                } else {
                    contentStream = new JarOutputStream(new NullOutputStream());
                }
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

        public void readModule() throws IOException {
            extract = false;
            readStart();
            readRest();
        }

        public void readModule(File dst) throws IOException {
            readStart();
            readRest(dst, false);
        }

        private void readSignatureSection(DataInputStream stream,
                                          DigestInputStream dis)
            throws IOException
        {

            // Turn off digest computation before reading Signature Section
            dis.on(false);

            // Mark the starting position
            stream.mark(MAX_SECTION_HEADER_LENGTH);
            if (stream.read() != -1) {
                stream.reset();
                SectionHeader header = SectionHeader.read(stream);
                if (header != null &&
                    header.getType() == SectionType.SIGNATURE) {
                    readSectionContent(header, stream);
                } else {
                    // Revert back to the starting position
                    stream.reset();
                }
            }

            // Turn on digest computation again
            dis.on(true);
        }

        private SectionType readSection(DataInputStream stream)
            throws IOException
        {
            SectionHeader header = SectionHeader.read(stream);
            readSectionContent(header, stream);
            return header.getType();
        }

        private void readSectionContent(SectionHeader header,
                                        DataInputStream stream)
            throws IOException
        {
            SectionType type = header.getType();
            Compressor compressor = header.getCompressor();
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
            if (header.getType() != SectionType.SIGNATURE) {
                calculatedHashes.add(headerHash);
            }
        }

        public void readFile(DataInputStream in,
                             Compressor compressor,
                             SectionType type,
                             int csize)
            throws IOException
        {
            switch (compressor) {
            case NONE:
                if (type == SectionType.MODULE_INFO) {
                    moduleInfoBytes = readModuleInfo(in, csize);

                } else if (type == SectionType.SIGNATURE) {
                    // Examine the Signature header
                    moduleSignatureType = (int)in.readShort();
                    int length = in.readInt();
                    moduleSignatureBytes = readModuleSignature(in, csize - 6);
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

        private OutputStream openOutputStream(SectionType type,
                                              String path)
            throws IOException
        {
            if (!extract)
                return new NullOutputStream();
            currentPath = null;
            assert type != SectionType.CLASSES;
            if (type == SectionType.RESOURCES)
                return Files.newOutputStream(contentStream(), path);
            currentPath = computeRealPath(type, path);
            File parent = currentPath.getParentFile();
            if (!parent.exists())
                Files.mkdirs(parent, currentPath.getName());
            return new BufferedOutputStream(new FileOutputStream(currentPath));
        }

        private static class NullOutputStream extends OutputStream {
            @Override
            public void write(int b) throws IOException {}
            @Override
            public void write(byte[] b) throws IOException {}
            @Override
            public void write(byte[] b, int off, int len) throws IOException {}
        }

        public void readGZIPCompressedFile(DataInputStream in,
                                           SectionType type)
            throws IOException
        {
            SubSectionFileHeader header = SubSectionFileHeader.read(in);
            int csize = header.getCSize();

            // Splice off the compressed file from input stream
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            copyStream(new CountingInputStream(in, csize), baos, csize);

            byte[] compressedfile = baos.toByteArray();
            ByteArrayInputStream bain
                = new ByteArrayInputStream(compressedfile);
            try (GZIPInputStream gin = new GZIPInputStream(bain);
                 OutputStream out = openOutputStream(type, header.getPath())) {
                copyStream(gin, out);
            }

            if (extract)
                markNativeCodeExecutable(type, currentPath);
        }

        public void readUncompressedFile(DataInputStream in,
                                         SectionType type,
                                         int csize)
            throws IOException
        {
            assert type != SectionType.MODULE_INFO;
            SubSectionFileHeader header = SubSectionFileHeader.read(in);
            csize = header.getCSize();
            try (OutputStream out = openOutputStream(type, header.getPath())) {
                CountingInputStream cin = new CountingInputStream(in, csize);
                byte[] buf = new byte[8192];
                int n;
                while ((n = cin.read(buf)) >= 0)
                    out.write(buf, 0, n);
            }
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

        private File computeRealPath(SectionType type,
                                     String storedpath)
            throws IOException
        {
            String dir = getSubdirOfSection(type);
            return computeRealPath(dir + File.separatorChar + storedpath);
        }

        private static void markNativeCodeExecutable(SectionType type,
                                                     File file)
        {
            if (type == SectionType.NATIVE_CMDS
                || (type == SectionType.NATIVE_LIBS
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
            if (deflate) {
                Map<String,String> p = unpacker.properties();
                p.put(Pack200.Unpacker.DEFLATE_HINT, Pack200.Unpacker.TRUE);
            }
            unpacker.unpack(gis, contentStream());
        }

    }

    private static void checkCompressor(SectionType type,
                                        Compressor compressor) {

        if ((SectionType.MODULE_INFO == type &&
             Compressor.NONE != compressor)
            || (SectionType.CLASSES == type &&
                Compressor.PACK200_GZIP != compressor))
            throw new IllegalArgumentException(type
                                               + " may not use compressor "
                                               + compressor);
    }

    private static void checkSubsectionCount(SectionType type,
                                             short subsections) {
        if (!type.hasFiles() && subsections != 0)
            throw new IllegalArgumentException(type
                                               + " subsection count not 0: "
                                               + subsections);
        else if (type.hasFiles() && subsections == 0)
            throw new IllegalArgumentException(type + " subsection count is 0");
    }

    private static void copyStream(InputStream in, DataOutput out)
        throws IOException
    {

        byte[] buffer = new byte[1024 * 8];
        for (int b_read = in.read(buffer);
             -1 != b_read;
             b_read = in.read(buffer))
            out.write(buffer, 0, b_read);
    }

    private static void copyStream(InputStream in, OutputStream out)
        throws IOException
    {
        copyStream(in, (DataOutput) new DataOutputStream(out));
    }

    private static void copyStream(InputStream in, DataOutput out,
                                   int count)
        throws IOException
    {
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
        throws IOException
    {
        copyStream(in, (DataOutput) new DataOutputStream(out), count);
    }

    private static void ensureNonAbsolute(File path) throws IOException {
        if (path.isAbsolute())
            throw new IOException("Abolute path instead of relative: " + path);
    }

    private static void ensureNonNegativity(long size, String parameter) {
        if (size < 0)
            throw new IllegalArgumentException(parameter + "<0: " + size);
    }

    private static void ensureNonNull(Object reference, String parameter) {
        if (null == reference)
            throw new IllegalArgumentException(parameter + " == null");
    }

    private static void ensureMatch(int found, int expected, String field)
        throws IOException
    {
        if (found != expected)
            throw new IOException(field + " expected : "
                + Integer.toHexString(expected) + " found: "
                + Integer.toHexString(found));
    }

    private static void ensureShortNativePath(File path, String name)
        throws IOException
    {
            // TODO: check for native code file in a stricter way
            if (path.canExecute()
                && name.indexOf('/') != -1)
                throw new IOException("Native code path too long: " + path);
    }

    private static void ensureValidFileSize(long size, File path)
        throws IOException
    {
        if (size < 0 || size > Integer.MAX_VALUE)
            throw new IOException("File " + path + " too large: " + size);
    }

    static MessageDigest getHashInstance(HashType hashtype)
        throws IOException
    {
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
        throws IOException
    {
        File realpath = new File(directory, path);
        if (directory != null &&
            ! realpath.toPath().startsWith(directory.toPath()))
            throw new IOException("Bogus relative path: " + path);

        return realpath;
    }

    private static short readHashLength(DataInputStream in) throws IOException {
        final short hashLength = in.readShort();
        ensureNonNegativity(hashLength, "hashLength");

        return hashLength;
    }

    private static byte[] readHashBytes(DataInputStream in, short hashLength)
        throws IOException
    {

        final byte[] hash = new byte[hashLength];
        in.readFully(hash);

        return hash;
    }

    private static byte[] readHash(DataInputStream in) throws IOException {
        return readHashBytes(in, readHashLength(in));
    }

    private static byte[] readFileHash(DigestInputStream dis)
        throws IOException
    {

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
        public static final int LENGTH_WITHOUT_HASH = 30;
        public static final int LENGTH =
            LENGTH_WITHOUT_HASH + HashType.SHA256.length();

        // Fields are specified as unsigned. Treat signed values as bugs.
        private final int magic;                // MAGIC
        private final FileConstants.Type type;  // Type.MODULE_FILE
        private final short major;              // ModuleFile.MAJOR_VERSION
        private final short minor;              // ModuleFile.MINOR_VERSION
        private final long csize;               // Size of rest of file, compressed
        private final long usize;               // Space required for uncompressed contents
                                                //   (upper private final ound; need not be exact)
        private final HashType hashType;        // One of ModuleFile.HashType
                                                //   (applies final o all hashes in this file)
        private final byte[] hash;              // Hash of entire file (except this hash
                                                // and the Signature section, if present)

        public byte[] getHash() {
            return hash.clone();
        }

        private byte[] getHashNoClone() {
            return hash;
        }

        public ModuleFileHeader(long csize, long usize,
                                HashType hashType, byte[] hash) {
            ensureNonNegativity(csize, "csize");
            ensureNonNegativity(usize, "usize");

            magic = FileConstants.MAGIC;
            type = FileConstants.Type.MODULE_FILE;
            major = MAJOR_VERSION;
            minor = MINOR_VERSION;

            this.csize = csize;
            this.usize = usize;
            this.hashType = hashType;
            this.hash = hash.clone();
        }

        public void write(final DataOutput out) throws IOException {
            out.writeInt(magic);
            out.writeShort(type.value());
            out.writeShort(major);
            out.writeShort(minor);
            out.writeLong(csize);
            out.writeLong(usize);
            out.writeShort(hashType.value());
            writeHash(out, hash);
        }

        private static HashType lookupHashType(short value) {
            for (HashType i : HashType.class.getEnumConstants()) {
                if (i.value() == value) return i;
            }

            throw new IllegalArgumentException("No HashType exists with value "
                    + value);
        }

        public static ModuleFileHeader read(final DigestInputStream dis)
                throws IOException
        {
            DataInputStream in = new DataInputStream(dis);

            final int magic = in.readInt();
            ensureMatch(magic, FileConstants.MAGIC,
                        "FileConstants.MAGIC");

            final short type = in.readShort();
            ensureMatch(type, FileConstants.Type.MODULE_FILE.value(),
                       "Type.MODULE_FILE");

            final short major = in.readShort();
            ensureMatch(major, MAJOR_VERSION,
                        "ModuleFile.MAJOR_VERSION");

            final short minor = in.readShort();
            ensureMatch(minor, MINOR_VERSION,
                        "ModuleFile.MINOR_VERSION");

            final long csize = in.readLong();
            final long usize = in.readLong();
            final short hashTypeValue = in.readShort();
            HashType hashType = lookupHashType(hashTypeValue);
            final byte[] hash = readFileHash(dis);

            return new ModuleFileHeader(csize, usize, hashType, hash);
        }

        public String toString() {
            return "MODULE{csize=" + csize +
                   ", hash=" + hashHexString(hash) + "}";
        }
    }

    public final static class SectionHeader {
        public static final int LENGTH_WITHOUT_HASH = 12;
        public static final int LENGTH =
            LENGTH_WITHOUT_HASH + HashType.SHA256.length();

        // Fields are specified as unsigned. Treat signed values as bugs.
        private final SectionType type;
        private final Compressor compressor;
        private final int csize;               // Size of section content, compressed
        private final short subsections;       // Number of following subsections
        private final byte[] hash;             // Hash of section content

        public SectionHeader(SectionType type,
                             Compressor compressor,
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
            this.hash = hash.clone();
        }

        public void write(DataOutput out) throws IOException {
            out.writeShort(type.value());
            out.writeShort(compressor.value());
            out.writeInt(csize);
            out.writeShort(subsections);
            writeHash(out, hash);
        }

        private static SectionType lookupSectionType(short value) {
            for (SectionType i : SectionType.class.getEnumConstants()) {
                if (i.value() == value) return i;
            }

            throw new
                IllegalArgumentException("No SectionType exists with value "
                                         + value);
        }

        private static Compressor lookupCompressor(short value) {
            for (Compressor i : Compressor.class.getEnumConstants()) {
                if (i.value() == value) return i;
            }

            throw new
                IllegalArgumentException("No Compressor exists with value "
                                         + value);
        }

        public static SectionHeader read(DataInputStream in) throws IOException {
            short tvalue = in.readShort();
            final SectionType type = lookupSectionType(tvalue);
            short cvalue = in.readShort();
            final Compressor compressor = lookupCompressor(cvalue);
            final int csize = in.readInt();
            final short sections = in.readShort();
            final byte[] hash = readHash(in);

            return new SectionHeader(type, compressor, csize,
                    sections, hash);
        }

        public SectionType getType() {
            return type;
        }

        public Compressor getCompressor() {
            return compressor;
        }

        public int getCSize() {
            return csize;
        }

        public short getSubsections() {
            return subsections;
        }

        public byte[] getHash() {
            return hash.clone();
        }

        private byte[] getHashNoClone() {
            return hash;
        }

        public String toString() {
            return "SectionHeader{type= " + type
                    + ", compressor=" + compressor
                    + ", csize=" + csize
                    + ", subsections=" + subsections
                    + ", hash=" + hashHexString(hash) + "}";
        }
    }

    public final static class SubSectionFileHeader {
        private final int csize;              // Size of file, compressed
        private final String path;            // Path name, in Java-modified UTF-8

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
            out.writeShort(SubSectionType.FILE.value());
            out.writeInt(csize);
            out.writeUTF(path);
        }

        public static SubSectionFileHeader read(DataInputStream in)
                throws IOException
        {
            final short type = in.readShort();
            ensureMatch(type, SubSectionType.FILE.value(),
                        "ModuleFile.SubSectionType.FILE");
            final int csize = in.readInt();
            final String path = in.readUTF();

            return new SubSectionFileHeader(csize, path);
        }
    }

    private static void writeHash(DataOutput out, byte[] hash)
            throws IOException
    {
        out.writeShort(hash.length);
        out.write(hash);
    }
}
