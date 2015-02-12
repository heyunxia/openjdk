/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

import static java.nio.file.StandardCopyOption.*;
import static java.nio.file.StandardOpenOption.*;
import org.openjdk.jigsaw.Repository.ModuleType;

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
     * <p> Storage options supported by the {@link SimpleLibrary}
     */
    public static enum StorageOption {
        DEFLATED
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
    private final ModuleDictionary moduleDictionary;
    private final File lockf;
    private final File trash;

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

        lockf = new File(root, FileConstants.META_PREFIX + "lock");
        trash = new File(root, TRASH);
        moduleDictionary = new ModuleDictionary(root);
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

        lockf = new File(root, FileConstants.META_PREFIX + "lock");
        lockf.createNewFile();
        trash = new File(root, TRASH);
        Files.mkdirs(trash, "module library trash");
        moduleDictionary = new ModuleDictionary(canonicalRoot);
        moduleDictionary.store();
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

            // Context names and package names
            // Store these strings only once and the subsequent sections will
            // reference these names by its index.
            List<String> cxns = new ArrayList<>();
            Set<String> pkgs = new HashSet<>();
            for (Context cx : cf.contexts()) {
                String cxn = cx.name();
                cxns.add(cxn);
                pkgs.addAll(cx.remotePackages());
                for (String cn : cx.localClasses()) {
                    int i = cn.lastIndexOf('.');
                    if (i >= 0)
                        pkgs.add(cn.substring(0, i));
                }
            }
            List<String> packages = Arrays.asList(pkgs.toArray(new String[0]));
            Collections.sort(packages);
            out.writeInt(cf.contexts().size());
            for (String cxn : cxns) {
                out.writeUTF(cxn);
            }
            out.writeInt(packages.size());
            for (String pn : packages) {
                out.writeUTF(pn);
            }

            // Contexts
            for (Context cx : cf.contexts()) {
                // Module ids, and their libraries
                out.writeInt(cx.modules().size());
                List<ModuleId> mids = new ArrayList<>(cx.modules());
                for (ModuleId mid : mids) {
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
                    String cn = me.getKey();
                    int i = cn.lastIndexOf('.');
                    if (i == -1) {
                        out.writeInt(-1);
                        out.writeUTF(cn);
                    } else {
                        String pn = cn.substring(0, i);
                        assert packages.contains(pn);
                        out.writeInt(packages.indexOf(pn));
                        out.writeUTF(cn.substring(i+1, cn.length()));
                    }
                    assert mids.contains(me.getValue());
                    out.writeInt(mids.indexOf(me.getValue()));
                }

                // Remote package map
                out.writeInt(cx.contextForRemotePackageMap().size());
                for (Map.Entry<String,String> me
                         : cx.contextForRemotePackageMap().entrySet()) {
                    assert packages.contains(me.getKey()) && cxns.contains(me.getValue());
                    out.writeInt(packages.indexOf(me.getKey()));
                    out.writeInt(cxns.indexOf(me.getValue()));
                }

                // Suppliers
                out.writeInt(cx.remoteContexts().size());
                for (String cxn : cx.remoteContexts()) {
                    assert cxns.contains(cxn);
                    out.writeInt(cxns.indexOf(cxn));
                }

                // Local service implementations
                Map<String,Set<String>> services = cx.services();
                out.writeInt(services.size());
                for (Map.Entry<String,Set<String>> me: services.entrySet()) {
                    out.writeUTF(me.getKey());
                    Set<String> values = me.getValue();
                    out.writeInt(values.size());
                    for (String value: values) {
                        out.writeUTF(value);
                    }
                }
            }
        }

        // NOTE: jigsaw.c load_config is the native implementation of this method.
        // Any change to the format of StoredConfiguration should be reflectd in
        // both native and Java implementation
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
            cf = new Configuration<>(roots);

            // Context names
            int nContexts = in.readInt();
            List<String> contexts = new ArrayList<>(nContexts);
            for (int i = 0; i < nContexts; i++) {
                contexts.add(in.readUTF());
            }

            // Package names
            int nPkgs = in.readInt();
            List<String> packages = new ArrayList<>(nPkgs);
            for (int i = 0; i < nPkgs; i++) {
                packages.add(in.readUTF());
            }

            // Contexts
            for (String cxn : contexts) {
                Context cx = new Context();
                // Module ids
                int nModules = in.readInt();
                List<ModuleId> mids = new ArrayList<>(nModules);
                for (int j = 0; j < nModules; j++) {
                    ModuleId mid = jms.parseModuleId(in.readUTF());
                    mids.add(mid);
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
                for (int j = 0; j < nClasses; j++) {
                    int idx = in.readInt();
                    String name = in.readUTF();
                    String cn = (idx == -1) ? name : packages.get(idx) + "." + name;
                    ModuleId mid = mids.get(in.readInt());
                    cx.putModuleForLocalClass(cn, mid);
                }
                // Remote package map
                int nPackages = in.readInt();
                for (int j = 0; j < nPackages; j++) {
                    String pn = packages.get(in.readInt());
                    String rcxn = contexts.get(in.readInt());
                    cx.putContextForRemotePackage(pn, rcxn);
                }
                // Suppliers
                int nSuppliers = in.readInt();
                for (int j = 0; j < nSuppliers; j++) {
                    String rcxn = contexts.get(in.readInt());
                    cx.addSupplier(rcxn);
                }
                // Local service implementations
                int nServices = in.readInt();
                for (int j = 0; j < nServices; j++) {
                    String sn = in.readUTF();
                    int nImpl = in.readInt();
                    for (int k = 0; k < nImpl; k++) {
                        String cn = in.readUTF();
                        cx.putService(sn, cn);
                    }
                }
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

        private static final String FILE = "signer";
        private static final int MAJOR_VERSION = 0;
        private static final int MINOR_VERSION = 1;
        private static final String ENCODING = "PkiPath";

        private CertificateFactory cf;
        private Set<CodeSigner> signers;
        private Set<CodeSigner> signers() { return signers; }

        private Signers(File root, Set<CodeSigner> signers) {
            super(MAJOR_VERSION, MINOR_VERSION,
                  FileConstants.Type.LIBRARY_MODULE_SIGNER,
                  new File(root, FILE));
            this.signers = signers;
        }

        @Override
        protected void storeRest(DataOutputStream out)
            throws IOException
        {
            out.writeInt(signers.size());
            for (CodeSigner signer : signers) {
                try {
                    CertPath signerCertPath = signer.getSignerCertPath();
                    out.write(signerCertPath.getEncoded(ENCODING));
                    Timestamp ts = signer.getTimestamp();
                    if (ts != null) {
                        out.writeByte(1);
                        out.writeLong(ts.getTimestamp().getTime());
                        out.write(ts.getSignerCertPath().getEncoded(ENCODING));
                    } else {
                        out.writeByte(0);
                    }
                } catch (CertificateEncodingException cee) {
                    throw new IOException(cee);
                }
            }
        }

        @Override
        protected void loadRest(DataInputStream in)
            throws IOException
        {
            int size = in.readInt();
            for (int i = 0; i < size; i++) {
                try {
                    if (cf == null)
                        cf = CertificateFactory.getInstance("X.509");
                    CertPath signerCertPath = cf.generateCertPath(in, ENCODING);
                    int b = in.readByte();
                    if (b != 0) {
                        Date timestamp = new Date(in.readLong());
                        CertPath tsaCertPath = cf.generateCertPath(in, ENCODING);
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

    protected void gatherLocalModuleIds(String moduleName,
                                        Set<ModuleId> mids)
        throws IOException
    {
        moduleDictionary.gatherLocalModuleIds(moduleName, mids);
    }

    protected void gatherLocalDeclaringModuleIds(Set<ModuleId> mids)
        throws IOException
    {
        mids.addAll(moduleDictionary.modules());
    }

    private void checkModuleId(ModuleId mid) {
        Version v = mid.version();
        if (v == null)
            return;
        if (!(v instanceof JigsawVersion))
            throw new IllegalArgumentException(mid + ": Not a Jigsaw module id");
    }

    private static File moduleDir(File root, ModuleId mid) {
        Version v = mid.version();
        String vs = (v != null) ? v.toString() : "default";
        return new File(new File(root, mid.name()), vs);
    }

    private static void checkModuleDir(File md)
        throws IOException
    {
        if (!md.isDirectory())
            throw new IOException(md + ": Not a directory");
        if (!md.canRead())
            throw new IOException(md + ": Not readable");
    }

    private File preinstallModuleDir(File dst, ModuleInfo mi) throws IOException {
        File md = moduleDir(dst, mi.id());
        if (md.exists()) {
            Files.deleteTree(md);
        }
        if (!md.mkdirs()) {
            throw new IOException(md + ": Cannot create");
        }
        return md;
    }

    public byte[] readLocalModuleInfoBytes(ModuleId mid)
        throws IOException
    {
        File md = moduleDictionary.findDeclaringModuleDir(mid);
        if (md == null)
            return null;
        return Files.load(new File(md, "info"));
    }

    @Override
    public CodeSigner[] readLocalCodeSigners(ModuleId mid)
        throws IOException
    {
        File md = moduleDictionary.findDeclaringModuleDir(mid);
        if (md == null)
            return null;

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
        ModuleId dmid = moduleDictionary.getDeclaringModule(mid);
        Object o = contentForModule.get(dmid);
        if (o == NONE)
            return null;
        if (o != null)
            return o;
        File md = moduleDictionary.findDeclaringModuleDir(dmid);
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
        File md = moduleDictionary.findDeclaringModuleDir(mid);
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
        File md = moduleDictionary.findDeclaringModuleDir(mid);
        if (md == null) {
            if (parent != null) {
                return parent.readConfiguration(mid);
            }
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

        File md = moduleDictionary.findDeclaringModuleDir(mid);
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
            try (FileInputStream fis = new FileInputStream(cd);
                 ZipInputStream zis = new ZipInputStream(fis))
            {
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

    private ModuleId installWhileLocked(Manifest mf, File dst, boolean strip)
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

        try {
            File mdst;
            if (dst.equals(root)) {
                mdst = moduleDictionary.add(mi);
            } else {
                mdst = preinstallModuleDir(dst, mi);
            }
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
                    }
                });
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
                if (strip) {
                    strip(mdst);
                }
            }
        } catch (ConfigurationException x) {
            // module already exists
            throw new IOException(x);
        } catch (IOException x) {
            try {
                moduleDictionary.remove(mi);
            } catch (IOException y) {
                x.addSuppressed(y);
            }
            throw x;
        }
        return mi.id();
    }

    public void installFromManifests(Collection<Manifest> mfs, boolean strip)
        throws ConfigurationException, IOException
    {
        boolean complete = false;
        List<ModuleId> mids = new ArrayList<>();
        FileChannel fc = FileChannel.open(lockf.toPath(), WRITE);
        try {
            fc.lock();
            moduleDictionary.load();
            for (Manifest mf : mfs) {
                mids.add(installWhileLocked(mf, root, strip));
            }
            configureWhileModuleDirectoryLocked(null);
            complete = true;
        } catch (ConfigurationException | IOException x) {
            try {
                for (ModuleId mid : mids) {
                    ModuleInfo mi = readLocalModuleInfo(mid);
                    if (mi != null) {
                        moduleDictionary.remove(mi);
                    }
                }
            } catch (IOException y) {
                x.addSuppressed(y);
            }
            throw x;
        } finally {
            if (complete) {
                moduleDictionary.store();
            }
            fc.close();
        }
    }

    @Override
    public void installFromManifests(Collection<Manifest> mfs)
        throws ConfigurationException, IOException
    {
        installFromManifests(mfs, false);
    }

    private ModuleId installWhileLocked(ModuleType type, InputStream is, boolean verifySignature,
                                        boolean strip)
        throws ConfigurationException, IOException, SignatureException
    {
        switch (type) {
            case JAR:
                Path jf = java.nio.file.Files.createTempFile(null, null);
                try {
                    java.nio.file.Files.copy(is, jf, StandardCopyOption.REPLACE_EXISTING);
                    return installFromJarFile(jf.toFile(), verifySignature, strip);
                } finally {
                    java.nio.file.Files.delete(jf);
                }
            case JMOD:
            default:
                return installWhileLocked(is, verifySignature, strip);
        }
    }

    private ModuleId installWhileLocked(InputStream is, boolean verifySignature,
                                        boolean strip)
        throws ConfigurationException, IOException, SignatureException
    {
        BufferedInputStream bin = new BufferedInputStream(is);
        DataInputStream in = new DataInputStream(bin);
        ModuleInfo mi = null;
        try (ModuleFile.Reader mr = new ModuleFile.Reader(in)) {
            ModuleInfo moduleInfo = jms.parseModuleInfo(mr.getModuleInfoBytes());
            File md = moduleDictionary.add(moduleInfo);
            mi = moduleInfo;
            if (verifySignature && mr.hasSignature()) {
                // Verify the module signature
                SignedModule sm = new SignedModule(mr);
                Set<CodeSigner> signers = sm.verifySignature();

                // Validate the signers
                try {
                    SignedModule.validateSigners(signers);
                } catch (CertificateException x) {
                    throw new SignatureException(x);
                }

                // ## TODO: Check policy and determine if signer is trusted
                // ## and what permissions should be granted.
                // ## If there is no policy entry, show signers and prompt
                // ## user to accept before proceeding.

                // Verify the module header hash and the module info hash
                sm.verifyHashesStart();

                // Extract remainder of the module file, and calculate hashes
                mr.extractTo(md, isDeflated(), natlibs(), natcmds(), configs());

                // Verify the rest of the hashes
                sm.verifyHashesRest();

                // Store signer info
                new Signers(md, signers).store();
            } else {
                mr.extractTo(md, isDeflated(), natlibs(), natcmds(), configs());
            }

            if (strip)
                strip(md);
            reIndex(mi.id());         // ## Could do this while reading module file

            return mi.id();

        } catch (ConfigurationException | IOException | SignatureException |
                 ModuleFileParserException x) { // ## should we catch Throwable
            if (mi != null) {
                try {
                    moduleDictionary.remove(mi);
                } catch (IOException y) {
                    x.addSuppressed(y);
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
            ModuleInfo moduleInfo = jf.getModuleInfo();
            if (moduleInfo == null)
                throw new ConfigurationException(mf + ": not a modular JAR file");

            File md = moduleDictionary.add(moduleInfo);
            mi = moduleInfo;
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

            return mid;
        } catch (ConfigurationException | IOException | SignatureException x) {
            if (mi != null) {
                try {
                    moduleDictionary.remove(mi);
                } catch (IOException y) {
                    x.addSuppressed(y);
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
            else if (signers.retainAll(jeSignerSet) && signers.isEmpty())
                throw new SignatureException("No signers in common in "
                                             + "signed modular JAR");
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

    private ModuleId installWhileLocked(File mf, boolean verifySignature, boolean strip)
        throws ConfigurationException, IOException, SignatureException
    {
        if (mf.getName().endsWith(".jar"))
            return installFromJarFile(mf, verifySignature, strip);
        else {
            // Assume jmod file
            try (FileInputStream in = new FileInputStream(mf)) {
                return installWhileLocked(in, verifySignature, strip);
            }
        }
    }

    public void install(Collection<File> mfs, boolean verifySignature, boolean strip)
        throws ConfigurationException, IOException, SignatureException
    {
        List<ModuleId> mids = new ArrayList<>();
        boolean complete = false;
        FileChannel fc = FileChannel.open(lockf.toPath(), WRITE);
        try {
            fc.lock();
            moduleDictionary.load();
            for (File mf : mfs)
                mids.add(installWhileLocked(mf, verifySignature, strip));
            configureWhileModuleDirectoryLocked(mids);
            complete = true;
        } catch (ConfigurationException | IOException | SignatureException |
                 ModuleFileParserException x) {  // ## catch throwable??
            try {
                for (ModuleId mid : mids) {
                    ModuleInfo mi = readLocalModuleInfo(mid);
                    if (mi != null) {
                        moduleDictionary.remove(mi);
                    }
                }
            } catch (IOException y) {
                x.addSuppressed(y);
            }
            throw x;
        } finally {
            if (complete) {
                moduleDictionary.store();
            }
            fc.close();
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
    @Override
    public Resolution resolve(Collection<ModuleIdQuery> midqs)
        throws ConfigurationException, IOException
    {
        try (FileChannel fc = FileChannel.open(lockf.toPath(), WRITE)) {
            fc.lock();
            return Resolver.run(this, midqs);
        }
    }

    public void install(Resolution res, boolean verifySignature, boolean strip)
        throws ConfigurationException, IOException, SignatureException
    {
        boolean complete = false;
        FileChannel fc = FileChannel.open(lockf.toPath(), WRITE);
        try {
            fc.lock();
            moduleDictionary.load();

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
                installWhileLocked(rr.fetchMetaData(mid).getType(),
                                   rr.fetch(mid),
                                   verifySignature,
                                   strip);
                res.locationForName.put(mid.name(), location());
                // ## If something goes wrong, delete all our modules
            }

            // Configure
            //
            configureWhileModuleDirectoryLocked(res.modulesNeeded());
            complete = true;
        } catch (ConfigurationException | IOException | SignatureException |
                 ModuleFileParserException x) {  // ## catch throwable??
            try {
                for (ModuleId mid : res.modulesNeeded()) {
                    ModuleInfo mi = readLocalModuleInfo(mid);
                    if (mi != null) {
                        moduleDictionary.remove(mi);
                    }
                }
            } catch (IOException y) {
                x.addSuppressed(y);
            }
            throw x;
        } finally {
            if (complete) {
                moduleDictionary.store();
            }
            fc.close();
        }
    }

    @Override
    public void install(Resolution res, boolean verifySignature)
        throws ConfigurationException, IOException, SignatureException
    {
        install(res, verifySignature, false);
    }

    @Override
    public void removeForcibly(List<ModuleId> mids)
        throws IOException
    {
        try {
            remove(mids, true, false);
        } catch (ConfigurationException x) {
            throw new Error("should not be thrown when forcibly removing", x);
        }
    }

    @Override
    public void remove(List<ModuleId> mids, boolean dry)
        throws ConfigurationException, IOException
    {
        remove(mids, false,  dry);
    }

    private void remove(List<ModuleId> mids, boolean force, boolean dry)
        throws ConfigurationException, IOException
    {
        IOException ioe = null;

        try (FileChannel fc = FileChannel.open(lockf.toPath(), WRITE)) {
            fc.lock();
            for (ModuleId mid : mids) {
                // ## Should we support alias and/or non-default view names??
                if (moduleDictionary.findDeclaringModuleDir(mid) == null)
                    throw new IllegalArgumentException(mid + ": No such module");
            }
            if (!force)
                ensureNotInConfiguration(mids);
            if (dry)
                return;

            // The library may be altered after this point, so the modules
            // dictionary needs to be refreshed
            List<IOException> excs = removeWhileLocked(mids);
            try {
                moduleDictionary.refresh();
                moduleDictionary.store();
            } catch (IOException x) {
                excs.add(x);
            }
            if (!excs.isEmpty()) {
                ioe = excs.remove(0);
                for (IOException x : excs)
                    ioe.addSuppressed(x);
            }
        } finally {
            if (ioe != null)
                throw ioe;
        }
    }

    private void ensureNotInConfiguration(List<ModuleId> mids)
        throws ConfigurationException, IOException
    {
        // ## We do not know if a root module in a child library depends on one
        // ## of the 'to be removed' modules. We would break it's configuration.

        // check each root configuration for reference to a module in mids
        for (ModuleId rootid : libraryRoots()) {
            // skip any root modules being removed
            if (mids.contains(rootid))
                continue;

            Configuration<Context> cf = readConfiguration(rootid);
            for (Context cx : cf.contexts()) {
                for (ModuleId mid : cx.modules()) {
                    if (mids.contains(mid))
                        throw new ConfigurationException(mid +
                                ": being used by " + rootid);
                }
            }
        }
    }

    private static final String TRASH = ".trash";
    // lazy initialization of Random
    private static class LazyInitialization {
        static final Random random = new Random();
    }
    private static Path moduleTrashDir(File trash, ModuleId mid)
        throws IOException
    {
        String mn = mid.name();
        Version version = mid.version();
        String v = (version != null) ? version.toString() : "default";
        for (;;) {
            long n = LazyInitialization.random.nextLong();
            n = (n == Long.MIN_VALUE) ? 0 : Math.abs(n);
            String modTrashName = mn + '_' + v + '_' + Long.toString(n);
            File mtd = new File(trash, modTrashName);
            if (!mtd.exists())
                return mtd.toPath();
        }
    }

    private List<IOException> removeWhileLocked(List<ModuleId> mids) {
        List<IOException> excs = new ArrayList<>();
        // First move the modules to the .trash dir
        for (ModuleId mid : mids) {
            try {
                File md = moduleDir(root, mid);
                java.nio.file.Files.move(md.toPath(),
                                         moduleTrashDir(trash, mid),
                                         ATOMIC_MOVE);
                File p = md.getParentFile();
                if (p.list().length == 0)
                    java.nio.file.Files.delete(p.toPath());
            } catch (IOException x) {
                excs.add(x);
            }
        }
        for (String tm : trash.list())
            excs.addAll(ModuleFile.Reader.remove(new File(trash, tm)));

        return excs;
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
        try (FileChannel fc = FileChannel.open(lockf.toPath(), WRITE)) {
            fc.lock();
            for (Manifest mf : mfs) {
                installWhileLocked(mf, dst, false);
            }
            // no update to the module directory
        }
    }

    public void preInstall(Manifest mf, File dst)
        throws IOException
    {
        preInstall(Collections.singleton(mf), dst);
    }

    /**
     * Refresh the module library.
     */
    public void refresh() throws IOException {
        try (FileChannel fc = FileChannel.open(lockf.toPath(), WRITE)) {
            fc.lock();
            moduleDictionary.refresh();
            moduleDictionary.store();
        }
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
    public void configure(Collection<ModuleId> mids)
        throws ConfigurationException, IOException
    {
        try (FileChannel fc = FileChannel.open(lockf.toPath(), WRITE)) {
            fc.lock();
            configureWhileModuleDirectoryLocked(mids);
        }
    }

    private void configureWhileModuleDirectoryLocked(Collection<ModuleId> mids)
        throws ConfigurationException, IOException
    {
        // ## mids not used yet
        for (ModuleId mid : libraryRoots()) {
            // ## We could be a lot more clever about this!
            Configuration<Context> cf
                = Configurator.configure(this, mid.toQuery());
            File md = moduleDictionary.findDeclaringModuleDir(mid);
            new StoredConfiguration(md, cf).store();
        }
    }

    private List<ModuleId> libraryRoots()
        throws IOException
    {
        List<ModuleId> roots = new ArrayList<>();
        for (ModuleId mid : listLocalDeclaringModuleIds()) {
            // each module can have multiple entry points, but
            // only one configuration for each module.
            ModuleInfo mi = readModuleInfo(mid);
            for (ModuleView mv : mi.views()) {
                if (mv.mainClass() != null) {
                    roots.add(mid);
                    break;
                }
            }
        }
        return roots;
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
            f = moduleDictionary.findDeclaringModuleDir(mid);
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
        File md = moduleDictionary.findDeclaringModuleDir(mid);
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

    private static final class ModuleDictionary
    {
        private static final String FILE
            = FileConstants.META_PREFIX + "mids";

        private final File root;
        private final File file;
        private Map<String,Set<ModuleId>> moduleIdsForName;
        private Map<ModuleId,ModuleId> providingModuleIds;
        private Set<ModuleId> modules;
        private long lastUpdated;

        ModuleDictionary(File root) {
            this.root = root;
            this.file = new File(root, FILE);
            this.providingModuleIds = new LinkedHashMap<>();
            this.moduleIdsForName = new LinkedHashMap<>();
            this.modules = new HashSet<>();
            this.lastUpdated = -1;
        }

        private static FileHeader fileHeader() {
            return (new FileHeader()
                    .type(FileConstants.Type.LIBRARY_MODULE_IDS)
                    .majorVersion(Header.MAJOR_VERSION)
                    .minorVersion(Header.MINOR_VERSION));
        }

        void load() throws IOException {
            if (lastUpdated == file.lastModified())
                return;

            providingModuleIds = new LinkedHashMap<>();
            moduleIdsForName = new LinkedHashMap<>();
            modules = new HashSet<>();
            lastUpdated = file.lastModified();

            try (FileInputStream fin = new FileInputStream(file);
                 DataInputStream in = new DataInputStream(new BufferedInputStream(fin)))
            {
                FileHeader fh = fileHeader();
                fh.read(in);
                int nMids = in.readInt();
                for (int j = 0; j < nMids; j++) {
                    ModuleId mid = jms.parseModuleId(in.readUTF());
                    ModuleId pmid = jms.parseModuleId(in.readUTF());
                    providingModuleIds.put(mid, pmid);
                    addModuleId(mid);
                    addModuleId(pmid);
                    if (mid.equals(pmid))
                        modules.add(mid);
                }
            }
        }

        void store() throws IOException {
            File newfn = new File(root, "mids.new");
            FileOutputStream fout = new FileOutputStream(newfn);
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(fout));
            try {
                try {
                    fileHeader().write(out);
                    out.writeInt(providingModuleIds.size());
                    for (Map.Entry<ModuleId, ModuleId> e : providingModuleIds.entrySet()) {
                        out.writeUTF(e.getKey().toString());
                        out.writeUTF(e.getValue().toString());
                    }
                } finally {
                    out.close();
                }
            } catch (IOException x) {
                newfn.delete();
                throw x;
            }
            java.nio.file.Files.move(newfn.toPath(), file.toPath(), ATOMIC_MOVE);
        }

        void gatherLocalModuleIds(String moduleName, Set<ModuleId> mids)
                throws IOException
        {
            if (lastUpdated != file.lastModified())
                load();

            if (moduleName == null) {
                mids.addAll(providingModuleIds.keySet());
            } else {
                Set<ModuleId> res = moduleIdsForName.get(moduleName);
                if (res != null)
                    mids.addAll(res);
            }
        }

        ModuleId getDeclaringModule(ModuleId mid) throws IOException {
            if (lastUpdated != file.lastModified())
                load();

            ModuleId pmid = providingModuleIds.get(mid);
            if (pmid != null && !pmid.equals(providingModuleIds.get(pmid))) {
                // mid is an alias
                pmid = providingModuleIds.get(pmid);
            }
            return pmid;
        }

        File findDeclaringModuleDir(ModuleId mid)
                throws IOException
        {
            ModuleId dmid = getDeclaringModule(mid);
            if (dmid == null)
                return null;

            File md = moduleDir(root, dmid);
            assert md.exists();
            checkModuleDir(md);
            return md;
        }

        Set<ModuleId> modules() throws IOException {
            if (lastUpdated != file.lastModified())
                load();
            return modules;
        }

        void addModuleId(ModuleId mid) {
            Set<ModuleId> mids = moduleIdsForName.get(mid.name());
            if (mids == null) {
                mids = new HashSet<>();
                moduleIdsForName.put(mid.name(), mids);
            }
            mids.add(mid);
        }

        File add(ModuleInfo mi)
                throws ConfigurationException, IOException
        {
            File md = ensureNewModule(mi);
            addToDirectory(mi);
            return md;
        }

        private void addToDirectory(ModuleInfo mi) {
            modules.add(mi.id());
            for (ModuleView view : mi.views()) {
                providingModuleIds.put(view.id(), mi.id());
                addModuleId(view.id());
                for (ModuleId alias : view.aliases()) {
                    providingModuleIds.put(alias, view.id());
                    addModuleId(alias);
                }
            }
        }

        void remove(ModuleInfo mi) throws IOException {
            modules.remove(mi.id());
            for (ModuleView view : mi.views()) {
                providingModuleIds.remove(view.id());
                Set<ModuleId> mids = moduleIdsForName.get(view.id().name());
                if (mids != null)
                    mids.remove(view.id());
                for (ModuleId alias : view.aliases()) {
                    providingModuleIds.remove(alias);
                    mids = moduleIdsForName.get(alias.name());
                    if (mids != null)
                        mids.remove(view.id());
                }
            }
            File md = moduleDir(root, mi.id());
            delete(md);
        }

        private void delete(File md) throws IOException {
            if (!md.exists())
                return;

            checkModuleDir(md);
            ModuleFile.Reader.remove(md);
            File parent = md.getParentFile();
            if (parent.list().length == 0)
                parent.delete();
        }

        void refresh() throws IOException {
            providingModuleIds = new LinkedHashMap<>();
            moduleIdsForName = new LinkedHashMap<>();
            modules = new HashSet<>();

            try (DirectoryStream<Path> ds = java.nio.file.Files.newDirectoryStream(root.toPath())) {
                for (Path mnp : ds) {
                    String mn = mnp.toFile().getName();
                    if (mn.startsWith(FileConstants.META_PREFIX) ||
                        TRASH.equals(mn)) {
                        continue;
                    }

                    try (DirectoryStream<Path> mds = java.nio.file.Files.newDirectoryStream(mnp)) {
                        for (Path versionp : mds) {
                            File v = versionp.toFile();
                            if (!v.isDirectory()) {
                                throw new IOException(versionp + ": Not a directory");
                            }
                            modules.add(jms.parseModuleId(mn, v.getName()));
                        }
                    }
                }
            }
            for (ModuleId mid : modules) {
                byte[] bs = Files.load(new File(moduleDir(root, mid), "info"));
                ModuleInfo mi = jms.parseModuleInfo(bs);
                addToDirectory(mi);
            }
        }

        private File ensureNewModule(ModuleInfo mi)
                throws ConfigurationException, IOException
        {
            for (ModuleView view : mi.views()) {
                if (providingModuleIds.containsKey(view.id())) {
                    throw new ConfigurationException("module view " + view.id()
                            + " already installed");
                }
                for (ModuleId alias : view.aliases()) {
                    ModuleId mid = alias;
                    if (providingModuleIds.containsKey(mid)) {
                        throw new ConfigurationException("alias " + alias
                                + " already installed");
                    }
                }
            }
            File md = moduleDir(root, mi.id());
            if (md.exists()) {
                throw new ConfigurationException("module " + mi.id()
                        + " already installed");
            }
            if (!md.mkdirs()) {
                throw new IOException(md + ": Cannot create");
            }
            return md;
        }
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
