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
import java.nio.channels.*;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.lang.module.*;
import java.net.URI;

import org.openjdk.jigsaw.RepositoryCatalog.StreamedRepositoryCatalog;

import static java.lang.System.out;

import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.StandardCopyOption.*;

import static org.openjdk.jigsaw.FileConstants.ModuleFile;
import static org.openjdk.jigsaw.FileConstants.ModuleFile.HashType;
import static org.openjdk.jigsaw.FileConstants.ModuleFile.SectionType;
import static org.openjdk.jigsaw.FileConstants.ModuleFile.Compressor;
import static org.openjdk.jigsaw.RepositoryCatalog.Entry;


/**
 * <p> A local module repository, to which modules can be published </p>
 */

public class PublishedRepository
    extends Repository
{

    private static final JigsawModuleSystem jms
        = JigsawModuleSystem.instance();

    private final URI uri;
    private final Path path;

    private static final String CATALOG_FILE = "%catalog";
    private static final String LOCK_FILE = "%lock";

    private final Path catp;
    private final Path lockp;

    public String name() {
        return path.toString();
    }

    public String toString() {
        return name();
    }

    public URI location() {
        return uri;
    }

    private StreamedRepositoryCatalog loadCatalog()
        throws IOException
    {
        return RepositoryCatalog.load(catp.newInputStream(READ));
    }

    private void storeCatalogWhileLocked(StreamedRepositoryCatalog cat)
        throws IOException
    {
        Path newp = path.resolve(CATALOG_FILE + ".new");
        OutputStream os = newp.newOutputStream(WRITE, CREATE_NEW);
        try {
            cat.store(os);
        } finally {
            os.close();
        }
        try {
            newp.moveTo(catp, ATOMIC_MOVE, REPLACE_EXISTING);
        } catch (IOException x) {
            newp.deleteIfExists();
            throw x;
        }
    }

    private void storeCatalog(StreamedRepositoryCatalog cat)
        throws IOException
    {
        FileChannel lc = FileChannel.open(lockp, READ, WRITE);
        try {
            lc.lock();
            storeCatalogWhileLocked(cat);
        } finally {
            lc.close();
        }
    }

    private PublishedRepository(Path p, boolean create) throws IOException {
        path = p;
        uri = path.toUri();
        catp = path.resolve(CATALOG_FILE);
        lockp = path.resolve(LOCK_FILE);
        if (path.exists()) {
            loadCatalog();              // Just to validate
        } else if (create) {
            path.createDirectory();
            lockp.createFile();
            storeCatalog(RepositoryCatalog.load(null));
        } else {
            throw new NoSuchFileException(p.toString());
        }
    }

    public static PublishedRepository open(Path p, boolean create)
        throws IOException
    {
        return new PublishedRepository(p, create);
    }

    public static PublishedRepository open(File f, boolean create)
        throws IOException
    {
        return open(f.toPath(), create);
    }

    public PublishedRepository parent() { return null; }

    protected void gatherLocalModuleIds(String moduleName,
                                        Set<ModuleId> mids)
        throws IOException
    {
        RepositoryCatalog cat = loadCatalog();
        cat.gatherModuleIds(moduleName, mids);
    }

    protected ModuleInfo readLocalModuleInfo(ModuleId mid)
        throws IOException
    {
        RepositoryCatalog cat = loadCatalog();
        Entry e = cat.get(mid);
        if (e == null)
            throw new IllegalArgumentException(mid + ": No such module");
        return jms.parseModuleInfo(e.mibs);
    }

    private static MessageDigest getDigest(HashType ht) {
        try {
            return MessageDigest.getInstance(ht.algorithm());
        } catch (NoSuchAlgorithmException x) {
            throw new Error(ht + ": Unsupported hash type");
        }
    }

    private Entry readModuleBits(FileChannel fc) throws IOException {

        ByteBuffer bb = ByteBuffer.allocate(8192);
        fc.read(bb);
        bb.flip();

        FileHeader fh = (new FileHeader()
                         .type(FileConstants.Type.MODULE_FILE)
                         .majorVersion(ModuleFile.MAJOR_VERSION)
                         .minorVersion(ModuleFile.MINOR_VERSION));
        fh.read(new DataInputStream(new ByteArrayInputStream(bb.array())));

        // Skip
        bb.position(4                   // magic
                    + 2                 // type
                    + 2                 // major
                    + 2);               // minor

        long csize = bb.getLong();
        long usize = bb.getLong();
        bb.getShort();                  // sections

        // Hash
        HashType ht = HashType.valueOf(bb.getShort());
        int hl = bb.getShort();
        byte[] fhb = new byte[hl];
        bb.get(fhb);

        SectionType st = SectionType.valueOf(bb.getShort());
        Compressor ct = Compressor.valueOf(bb.getShort());
        int misize = bb.getInt();
        int subsections = bb.getShort();

        hl = bb.getShort();
        byte[] hb = new byte[hl];
        bb.get(hb);

        if (bb.remaining() < misize)
            throw new AssertionError("module-info buffer too small"); // ##

        byte[] mibs = new byte[misize];
        bb.get(mibs);

        MessageDigest md = getDigest(ht);
        md.update(mibs);
        byte[] rhb = md.digest();
        if (!Arrays.equals(hb, rhb))
            throw new IOException("Hash mismatch");

        return new Entry(mibs, csize, usize, ht, fhb);

    }

    private Entry readModuleBits(Path modp) throws IOException {
        FileChannel fc = FileChannel.open(modp, READ);
        try {
            return readModuleBits(fc);
        } finally {
            fc.close();
        }
    }

    private Path modulePath(ModuleId mid, String ext) {
        return path.resolve(mid.toString() + ext);
    }

    private Path modulePath(ModuleId mid) {
        return modulePath(mid, ".jmod");
    }

    public void publish(Path modp) throws IOException {

        Entry e = readModuleBits(modp);
        ModuleInfo mi = jms.parseModuleInfo(e.mibs);
        ModuleId mid = mi.id();

        FileChannel lc = FileChannel.open(lockp, READ, WRITE);
        try {
            lc.lock();

            // Update the module file first
            Path dstp = modulePath(mid);
            Path newp = modulePath(mid, ".jmod.new");
            try {
                modp.copyTo(newp, REPLACE_EXISTING);
                newp.moveTo(dstp, REPLACE_EXISTING, ATOMIC_MOVE);
            } catch (IOException x) {
                newp.deleteIfExists();
                throw x;
            }

            // Then update the catalog
            StreamedRepositoryCatalog cat = loadCatalog();
            cat.add(e);
            storeCatalogWhileLocked(cat);

        } finally {
            lc.close();
        }

    }

    public InputStream fetch(ModuleId mid) throws IOException {
        RepositoryCatalog cat = loadCatalog();
        Entry e = cat.get(mid);
        if (e == null)
            throw new IllegalArgumentException(mid + ": No such module");
        return modulePath(mid).newInputStream();
    }

    public ModuleSize sizeof(ModuleId mid) throws IOException {
        RepositoryCatalog cat = loadCatalog();
        Entry e = cat.get(mid);
        if (e == null)
            throw new IllegalArgumentException(mid + ": No such module");
        return new ModuleSize(e.csize, e.usize);
    }

    public boolean remove(ModuleId mid) throws IOException {

        FileChannel lc = FileChannel.open(lockp, READ, WRITE);
        try {
            lc.lock();

            // Update catalog first
            StreamedRepositoryCatalog cat = loadCatalog();
            if (!cat.remove(mid))
                return false;
            storeCatalogWhileLocked(cat);

            // Then remove the file
            modulePath(mid).delete();

        } finally {
            lc.close();
        }

        return true;

    }

    private <T> Set<T> del(Set<T> all, Set<T> todel) {
        Set<T> s = new HashSet<>(all);
        s.removeAll(todel);
        return s;
    }

    private void gatherModuleIdsFromDirectoryWhileLocked(Set<ModuleId> mids)
        throws IOException
    {
        DirectoryStream<Path> ds = path.newDirectoryStream();
        try {
            for (Path modp : ds) {
                String fn = modp.getName().toString();
                if (fn.endsWith(".jmod")) {
                    ModuleId mid
                        = jms.parseModuleId(fn.substring(0, fn.length() - 5));
                    mids.add(mid);
                }
            }
        } finally {
            ds.close();
        }
    }

    private static void msg(List<String> msgs, String fmt, Object ... args) {
        String msg = String.format(fmt, args);
        if (msgs != null)
            msgs.add(msg);
        else
            out.println(msg);
    }

    public boolean validate(List<String> msgs) throws IOException {

        int errors = 0;

        FileChannel lc = FileChannel.open(lockp, READ, WRITE);
        try {
            lc.lock();

            StreamedRepositoryCatalog cat = loadCatalog();
            Set<ModuleId> cmids = new HashSet<>();
            cat.gatherModuleIds(null, cmids);

            Set<ModuleId> fmids = new HashSet<>();
            gatherModuleIdsFromDirectoryWhileLocked(fmids);

            if (!cmids.equals(fmids)) {
                errors++;
                msg(msgs, "%s: Catalog and directory do not match",
                    path);
                if (!cmids.containsAll(fmids))
                    msg(msgs, "  Extra module files: %s", del(fmids, cmids));
                if (!fmids.containsAll(cmids))
                    msg(msgs, "  Extra catalog entries: %s", del(cmids, fmids));
            }

            cmids.retainAll(fmids);
            for (ModuleId mid : cmids) {
                byte[] cmibs = cat.readModuleInfoBytes(mid);
                Entry e = readModuleBits(modulePath(mid));
                if (!Arrays.equals(cmibs, e.mibs)) {
                    errors++;
                    msg(msgs, "%s: %s: Module-info files do not match",
                        path, mid);
                }
            }

        } finally {
            lc.close();
        }

        return errors == 0;

    }

    public void reCatalog() throws IOException {

        FileChannel lc = FileChannel.open(lockp, READ, WRITE);
        try {
            lc.lock();

            Set<ModuleId> mids = new HashSet<>();
            gatherModuleIdsFromDirectoryWhileLocked(mids);

            StreamedRepositoryCatalog cat = RepositoryCatalog.load(null);
            for (ModuleId mid : mids) {
                Entry e = readModuleBits(modulePath(mid));
                cat.add(e);
            }
            storeCatalogWhileLocked(cat);

        } finally {
            lc.close();
        }

    }

}
