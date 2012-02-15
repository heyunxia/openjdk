/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.module.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

import static java.nio.file.StandardCopyOption.*;

/**
 * A simple module library which stores data directly in the filesystem
 *
 * @see Library
 */

// ## TODO: Move remaining parent-searching logic upward into Library class

// On-disk library layout
//
//   $LIB/%jigsaw-library
//        com.foo.bar/1.2.3/info (= module-info.class)
//                          index (list of defined classes)
//                          config (resolved configuration, if a root)
//                          classes/com/foo/bar/...
//                          resources/com/foo/bar/...
//                          lib/libbar.so
//                          bin/bar
//                          signer (signer's certchain & timestamp)
//
// ## Issue: Concurrent access to the module library
// ## e.g. a module is being removed while a running application
// ## is depending on it

public final class SimpleLibrary
    extends Library
{

    private static abstract class MetaData {

        protected final int maxMajorVersion;
        protected final int maxMinorVersion;
        protected int majorVersion;
        protected int minorVersion;
        private final FileConstants.Type type;
        private final File file;

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
            try (OutputStream fos = new FileOutputStream(file);
                 BufferedOutputStream bos = new BufferedOutputStream(fos);
                 DataOutputStream out = new DataOutputStream(bos)) {
                out.writeInt(FileConstants.MAGIC);
                out.writeShort(type.value());
                out.writeShort(majorVersion);
                out.writeShort(minorVersion);
                storeRest(out);
            }
        }

        protected abstract void loadRest(DataInputStream in)
            throws IOException;

        protected void load() throws IOException {
            try (InputStream fis = new FileInputStream(file);
                 BufferedInputStream bis = new BufferedInputStream(fis);
                 DataInputStream in = new DataInputStream(bis)) {
                if (in.readInt() != FileConstants.MAGIC)
                    throw new IOException(file + ": Invalid magic number");
                if (in.readShort() != type.value())
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
                throw new IOException(file + ": Invalid library metadata", x);
            }
        }
    }

    /**
     * Defines the storage options that SimpleLibrary supports.
     */
    public static enum StorageOption {
        DEFLATED,
    }

    private static final class Header
        extends MetaData
    {
        private static final String FILE
            = FileConstants.META_PREFIX + "jigsaw-library";

        private static final int MAJOR_VERSION = 0;
        private static final int MINOR_VERSION = 1;

        private static final int DEFLATED = 1 << 0;

        private File parent;
        // location of native libs for this library (may be outside the library)
        // null:default, to use a per-module 'lib' directory
        private File natlibs;
        // location of native cmds for this library (may be outside the library)
        // null:default, to use a per-module 'bin' directory
        private File natcmds;
        // location of config files for this library (may be outside the library)
        // null:default, to use a per-module 'etc' directory
        private File configs;
        private Set<StorageOption> opts;

        public File parent()  { return parent;  }
        public File natlibs() { return natlibs; }
        public File natcmds() { return natcmds; }
        public File configs() { return configs; }
        public boolean isDeflated() {
            return opts.contains(StorageOption.DEFLATED);
        }

        private Header(File root) {
            super(MAJOR_VERSION, MINOR_VERSION,
                  FileConstants.Type.LIBRARY_HEADER,
                  new File(root, FILE));
        }

        private Header(File root, File parent, File natlibs, File natcmds,
                       File configs, Set<StorageOption> opts) {
            this(root);
            this.parent = parent;
            this.natlibs = natlibs;
            this.natcmds = natcmds;
            this.configs = configs;
            this.opts = new HashSet<>(opts);
        }

        private void storePath(File p, DataOutputStream out) throws IOException {
            if (p != null) {
                out.writeByte(1);
                out.writeUTF(Files.convertSeparator(p.toString()));
            } else {
                out.write(0);
            }
        }

        protected void storeRest(DataOutputStream out) throws IOException {
            int flags = 0;
            if (isDeflated())
                flags |= DEFLATED;
            out.writeShort(flags);

            storePath(parent, out);
            storePath(natlibs, out);
            storePath(natcmds, out);
            storePath(configs, out);
        }

        private File loadPath(DataInputStream in) throws IOException {
            if (in.readByte() != 0)
                return new File(Files.platformSeparator(in.readUTF()));
            return null;
        }

        protected void loadRest(DataInputStream in) throws IOException {
            opts = new HashSet<StorageOption>();
            int flags = in.readShort();
            if ((flags & DEFLATED) == DEFLATED)
                opts.add(StorageOption.DEFLATED);
            parent = loadPath(in);
            natlibs = loadPath(in);
            natcmds = loadPath(in);
            configs = loadPath(in);
        }

        private static Header load(File f) throws IOException {
            Header h = new Header(f);
            h.load();
            return h;
        }
    }

    private final File root;
    private final File canonicalRoot;
    private final File parentPath;
    private final File natlibs;
    private final File natcmds;
    private final File configs;
    private final SimpleLibrary parent;
    private final Header hd;

    public String name() { return root.toString(); }
    public File root() { return canonicalRoot; }
    public int majorVersion() { return hd.majorVersion; }
    public int minorVersion() { return hd.minorVersion; }
    public SimpleLibrary parent() { return parent; }
    public File natlibs() { return natlibs; }
    public File natcmds() { return natcmds; }
    public File configs() { return configs; }
    public boolean isDeflated() { return hd.isDeflated(); }

    private URI location = null;
    public URI location() {
        if (location == null)
            location = root().toURI();
        return location;
    }

    @Override
    public String toString() {
        return (this.getClass().getName()
                + "[" + canonicalRoot
                + ", v" + hd.majorVersion + "." + hd.minorVersion + "]");
    }

    private static File resolveAndEnsurePath(File path) throws IOException {
        if (path == null) { return null; }

        File p = path.getCanonicalFile();
        if (!p.exists()) {
            Files.mkdirs(p, p.toString());
        } else {
            Files.ensureIsDirectory(p);
            Files.ensureWriteable(p);
        }
        return p;
    }

    private File relativize(File path) throws IOException {
        if (path == null) { return null; }
        // Return the path relative to the canonical root
        return (canonicalRoot.toPath().relativize(path.toPath().toRealPath())).toFile();
    }

    // Opens an existing library
    private SimpleLibrary(File path) throws IOException {
        root = path;
        canonicalRoot = root.getCanonicalFile();
        Files.ensureIsDirectory(root);
        hd = Header.load(root);

        parentPath = hd.parent();
        parent = parentPath != null ? open(parentPath) : null;

        natlibs = hd.natlibs() == null ? null :
            new File(canonicalRoot, hd.natlibs().toString()).getCanonicalFile();
        natcmds = hd.natcmds() == null ? null :
            new File(canonicalRoot, hd.natcmds().toString()).getCanonicalFile();
        configs = hd.configs() == null ? null :
            new File(canonicalRoot, hd.configs().toString()).getCanonicalFile();
    }

    // Creates a new library
    private SimpleLibrary(File path, File parentPath, File natlibs, File natcmds,
                          File configs, Set<StorageOption> opts)
        throws IOException
    {
        root = path;
        canonicalRoot = root.getCanonicalFile();
        if (root.exists()) {
            Files.ensureIsDirectory(root);
            if (root.list().length != 0)
                throw new IOException(root + ": Already Exists");
            Files.ensureWriteable(root);
        } else
            Files.mkdirs(root, root.toString());

        this.parent = parentPath != null ? open(parentPath) : null;
        this.parentPath = parentPath != null ? this.parent.root() : null;

        this.natlibs = resolveAndEnsurePath(natlibs);
        this.natcmds = resolveAndEnsurePath(natcmds);
        this.configs = resolveAndEnsurePath(configs);

        hd = new Header(canonicalRoot, this.parentPath, relativize(this.natlibs),
                        relativize(this.natcmds), relativize(this.configs), opts);
        hd.store();
    }

    public static SimpleLibrary create(File path, File parent, File natlibs,
                                       File natcmds, File configs,
                                       Set<StorageOption> opts)
        throws IOException
    {
        return new SimpleLibrary(path, parent, natlibs, natcmds, configs, opts);
    }

    public static SimpleLibrary create(File path, File parent, Set<StorageOption> opts)
        throws IOException
    {
        return new SimpleLibrary(path, parent, null, null, null, opts);
    }

    public static SimpleLibrary create(File path, File parent)
        throws IOException
    {
	return SimpleLibrary.create(path, parent, Collections.<StorageOption>emptySet());
    }

    public static SimpleLibrary create(File path, Set<StorageOption> opts)
        throws IOException
    {
        // ## Should default parent to $JAVA_HOME/lib/modules
        return SimpleLibrary.create(path, null, opts);
    }

    public static SimpleLibrary open(File path)
        throws IOException
    {
        return new SimpleLibrary(path);
    }

    private static final JigsawModuleSystem jms
        = JigsawModuleSystem.instance();

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

        private Configuration<Context> cf;

        private static void delete(File root) {
            new File(root, FILE).delete();
        }

        private StoredConfiguration(File root, Configuration<Context> conf)
        {
            super(MAJOR_VERSION, MINOR_VERSION,
                  FileConstants.Type.LIBRARY_MODULE_CONFIG,
                  new File(root, FILE));
            cf = conf;
        }

        protected void storeRest(DataOutputStream out)
            throws IOException
        {
            // Roots
            out.writeInt(cf.roots().size());
            for (ModuleId mid : cf.roots()) {
                out.writeUTF(mid.toString());
            }
            // Contexts
            out.writeInt(cf.contexts().size());
            for (Context cx : cf.contexts()) {
                out.writeUTF(cx.name());
                // Module ids, and their libraries
                out.writeInt(cx.modules().size());
                for (ModuleId mid : cx.modules()) {
                    out.writeUTF(mid.toString());
                    File lp = cx.findLibraryPathForModule(mid);
                    if (lp == null)
                        out.writeUTF("");
                    else
                        out.writeUTF(lp.toString());

                    // Module views
                    out.writeInt(cx.views(mid).size());
                    for (ModuleId id : cx.views(mid)) {
                        out.writeUTF(id.toString());
                    }
                }

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

                // Suppliers
                out.writeInt(cx.remoteContexts().size());
                for (String cxn : cx.remoteContexts()) {
                    out.writeUTF(cxn);
                }

            }
        }

        protected void loadRest(DataInputStream in)
            throws IOException
        {
            // Roots
            int nRoots = in.readInt();
            List<ModuleId> roots = new ArrayList<>();
            for (int i = 0; i < nRoots; i++) {
                String root = in.readUTF();
                ModuleId rmid = jms.parseModuleId(root);
                roots.add(rmid);
            }
            cf = new Configuration<Context>(roots);
            // Contexts
            int nContexts = in.readInt();
            for (int i = 0; i < nContexts; i++) {
                Context cx = new Context();
                String cxn = in.readUTF();
                // Module ids
                int nModules = in.readInt();
                for (int j = 0; j < nModules; j++) {
                    ModuleId mid = jms.parseModuleId(in.readUTF());
                    String lps = in.readUTF();
                    if (lps.length() > 0)
                        cx.putLibraryPathForModule(mid, new File(lps));
                    // Module Views
                    int nViews = in.readInt();
                    Set<ModuleId> views = new HashSet<>();
                    for (int k = 0; k < nViews; k++) {
                        ModuleId id = jms.parseModuleId(in.readUTF());
                        views.add(id);
                        cf.put(id.name(), cx);
                    }
                    cx.add(mid, views);
                }
                cx.freeze();
                assert cx.name().equals(cxn);
                cf.add(cx);
                // Local class map
                int nClasses = in.readInt();
                for (int j = 0; j < nClasses; j++)
                    cx.putModuleForLocalClass(in.readUTF(),
                                              jms.parseModuleId(in.readUTF()));
                // Remote package map
                int nPackages = in.readInt();
                for (int j = 0; j < nPackages; j++)
                    cx.putContextForRemotePackage(in.readUTF(), in.readUTF());

                // Suppliers
                int nSuppliers = in.readInt();
                for (int j = 0; j < nSuppliers; j++)
                    cx.addSupplier(in.readUTF());
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

    private static final class Signers
        extends MetaData {

        private static String FILE = "signer";
        private static int MAJOR_VERSION = 0;
        private static int MINOR_VERSION = 1;

        private CertificateFactory cf = null;
        private Set<CodeSigner> signers;
        private Set<CodeSigner> signers() { return signers; }

        private Signers(File root, Set<CodeSigner> signers) {
            super(MAJOR_VERSION, MINOR_VERSION,
                  FileConstants.Type.LIBRARY_MODULE_SIGNER,
                  new File(root, FILE));
            this.signers = signers;
        }

        protected void storeRest(DataOutputStream out)
            throws IOException
        {
            out.writeInt(signers.size());
            for (CodeSigner signer : signers) {
                try {
                    CertPath signerCertPath = signer.getSignerCertPath();
                    out.write(signerCertPath.getEncoded("PkiPath"));
                    Timestamp ts = signer.getTimestamp();
                    out.writeByte((ts != null) ? 1 : 0);
                    if (ts != null) {
                        out.writeLong(ts.getTimestamp().getTime());
                        out.write(ts.getSignerCertPath().getEncoded("PkiPath"));
                    }
                } catch (CertificateEncodingException cee) {
                    throw new IOException(cee);
                }
            }
        }

        protected void loadRest(DataInputStream in)
            throws IOException
        {
            int size = in.readInt();
            for (int i = 0; i < size; i++) {
                try {
                    if (cf == null)
                        cf = CertificateFactory.getInstance("X.509");
                    CertPath signerCertPath = cf.generateCertPath(in, "PkiPath");
                    int b = in.readByte();
                    if (b != 0) {
                        Date timestamp = new Date(in.readLong());
                        CertPath tsaCertPath = cf.generateCertPath(in, "PkiPath");
                        Timestamp ts = new Timestamp(timestamp, tsaCertPath);
                        signers.add(new CodeSigner(signerCertPath, ts));
                    } else {
                        signers.add(new CodeSigner(signerCertPath, null));
                    }
                } catch (CertificateException ce) {
                    throw new IOException(ce);
                }
            }
        }

        private static Signers load(File f)
            throws IOException
        {
            Signers signers = new Signers(f, new HashSet<CodeSigner>());
            signers.load();
            return signers;
        }
    }

    private void gatherLocalModuleIds(File mnd, Set<ModuleId> mids)
        throws IOException
    {
        if (!mnd.isDirectory())
            throw new IOException(mnd + ": Not a directory");
        if (!mnd.canRead())
            throw new IOException(mnd + ": Not readable");
        for (String v : mnd.list()) {
            mids.add(jms.parseModuleId(mnd.getName(), v));
        }
    }

    private void gatherLocalModuleIds(Set<ModuleId> mids)
        throws IOException
    {
        File[] mnds = root.listFiles();
        for (File mnd : mnds) {
            if (mnd.getName().startsWith(FileConstants.META_PREFIX))
                continue;
            gatherLocalModuleIds(mnd, mids);
        }
    }

    protected void gatherLocalModuleIds(String moduleName,
                                        Set<ModuleId> mids)
        throws IOException
    {
        if (moduleName == null) {
            gatherLocalModuleIds(mids);
            return;
        }
        File mnd = new File(root, moduleName);
        if (mnd.exists())
            gatherLocalModuleIds(mnd, mids);
    }

    private void checkModuleId(ModuleId mid) {
        Version v = mid.version();
        if (v == null)
            return;
        if (!(v instanceof JigsawVersion))
            throw new IllegalArgumentException(mid + ": Not a Jigsaw module id");
    }

    private File moduleDir(File root, ModuleId mid) {
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
        File md = moduleDir(root, mid);
        if (!md.exists())
            return null;
        checkModuleDir(md);

        // mid may be a view or alias of a module
        byte[] mib = Files.load(new File(md, "info"));
        ModuleInfo mi = jms.parseModuleInfo(mib);
        if (!mid.equals(mi.id())) {
            md = moduleDir(root, mi.id());
            if (!md.exists())
                throw new IOException(mid + ": " + md + " does not exist");
            checkModuleDir(md);
        }
        return md;
    }

    private File makeModuleDir(File root, ModuleInfo mi)
        throws ConfigurationException, IOException
    {
        // view name is unique
        for (ModuleView mv : mi.views()) {
            File md = moduleDir(root, mv.id());
            if (md.exists()) {
                throw new ConfigurationException("module view " +
                    mv.id() + " already installed");
            }
            if (!md.mkdirs()) {
                throw new IOException(md + ": Cannot create");
            }
        }

        return moduleDir(root, mi.id());
    }

    private void deleteModuleDir(File root, ModuleInfo mi)
        throws IOException
    {
        // delete the default view and the module content
        ModuleId mid = mi.defaultView().id();
        File md = moduleDir(root, mid);
        if (md.exists())
            ModuleFile.Reader.remove(md);
        // delete all views
        for (ModuleView mv : mi.views()) {
            md = moduleDir(root, mv.id());
            if (md.exists()) {
                Files.deleteTree(md);
            }
        }
    }

    private void deleteModuleDir(ModuleId mid)
        throws IOException
    {
        checkModuleId(mid);
        File md = moduleDir(root, mid);
        if (!md.exists())
            return;
        checkModuleDir(md);

        // mid may be a view or alias of a module
        byte[] mib = Files.load(new File(md, "info"));
        ModuleInfo mi = jms.parseModuleInfo(mib);
        if (!mid.equals(mi.id())) {
            throw new IOException(mi.id() + " found in the module directory for " + mid);
        }
        deleteModuleDir(root, mi);
    }

    private void copyModuleInfo(File root, ModuleInfo mi, byte[] mib)
        throws IOException
    {
        for (ModuleView mv : mi.views()) {
            if (mv.id().equals(mi.id())) {
                continue;
            }

            File mvd = moduleDir(root, mv.id());
            Files.store(mib, new File(mvd, "info"));
        }
    }
    public byte[] readLocalModuleInfoBytes(ModuleId mid)
        throws IOException
    {
        File md = findModuleDir(mid);
        if (md == null)
            return null;
        return Files.load(new File(md, "info"));
    }

    public CodeSigner[] readLocalCodeSigners(ModuleId mid)
        throws IOException
    {
        File md = findModuleDir(mid);
        if (md == null)
            return null;
        // Only one signer is currently supported
        File f = new File(md, "signer");
        // ## concurrency issues : what is the expected behavior if file is
        // ## removed by another thread/process here?
        if (!f.exists())
            return null;
        return Signers.load(md).signers().toArray(new CodeSigner[0]);
    }

    // ## Close all zip files when we close this library
    private Map<ModuleId, Object> contentForModule = new HashMap<>();
    private Object NONE = new Object();

    private Object findContent(ModuleId mid)
        throws IOException
    {
        Object o = contentForModule.get(mid);
        if (o != null)
            return o;
        if (o == NONE)
            return null;
        File md = findModuleDir(mid);
        if (md == null) {
            contentForModule.put(mid, NONE);
            return null;
        }
        File cf = new File(md, "classes");
        if (cf.isFile()) {
            ZipFile zf = new ZipFile(cf);
            contentForModule.put(mid, zf);
            return zf;
        }
        if (cf.isDirectory()) {
            contentForModule.put(mid, cf);
            return cf;
        }
        contentForModule.put(mid, NONE);
        return null;
    }

    private byte[] loadContent(ZipFile zf, String path)
        throws IOException
    {
        ZipEntry ze = zf.getEntry(path);
        if (ze == null)
            return null;
        return Files.load(zf.getInputStream(ze), (int)ze.getSize());
    }

    private byte[] loadContent(ModuleId mid, String path)
        throws IOException
    {
        Object o = findContent(mid);
        if (o == null)
            return null;
        if (o instanceof ZipFile) {
            ZipFile zf = (ZipFile)o;
            ZipEntry ze = zf.getEntry(path);
            if (ze == null)
                return null;
            return Files.load(zf.getInputStream(ze), (int)ze.getSize());
        }
        if (o instanceof File) {
            File f = new File((File)o, path);
            if (!f.exists())
                return null;
            return Files.load(f);
        }
        assert false;
        return null;
    }

    private URI locateContent(ModuleId mid, String path)
        throws IOException
    {
        Object o = findContent(mid);
        if (o == null)
            return null;
        if (o instanceof ZipFile) {
            ZipFile zf = (ZipFile)o;
            ZipEntry ze = zf.getEntry(path);
            if (ze == null)
                return null;
            return URI.create("jar:"
                              + new File(zf.getName()).toURI().toString()
                              + "!/" + path);
        }
        if (o instanceof File) {
            File f = new File((File)o, path);
            if (!f.exists())
                return null;
            return f.toURI();
        }
        return null;
    }

    public byte[] readLocalClass(ModuleId mid, String className)
        throws IOException
    {
        return loadContent(mid, className.replace('.', '/') + ".class");
    }

    public List<String> listLocalClasses(ModuleId mid, boolean all)
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

    public Configuration<Context> readConfiguration(ModuleId mid)
        throws IOException
    {
        File md = findModuleDir(mid);
        if (md == null) {
            if (parent != null)
                return parent.readConfiguration(mid);
            return null;
        }
        StoredConfiguration scf = StoredConfiguration.load(md);
        return scf.cf;
    }

    private boolean addToIndex(ClassInfo ci, Index ix)
        throws IOException
    {
        if (ci.isModuleInfo())
            return false;
        if (ci.moduleName() != null) {
            // ## From early Jigsaw development; can probably delete now
            throw new IOException("Old-style class file with"
                                  + " module attribute");
        }
        if (ci.isPublic())
            ix.publicClasses().add(ci.name());
        else
            ix.otherClasses().add(ci.name());
        return true;
    }

    private void reIndex(ModuleId mid)
        throws IOException
    {

        File md = findModuleDir(mid);
        if (md == null)
            throw new IllegalArgumentException(mid + ": No such module");
        File cd = new File(md, "classes");
        final Index ix = new Index(md);

        if (cd.isDirectory()) {
            Files.walkTree(cd, new Files.Visitor<File>() {
                public void accept(File f) throws IOException {
                    if (f.getPath().endsWith(".class"))
                        addToIndex(ClassInfo.read(f), ix);
                }
            });
        } else if (cd.isFile()) {
            FileInputStream fis = new FileInputStream(cd);
            ZipInputStream zis = new ZipInputStream(fis);
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (!ze.getName().endsWith(".class"))
                    continue;
                addToIndex(ClassInfo.read(Files.nonClosingStream(zis),
                                          ze.getSize(),
                                          mid + ":" + ze.getName()),
                           ix);
            }
        }

        ix.store();
    }

    /**
     * Strip the debug attributes from the classes in a given module
     * directory.
     */
    private void strip(File md) throws IOException {
        File classes = new File(md, "classes");
        if (classes.isFile()) {
            File pf = new File(md, "classes.pack");
            try (JarFile jf = new JarFile(classes);
                FileOutputStream out = new FileOutputStream(pf))
            {
                Pack200.Packer packer = Pack200.newPacker();
                Map<String,String> p = packer.properties();
                p.put("com.sun.java.util.jar.pack.strip.debug", Pack200.Packer.TRUE);
                packer.pack(jf, out);
            }

            try (OutputStream out = new FileOutputStream(classes);
                 JarOutputStream jos = new JarOutputStream(out))
            {
	        Pack200.Unpacker unpacker = Pack200.newUnpacker();
                unpacker.unpack(pf, jos);
            } finally {
                pf.delete();
           }
        }
    }

    private List<Path> listFiles(Path dir) throws IOException {
        final List<Path> files = new ArrayList<>();
        java.nio.file.Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException
            {
                if (!file.endsWith("module-info.class"))
                    files.add(file);

                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    private void install(Manifest mf, File dst, boolean strip)
        throws IOException
    {
        if (mf.classes().size() > 1)
            throw new IllegalArgumentException("Multiple module-class"
                                               + " directories"
                                               + " not yet supported");
        if (mf.classes().size() < 1)
            throw new IllegalArgumentException("At least one module-class"
                                               + " directory required");
        File classes = mf.classes().get(0);
        final String mn = mf.module();

        File mif = new File(classes, "module-info.class");
        File src = null;
        if (mif.exists()) {
            src = classes;
        } else {
            src = new File(classes, mn);
            mif = new File(src, "module-info.class");
        }
        byte[] bs =  Files.load(mif);
        ModuleInfo mi = jms.parseModuleInfo(bs);
        if (!mi.id().name().equals(mn)) {
            // ## Need a more appropriate throwable here
            throw new Error(mif + " is for module " + mi.id().name()
                            + ", not " + mn);
        }
        String m = mi.id().name();
        JigsawVersion v = (JigsawVersion)mi.id().version();
        String vs = (v == null) ? "default" : v.toString();
        deleteModuleDir(dst, mi);

         // view name is unique
        for (ModuleView mv : mi.views()) {
            File md = moduleDir(dst, mv.id());
            if (!md.mkdirs()) {
                throw new IOException(md + ": Cannot create");
            }
        }

        File mdst = moduleDir(dst, mi.id());
        Files.store(bs, new File(mdst, "info"));
        File cldst = new File(mdst, "classes");

        // Delete the config file, if one exists
        StoredConfiguration.delete(mdst);

        if (false) {

            // ## Retained for now in case we later want to add an option
            // ## to install into a tree rather than a zip file

            // Copy class files and build index
            final Index ix = new Index(mdst);
            Files.copyTree(src, cldst, new Files.Filter<File>() {
                    public boolean accept(File f) throws IOException {
                        if (f.isDirectory())
                            return true;
                        if (f.getName().endsWith(".class")) {
                            return addToIndex(ClassInfo.read(f), ix);
                        } else {
                            return true;
                        }
                    }});
            ix.store();
        } else {
            // Copy class/resource files and build index
            Index ix = new Index(mdst);
            Path srcPath = src.toPath();
            List<Path> files = listFiles(srcPath);

            if (!files.isEmpty()) {
                try (FileOutputStream fos = new FileOutputStream(new File(mdst, "classes"));
                     JarOutputStream jos = new JarOutputStream(new BufferedOutputStream(fos)))
                {
                    boolean deflate = isDeflated();
                    for (Path path : files) {
                        File file = path.toFile();
                        String jp = Files.convertSeparator(srcPath.relativize(path).toString());
                        try (OutputStream out = Files.newOutputStream(jos, deflate, jp)) {
                            java.nio.file.Files.copy(path, out);
                        }
                        if (file.getName().endsWith(".class"))
                            addToIndex(ClassInfo.read(file), ix);
                    }
                }
            }
            ix.store();
            copyModuleInfo(dst, mi, bs);
            if (strip)
                strip(mdst);
        }
    }

    private void install(Collection<Manifest> mfs, File dst, boolean strip)
        throws IOException
    {
        for (Manifest mf : mfs)
            install(mf, dst, strip);
    }

    public void installFromManifests(Collection<Manifest> mfs, boolean strip)
        throws ConfigurationException, IOException
    {
        install(mfs, root, strip);
        configure(null);
    }

    @Override
    public void installFromManifests(Collection<Manifest> mfs)
	throws ConfigurationException, IOException
    {
	installFromManifests(mfs, false);
    }

    private ModuleFileVerifier.Parameters mfvParams;
    private ModuleId install(InputStream is, boolean verifySignature, boolean strip)
        throws ConfigurationException, IOException, SignatureException
    {
        BufferedInputStream bin = new BufferedInputStream(is);
        DataInputStream in = new DataInputStream(bin);
        ModuleInfo mi = null;
        try (ModuleFile.Reader mr = new ModuleFile.Reader(in)) {
            byte[] mib = mr.readStart();
            mi = jms.parseModuleInfo(mib);
            File md = makeModuleDir(root, mi);
            if (verifySignature && mr.hasSignature()) {
                ModuleFileVerifier mfv = new SignedModule.PKCS7Verifier(mr);
                if (mfvParams == null) {
                    mfvParams = new SignedModule.VerifierParameters();
                }
                // Verify the module signature and validate the signer's
                // certificate chain
                Set<CodeSigner> signers = mfv.verifySignature(mfvParams);

                // Verify the module header hash and the module info hash
                mfv.verifyHashesStart(mfvParams);

                // ## Check policy - is signer trusted and what permissions
                // ## should be granted?

                // Store signer info
                new Signers(md, signers).store();

                // Read and verify the rest of the hashes
                mr.readRest(md, isDeflated(), natlibs(), natcmds(), configs());
                mfv.verifyHashesRest(mfvParams);
            } else {
                mr.readRest(md, isDeflated(), natlibs(), natcmds(), configs());
            }

            if (strip)
                strip(md);
            reIndex(mi.id());         // ## Could do this while reading module file

            // copy module-info.class to each view
            copyModuleInfo(root, mi, mib);
            return mi.id();

        } catch (IOException | SignatureException x) {
            if (mi != null) {
                try {
                    deleteModuleDir(root, mi);
                } catch (IOException y) {
                    y.initCause(x);
                    throw y;
                }
            }
            throw x;
        }
    }

    private ModuleId installFromJarFile(File mf, boolean verifySignature, boolean strip)
        throws ConfigurationException, IOException, SignatureException
    {
        ModuleInfo mi = null;
        try (JarFile jf = new JarFile(mf, verifySignature)) {
            mi = jf.getModuleInfo();
            if (mi == null)
                throw new ConfigurationException(mf + ": not a modular JAR file");

            File md = makeModuleDir(root, mi);
            ModuleId mid = mi.id();

            boolean signed = false;

            // copy the jar file to the module library
            File classesDir = new File(md, "classes");
            try (FileOutputStream fos = new FileOutputStream(classesDir);
                 BufferedOutputStream bos = new BufferedOutputStream(fos);
                 JarOutputStream jos = new JarOutputStream(bos)) {
                jos.setLevel(0);

                Enumeration<JarEntry> entries = jf.entries();
                while (entries.hasMoreElements()) {
                    JarEntry je = entries.nextElement();
                    try (InputStream is = jf.getInputStream(je)) {
                        if (je.getName().equals(JarFile.MODULEINFO_NAME)) {
                            java.nio.file.Files.copy(is, md.toPath().resolve("info"));
                        } else {
                            writeJarEntry(is, je, jos);
                        }
                    }
                    if (!signed) {
                        String name = je.getName().toUpperCase(Locale.ENGLISH);
                        signed = name.startsWith("META-INF/")
                                 && name.endsWith(".SF");
                    }
                }
            }

            try {
                if (verifySignature && signed) {
                    // validate the code signers
                    Set<CodeSigner> signers = getSigners(jf);
                    SignedModule.validateSigners(signers);
                    // store the signers
                    new Signers(md, signers).store();
                }
            } catch (CertificateException ce) {
                throw new SignatureException(ce);
            }

            if (strip)
                strip(md);
            reIndex(mid);

            // copy module-info.class to each view
            byte[] mib = java.nio.file.Files.readAllBytes(md.toPath().resolve("info"));
            copyModuleInfo(root, mi, mib);
            return mid;
        } catch (IOException | SignatureException x) {
            if (mi != null) {
                try {
                    deleteModuleDir(root, mi);
                } catch (IOException y) {
                    y.initCause(x);
                    throw y;
                }
            }
            throw x;
        }
    }

    /**
     * Returns the set of signers of the specified jar file. Each signer
     * must have signed all relevant entries.
     */
    private static Set<CodeSigner> getSigners(JarFile jf)
        throws SignatureException
    {
        Set<CodeSigner> signers = new HashSet<>();
        Enumeration<JarEntry> entries = jf.entries();
        while (entries.hasMoreElements()) {
            JarEntry je = entries.nextElement();
            String name = je.getName().toUpperCase(Locale.ENGLISH);
            if (name.endsWith("/") || isSigningRelated(name))
                continue;

            // A signed modular jar can be signed by multiple signers.
            // However, all entries must be signed by each of these signers.
            // Signers that only sign a subset of entries are ignored.
            CodeSigner[] jeSigners = je.getCodeSigners();
            if (jeSigners == null || jeSigners.length == 0)
                throw new SignatureException("Found unsigned entry in "
                                             + "signed modular JAR");

            Set<CodeSigner> jeSignerSet =
                new HashSet<>(Arrays.asList(jeSigners));
            if (signers.isEmpty())
                signers.addAll(jeSignerSet);
            else {
                if (signers.retainAll(jeSignerSet) && signers.isEmpty())
                    throw new SignatureException("No signers in common in "
                                                 + "signed modular JAR");
            }
        }
        return signers;
    }

    // true if file is part of the signature mechanism itself
    private static boolean isSigningRelated(String name) {
        if (!name.startsWith("META-INF/")) {
            return false;
        }
        name = name.substring(9);
        if (name.indexOf('/') != -1) {
            return false;
        }
        if (name.endsWith(".DSA") ||
            name.endsWith(".RSA") ||
            name.endsWith(".SF")  ||
            name.endsWith(".EC")  ||
            name.startsWith("SIG-") ||
            name.equals("MANIFEST.MF")) {
            return true;
        }
        return false;
    }

    private void writeJarEntry(InputStream is, JarEntry je, JarOutputStream jos)
        throws IOException, SignatureException
    {
        JarEntry entry = new JarEntry(je.getName());
        entry.setMethod(isDeflated() ? ZipEntry.DEFLATED : ZipEntry.STORED);
        entry.setTime(je.getTime());
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            int size = 0;
            byte[] bs = new byte[1024];
            int cc = 0;
            // This will throw a SecurityException if a signature is invalid.
            while ((cc = is.read(bs)) > 0) {
                baos.write(bs, 0, cc);
                size += cc;
            }
            if (!isDeflated()) {
                entry.setSize(size);
                entry.setCrc(je.getCrc());
                entry.setCompressedSize(size);
            }
            jos.putNextEntry(entry);
            if (baos.size() > 0)
                baos.writeTo(jos);
            jos.closeEntry();
        } catch (SecurityException se) {
            throw new SignatureException(se);
        }
    }

    private ModuleId install(File mf, boolean verifySignature, boolean strip)
        throws ConfigurationException, IOException, SignatureException
    {
        if (mf.getName().endsWith(".jar"))
            return installFromJarFile(mf, verifySignature, strip);
        else {
            // Assume jmod file
            try (FileInputStream in = new FileInputStream(mf)) {
                return install(in, verifySignature, strip);
            }
        }
    }

    public void install(Collection<File> mfs, boolean verifySignature, boolean strip)
        throws ConfigurationException, IOException, SignatureException
    {
        List<ModuleId> mids = new ArrayList<>();
        boolean complete = false;
        Throwable ox = null;
        try {
            for (File mf : mfs)
                mids.add(install(mf, verifySignature, strip));
            configure(mids);
            complete = true;
        } catch (IOException|ConfigurationException x) {
            ox = x;
            throw x;
        } finally {
            if (!complete) {
                try {
                    for (ModuleId mid : mids)
                        deleteModuleDir(mid);
                } catch (IOException x) {
                    if (ox != null)
                        x.initCause(ox);
                    throw x;
                }
            }
        }
    }

    @Override
    public void install(Collection<File> mfs, boolean verifySignature)
        throws ConfigurationException, IOException, SignatureException
    {
	install(mfs, verifySignature, false);
    }

    // Public entry point, since the Resolver itself is package-private
    //
    public Resolution resolve(Collection<ModuleIdQuery> midqs)
        throws ConfigurationException, IOException
    {
        return Resolver.run(this, midqs);
    }

    public void install(Resolution res, boolean verifySignature, boolean strip)
        throws ConfigurationException, IOException, SignatureException
    {
        // ## Handle case of installing multiple root modules
        assert res.rootQueries.size() == 1;
        ModuleIdQuery midq = res.rootQueries.iterator().next();
        ModuleInfo root = null;
        for (String mn : res.moduleViewForName.keySet()) {
            ModuleView mv = res.moduleViewForName.get(mn);
            if (midq.matches(mv.id())) {
                root = mv.moduleInfo();
                break;
            }
        }
        assert root != null;

        // Download
        //
        for (ModuleId mid : res.modulesNeeded()) {
            URI u = res.locationForName.get(mid.name());
            assert u != null;
            RemoteRepository rr = repositoryList().firstRepository();
            assert rr != null;
            install(rr.fetch(mid), verifySignature, strip);
            res.locationForName.put(mid.name(), location());
            // ## If something goes wrong, delete all our modules
        }

        // Configure
        //
        Configuration<Context> cf
            = Configurator.configure(this, res);
        new StoredConfiguration(findModuleDir(root.id()), cf).store();
    }

    @Override
    public void install(Resolution res, boolean verifySignature)
        throws ConfigurationException, IOException, SignatureException
    {
	install(res, verifySignature, false);
    }

    /**
     * <p> Pre-install one or more modules to an arbitrary destination
     * directory. </p>
     *
     * <p> A pre-installed module has the same format as within the library
     * itself, except that there is never a configuration file. </p>
     *
     * <p> This method is provided for use by the module-packaging tool. </p>
     *
     * @param   mfs
     *          The manifest describing the contents of the modules to be
     *          pre-installed
     *
     * @param   dst
     *          The destination directory, with one subdirectory per module
     *          name, each of which contains one subdirectory per version
     */
    public void preInstall(Collection<Manifest> mfs, File dst)
        throws IOException
    {
        Files.mkdirs(dst, "module destination");
        install(mfs, dst, false);
    }

    public void preInstall(Manifest mf, File dst)
        throws IOException
    {
        preInstall(Collections.singleton(mf), dst);
    }

    /**
     * <p> Update the configurations of any root modules affected by the
     * copying of the named modules, in pre-installed format, into this
     * library. </p>
     *
     * @param   mids
     *          The module ids of the new or updated modules, or
     *          {@code null} if the configuration of every root module
     *          should be (re)computed
     */
    public void configure(List<ModuleId> mids)
        throws ConfigurationException, IOException
    {
        // ## mids not used yet
        List<ModuleId> roots = new ArrayList<>();
        for (ModuleView mv : listLocalRootModuleViews()) {
            // each module can have multiple entry points
            // only configure once for each module.
            if (!roots.contains(mv.moduleInfo().id()))
                roots.add(mv.moduleInfo().id());
        }

        for (ModuleId mid : roots) {
            // ## We could be a lot more clever about this!
            Configuration<Context> cf
                = Configurator.configure(this, mid.toQuery());
            new StoredConfiguration(findModuleDir(mid), cf).store();
        }
    }

    public URI findLocalResource(ModuleId mid, String name)
        throws IOException
    {
        return locateContent(mid, name);
    }

    public File findLocalNativeLibrary(ModuleId mid, String name)
        throws IOException
    {
        File f = natlibs();
        if (f == null) {
            f = findModuleDir(mid);
            if (f == null)
                return null;
            f = new File(f, "lib");
        }
        f = new File(f, name);
        if (!f.exists())
            return null;
        return f;
    }

    public File classPath(ModuleId mid)
        throws IOException
    {
        File md = findModuleDir(mid);
        if (md == null) {
            if (parent != null)
                return parent.classPath(mid);
            return null;
        }
        // ## Check for other formats here
        return new File(md, "classes");
    }

    /**
     * <p> Re-index the classes of the named previously-installed modules, and
     * then update the configurations of any affected root modules. </p>
     *
     * <p> This method is intended for use during development, when a build
     * process may update a previously-installed module in place, adding or
     * removing classes. </p>
     *
     * @param   mids
     *          The module ids of the new or updated modules, or
     *          {@code null} if the configuration of every root module
     *          should be (re)computed
     */
    public void reIndex(List<ModuleId> mids)
        throws ConfigurationException, IOException
    {
        for (ModuleId mid : mids)
            reIndex(mid);
        configure(mids);
    }


    // -- Repositories --

    private static class RepoList
        implements RemoteRepositoryList
    {

        private static final int MINOR_VERSION = 0;
        private static final int MAJOR_VERSION = 0;

        private final File root;
        private final File listFile;

        private RepoList(File r) {
            root = new File(r, FileConstants.META_PREFIX + "repos");
            listFile = new File(root, FileConstants.META_PREFIX + "list");
        }

        private static FileHeader fileHeader() {
            return (new FileHeader()
                    .type(FileConstants.Type.REMOTE_REPO_LIST)
                    .majorVersion(MAJOR_VERSION)
                    .minorVersion(MINOR_VERSION));
        }

        private List<RemoteRepository> repos = null;
        private long nextRepoId = 0;

        private File repoDir(long id) {
            return new File(root, Long.toHexString(id));
        }

        private void load() throws IOException {

            repos = new ArrayList<>();
            if (!root.exists() || !listFile.exists())
                return;
            FileInputStream fin = new FileInputStream(listFile);
            DataInputStream in
                = new DataInputStream(new BufferedInputStream(fin));
            try {

                FileHeader fh = fileHeader();
                fh.read(in);
                nextRepoId = in.readLong();
                int n = in.readInt();
                long[] ids = new long[n];
                for (int i = 0; i < n; i++)
                    ids[i] = in.readLong();
                RemoteRepository parent = null;

                // Load in reverse order so that parents are correct
                for (int i = n - 1; i >= 0; i--) {
                    long id = ids[i];
                    RemoteRepository rr
                        = RemoteRepository.open(repoDir(id), id, parent);
                    repos.add(rr);
                    parent = rr;
                }
                Collections.reverse(repos);

            } finally {
                in.close();
            }

        }

        private List<RemoteRepository> roRepos = null;

        // Unmodifiable
        public List<RemoteRepository> repositories() throws IOException {
            if (repos == null) {
                load();
                roRepos = Collections.unmodifiableList(repos);
            }
            return roRepos;
        }

        public RemoteRepository firstRepository() throws IOException {
            repositories();
            return repos.isEmpty() ? null : repos.get(0);
        }

        private void store() throws IOException {
            File newfn = new File(root, "list.new");
            FileOutputStream fout = new FileOutputStream(newfn);
            DataOutputStream out
                = new DataOutputStream(new BufferedOutputStream(fout));
            try {
                try {
                    fileHeader().write(out);
                    out.writeLong(nextRepoId);
                    out.writeInt(repos.size());
                    for (RemoteRepository rr : repos)
                        out.writeLong(rr.id());
                } finally {
                    out.close();
                }
            } catch (IOException x) {
                newfn.delete();
                throw x;
            }
            java.nio.file.Files.move(newfn.toPath(), listFile.toPath(), ATOMIC_MOVE);
        }

        public RemoteRepository add(URI u, int position)
            throws IOException
        {

            if (repos == null)
                load();
            for (RemoteRepository rr : repos) {
                if (rr.location().equals(u)) // ## u not canonical
                    throw new IllegalStateException(u + ": Already in"
                                                    + " repository list");
            }
            if (!root.exists()) {
                if (!root.mkdir())
                    throw new IOException(root + ": Cannot create directory");
            }

            if (repos.size() == Integer.MAX_VALUE)
                throw new IllegalStateException("Too many repositories");
            if (position < 0)
                throw new IllegalArgumentException("Invalid index");

            long id = nextRepoId++;
            RemoteRepository rr = RemoteRepository.create(repoDir(id), u, id);
            try {
                rr.updateCatalog(true);
            } catch (IOException x) {
                rr.delete();
                nextRepoId--;
                throw x;
            }

            if (position >= repos.size()) {
                repos.add(rr);
            } else if (position >= 0) {
                List<RemoteRepository> prefix
                    = new ArrayList<>(repos.subList(0, position));
                List<RemoteRepository> suffix
                    = new ArrayList<>(repos.subList(position, repos.size()));
                repos.clear();
                repos.addAll(prefix);
                repos.add(rr);
                repos.addAll(suffix);
            }
            store();

            return rr;

        }

        public boolean remove(RemoteRepository rr)
            throws IOException
        {
            if (!repos.remove(rr))
                return false;
            store();
            File rd = repoDir(rr.id());
            for (File f : rd.listFiles()) {
                if (!f.delete())
                    throw new IOException(f + ": Cannot delete");
            }
            if (!rd.delete())
                throw new IOException(rd + ": Cannot delete");
            return true;
        }

        public boolean areCatalogsStale() throws IOException {
            for (RemoteRepository rr : repos) {
                if (rr.isCatalogStale())
                    return true;
            }
            return false;
        }

        public boolean updateCatalogs(boolean force) throws IOException {
            boolean updated = false;
            for (RemoteRepository rr : repos) {
                if (rr.updateCatalog(force))
                    updated = true;
            }
            return updated;
        }

    }

    private RemoteRepositoryList repoList = null;

    public RemoteRepositoryList repositoryList()
        throws IOException
    {
        if (repoList == null)
            repoList = new RepoList(root);
        return repoList;
    }

}
