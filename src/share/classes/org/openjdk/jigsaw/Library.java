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

import java.lang.module.*;
import java.io.*;
import java.util.*;


public final class Library {

    private static final class MetaData {

	private static String FILE
	    = FileConstants.META_PREFIX + "jigsaw-library";

	private static int MAJOR_VERSION = 0;
	private static int MINOR_VERSION = 1;

	private int majorVersion;
	private int minorVersion;

	private Map<String,String> env = new HashMap<String,String>();

	private MetaData(int maj, int min) {
	    majorVersion = maj;
	    minorVersion = min;
	}

	private MetaData() {
	    this(MAJOR_VERSION, MINOR_VERSION);
	}

	private Map<String,String> env() { return env; }

	private void store(File root) throws IOException {
	    File f = new File(root, FILE);
	    OutputStream fo = new FileOutputStream(f);
	    DataOutputStream out
		= new DataOutputStream(new BufferedOutputStream(fo));
	    try {
		out.writeInt(FileConstants.MAGIC);
		out.writeShort(FileConstants.Type.LIBRARY_HEADER.value());
		out.writeShort(MAJOR_VERSION);
		out.writeShort(MINOR_VERSION);
		out.writeInt(env.size());
		for (Map.Entry<String,String> me : env.entrySet()) {
		    out.writeUTF(me.getKey());
		    out.writeUTF(me.getValue());
		}
	    } finally {
		out.close();
	    }
	}

	private static MetaData load(File root) throws IOException {
	    File f = new File(root, FILE);
	    InputStream fi = new FileInputStream(f);
	    try {
		DataInputStream in
		    = new DataInputStream(new BufferedInputStream(fi));
		int m = in.readInt();
		if (m != FileConstants.MAGIC)
		    throw new IOException(f + ": Invalid magic number");
		int typ = in.readShort();
		if (typ != FileConstants.Type.LIBRARY_HEADER.value())
		    throw new IOException(f + ": Invalid file type");
		int maj = in.readShort();
		int min = in.readShort();
		if (   maj > MAJOR_VERSION
		    || (maj == MAJOR_VERSION && min > MINOR_VERSION)) {
		    throw new IOException(f + ": Futuristic version number");
		}
		int n = in.readInt();
		MetaData md = new MetaData(maj, min);
		for (int i = 0; i < n; i++) {
		    String k = in.readUTF();
		    String v = in.readUTF();
		    md.env().put(k, v);
		}
		return md;
	    } catch (EOFException x) {
		throw new IOException(f + ": Invalid library metadata",
				      x);
	    } finally {
		fi.close();
	    }
	}

    }

    private final File root;
    private final MetaData md;

    public File path() { return root; }
    public int majorVersion() { return md.majorVersion; }
    public int minorVersion() { return md.minorVersion; }

    @Override
    public String toString() {
	return (this.getClass().getName()
		+ "[" + root
		+ ", v" + md.majorVersion + "." + md.minorVersion + "]");
    }

    private Library(File path, boolean create)
	throws IOException
    {
	root = path;
	if (root.exists()) {
	    if (!root.isDirectory())
		throw new IOException(root + ": Exists but is not a directory");
	    md = MetaData.load(root);
	    return;
	}
	if (!create)
	    throw new FileNotFoundException(root.toString());
	if (!root.mkdir())
	    throw new IOException(root + ": Cannot create library directory");
	md = new MetaData();
	md.store(root);
    }

    public static Library open(java.io.File path, boolean create)
	throws IOException
    {
	return new Library(path, create);
    }

    // Library layout
    //
    //   $LIB/=jigsaw-library
    //        com.foo.bar/1.2.3/info (= module-info.class)
    //                          classes/com/foo/bar/...
    //                          lib/libbar.so
    //                          bin/bar

    public void install(java.io.File classes, String moduleName)
	throws IOException
    {

	String path = moduleName.replace('.', '/');
	File src = new File(classes, path);
	byte[] bs =  Files.load(new File(src, "module-info.class"));
	ModuleInfo mi = JigsawModuleSystem.instance().parseModuleInfo(bs);
	String m = mi.id().name();
	JigsawVersion v = (JigsawVersion)mi.id().version();
	String vs = (v == null) ? "default" : v.toString();
	File mdst = new File(new File(root, m), vs);
	Files.mkdirs(mdst, "module");
	Files.store(bs, new File(mdst, "info"));
	File cldst = new File(mdst, "classes");
	File dst = new File(cldst, path);
	Files.mkdirs(dst, "classes");

	// ## Very crude: This assumes that all packages in the module are in
	// ## proper subpackages of the module's principal package.  We really
	// ## need to scan the entire src directory to find all classes in the
	// ## specified module.  See com.sun.tools.classfile.
	Files.copyTree(src, dst, new Files.Filter<String>() {
	    public boolean accept(String s) {
		return !s.equals("module-info.class");
	    }});

    }

    public static interface ModuleVisitor {
	public void accept(ModuleInfo mi);
    }

    public void visitModules(ModuleVisitor mv)
	throws IOException
    {
	for (File mf : root.listFiles()) {
	    if (mf.getName().startsWith(FileConstants.META_PREFIX))
		continue;
	    for (File vf : mf.listFiles()) {
		byte[] bs = Files.load(new File(vf, "info"));
		ModuleInfo mi = JigsawModuleSystem.instance().parseModuleInfo(bs);
		mv.accept(mi);
	    }
	}
    }

}
