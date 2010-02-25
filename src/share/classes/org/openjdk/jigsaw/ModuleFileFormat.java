/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

import static org.openjdk.jigsaw.FileConstants.*;

public final class ModuleFileFormat {
    public final static class Writer {
        private DataOutputStream stream;
        private File sourcedir;
	private ModuleFile.HashType hashtype;
	private long usize;

	public Writer (DataOutputStream outstream, File sourcedir) {
	    hashtype = ModuleFile.HashType.SHA256;
	    this.sourcedir = sourcedir;
	    this.stream = outstream;
	}

	public void writeModule(File classes, 
				File resources,
				File nativelibs,
				File nativecmds,
				File config) 
	    throws IOException {

	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    MessageDigest md = getHashInstance(hashtype);
	    DigestOutputStream ds = new DigestOutputStream(baos, md);
	    DataOutputStream out = new DataOutputStream(ds);

	    File miclass = new File("module-info.class");
	    writeSection(out, ModuleFile.SectionType.MODULE_INFO, 
			 miclass, ModuleFile.Compressor.NONE);

	    short sections = 1;

	    if (classes != null) {
		writeSection(out, ModuleFile.SectionType.CLASSES, 
			     classes, ModuleFile.Compressor.PACK200_GZIP);
		sections++;
	    }
	    if (resources != null) {
		writeSection(out, ModuleFile.SectionType.RESOURCES, 
			     resources, ModuleFile.Compressor.GZIP);
		sections++;
	    }
	    if (nativelibs != null) {
		writeSection(out, ModuleFile.SectionType.NATIVE_LIBS, 
			     nativelibs, ModuleFile.Compressor.GZIP);
		sections++;
	    }
	    if (nativecmds != null) {
		writeSection(out, ModuleFile.SectionType.NATIVE_CMDS, 
			     nativecmds, ModuleFile.Compressor.GZIP);
		sections++;
	    }
	    if (config != null) {
		writeSection(out, ModuleFile.SectionType.CONFIG, 
			     config, ModuleFile.Compressor.GZIP);
		sections++;
	    }

	    byte [] hash = md.digest();
	    int csize = baos.size();

	    ModuleFileHeader header = 
		new ModuleFileHeader(csize, usize, sections, hashtype, hash);

	    header.write(stream);
	    baos.writeTo(stream);
	    stream.close();
	}
        
	public int writeSection(DataOutputStream stream,
				ModuleFile.SectionType type, 
				File dir, 
				ModuleFile.Compressor compressor) 
	    throws IOException {

	    checkFileName(type, dir);

	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    MessageDigest md = getHashInstance(hashtype);
	    DigestOutputStream ds = new DigestOutputStream(baos, md);
	    DataOutputStream out = new DataOutputStream(ds);

	    short count = writeFile(out, dir, compressor, type);

	    byte [] hash = md.digest();
	    int csize = baos.size();

	    // A section type that only allows a single file
	    // has a section count of 0.
	    if (count > Short.MAX_VALUE)
		throw new IOException("Too many files: " + count);

	    short subsections = type.hasFiles() ? count : 0;

	    SectionHeader header = 
		new SectionHeader(type, compressor, csize, subsections, hash);
	    header.write(stream);
	    baos.writeTo(stream);

	    // Dump the sections to disk
	    // $tail --bytes=+45 CLASSES | sha256sum 
	    // to check the hashes
	    //FileOutputStream fos = new FileOutputStream(new File(type.toString()));
	    //header.write(new DataOutputStream(fos));
	    //baos.writeTo(fos);
						    
	    return baos.size();
	}

        public short writeFile(DataOutputStream out, File path, 
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
		Queue<File> files = 
		    new LinkedList(Arrays.asList(path.listFiles()));
		// System.out.println("Gipping: " + path);
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
            

	public void writeClasses(DataOutputStream out, File dir) 
	    throws IOException {

	    copyStream(packAndGzip(jar(dir)), out);
	}

	public void writeGZIPCompressedFile(DataOutputStream out,
					    File path) throws IOException {

	    File realpath = computeRealPath(path);
	    String storedpath = computeStoredPath(realpath);
	    // System.out.println("Gzipping " + realpath);
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    GZIPOutputStream gos = new GZIPOutputStream(baos);
            InputStream is = new FileInputStream(realpath);
            BufferedInputStream in = new BufferedInputStream(is);
	    copyStream(in, gos);
            in.close();
	    gos.finish();
	    gos.close();

	    final int csize = baos.size();
	    SubSectionFileHeader header 
                = new SubSectionFileHeader(csize, storedpath);
            header.write(out);
	    baos.writeTo(out);
	}

        public void writeUncompressedFile(DataOutputStream out, File path,
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
            // Absolute path names are not permitted.
            ensureNonAbsolute(path);
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
            ensureShortNativePath(file, stored);
	    return stored;
	}

	private JarInputStream jar(File dir)
	    throws IOException {

	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    JarOutputStream jos = new JarOutputStream(baos);
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
			copyStream(new FileInputStream(file), jos);
			jos.closeEntry();
		    }
		    usize += file.length();
		}
	    }
	    jos.close();

	    byte [] jar = baos.toByteArray();
	    ByteArrayInputStream bain = new ByteArrayInputStream(jar);
	    return new JarInputStream(bain);
	}

	private InputStream packAndGzip(JarInputStream jar) 
	    throws IOException {

	    Pack200.Packer packer = Pack200.newPacker();
	    Map p = packer.properties();
	    p.put(Pack200.Packer.SEGMENT_LIMIT, "-1");
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

	public Reader(DataInputStream stream, File destination) {
	    hashtype = ModuleFile.HashType.SHA256;
	    this.stream = stream;
	    this.destination = destination;
	}

	

	private void checkHashMatch(byte [] expected, byte [] computed) 
	    throws IOException {
	    if (!Arrays.equals(expected, computed))
		throw new IOException("Expected hash " 
				      + hashHexString(expected)
				      + " instead of " 
				      + hashHexString(computed));
	}

	public void readModule() throws IOException {
	    ModuleFileHeader header = ModuleFileHeader.read(stream);
	    // System.out.println(header.toString());
	    MessageDigest md = getHashInstance(hashtype);
	    DigestInputStream dis = new DigestInputStream(stream, md);
	    DataInputStream in = new DataInputStream(dis);

	    for (short sections = header.getSections(); 
		 sections > 0;
		 sections --) 
		readSection(in);

	    checkHashMatch(header.getHash(), md.digest());
	}

	private void readSection(DataInputStream stream)
	    throws IOException {

	    SectionHeader header = SectionHeader.read(stream);
	    // System.out.println(header.toString());
	    ModuleFile.SectionType type = header.getType();
	    ModuleFile.Compressor compressor = header.getCompressor();
	    int csize = header.getCSize();
	    short subsections = 
		type.hasFiles() ? header.getSubsections() : 1;

	    CountingInputStream cs = new CountingInputStream(stream, csize);
	    MessageDigest md = getHashInstance(hashtype);
	    DigestInputStream dis = new DigestInputStream(cs, md);
	    DataInputStream in = new DataInputStream(dis);

	    for (int subsection = 0; subsection < subsections; subsection++)
		readFile(in, compressor, type, csize);

	    checkHashMatch(header.getHash(), md.digest());
	}

        public void readFile(DataInputStream in, 
			    ModuleFile.Compressor compressor,
			    ModuleFile.SectionType type,
			    int csize) 
            throws IOException {

            switch (compressor) {
	    case NONE:
		readUncompressedFile(in, type, csize);
		break;
	    case GZIP:
		readGZIPCompressedFile(in, type);
		break;
	    case PACK200_GZIP:
		readClasses(new DataInputStream(new CountingInputStream(in, csize)));
		break;
	    default:
		throw new IOException("Unsupported Compressor for files: " +
				      compressor);
            }
        }

	public void readClasses(DataInputStream in) throws IOException {
	    extractClasses(unpack200gzip(in));
	}

	public void readGZIPCompressedFile(DataInputStream in, 
					   ModuleFile.SectionType type) 
	    throws IOException {

	    SubSectionFileHeader header = SubSectionFileHeader.read(in);
	    int csize = header.getCSize();
	    String filename = 
		type.toString() + File.separator + header.getPath();
	    File path = computeRealPath(filename);

            // Splice off the compressed file from input stream
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    copyStream(new CountingInputStream(in, csize), baos, csize);
	    
	    byte [] compressedfile = baos.toByteArray();
	    ByteArrayInputStream bain = new ByteArrayInputStream(compressedfile);
	    GZIPInputStream gin = new GZIPInputStream(bain);

            OutputStream os = new FileOutputStream(path);
            BufferedOutputStream out = new BufferedOutputStream(os);
            copyStream(gin, out);
            out.close();
	    gin.close();
            
	    if (type == ModuleFile.SectionType.NATIVE_LIBS
		|| type == ModuleFile.SectionType.NATIVE_CMDS)
		path.setExecutable(true);
	}

        public void readUncompressedFile(DataInputStream in, 
					 ModuleFile.SectionType type,
					 int csize) throws IOException {
	    File realpath;

	    // module-info.class has no header
	    if (ModuleFile.SectionType.MODULE_INFO != type) {
		SubSectionFileHeader header = SubSectionFileHeader.read(in);
		csize = header.getCSize();
		realpath = computeRealPath(header.getPath());
	    }
	    else 
		realpath = computeRealPath("module-info.class");

            // Create the file 
	    File parent = realpath.getParentFile();
	    if (!parent.exists())
		Files.mkdirs(parent, realpath.getName());

            // Write the file
            OutputStream os = new FileOutputStream(realpath);
            BufferedOutputStream out = new BufferedOutputStream(os);
            copyStream(new CountingInputStream(in, csize), out, csize);
            out.close();

	    if (type == ModuleFile.SectionType.NATIVE_LIBS
		|| type == ModuleFile.SectionType.NATIVE_CMDS)
		realpath.setExecutable(true);
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

	private JarInputStream unpack200gzip(DataInputStream in) 
	    throws IOException {

	    GZIPInputStream gis = new GZIPInputStream(in) {
		    public void close() throws IOException {}
		};
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    JarOutputStream jos = new JarOutputStream(baos);
	    Pack200.Unpacker unpacker = Pack200.newUnpacker();
	    unpacker.unpack(gis, jos);
	    jos.close();

	    byte[] unpacked = baos.toByteArray();
	    ByteArrayInputStream bin = new ByteArrayInputStream(unpacked);
	    return new JarInputStream(bin);
	}

	private void extractClasses(JarInputStream jin)
	    throws IOException {

	    for (JarEntry entry = jin.getNextJarEntry();
		 entry != null;
		 entry = jin.getNextJarEntry()) {
		File path = computeRealPath(entry.getName());
		
		FileOutputStream file = new FileOutputStream(path);
		copyStream(jin, file);
		file.close();
	    }
	}
    }

    private static void checkCompressor(ModuleFile.SectionType type,
					ModuleFile.Compressor compressor)
	throws IllegalArgumentException {
	
	if ((ModuleFile.SectionType.MODULE_INFO == type && 
	     ModuleFile.Compressor.NONE != compressor)
	    || (ModuleFile.SectionType.CLASSES == type &&
		ModuleFile.Compressor.PACK200_GZIP != compressor))
	    throw new IllegalArgumentException(type + " may not use compressor "
					       + compressor);
    }

    private static void checkSubsectionCount(ModuleFile.SectionType type,
					     short subsections) 
	throws IllegalArgumentException {

	if (!type.hasFiles() && subsections != 0)
	    throw new IllegalArgumentException(type + " subsection count not 0: "
					       + subsections);
	else if (type.hasFiles() && subsections == 0) 
	    throw new IllegalArgumentException(type + " subsection count is 0");
    }

    private static void copyStream(InputStream in, OutputStream out) 
	throws IOException{

	byte [] buffer = new byte[1024 * 8];
	for (int b_read = in.read(buffer); 
	     -1 != b_read; 
	     b_read = in.read(buffer))
	    out.write(buffer, 0, b_read);
    }

    private static void copyStream(InputStream in, OutputStream out,
				   int count) 
	throws IOException{

	byte [] buffer = new byte[1024 * 8];

	while(count > 0) {
	    int b_read = in.read(buffer, 0, Math.min(count, buffer.length)); 
	    if (-1 == b_read) 
		return;
	    out.write(buffer, 0, b_read);
	    count-=b_read;
	}
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

    private static void ensureShortNativePath(File path, String name) throws IOException {
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

    private static String hashHexString(byte [] hash) {
	String hex = "0x";
	for (int i = 0; i < hash.length; i++) {
	    int val = (hash[i] & 0xFF); 
	    if (val <= 16)
		hex += "0";
	    hex += Integer.toHexString(val);
	}
	return hex;
    }

    private static File resolveAndNormalize(File directory, String path) 
        throws IOException {

        File realpath = new File(directory, path);
        if (directory != null && 
	    ! realpath.toPath().startsWith(directory.toPath()))
            throw new IOException("Bogus relative path: " + path);

        return realpath;
    }
    
    private static void writeHash(DataOutputStream out, byte [] hash) 
        throws IOException {

        out.writeShort(hash.length);
        out.write(hash);
    }
    
    private static byte [] readHash(DataInputStream in) throws IOException {
        final short hashLength = in.readShort();
        ensureNonNegativity(hashLength, "hashLength");
   
        final byte [] hash = new byte[hashLength];
        in.readFully(hash);

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
        byte[] hash;                // Hash of entire file (except this hash)
                  
	public String toString() {
	    return "MODULE{csize=" + csize 
		+ ", sections=" + sections 
		+ ", hash=" + hashHexString(hash) + "}";
	}

	public short getSections() {
	    return sections;
	}

	public byte [] getHash() {
	    return (byte []) hash.clone();
	}

        public ModuleFileHeader (long csize, long usize, short sections,
                                 ModuleFile.HashType hashType, byte [] hash) 
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
            this.hash = (byte []) hash.clone();
        }
        
        public void write(final DataOutputStream out) throws IOException {

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
        
        public static ModuleFileHeader read(final DataInputStream in) 
            throws IOException {

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
            final byte [] hash = readHash(in);

            return new ModuleFileHeader(csize, usize, sections, hashType, hash);
        }
	}
	
	public final static class SectionHeader {
	    // Fields are specified as unsigned. Treat signed values as bugs.
	    private ModuleFile.SectionType type;
	    private ModuleFile.Compressor compressor;
	    private int csize;          // Size of section content, compressed
	    private short subsections;  // Number of following subsections
	    private byte [] hash;       // Hash of section content
	    
	    public SectionHeader(ModuleFile.SectionType type, 
	                         ModuleFile.Compressor compressor,
	                         int csize, short subsections, byte [] hash) {
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
	        this.hash = (byte []) hash.clone();
	    }

	    public void write(DataOutputStream out) throws IOException {
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
	        final ModuleFile.Compressor compressor = lookupCompressor(cvalue);
	        final int csize = in.readInt();
	        final short sections = in.readShort();
	        final byte [] hash = readHash(in);

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

	    public byte [] getHash() {
		return (byte []) hash.clone();
	    }

	    public String toString() {
		return "SectionHeader{type= " + getType() 
		    + ", compressor=" + getCompressor() 
		    + ", csize=" + getCSize()
		    + ", subsections=" + getSubsections()
		    + ", hash=" + hashHexString(getHash()) + "}";
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

	    public void write(DataOutputStream out) throws IOException {
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
}
