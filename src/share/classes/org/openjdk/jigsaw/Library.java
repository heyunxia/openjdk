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


public final class Library {

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

    public File path() { return root; }
    public int majorVersion() { return hd.majorVersion; }
    public int minorVersion() { return hd.minorVersion; }

    /**
     * Return a string describing this module library.
     */
    @Override
    public String toString() {
	return (this.getClass().getName()
		+ "[" + canonicalRoot
		+ ", v" + hd.majorVersion + "." + hd.minorVersion + "]");
    }

    private Library(File path, boolean create)
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
    //                          index (list of types)
    //                          suppliers (type -> module id map)
    //                          classes/com/foo/bar/...
    //                          lib/libbar.so
    //                          bin/bar

    private static final class Index
	extends MetaData
    {

	private static String FILE = "index";

	private static int MAJOR_VERSION = 0;
	private static int MINOR_VERSION = 1;

	private Set<String> types;
	public Set<String> types() { return types; }

	private Index(File root) {
	    super(MAJOR_VERSION, MINOR_VERSION,
		  FileConstants.Type.LIBRARY_MODULE_INDEX,
		  new File(root, FILE));
	    // Unsorted on input, because we don't need it sorted
	    types = new HashSet<String>();
	}

	protected void storeRest(DataOutputStream out)
	    throws IOException
	{
	    // Sorted on output, because we can afford it
	    types = new TreeSet<String>(types);
	    out.writeInt(types.size());
	    for (String tn : types)
		out.writeUTF(tn);
	}

	protected void loadRest(DataInputStream in)
	    throws IOException
	{
	    int n = in.readInt();
	    for (int i = 0; i < n; i++)
		types.add(in.readUTF());
	}

	private static Index load(File f)
	    throws IOException
	{
	    Index ix = new Index(f);
	    ix.load();
	    return ix;
	}

    }

    private static final class Suppliers
	extends MetaData
    {

	private static String FILE = "suppliers";

	private static int MAJOR_VERSION = 0;
	private static int MINOR_VERSION = 1;

	private Map<String,ModuleId> moduleForClass;
	public Map<String,ModuleId> map() { return moduleForClass; }

	private Suppliers(File root) {
	    super(MAJOR_VERSION, MINOR_VERSION,
		  FileConstants.Type.LIBRARY_MODULE_SUPPLIERS,
		  new File(root, FILE));
	    // Unsorted on input, because we don't need it sorted
	    moduleForClass = new HashMap<String,ModuleId>();
	}

	protected void storeRest(DataOutputStream out)
	    throws IOException
	{
	    // Sorted on output, because we can afford it
	    moduleForClass = new TreeMap<String,ModuleId>(moduleForClass);
	    out.writeInt(moduleForClass.size());
	    for (Map.Entry<String,ModuleId> me : moduleForClass.entrySet()) {
		out.writeUTF(me.getKey());
		out.writeUTF(me.getValue().toString());
	    }
	}

	protected void loadRest(DataInputStream in)
	    throws IOException
	{
	    int n = in.readInt();
	    for (int i = 0; i < n; i++) {
		String cn = in.readUTF();
		String mn = in.readUTF();
		moduleForClass.put(cn, jms.parseModuleId(mn));
	    }
	}

	private static Suppliers load(File f)
	    throws IOException
	{
	    Suppliers sp = new Suppliers(f);
	    sp.load();
	    return sp;
	}

    }

    private ModuleId findBestModuleId(ModuleIdQuery midq)
	throws IOException
    {
	return findLatestModuleId(midq);
    }

    // A module mi has been installed.  Update its supplier map.
    // Assume that all of its dependents have already been installed.
    //
    // ## Eventually we must consider updating the supplier maps
    // ## of every module that might now depend upon this new module.
    // 
    private void updateSuppliers(ModuleInfo mi)
	throws IOException
    {
	if (tracing)
	    trace(0, "updateSuppliers %s", mi);
	Suppliers sp = new Suppliers(moduleDir(mi.id()));
	for (Dependence dep : mi.requires()) {
	    if (tracing)
		trace(1, "%s", dep);
	    ModuleId smid = findBestModuleId(dep.query());
	    if (smid == null)
		throw new Error(mi.id() + ": Unsatisfied dependence on " + dep.query());
	    Index six = Index.load(moduleDir(smid));
	    for (String cn : six.types()) {
		// ## Check for duplicates
		if (tracing)
		    trace(2, "%s %s", smid, cn);
		sp.map().put(cn, smid);
	    }
	}
	sp.store();
    }

    public void install(File classes, final String moduleName)
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

	final Index ix = new Index(mdst);
	Files.copyTree(classes, cldst, new Files.Filter<File>() {
	    public boolean accept(File f) throws IOException {
		if (f.isDirectory())
		    return true;
		ClassInfo ci = ClassInfo.read(f);
		if (ci.moduleName() == null)
		    return false;
		if (!ci.isModuleInfo() && ci.moduleName().equals(moduleName)) {
		    ix.types().add(ci.name());
		    return true;
		}
		return false;
	    }});

	ix.store();
	updateSuppliers(mi);

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
	    throw new IOException(mnd + ": Not readable");
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

    /**
     * Find the most recently-created module matching the given query in this
     * library.
     *
     * @param   moduleIdQuery
     *          The query to match against
     *
     * @return  The identification of the latest module matching the given
     *          query, or {@code null} if none is found
     */
    public ModuleId findLatestModuleId(ModuleIdQuery midq)
	throws IOException
    {
	Set<ModuleId> mids = findModuleIds(midq);
	if (mids.isEmpty())
	    return null;
	if (mids.size() == 1)
	    return mids.iterator().next();
	SortedSet<ModuleId> ans = new TreeSet(midq.versionQuery());
	ans.addAll(mids);
	return ans.first();
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

    public byte[] findModuleInfoBytes(ModuleId mid) // ##
	throws IOException
    {
	checkModuleId(mid);
	File md = findModuleDir(mid);
	if (md == null)
	    return null;
	return Files.load(new File(md, "info"));
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

    private Map<ModuleId,Suppliers> suppliersCache
	= new HashMap<ModuleId,Suppliers>();

    /**
     * Find the identification of the module capable of supplying the named
     * class to the named requesting module.
     *
     * @param   className
     *          The binary name of the requested class
     *
     * @param   requestor
     *          The identification of the requesting module
     *
     * @return  The class supplier's identification
     *
     * @throws  ClassNotFoundException
     *          If no supplier for the named class can be found
     */
    public ModuleId findModuleForClass(String className, ModuleId requestor)
	throws ClassNotFoundException
    {
	Suppliers sp = suppliersCache.get(requestor);
	if (sp == null) {
	    try {
		sp = Suppliers.load(moduleDir(requestor));
		suppliersCache.put(requestor, sp);
	    } catch (IOException x) {
		throw new Error(x);		// ##
	    }
	}
	ModuleId smid = sp.map().get(className);
	if (smid == null)
	    throw new ClassNotFoundException(className);
	return smid;
    }

}
