/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map.Entry;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import org.openjdk.jigsaw.FileConstants.ModuleFile.Compressor;
import org.openjdk.jigsaw.FileConstants.ModuleFile.HashType;
import org.openjdk.jigsaw.FileConstants.ModuleFile.SectionType;
import static org.openjdk.jigsaw.FileConstants.ModuleFile.SectionType.*;
import org.openjdk.jigsaw.ModuleFile.ModuleFileHeader;
import org.openjdk.jigsaw.ModuleFile.SectionHeader;
import org.openjdk.jigsaw.ModuleFile.SubSectionFileHeader;
import static org.openjdk.jigsaw.ModuleFileParser.Event.*;

/*package*/ class ModuleFileParserImpl
    implements ModuleFileParser
{
    private static class CountingInputStream extends FilterInputStream {
        private long count;
        public CountingInputStream(InputStream stream, long count) {
            super(stream);
            this.count = count;
        }

        public int available() throws IOException {
            return (int)Math.min(Integer.MAX_VALUE, count);
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
            len = (int)Math.min(len, count);
            int read = super.read(b, off, len);
            if (-1 != read)
                count-=read;
            return read;
        }

        public void reset() throws IOException {
            throw new IOException("Can't reset this stream");
        }

        public long skip(long n) throws IOException {
            // ## never skip, always read for digest, skip could just call read
            throw new IOException("skip should never be called");
        }

        public void close() throws IOException {
            // Do nothing, CountingInputStream is used to wrap (sub)section
            // content. We never want to close the underlying stream.
        }
    }

    private final DataInputStream stream;   // dataInput wrapped raw stream
    private final HashType hashtype = HashType.SHA256;
    private final ModuleFileHeader fileHeader;
    private final MessageDigest fileDigest;
    private final MessageDigest sectionDigest;
    private DataInputStream digestStream;   // fileDigest, wrapper input stream

    // parser state
    private Event curEvent;
    private SectionHeader curSectionHeader;
    private SubSectionFileHeader curSubSectionHeader;
    private InputStream curSectionIn;
    private InputStream curSubSectionIn;
    private int subSectionCount;
    private byte[] hash;
    private ModuleFileParserException parserException;

    /*package*/ ModuleFileParserImpl(InputStream in) {
        InputStream bin = new BufferedInputStream(in);
        try {
            fileDigest = getHashInstance(hashtype);
            sectionDigest = getHashInstance(hashtype);
            DigestInputStream dis = new DigestInputStream(bin, fileDigest);
            fileHeader = ModuleFileHeader.read(dis);
            // calculate module header hash
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            fileHeader.write(new DataOutputStream(baos));
            sectionDigest.update(baos.toByteArray());
            hash = sectionDigest.digest();

            // ## cannot depend on csize until the signer is fixed to
            // ## update csize in the header after adding the signature
            //InputStream cis = new CountingInputStream(bin, fileHeader.getCSize());
            InputStream cis = bin;

            stream = new DataInputStream(cis);
            digestStream = new DataInputStream(new DigestInputStream(cis, fileDigest));
            curEvent = START_FILE;
        } catch (IOException | ModuleFileParserException x) {
            throw parserException(x);
        }
    }

    @Override
    public ModuleFileHeader fileHeader() {
        return fileHeader;
    }

    @Override
    public Event event() {
        return curEvent;
    }

    @Override
    public boolean hasNext() {
        if (parserException != null)
            return false;

        return curEvent != END_FILE;
    }

    @Override
    public Event next() {
        if (!hasNext()) {
            if (parserException != null)
                throw new NoSuchElementException("END_FILE reached");
            else
                throw parserException(
                   "Error processing input. The input stream is not complete.");
        }

        // Reset general state
        hash = null;

        try {
            switch (curEvent) {
                case START_FILE:
                    // can only transition to START_SECTION, module-info
                    curSectionHeader = SectionHeader.read(digestStream);
                    SectionType type = curSectionHeader.getType();
                    if (type != MODULE_INFO)
                        throw parserException(type + ": expected MODULE_INFO");
                    sectionDigest.reset();
                    curSectionIn = new DigestInputStream(new CountingInputStream(digestStream,
                                               curSectionHeader.getCSize()), sectionDigest);
                    return curEvent = START_SECTION;
                case START_SECTION :
                    // can only transition to START_SUBSECTION or END_SECTION
                    if (subSectionCount != 0)
                        return curEvent = startSubSection();
                    // END_SECTION
                    skipAnyUnread(curSectionIn);
                    hash = sectionDigest.digest();
                    return curEvent = END_SECTION;
                case START_SUBSECTION :
                    // can only transition to END_SUBSECTION
                    skipAnyUnread(curSubSectionIn);
                    return curEvent = END_SUBSECTION;
                case END_SUBSECTION :
                    // can only transition to START_SUBSECTION or END_SECTION
                    if (subSectionCount != 0)
                        return curEvent = startSubSection();
                    checkAllRead(curSectionIn,
                                 "subsections do not consume all section data");
                    hash = sectionDigest.digest();
                    return curEvent = END_SECTION;
                case END_SECTION :
                    if (stream.available() == 0) {
                        hash = fileDigest.digest();
                        return curEvent = END_FILE;
                    }
                    // START_SECTION
                    return curEvent = startSection();
                case END_FILE :
                    throw parserException(
                            "should not reach here, next with current event END_FILE");
                default :
                    throw parserException("Unknown event: " + curEvent);
            }
        } catch (IOException | ModuleFileParserException x) {
            throw parserException(x);
        }
    }

    private Event startSection() throws IOException {
        curSectionHeader = SectionHeader.read(stream);
        if (curSectionHeader.getType() == MODULE_INFO)
            throw parserException("Unexpected MODULE_INFO");

        DataInputStream in = digestStream;
        if (curSectionHeader.getType() == SIGNATURE ) {
            // special handling for SIGNATURE section, skip file digest
            in = stream;
        } else {
            // update file digest with header
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            curSectionHeader.write(new DataOutputStream(baos));
            fileDigest.update(baos.toByteArray());
        }
        sectionDigest.reset();
        curSectionIn = new DigestInputStream(new CountingInputStream(in,
                                  curSectionHeader.getCSize()), sectionDigest);

        if (curSectionHeader.getType().hasFiles())
            subSectionCount = curSectionHeader.getSubsections();
        else
            subSectionCount = 0;
        curSubSectionIn = null;
        return START_SECTION;
    }

    private Event startSubSection() throws IOException {
        curSubSectionHeader = SubSectionFileHeader.read(new DataInputStream(curSectionIn));
        curSubSectionIn = new CountingInputStream(curSectionIn, curSubSectionHeader.getCSize());
        subSectionCount--;
        return START_SUBSECTION;
    }

    private ModuleFileParserException parserException(String message) {
        return parserException = new ModuleFileParserException(message);
    }

    private ModuleFileParserException parserException(Exception x) {
        if (x instanceof ModuleFileParserException)
            return parserException = (ModuleFileParserException) x;

        return parserException = new ModuleFileParserException(x);
    }

    private static void skipAnyUnread(InputStream is)
        throws IOException
    {
        byte[] ba = new byte[8192];
        while (is.read(ba) != -1) { /* gulp! */ }
    }

    private void checkAllRead(InputStream is, String message)
        throws IOException
    {
        if (is.read() != -1)
            throw parserException(message);
    }

    @Override
    public boolean skipToNextStartSection() {
        if (curEvent == END_FILE) return false;

        while (hasNext()) {
            Event e = next();
            if (e == START_SECTION)
                return true;
            if (e == END_FILE)
                return false;
        }
        return false;
    }

    @Override
    public boolean skipToNextStartSubSection() {
        if (!(curEvent == START_SECTION || curEvent == START_SUBSECTION ||
              curEvent == END_SUBSECTION))
            return false;

        if (!getSectionHeader().getType().hasFiles())
            throw parserException(getSectionHeader().getType() +
                                      " section does not contain subsections");

        while(hasNext()) {
            Event e = next();
            if (e == START_SUBSECTION) return true;
            if (e == END_SECTION) return false;
        }
        return false;
    }

    @Override
    public SectionHeader getSectionHeader() {
        if (curEvent == START_FILE || curEvent == END_FILE)
            throw parserException("No section header for: " + curEvent);
        return curSectionHeader;
    }

    @Override
    public SubSectionFileHeader getSubSectionFileHeader() {
        if (!(curEvent == START_SUBSECTION || curEvent == END_SUBSECTION))
            throw parserException("No subsection header for " + curEvent);
        return curSubSectionHeader;
    }

    @Override
    public byte[] getHash() {
        if (!(curEvent == START_FILE || curEvent == END_SECTION ||
              curEvent == END_FILE))
            throw parserException("Hash not calculatable at " + curEvent);

        return hash;
    }

    @Override
    public InputStream getContentStream() {
        if (!(curEvent == START_SECTION || curEvent == START_SUBSECTION))
            throw parserException("current event " + curEvent +
                         ", expected one of START_SECTION or START_SUBSECTION");

        InputStream is = curSubSectionIn != null ? curSubSectionIn : curSectionIn;

        SectionType type = curSectionHeader.getType();
        Compressor compressor = curSectionHeader.getCompressor();

        if (type == CLASSES) {
            throw parserException("should not be called for CLASSES");
        } else {
            try {
                Decompressor decompressor = Decompressor.newInstance(is, compressor);
                return decompressor.extractStream();
            } catch (IOException | ModuleFileParserException x) {
                throw parserException(x);
            }
        }
    }

    @Override
    public InputStream getRawStream() {
        if (!(curEvent == START_SECTION || curEvent == START_SUBSECTION))
            throw parserException("current event " + curEvent +
                       ", expected one of START_SECTION or START_SUBSECTION");

        return curSubSectionIn != null ? curSubSectionIn : curSectionIn;
    }

    @Override
    public Iterator<Entry<String,InputStream>> getClasses() {
        if (curEvent != START_SECTION)
            throw parserException("current event " + curEvent +
                                  ", expected  START_SECTION");

        SectionType type = curSectionHeader.getType();
        Compressor compressor = curSectionHeader.getCompressor();

        if (type != CLASSES)
            throw parserException(type + ": not classes section");
        if (curSectionIn == null)
            throw parserException("not at a valid classes section");

        try {
            ClassesDecompressor decompressor =
                ClassesDecompressor.newInstance(curSectionIn, compressor, /*deflate*/false);
            ClassesJarOutputStream cjos = new ClassesJarOutputStream();
            decompressor.extractTo(cjos);

            return cjos.classes().iterator();
        } catch (IOException | ModuleFileParserException x) {
            throw parserException(x);
        }
    }

    private static class ClassesEntry
        implements Entry<String,InputStream>, java.io.Serializable
    {
        private static final long serialVersionUID = -1094388804281091831L;
        private final String key;
        private InputStream value;

        ClassesEntry(String key, InputStream value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public InputStream getValue() {
            return value;
        }

        @Override
        public InputStream setValue(InputStream value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ClassesEntry))
                return false;
            ClassesEntry e = (ClassesEntry)o;
            if (key == null ? e.key != null : !key.equals(e.key))
                return false;
            if (value == null ? e.value != null : !value.equals(e.value))
                return false;
            return true;
        }

        @Override
        public int hashCode() {
            return (key == null ? 0 : key.hashCode()) ^
                   (value == null ? 0 : value.hashCode());
        }

        @Override
        public String toString() {
            return key + ":" + value;
        }
    }

    private static class ClassesJarOutputStream extends JarOutputStream {
        private Set<Entry<String,InputStream>> classes;
        private ByteArrayOutputStream classBytes;
        private String path;

        ClassesJarOutputStream()
            throws IOException
        {
            super(nullOutputStream);
            classes = new HashSet<>();
            classBytes = new ByteArrayOutputStream();
        }

        @Override
        public void putNextEntry(ZipEntry ze) throws IOException {
            classBytes.reset();
            path = ze.getName();
        }

        @Override
        public void closeEntry() throws IOException {
            classes.add(new ClassesEntry(path,
                     new ByteArrayInputStream(classBytes.toByteArray())));
        }

        @Override
        public void write(int b) throws IOException {
            classBytes.write(b);
        }

        @Override
        public void write(byte[] ba) throws IOException {
            classBytes.write(ba);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            classBytes.write(b, off, len);
        }

        Set<Entry<String,InputStream>> classes() {
            return classes;
        }
    }

    private static OutputStream nullOutputStream = new NullOutputStream();

    static class NullOutputStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {}
        @Override
        public void write(byte[] b) throws IOException {}
        @Override
        public void write(byte[] b, int off, int len) throws IOException {}
    }

    static class Decompressor {
        protected InputStream in;
        protected Decompressor() { }
        protected Decompressor(InputStream in) {
            // no decompression
            this.in = in;
        }

        InputStream extractStream() {
            return in;
        }

        static Decompressor newInstance(InputStream in,
                                        Compressor compressor)
            throws IOException
        {
            switch (compressor) {
                case NONE:
                    return new Decompressor(in);
                case GZIP:
                    return new GZIPDecompressor(in);
                default:
                    throw new ModuleFileParserException(
                            "Unsupported compressor type: " + compressor);
            }
        }
    }

    static class GZIPDecompressor extends Decompressor {
        GZIPDecompressor(InputStream in) throws IOException {
            this.in = new GZIPInputStream(in) {
                @Override
                public void close() throws IOException {}
            };
        }
    }

    static abstract class ClassesDecompressor {
        protected InputStream in;

        abstract void extractTo(JarOutputStream out) throws IOException;

        static ClassesDecompressor newInstance(InputStream in,
                                               Compressor compressor,
                                               boolean deflate)
            throws IOException
        {
            switch (compressor) {
                case PACK200_GZIP:
                    return new Pack200GZIPDecompressor(in, deflate);
                default:
                    throw new ModuleFileParserException(
                            "Unsupported compressor type: " + compressor);
            }
        }
    }

    static class Pack200GZIPDecompressor extends ClassesDecompressor {
        private Pack200.Unpacker unpacker;

        Pack200GZIPDecompressor(InputStream in, boolean deflate)
            throws IOException
        {
            this.in = new GZIPInputStream(in) {
                @Override
                public void close() throws IOException {}
            };
            unpacker = Pack200.newUnpacker();
            if (deflate) {
                Map<String,String> p = unpacker.properties();
                p.put(Pack200.Unpacker.DEFLATE_HINT, Pack200.Unpacker.TRUE);
            }
        }

        @Override
        void extractTo(JarOutputStream out) throws IOException {
            unpacker.unpack(in, out);
        }
    }

    static MessageDigest getHashInstance(HashType hashtype) {
        try {
            switch(hashtype) {
            case SHA256:
                return MessageDigest.getInstance("SHA-256");
            default:
                throw new ModuleFileParserException("Unknown hash type: " + hashtype);
            }
        } catch (NoSuchAlgorithmException x) {
            throw new ModuleFileParserException(hashtype + " not found", x);
        }
    }
}
