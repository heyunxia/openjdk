/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.*;
import java.util.*;
import java.lang.module.*;
import java.net.URI;

import org.openjdk.jigsaw.ModuleFile.ModuleFileHeader;
import org.openjdk.jigsaw.RepositoryCatalog.Entry;
import org.openjdk.jigsaw.RepositoryCatalog.StreamedRepositoryCatalog;

import static java.lang.System.out;

import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.StandardCopyOption.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.openjdk.jigsaw.FileConstants.ModuleFile.HashType;


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

    @Override
    public String name() {
        return path.toString();
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public URI location() {
        return uri;
    }

    private StreamedRepositoryCatalog loadCatalog()
        throws IOException
    {
        return RepositoryCatalog.load(Files.newInputStream(catp, READ));
    }

    private void storeCatalogWhileLocked(StreamedRepositoryCatalog cat)
        throws IOException
    {
        Path newp = path.resolve(CATALOG_FILE + ".new");
        try (OutputStream os = Files.newOutputStream(newp, WRITE, CREATE_NEW)) {
            cat.store(os);
        }
        try {
            Files.move(newp, catp, ATOMIC_MOVE);
        } catch (IOException x) {
            Files.deleteIfExists(newp);
            throw x;
        }
    }

    private void storeCatalog(StreamedRepositoryCatalog cat)
        throws IOException
    {
        try (FileChannel lc = FileChannel.open(lockp, READ, WRITE)) {
            lc.lock();
            storeCatalogWhileLocked(cat);
        }
    }

    private PublishedRepository(Path p, boolean create) throws IOException {
        path = p;
        uri = path.toUri();
        catp = path.resolve(CATALOG_FILE);
        lockp = path.resolve(LOCK_FILE);
        if (Files.exists(path)) {
            loadCatalog();              // Just to validate
        } else if (create) {
            Files.createDirectory(path);
            Files.createFile(lockp);
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

    @Override
    public PublishedRepository parent() { return null; }

    @Override
    protected void gatherLocalModuleIds(String moduleName,
                                        Set<ModuleId> mids)
        throws IOException
    {
        RepositoryCatalog cat = loadCatalog();
        cat.gatherModuleIds(moduleName, mids);
    }

    @Override
    protected void gatherLocalDeclaringModuleIds(Set<ModuleId> mids)
        throws IOException
    {
        RepositoryCatalog cat = loadCatalog();
        cat.gatherDeclaringModuleIds(mids);
    }

    @Override
    protected ModuleInfo readLocalModuleInfo(ModuleId mid)
        throws IOException
    {
        RepositoryCatalog cat = loadCatalog();
        Entry e = cat.get(mid);
        if (e == null)
            throw new IllegalArgumentException(mid + ": No such module");
        return jms.parseModuleInfo(e.mibs);
    }

    private ModuleType getModuleType(Path modp) {
        for (ModuleType type: ModuleType.values()) {
            if (modp.getFileName().toString().endsWith(type.getFileNameSuffix())) {
                return type;
            }
        }

        // ## check magic numbers?
        throw new IllegalArgumentException(modp + ": Unrecognized module file");
    }

    private Path getModulePath(ModuleId mid, String ext) throws IOException {
        return path.resolve(mid.toString() + ext);
    }

    private Path getModulePath(ModuleId mid, ModuleType type) throws IOException {
        return getModulePath(mid, type.getFileNameSuffix());
    }

    private Path getModulePath(ModuleId mid) throws IOException {
        for (ModuleType type: ModuleType.values()) {
            Path file = getModulePath(mid, type);
            if (Files.exists(file)) {
                return file;
            }
        }
        throw new IllegalArgumentException(mid + ": No such module file");
    }

    private Entry readModuleInfo(Path modp) throws IOException {
        return readModuleInfo(modp, getModuleType(modp));
    }

    private Entry getModuleInfo(ModuleId mid) throws IOException {
        return readModuleInfo(getModulePath(mid));
    }

    private Entry readModuleInfo(Path modp, ModuleType type) throws IOException {
        switch(getModuleType(modp)) {
            case JAR:
                return readModuleInfoFromModularJarFile(modp);
            case JMOD:
                return readModuleInfoFromJmodFile(modp);
            default:
                // Cannot occur;
                throw new AssertionError();
        }
    }

    private Entry readModuleInfoFromJmodFile(Path modp) throws IOException {
        try (InputStream mfis = Files.newInputStream(modp)) {
            ValidatingModuleFileParser parser =
                    ModuleFile.newValidatingParser(mfis);

            ModuleFileHeader mfh = parser.fileHeader();

            // Move to the module info section
            parser.next();

            return new Entry(ModuleType.JMOD,
                             toByteArray(parser.getContentStream()),
                             mfh.getCSize(),
                             mfh.getUSize(),
                             mfh.getHashType(),
                             mfh.getHash());
        }
    }

    private Entry readModuleInfoFromModularJarFile(Path modp) throws IOException {
        File jf = modp.toFile();
        try (JarFile j = new JarFile(jf)) {
            JarEntry moduleInfo = j.getJarEntry(JarFile.MODULEINFO_NAME);
            if (moduleInfo == null) {
                throw new IllegalArgumentException(modp + ": not a modular JAR file");
            }

            long usize = 0;
            for (JarEntry je: Collections.list(j.entries())) {
                if (je.isDirectory()) {
                    continue;
                }

                usize += je.getSize();
            }

            return new Entry(ModuleType.JAR,
                            toByteArray(j, moduleInfo),
                            jf.length(),
                            usize,
                            HashType.SHA256,
                            digest(jf));
        }
    }

    private byte[] digest(File f) throws IOException {
        MessageDigest md;
        try {
             md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            // Cannot occur
            throw new AssertionError();
        }

        try (DigestInputStream in = new DigestInputStream(new FileInputStream(f), md)) {
            byte[] buf = new byte[4096];
            while (in.read(buf) != -1) {
            }

            return in.getMessageDigest().digest();
        }
    }

    private byte[] toByteArray(JarFile j, JarEntry je) throws IOException {
        try (InputStream in = j.getInputStream(je)) {
            return toByteArray(in);
        }
    }

    private byte[] toByteArray(InputStream in) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final byte buf[] = new byte[4096];
        int len;
        while ((len = in.read(buf)) != -1) {
            baos.write(buf, 0, len);
        }
        return baos.toByteArray();
    }

    public void publish(Path modp) throws IOException {
        Entry e = readModuleInfo(modp);
        ModuleInfo mi = jms.parseModuleInfo(e.mibs);
        ModuleId mid = mi.id();
        try (FileChannel lc = FileChannel.open(lockp, READ, WRITE)) {
            lc.lock();

            // Update the module file first
            Path dstp = getModulePath(mid, e.type);
            Path newp = getModulePath(mid, e.type.getFileNameSuffix() + ".new");
            try {
                Files.copy(modp, newp, REPLACE_EXISTING);
                Files.move(newp, dstp, ATOMIC_MOVE);
            } catch (IOException x) {
                Files.deleteIfExists(newp);
                throw x;
            }

            // Then update the catalog
            StreamedRepositoryCatalog cat = loadCatalog();
            cat.add(e);
            storeCatalogWhileLocked(cat);
        }
    }

    @Override
    public InputStream fetch(ModuleId mid) throws IOException {
        RepositoryCatalog cat = loadCatalog();
        Entry e = cat.get(mid);
        if (e == null)
            throw new IllegalArgumentException(mid + ": No such module");
        return Files.newInputStream(getModulePath(mid, e.type));
    }

    @Override
    public ModuleMetaData fetchMetaData(ModuleId mid) throws IOException {
        RepositoryCatalog cat = loadCatalog();
        Entry e = cat.get(mid);
        if (e == null)
            throw new IllegalArgumentException(mid + ": No such module");
        return new ModuleMetaData(e.type, e.csize, e.usize);
    }

    public boolean remove(ModuleId mid) throws IOException {
        try (FileChannel lc = FileChannel.open(lockp, READ, WRITE)) {
            lc.lock();

            // Update catalog first
            StreamedRepositoryCatalog cat = loadCatalog();
            Entry e = cat.get(mid);
            if (!cat.remove(mid))
                return false;
            storeCatalogWhileLocked(cat);

            // Then remove the file
            Files.delete(getModulePath(mid, e.type));
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
        // ## Change to use String joiner when lamda is merged into JDK8
        StringBuilder sb = new StringBuilder();
        sb.append("*.{");
        int l = sb.length();
        for (ModuleType type: ModuleType.values()) {
            if (sb.length() > l) {
                sb.append(",");
            }
            sb.append(type.getFileNameExtension());
        }
        sb.append("}");
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(path, sb.toString())) {
            for (Path modp : ds) {
                ModuleType type = getModuleType(modp);
                String fn = modp.getFileName().toString();
                ModuleId mid
                    = jms.parseModuleId(fn.substring(0, fn.length() - type.getFileNameSuffix().length()));
                mids.add(mid);
            }
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
        try (FileChannel lc = FileChannel.open(lockp, READ, WRITE)) {
            lc.lock();

            StreamedRepositoryCatalog cat = loadCatalog();
            Set<ModuleId> cmids = new HashSet<>();
            cat.gatherDeclaringModuleIds(cmids);

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
                Entry e = getModuleInfo(mid);
                if (!Arrays.equals(cmibs, e.mibs)) {
                    errors++;
                    msg(msgs, "%s: %s: Module-info files do not match",
                        path, mid);
                }
            }
        }

        return errors == 0;
    }

    public void reCatalog() throws IOException {
        try (FileChannel lc = FileChannel.open(lockp, READ, WRITE)) {
            lc.lock();

            Set<ModuleId> mids = new HashSet<>();
            gatherModuleIdsFromDirectoryWhileLocked(mids);

            StreamedRepositoryCatalog cat = RepositoryCatalog.load(null);
            for (ModuleId mid : mids) {
                Entry e = getModuleInfo(mid);
                cat.add(e);
            }
            storeCatalogWhileLocked(cat);
        }
    }
}
