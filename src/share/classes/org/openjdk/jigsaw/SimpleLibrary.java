/*
 * Copyright 2009-2010 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.net.URI;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

import static org.openjdk.jigsaw.Trace.*;


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
//                          signer

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

        private File parent;
        public File parent() { return parent; }

        private Header(File root, File p) {
            super(MAJOR_VERSION, MINOR_VERSION,
                  FileConstants.Type.LIBRARY_HEADER,
                  new File(root, FILE));
            this.parent = p;
        }

        private Header(File root) {
            this(root, null);
        }

        protected void storeRest(DataOutputStream out)
            throws IOException
        {
            out.writeByte((parent != null) ? 1 : 0);
            if (parent != null)
                out.writeUTF(parent.toString());
        }

        protected void loadRest(DataInputStream in)
            throws IOException
        {
            int b = in.readByte();
            if (b != 0)
                parent = new File(in.readUTF());
        }

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
    private File parentPath = null;
    private SimpleLibrary parent = null;
    private final Header hd;

    public String name() { return root.toString(); }
    public File root() { return canonicalRoot; }
    public int majorVersion() { return hd.majorVersion; }
    public int minorVersion() { return hd.minorVersion; }
    public SimpleLibrary parent() { return parent; }

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

    private SimpleLibrary(File path, boolean create, File parentPath)
        throws IOException
    {
        root = path;
        canonicalRoot = root.getCanonicalFile();
        if (root.exists()) {
            if (!root.isDirectory())
                throw new IOException(root + ": Exists but is not a directory");
            hd = Header.load(root);
            if (hd.parent() != null) {
                parent = open(hd.parent());
                parentPath = hd.parent();
            }
            return;
        }
        if (!create)
            throw new FileNotFoundException(root.toString());
        if (parentPath != null) {
            this.parent = open(parentPath);
            this.parentPath = this.parent.root();
        }
        if (!root.mkdirs())
            throw new IOException(root + ": Cannot create library directory");
        hd = new Header(canonicalRoot, this.parentPath);
        hd.store();
    }

    public static SimpleLibrary open(File path, boolean create, File parent)
        throws IOException
    {
        return new SimpleLibrary(path, create, parent);
    }

    public static SimpleLibrary open(File path, boolean create)
        throws IOException
    {
        // ## Should default parent to $JAVA_HOME/lib/modules
        return new SimpleLibrary(path, create, null);
    }

    public static SimpleLibrary open(File path)
        throws IOException
    {
        return new SimpleLibrary(path, false, null);
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
            assert cf.roots().size() == 1;
            out.writeUTF(cf.roots().iterator().next().toString());
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
            }
        }

        protected void loadRest(DataInputStream in)
            throws IOException
        {
            String root = in.readUTF();
            ModuleId rmid = jms.parseModuleId(root);
            cf = new Configuration<Context>(rmid);
            // Contexts
            int nContexts = in.readInt();
            for (int i = 0; i < nContexts; i++) {
                Context cx = new Context();
                String cxn = in.readUTF();
                // Module ids
                int nModules = in.readInt();
                for (int j = 0; j < nModules; j++) {
                    ModuleId mid = jms.parseModuleId(in.readUTF());
                    cx.add(mid);
                    cf.put(mid.name(), cx);
                    String lps = in.readUTF();
                    if (lps.length() > 0)
                        cx.putLibraryPathForModule(mid, new File(lps));
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

    private void deleteModuleDir(ModuleId mid)
        throws IOException
    {
        File md = findModuleDir(mid);
        if (md == null)
            return;
        Files.deleteTree(md);
        File mnd = md.getParentFile();
        if (mnd.list().length == 0) {
            if (!mnd.delete())
                throw new IOException(mnd + ": Cannot delete");
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

        // ## add support for multiple signers
        File f = new File(md, "signer");
        // ## concurrency issues : what is the expected behavior if file is
        // ## removed by another thread/process here?
        if (!f.exists())
            return null;
        InputStream is = new FileInputStream(f);
        ObjectInputStream ois = null;
        CodeSigner signer = null;
        try {
            ois = new ObjectInputStream(is);
            signer = (CodeSigner)ois.readObject();
        } catch (ClassNotFoundException cnfe) {
            throw new InternalError(cnfe.getMessage());
        } finally {
            if (ois != null)
                ois.close();
            else
                is.close();
        }
        return new CodeSigner[] {signer};
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
        assert false;
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
        } else {
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

    private void install(Manifest mf, File dst)
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
        File mdst = new File(new File(dst, m), vs);
        if (mdst.exists())
            Files.deleteTree(mdst);
        Files.mkdirs(mdst, "module");
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
                        return addToIndex(ClassInfo.read(f), ix);
                    }});
            ix.store();

            // Copy resources
            for (File rsrc : mf.resources())
                Files.copyTree(rsrc, cldst);

        } else {

            FileOutputStream fos
                = new FileOutputStream(new File(mdst, "classes"));
            JarOutputStream jos
                = new JarOutputStream(new BufferedOutputStream(fos));
            try {

                // Copy class files and build index
                final Index ix = new Index(mdst);
                Files.storeTree(src, jos, new Files.Filter<File>() {
                        public boolean accept(File f) throws IOException {
                            if (f.isDirectory())
                                return true;
                            return addToIndex(ClassInfo.read(f), ix);
                        }});
                ix.store();

                // Copy resources
                for (File rsrc : mf.resources())
                    Files.storeTree(rsrc, jos);

            } finally {
                jos.close();
            }

        }

    }

    private void install(Collection<Manifest> mfs, File dst)
        throws IOException
    {
        for (Manifest mf : mfs)
            install(mf, dst);
    }

    public void installFromManifests(Collection<Manifest> mfs)
        throws ConfigurationException, IOException
    {
        install(mfs, root);
        configure(null);
    }

    private ModuleId install(InputStream is)
        throws ConfigurationException, IOException, SignatureException
    {
        return install(is, new ModuleFileFormat.PKCS7Verifier(), null);
    }

    private ModuleId install(InputStream is,
                             ModuleFileVerifier verifier,
                             ModuleFileVerifier.Parameters parameters)
        throws ConfigurationException, IOException, SignatureException
    {
        BufferedInputStream bin = new BufferedInputStream(is);
        DataInputStream in = new DataInputStream(bin);
        ModuleFileFormat.Reader mr = new ModuleFileFormat.Reader(in);
        File md = null;
        try {
            byte[] mib = mr.readStart();
            ModuleInfo mi = jms.parseModuleInfo(mib);
            md = moduleDir(mi.id());
            ModuleId mid = mi.id();
            if (md.exists())
                throw new ConfigurationException(mid + ": Already installed");
            if (!md.mkdirs())
                throw new IOException(md + ": Cannot create");
            mr.setVerificationMechanism(verifier, parameters);
            Set<CodeSigner> signers = mr.verifySignature();
            // ## add support for storing multiple signers
            if (!signers.isEmpty()) {
                CodeSigner signer = signers.iterator().next();
                Files.store(signer, new File(md, "signer"));
            }
            mr.readRest(md);
            mr.verifyHashes();
            reIndex(mid);         // ## Could do this while reading module file
            return mid;

        } catch (IOException x) {
            if (md != null && md.exists()) {
                try {
                    Files.deleteTree(md);
                } catch (IOException y) {
                    y.initCause(x);
                    throw y;
                }
            }
            throw x;
        } catch (SignatureException x) {
            if (md != null && md.exists()) {
                try {
                    Files.deleteTree(md);
                } catch (IOException y) {
                    y.initCause(x);
                    throw y;
                }
            }
            throw x;
        } finally {
            mr.close();
        }
    }

    private ModuleId install(File mf)
        throws ConfigurationException, IOException, SignatureException
    {
        return install(new FileInputStream(mf));
    }

    public void install(Collection<File> mfs)
        throws ConfigurationException, IOException, SignatureException
    {
        List<ModuleId> mids = new ArrayList<>();
        boolean complete = false;
        Throwable ox = null;
        try {
            for (File mf : mfs)
                mids.add(install(mf));
            configure(mids);
            complete = true;
        } catch (IOException x) {
            ox = x;
            throw x;
        } catch (ConfigurationException x) {
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

    // Public entry point, since the Resolver itself is package-private
    //
    public Resolution resolve(Collection<ModuleIdQuery> midqs)
        throws ConfigurationException, IOException
    {
        return Resolver.run(this, midqs);
    }

    public void install(Resolution res)
        throws ConfigurationException, IOException, SignatureException
    {

        // ## Handle case of installing multiple root modules
        assert res.rootQueries.size() == 1;
        ModuleIdQuery midq = res.rootQueries.iterator().next();
        ModuleInfo root = null;
        for (ModuleInfo mi : res.modules) {
            if (midq.matches(mi.id())) {
                root = mi;
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
            install(rr.fetch(mid));
            res.locationForName.put(mid.name(), location());
            // ## If something goes wrong, delete all our modules
        }

        // Configure
        //
        Configuration<Context> cf
            = Configurator.configure(this, res);
        new StoredConfiguration(moduleDir(root.id()), cf).store();

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
        install(mfs, dst);
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
        for (ModuleInfo mi : listLocalRootModuleInfos()) {
            // ## We could be a lot more clever about this!
            Configuration<Context> cf
                = Configurator.configure(this, mi.id().toQuery());
            new StoredConfiguration(moduleDir(mi.id()), cf).store();
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
        File md = findModuleDir(mid);
        if (md == null)
            return null;
        File f = new File(new File(md, "lib"), name);
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
            if (!newfn.renameTo(listFile))  // ## Not guaranteed atomic
                throw new IOException(newfn + ": Cannot rename to " + listFile);
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
                repoDir(id).delete();
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
