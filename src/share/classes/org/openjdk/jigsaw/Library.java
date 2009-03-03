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
    private final File canonicalRoot;
    private final MetaData md;

    public File path() { return root; }
    public int majorVersion() { return md.majorVersion; }
    public int minorVersion() { return md.minorVersion; }

    /**
     * Return a string describing this module library.
     */
    @Override
    public String toString() {
	return (this.getClass().getName()
		+ "[" + canonicalRoot
		+ ", v" + md.majorVersion + "." + md.minorVersion + "]");
    }

    private Library(File path, boolean create)
	throws IOException
    {
	root = path;
	canonicalRoot = root.getCanonicalFile();
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

    private static final JigsawModuleSystem jms
	= JigsawModuleSystem.instance();

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
	ModuleInfo mi = jms.parseModuleInfo(bs);
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
	File[] mnds = root.listFiles();
	Arrays.sort(mnds);
	for (File mnd : mnds) {
	    if (mnd.getName().startsWith(FileConstants.META_PREFIX))
		continue;
	    File[] mds = mnd.listFiles();
	    Arrays.sort(mds);
	    for (File md : mds) {
		byte[] bs = Files.load(new File(md, "info"));
		ModuleInfo mi = jms.parseModuleInfo(bs);
		mv.accept(mi);
	    }
	}
    }

    /**
     * Find all modules with the given name in this library.
     *
     * @param   moduleName
     *          The name of the modules being sought
     *
     * @return  An unordered set containing the module identifiers of the found
     *          modules; if no modules were found then the set will be empty
     */
    public Set<ModuleId> findModuleIds(String moduleName)
	throws IOException
    {
	ModuleSystem.checkModuleName(moduleName);
	File mnd = new File(root, moduleName);
	if (!mnd.exists())
	    return Collections.emptySet();
	if (!mnd.isDirectory())
	    throw new IOException(mnd + ": Not a directory");
	if (!mnd.canRead())
	    throw new IOException(md + ": Not readable");
	Set<ModuleId> ans = new HashSet<ModuleId>();
	for (String v : mnd.list()) {
	    // ## Need a MS.parseModuleId(String, Version) method
	    ans.add(jms.parseModuleId(mnd.getName() + "@" + v));
	}
	return ans;
    }

    /**
     * Find all modules matching the given query in this library.
     *
     * @param   moduleIdQuery
     *          The query to match against
     *
     * @return  An unordered set containing the module identifiers of the found
     *          modules; if no modules were found then the set will be empty
     */
    public Set<ModuleId> findModuleIds(ModuleIdQuery midq)
	throws IOException
    {
	Set<ModuleId> ans = findModuleIds(midq.name());
	if (ans.isEmpty() || midq.versionQuery() == null)
	    return ans;
	for (Iterator<ModuleId> i = ans.iterator(); i.hasNext();) {
	    ModuleId mid = i.next();
	    if (!midq.matches(mid))
		i.remove();
	}
	return ans;
    }

    private void checkModuleId(ModuleId mid) {
	Version v = mid.version();
	if (v == null)
	    return;
	if (!(v instanceof JigsawVersion))
	    throw new IllegalArgumentException(mid + ": Not a Jigsaw module id");
    }

    private File moduleDir(ModuleId mid) {
	Version v = mid.version();
	String vs = (v != null) ? v.toString() : "default";
	return new File(new File(root, mid.name()), vs);
    }

    private void checkModuleDir(File md)
	throws IOException
    {
	if (!md.isDirectory())
	    throw new IOException(md + ": Not a directory");
	if (!md.canRead())
	    throw new IOException(md + ": Not readable");
    }

    private File findModuleDir(ModuleId mid)
	throws IOException
    {
	File md = moduleDir(mid);
	if (!md.exists())
	    return null;
	checkModuleDir(md);
	return md;
    }

    /**
     * Find the {@link java.lang.module.ModuleInfo ModuleInfo} object for the
     * module with the given identifier.
     *
     * @param   mid
     *          The identifier of the module being sought
     *
     * @return  The requested {@link java.lang.module.ModuleInfo ModuleInfo},
     *          or {@code null} if no such module is present in this library
     *
     * @throws  IllegalArgumentException
     *          If the given module identifier is not a Jigsaw module
     *          identifier
     */
    public ModuleInfo findModuleInfo(ModuleId mid)
	throws IOException
    {
	checkModuleId(mid);
	File md = findModuleDir(mid);
	if (md == null)
	    return null;
	return jms.parseModuleInfo(Files.load(new File(md, "info")));
    }

    /**
     * Read the class bytes of the given class within the given module.
     *
     * @param   mid
     *          The module's identifier
     *
     * @param   className
     *          The binary name of the requested class
     *
     * @return  The requested bytes, or {@code null} if the named module does
     *          not define such a class
     *
     * @throws  IllegalArgumentException
     *          If the given module identifier is not a Jigsaw module
     *          identifier
     *
     * @throws  IllegalArgumentException
     *          If the named module does not exist in this library
     */
    public byte[] findClass(ModuleId mid, String className)
	throws IOException
    {
	checkModuleId(mid);
	File md = findModuleDir(mid);
	if (md == null)
	    throw new IllegalArgumentException(mid +
					       ": No such module in library");
	File cf = new File(new File(md, "classes"),
			   className.replace('.', '/') + ".class");
	if (!cf.exists())
	    return null;
	return Files.load(cf);
    }

}
