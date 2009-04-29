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

import static org.openjdk.jigsaw.Trace.*;


/**
 * A simple module library which stores data directly in the filesystem
 *
 * @see Library
 */

public final class SimpleLibrary
    extends Library
{

    private static abstract class MetaData {

	protected final int maxMajorVersion;
	protected final int maxMinorVersion;
	protected int majorVersion;
	protected int minorVersion;
	private FileConstants.Type type;
	private File file;

	protected MetaData(int maxMajor, int maxMinor,
			   FileConstants.Type t, File f)
	{
	    maxMajorVersion = majorVersion = maxMajor;
	    maxMinorVersion = minorVersion = maxMinor;
	    type = t;
	    file = f;
	}

	protected abstract void storeRest(DataOutputStream out)
	    throws IOException;

	void store() throws IOException {
	    OutputStream fo = new FileOutputStream(file);
	    DataOutputStream out
		= new DataOutputStream(new BufferedOutputStream(fo));
	    try {
		out.writeInt(FileConstants.MAGIC);
		out.writeShort(type.value());
		out.writeShort(majorVersion);
		out.writeShort(minorVersion);
		storeRest(out);
	    } finally {
		out.close();
	    }
	}

	protected abstract void loadRest(DataInputStream in)
	    throws IOException;

	protected void load() throws IOException {
	    InputStream fi = new FileInputStream(file);
	    try {
		DataInputStream in
		    = new DataInputStream(new BufferedInputStream(fi));
		int m = in.readInt();
		if (m != FileConstants.MAGIC)
		    throw new IOException(file + ": Invalid magic number");
		int typ = in.readShort();
		if (typ != type.value())
		    throw new IOException(file + ": Invalid file type");
		int maj = in.readShort();
		int min = in.readShort();
		if (   maj > maxMajorVersion
		    || (maj == maxMajorVersion && min > maxMinorVersion)) {
		    throw new IOException(file
					  + ": Futuristic version number");
		}
		majorVersion = maj;
		minorVersion = min;
		loadRest(in);
	    } catch (EOFException x) {
		throw new IOException(file + ": Invalid library metadata",
				      x);
	    } finally {
		fi.close();
	    }
	}

    }

    private static final class Header
	extends MetaData
    {

	private static String FILE
	    = FileConstants.META_PREFIX + "jigsaw-library";

	private static int MAJOR_VERSION = 0;
	private static int MINOR_VERSION = 1;

	private Header(File root) {
	    super(MAJOR_VERSION, MINOR_VERSION,
		  FileConstants.Type.LIBRARY_HEADER,
		  new File(root, FILE));
	}

	protected void storeRest(DataOutputStream out) throws IOException { }

	protected void loadRest(DataInputStream in) throws IOException { }

	private static Header load(File f)
	    throws IOException
	{
	    Header h = new Header(f);
	    h.load();
	    return h;
	}

    }

    private final File root;
    private final File canonicalRoot;
    private final Header hd;

    public String name() { return root.toString(); }
    public int majorVersion() { return hd.majorVersion; }
    public int minorVersion() { return hd.minorVersion; }

    @Override
    public String toString() {
	return (this.getClass().getName()
		+ "[" + canonicalRoot
		+ ", v" + hd.majorVersion + "." + hd.minorVersion + "]");
    }

    private SimpleLibrary(File path, boolean create)
	throws IOException
    {
	root = path;
	canonicalRoot = root.getCanonicalFile();
	if (root.exists()) {
	    if (!root.isDirectory())
		throw new IOException(root + ": Exists but is not a directory");
	    hd = Header.load(root);
	    return;
	}
	if (!create)
	    throw new FileNotFoundException(root.toString());
	if (!root.mkdir())
	    throw new IOException(root + ": Cannot create library directory");
	hd = new Header(root);
	hd.store();
    }

    public static SimpleLibrary open(java.io.File path, boolean create)
	throws IOException
    {
	return new SimpleLibrary(path, create);
    }

    private static final JigsawModuleSystem jms
	= JigsawModuleSystem.instance();

    // Library layout
    //
    //   $LIB/%jigsaw-library
    //        com.foo.bar/1.2.3/info (= module-info.class)
    //                          index (list of defined classes)
    //                          config
    //                          classes/com/foo/bar/...
    //                          lib/libbar.so
    //                          bin/bar

    private static final class Index
	extends MetaData
    {

	private static String FILE = "index";

	private static int MAJOR_VERSION = 0;
	private static int MINOR_VERSION = 1;

	private Set<String> publicClasses;
	public Set<String> publicClasses() { return publicClasses; }

	private Set<String> otherClasses;
	public Set<String> otherClasses() { return otherClasses; }

	private Index(File root) {
	    super(MAJOR_VERSION, MINOR_VERSION,
		  FileConstants.Type.LIBRARY_MODULE_INDEX,
		  new File(root, FILE));
	    // Unsorted on input, because we don't need it sorted
	    publicClasses = new HashSet<String>();
	    otherClasses = new HashSet<String>();
	}

	private void storeSet(Set<String> cnset, DataOutputStream out)
	    throws IOException
	{
	    // Sorted on output, because we can afford it
	    List<String> cns = new ArrayList<String>(cnset);
	    Collections.sort(cns);
	    out.writeInt(cns.size());
	    for (String cn : cns)
		out.writeUTF(cn);
	}

	protected void storeRest(DataOutputStream out)
	    throws IOException
	{
	    storeSet(publicClasses, out);
	    storeSet(otherClasses, out);
	}

	private void loadSet(DataInputStream in, Set<String> cnset)
	    throws IOException
	{
	    int n = in.readInt();
	    for (int i = 0; i < n; i++)
		cnset.add(in.readUTF());
	}

	protected void loadRest(DataInputStream in)
	    throws IOException
	{
	    loadSet(in, publicClasses);
	    loadSet(in, otherClasses);
	}

	private static Index load(File f)
	    throws IOException
	{
	    Index ix = new Index(f);
	    ix.load();
	    return ix;
	}

    }

    private static final class StoredConfiguration
	extends MetaData
    {

	private static String FILE = "config";

	private static int MAJOR_VERSION = 0;
	private static int MINOR_VERSION = 1;

	private Configuration cf;

	private static void delete(File root) {
	    new File(root, FILE).delete();
	}

	private StoredConfiguration(File root, Configuration conf)
	{
	    super(MAJOR_VERSION, MINOR_VERSION,
		  FileConstants.Type.LIBRARY_MODULE_CONFIG,
		  new File(root, FILE));
	    cf = conf;
	}

	protected void storeRest(DataOutputStream out)
	    throws IOException
	{
	    out.writeUTF(cf.root().toString());
	    // Contexts
	    out.writeInt(cf.contexts().size());
	    for (Context cx : cf.contexts()) {
		out.writeUTF(cx.name());
		// Module ids
		out.writeInt(cx.modules().size());
		for (ModuleId mid : cx.modules())
		    out.writeUTF(mid.toString());
		// Local class map
		out.writeInt(cx.localClasses().size());
		for (Map.Entry<String,ModuleId> me
			 : cx.moduleForLocalClassMap().entrySet()) {
		    out.writeUTF(me.getKey());
		    out.writeUTF(me.getValue().toString());
		}
		// Remote package map
		out.writeInt(cx.contextForRemotePackageMap().size());
		for (Map.Entry<String,String> me
			 : cx.contextForRemotePackageMap().entrySet()) {
		    out.writeUTF(me.getKey());
		    out.writeUTF(me.getValue());
		}
	    }
	}

	protected void loadRest(DataInputStream in)
	    throws IOException
	{
	    String root = in.readUTF();
	    ModuleId rmid = jms.parseModuleId(root);
	    cf = new Configuration(rmid);
	    // Contexts
	    int nContexts = in.readInt();
	    for (int i = 0; i < nContexts; i++) {
		Context cx = new Context();
		cx.freeze(in.readUTF());
		cf.add(cx);
		// Module ids
		int nModules = in.readInt();
		for (int j = 0; j < nModules; j++) {
		    ModuleId mid = jms.parseModuleId(in.readUTF());
		    cx.add(mid);
		    cf.put(mid.name(), cx);
		}
		// Local class map
		int nClasses = in.readInt();
		for (int j = 0; j < nClasses; j++)
		    cx.putModuleForLocalClass(in.readUTF(),
					      jms.parseModuleId(in.readUTF()));
		// Remote package map
		int nPackages = in.readInt();
		for (int j = 0; j < nPackages; j++)
		    cx.putContextForRemotePackage(in.readUTF(), in.readUTF());
	    }

	}

	private static StoredConfiguration load(File f)
	    throws IOException
	{
	    StoredConfiguration sp = new StoredConfiguration(f, null);
	    sp.load();
	    return sp;
	}

    }

    public void visitModules(ModuleInfoVisitor mv)
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

    public List<ModuleId> findModuleIds(String moduleName)
	throws IOException
    {
	ModuleSystem.checkModuleName(moduleName);
	File mnd = new File(root, moduleName);
	if (!mnd.exists())
	    return Collections.emptyList();
	if (!mnd.isDirectory())
	    throw new IOException(mnd + ": Not a directory");
	if (!mnd.canRead())
	    throw new IOException(mnd + ": Not readable");
	List<ModuleId> ans = new ArrayList<ModuleId>();
	for (String v : mnd.list()) {
	    // ## Need a MS.parseModuleId(String, Version) method
	    ans.add(jms.parseModuleId(mnd.getName() + "@" + v));
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
	checkModuleId(mid);
	File md = moduleDir(mid);
	if (!md.exists())
	    return null;
	checkModuleDir(md);
	return md;
    }

    public byte[] readModuleInfoBytes(ModuleId mid)
	throws IOException
    {
	File md = findModuleDir(mid);
	if (md == null)
	    return null;
	return Files.load(new File(md, "info"));
    }

    public byte[] readClass(ModuleId mid, String className)
	throws IOException
    {
	File md = findModuleDir(mid);
	if (md == null)
	    return null;
	File cf = new File(new File(md, "classes"),
			   className.replace('.', '/') + ".class");
	if (!cf.exists())
	    return null;
	return Files.load(cf);
    }

    public List<String> listClasses(ModuleId mid, boolean all)
	throws IOException
    {
	File md = findModuleDir(mid);
	if (md == null)
	    return null;
	Index ix = Index.load(md);
	int os = all ? ix.otherClasses().size() : 0;
	ArrayList<String> cns
	    = new ArrayList<String>(ix.publicClasses().size() + os);
	cns.addAll(ix.publicClasses());
	if (all)
	    cns.addAll(ix.otherClasses());
	return cns;
    }

    public Configuration readConfiguration(ModuleId mid)
	throws IOException
    {
	File md = findModuleDir(mid);
	if (md == null)
	    return null;
	StoredConfiguration scf = StoredConfiguration.load(md);
	return scf.cf;
    }

    private void install(File classes, final String moduleName)
	throws ConfigurationException, IOException
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

	// Delete the config file, if one exists
	StoredConfiguration.delete(mdst);

	// Copy class files and build index
	final Index ix = new Index(mdst);
	Files.copyTree(classes, cldst, new Files.Filter<File>() {
	    public boolean accept(File f) throws IOException {
		if (f.isDirectory())
		    return true;
		ClassInfo ci = ClassInfo.read(f);
		if (ci.moduleName() == null)
		    return false;
		if (!ci.isModuleInfo() && ci.moduleName().equals(moduleName)) {
		    if (ci.isPublic())
			ix.publicClasses().add(ci.name());
		    else
			ix.otherClasses().add(ci.name());
		    return true;
		}
		return false;
	    }});
	ix.store();

    }

    public void install(File classes, List<String> moduleNames)
	throws ConfigurationException, IOException
    {

	// Install modules
	for (String mn : moduleNames)
	    install (classes, mn);

	// Update configurations
	// ## We could be a lot more clever about this!
	for (ModuleInfo mi : listRootModuleInfos()) {
	    Configuration cf
		= Resolver.create(this, mi.id().toQuery()).run();
	    new StoredConfiguration(moduleDir(mi.id()), cf).store();
	}

    }

}
