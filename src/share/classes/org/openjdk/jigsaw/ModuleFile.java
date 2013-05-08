/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.charset.Charset;
import java.security.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.jar.*;
import org.openjdk.jigsaw.ModuleFileParser.Event;
import static org.openjdk.jigsaw.FileConstants.ModuleFile.*;
import static org.openjdk.jigsaw.FileConstants.ModuleFile.SectionType.*;
import static org.openjdk.jigsaw.ModuleFileParser.Event.*;


/**
 * <p> A known <a
 * href="http://cr.openjdk.java.net/~mr/jigsaw/notes/module-file-format/">
 * module file</a> </p>
 */

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

    /**
     * Returns a ModuleFileParser instance.
     *
     * @param   stream
     *          module file stream
     *
     * @return  a module file parser
     *
     * @throws  ModuleFileParserException
     *          If there is an error processing the underlying module file
     */
    public static ModuleFileParser newParser(InputStream stream) {
        return new ModuleFileParserImpl(stream);
    }

    /**
     * Returns a ValidatingModuleFileParser instance.
     *
     * @param   stream
     *          module file stream
     *
     * @return  a validating module file parser
     *
     * @throws  ModuleFileParserException
     *          If there is an error processing the underlying module file
     */
    public static ValidatingModuleFileParser newValidatingParser(InputStream stream) {
        return new ValidatingModuleFileParserImpl(stream);
    }

    /**
     * <p> A module-file reader </p>
     */
    public final static class Reader implements Closeable {
        private final ValidatingModuleFileParser parser;
        private final ModuleFileHeader fileHeader;
        private final byte[] moduleInfoBytes;
        private final SignatureType moduleSignatureType;
        private final byte[] moduleSignatureBytes ;

        private File destination;
        private boolean deflate;
        private File natlibs;
        private File natcmds;
        private File configs;

        public Reader(InputStream stream) throws IOException {
            parser = ModuleFile.newValidatingParser(stream);
            fileHeader = parser.fileHeader();
            // Read the MODULE_INFO and the Signature section (if present),
            // but does not write any files.
            parser.next();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            copyStream(parser.getRawStream(), baos);
            moduleInfoBytes = baos.toByteArray();
            assert moduleInfoBytes != null;

            if (parser.next() != END_SECTION)
                throw new ModuleFileParserException(
                        "Expected END_SECTION of module-info");

            if (parser.hasNext()) {
                Event event = parser.next();
                if (event != END_FILE) {  // more sections
                    SectionHeader header = parser.getSectionHeader();
                    if (header.type == SIGNATURE) {
                        SignatureSection sh = SignatureSection.read(
                                new DataInputStream(parser.getRawStream()));
                        moduleSignatureType = SignatureType.valueOf(sh.getSignatureType());
                        moduleSignatureBytes = sh.getSignature();
                        if (parser.next() != END_SECTION)
                            throw new ModuleFileParserException(
                                    "Expected END_SECTION of signature");
                        if (parser.hasNext())
                            parser.next();  // position parser at next event
                        return;
                    }
                }
            }
            // no signature section, or possibly other sections at all.
            moduleSignatureBytes = null;
            moduleSignatureType = null;
        }

        public void extractTo(File dst) throws IOException {
            extractTo(dst, false);
        }

        public void extractTo(File dst, boolean deflate) throws IOException {
            extractTo(dst, deflate, null, null, null);
        }

        public void extractTo(File dst, boolean deflate, File natlibs,
                              File natcmds, File configs)
            throws IOException
        {
            this.deflate = deflate;
            this.destination = dst != null ? dst.getCanonicalFile() : null;
            this.natlibs = natlibs != null ? natlibs : new File(destination, "lib");
            this.natcmds = natcmds != null ? natcmds : new File(destination, "bin");
            this.configs = configs != null ? configs : new File(destination, "etc");

            try {
                Files.store(moduleInfoBytes, computeRealPath("info"));

                Event event = parser.event();
                if (event == END_FILE)
                    return;

                if (event != START_SECTION)
                    throw new ModuleFileParserException(
                                        "Expected START_SECTION, got : " + event);
                // Module-Info and Signature, if present, have been consumed
                do {
                    SectionHeader header = parser.getSectionHeader();
                    SectionType type = header.getType();
                    if (type.hasFiles()) {
                        while(parser.skipToNextStartSubSection()) {
                            readSubSection(type);
                        }
                    } else if (type == CLASSES) {
                        Iterator<Map.Entry<String,InputStream>> classes =
                                parser.getClasses();
                        while (classes.hasNext()) {
                            Map.Entry<String,InputStream> entry = classes.next();
                            try (OutputStream out = openOutputStream(type, entry.getKey())) {
                                copyStream(entry.getValue(), out);
                            }
                        }
                        // END_SECTION
                        parser.next();
                    } else {
                        throw new IllegalArgumentException("Unknown type: " + type);
                    }
                } while (parser.skipToNextStartSection());

                if (parser.event() != END_FILE)
                    throw new IOException("Expected END_FILE");
            } finally {
                close();
            }
        }

        public byte[] getModuleInfoBytes() {
            return moduleInfoBytes.clone();
        }

        public byte[] getHash() {
            return fileHeader.getHash();
        }

        public List<byte[]> getCalculatedHashes() {
            List<byte[]> hashes = new ArrayList<>();
            hashes.add(parser.getHeaderHash());
            for (Entry<SectionType,byte[]> entry : parser.getHashes().entrySet()) {
                if (entry.getKey() != SIGNATURE)
                    hashes.add(entry.getValue());
            }
            hashes.add(getHash());

            return hashes;
        }

        public boolean hasSignature() {
            return moduleSignatureBytes != null;
        }

        public SignatureType getSignatureType() {
            return moduleSignatureType;
        }

        public byte[] getSignature() {
            return moduleSignatureBytes == null ? null :
                   moduleSignatureBytes.clone();
        }

        /*package*/ byte[] getSignatureNoClone() {
            return moduleSignatureBytes;
        }

        public void close() throws IOException {
            try {
                if (contentStream != null) {
                    contentStream.close();
                    contentStream = null;
                }
            } finally {
                if (filesWriter != null) {
                    filesWriter.close();
                    filesWriter = null;
                }
            }
        }

        // subsections/files (resources, libs, cmds, configs)
        public void readSubSection(SectionType type) throws IOException {
            assert type == RESOURCES || type == NATIVE_LIBS ||
                   type == NATIVE_CMDS || type == CONFIG;

            SubSectionFileHeader subHeader = parser.getSubSectionFileHeader();
            String path = subHeader.getPath();
            try (OutputStream sink = openOutputStream(type, path)) {
                copyStream(parser.getContentStream(), sink);
            }

            // post processing for executable and files outside the module dir
            postExtract(type, currentPath);
        }

        private JarOutputStream contentStream = null;

        private JarOutputStream contentStream() throws IOException {
            if (contentStream != null)
                return contentStream;

            return contentStream = new JarOutputStream(
                    new BufferedOutputStream(
                        new FileOutputStream(computeRealPath("classes"))));
        }

        private File currentPath = null;

        private OutputStream openOutputStream(SectionType type, String path)
            throws IOException
        {
            currentPath = null;
            if (type == CLASSES || type == RESOURCES)
                return Files.newOutputStream(contentStream(), deflate, path);
            currentPath = computeRealPath(type, path);
            File parent = currentPath.getParentFile();
            if (!parent.exists())
                Files.mkdirs(parent, currentPath.getName());
            return new BufferedOutputStream(new FileOutputStream(currentPath));
        }

        // Track files installed outside the module library. For later removal.
        // files are relative to the modules directory.
        private PrintWriter filesWriter;

        private void trackFiles(File file)
            throws IOException
        {
            if (file == null || file.toPath().startsWith(destination.toPath()))
                return;

            // Lazy construction, not all modules will need this.
            if (filesWriter == null)
                filesWriter = new PrintWriter(computeRealPath("files"), "UTF-8");

            filesWriter.println(Files.convertSeparator(relativize(destination, file)));
            filesWriter.flush();
        }

        List<IOException> remove() {
            return ModuleFile.Reader.remove(destination);
        }

        // Removes a module, given its module install directory
        static List<IOException> remove(File moduleDir) {
            List<IOException> excs = new ArrayList<>();
            // Firstly remove any files installed outside of the module dir
            File files = new File(moduleDir, "files");
            if (files.exists()) {
                try {
                    List<String> filenames =
                        java.nio.file.Files.readAllLines(files.toPath(),
                                                         Charset.forName("UTF-8"));
                    for (String fn : filenames) {
                        try {
                            Files.delete(new File(moduleDir,
                                                  Files.platformSeparator(fn)));
                        } catch (IOException x) {
                            excs.add(x);
                        }
                    }
                } catch (IOException x) {
                    excs.add(x);
                }
            }

            excs.addAll(Files.deleteTreeUnchecked(moduleDir.toPath()));
            return excs;
        }

        // Returns the absolute path of the given section type.
        private File getDirOfSection(SectionType type) {
            if (type == NATIVE_LIBS)
                return natlibs;
            else if (type == NATIVE_CMDS)
                return natcmds;
            else if (type == CONFIG)
                return configs;

            // resolve sub dir section paths against the modules directory
            return new File(destination, ModuleFile.getSubdirOfSection(type));
        }

        private File computeRealPath(String path) throws IOException {
            return resolveAndNormalize(destination, path);
        }

        private File computeRealPath(SectionType type, String storedpath)
            throws IOException
        {
            File sectionPath = getDirOfSection(type);
            File realpath = new File(sectionPath,
                 Files.ensureNonAbsolute(Files.platformSeparator(storedpath)));

            validatePath(sectionPath, realpath);

            // Create the parent directories if necessary
            File parent = realpath.getParentFile();
            if (!parent.exists())
                Files.mkdirs(parent, realpath.getName());

            return realpath;
        }

        private static void markNativeCodeExecutable(SectionType type,
                                                     File file)
        {
            if (type == NATIVE_CMDS || (type == NATIVE_LIBS &&
                    System.getProperty("os.name").startsWith("Windows")))
                file.setExecutable(true);
        }

        private void postExtract(SectionType type, File path)
            throws IOException
        {
            markNativeCodeExecutable(type, path);
            trackFiles(path);
        }
    }

    private static void checkCompressor(SectionType type,
                                        Compressor compressor) {

        if ((MODULE_INFO == type && Compressor.NONE != compressor) ||
            (CLASSES == type && Compressor.PACK200_GZIP != compressor))
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

    private static void copyStream(InputStream in, OutputStream out)
        throws IOException
    {
        byte[] buf = new byte[8192];
        int read;
        while ((read = in.read(buf)) > 0)
            out.write(buf, 0, read);
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
            throw new IOException(hashtype + " not found", ex);
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


    private static String relativize(File directory, File path) throws IOException {
        return (directory.toPath().relativize(path.toPath().toRealPath())).toString();
    }

    private static void validatePath(File parent, File child)
        throws IOException
    {
        if (!child.toPath().startsWith(parent.toPath()) )
            throw new IOException("Bogus relative path: " + child);
        if (child.exists()) {
            // conflict, for now just fail
            throw new IOException("File " + child + " already exists");
        }
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

    /**
     * <p> A module-file header </p>
     */
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

        public HashType getHashType() {
            return hashType;
        }

        public byte[] getHash() {
            return hash.clone();
        }

        private byte[] getHashNoClone() {
            return hash;
        }

        public long getCSize() {
            return csize;
        }

        public long getUSize() {
            return usize;
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
            HashType hashType = null;
            try {
                hashType = HashType.valueOf(hashTypeValue);
            } catch (IllegalArgumentException x) {
                throw new IOException("Invalid hash type: " + hashTypeValue);
            }
            final byte[] hash = readFileHash(dis);

            return new ModuleFileHeader(csize, usize, hashType, hash);
        }

        @Override
        public String toString() {
            return "MODULE{csize=" + csize +
                   ", hash=" + hashHexString(hash) + "}";
        }
    }

    /**
     * <p> A module-file section header </p>
     */
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

        @Override
        public String toString() {
            return "SectionHeader{type= " + type
                    + ", compressor=" + compressor
                    + ", csize=" + csize
                    + ", subsections=" + subsections
                    + ", hash=" + hashHexString(hash) + "}";
        }
    }

    /**
     * <p> A module-file sub-section header </p>
     */
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

    /**
     * <p> A module-file signature section </p>
     */
    public final static class SignatureSection {
        public static final int HEADER_LENGTH = 6; // type + signature length

        private final int signatureType;   // One of FileConstants.ModuleFile.HashType
        private final int signatureLength; // Length of signature
        private final byte[] signature;    // Signature bytes

        public int getSignatureType() {
            return signatureType;
        }

        public int getSignatureLength() {
            return signatureLength;
        }

        public byte[] getSignature() {
            return signature;
        }

        public SignatureSection(int signatureType, int signatureLength,
                                byte[] signature) {
            ensureNonNegativity(signatureLength, "signatureLength");

            this.signatureType = signatureType;
            this.signatureLength = signatureLength;
            this.signature = signature.clone();
        }

        public void write(DataOutput out) throws IOException {
            out.writeShort(signatureType);
            out.writeInt(signatureLength);
            out.write(signature);
        }

        public static SignatureSection read(DataInputStream in)
            throws IOException
        {
            short signatureType = in.readShort();
            try {
                SignatureType.valueOf(signatureType);
            } catch (IllegalArgumentException x) {
                throw new IOException("Invalid signature type: " + signatureType);
            }
            final int signatureLength = in.readInt();
            ensureNonNegativity(signatureLength, "signatureLength");
            final byte[] signature = new byte[signatureLength];
            in.readFully(signature);
            return new SignatureSection(signatureType, signatureLength,
                                        signature);
        }
    }

    private static void writeHash(DataOutput out, byte[] hash)
            throws IOException
    {
        out.writeShort(hash.length);
        out.write(hash);
    }

}
