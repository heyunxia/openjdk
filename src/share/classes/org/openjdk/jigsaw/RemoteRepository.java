/*
 * Copyright (c) 2010, 2012 Oracle and/or its affiliates. All rights reserved.
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
import java.lang.module.*;
import java.util.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardCopyOption.*;

import static org.openjdk.jigsaw.Trace.*;


/**
 * <p> A remote module repository, whose catalog is cached locally </p>
 */

public class RemoteRepository
    extends Repository
{

    private static final JigsawModuleSystem jms
        = JigsawModuleSystem.instance();

    /*
    public static interface ProgressWatcher {
        public void start(int min, int max);
        public void progress(int cur);
        public void finish();
    }
    */

    @Override
    public String name() { return null; }

    private final RemoteRepository parent;
    @Override
    public RemoteRepository parent() { return null; }

    private URI uri;

    @Override
    public URI location() {
        return uri;
    }

    static URI canonicalize(URI u)
        throws IOException
    {
        String host = u.getHost();
        if (host != null) {
            InetAddress ia = InetAddress.getByName(host);
            String chn = ia.getCanonicalHostName().toLowerCase();
            String p = u.getPath();
            if (p == null)
                p = "/";
            else if (!p.endsWith("/"))
                p += "/";
            try {
                return new URI(u.getScheme(),
                               u.getUserInfo(),
                               chn,
                               u.getPort(),
                               p,
                               u.getQuery(),
                               u.getFragment());
            } catch (URISyntaxException x) {
                throw new AssertionError(x);
            }
        } else {
            String s = u.toString();
            if (s.endsWith("/"))
                return u;
            return URI.create(s + "/");
        }
    }

    private File dir;
    private long id;
    private File metaFile;
    private File catFile;

    private RemoteRepository(File d, long i, RemoteRepository p) {
        dir = d;
        id = i;
        parent = p;
        metaFile = new File(dir, "meta");
        catFile = new File(dir, "catalog");
    }

    public long id() { return id; }

    private static int MAJOR_VERSION = 0;
    private static int MINOR_VERSION = 0;

    private static FileHeader fileHeader() {
        return (new FileHeader()
                .type(FileConstants.Type.REMOTE_REPO_META)
                .majorVersion(MAJOR_VERSION)
                .minorVersion(MINOR_VERSION));
    }

    private long mtime = 0;
    private String etag = null;

    private void loadMeta()
        throws IOException
    {
        if (!metaFile.exists())
            return;
        FileInputStream fin = new FileInputStream(metaFile);
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(fin))) {
            FileHeader fh = fileHeader();
            fh.read(in);
            uri = URI.create(in.readUTF());
            mtime = in.readLong();
            String et = in.readUTF();
            etag = (et.length() == 0) ? null : et;
        }
    }

    private void storeMeta()
        throws IOException
    {
        File newfn = new File(dir, "meta.new");
        try (FileOutputStream fos = new FileOutputStream(newfn);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(fos)))
        {
            fileHeader().write(out);
            out.writeUTF(uri.toString());
            out.writeLong(mtime);
            out.writeUTF(etag == null ? "" : etag);

        } catch (IOException x) {
            deleteAfterException(newfn, x);
            throw x;
        }
        // move meta data into place
        try {
            Files.move(newfn.toPath(), metaFile.toPath(), ATOMIC_MOVE);
        } catch (IOException x) {
            deleteAfterException(newfn, x);
            throw x;
        }
    }

    public static RemoteRepository create(File dir, URI u, long id)
        throws IOException
    {
        if (u.isOpaque())
            throw new IllegalArgumentException(u + ": Opaque URIs not supported");
        RemoteRepository rr = new RemoteRepository(dir, id, null);
        rr.uri = canonicalize(u.normalize());
        if (dir.exists())
            throw new IllegalStateException(dir + ": Already exists");
        if (!dir.mkdir())
            throw new IOException(dir + ": Cannot create directory");
        try {
            rr.storeMeta();
        } catch (IOException x) {
            deleteAfterException(dir, x);
            throw x;
        }
        return rr;
    }

    public static RemoteRepository create(File dir, URI u)
        throws IOException
    {
        return create(dir, u, -1);
    }

    public static RemoteRepository open(File dir, long id,
                                        RemoteRepository parent)
        throws IOException
    {
        RemoteRepository rr = new RemoteRepository(dir, id, parent);
        if (!dir.exists())
            throw new IllegalStateException(dir + ": No such directory");
        if (!dir.isDirectory())
            throw new IOException(dir + ": Not a directory");
        rr.loadMeta();
        return rr;
    }

    public static RemoteRepository open(File dir, long id)
        throws IOException
    {
        return open(dir, id, null);
    }

    public static RemoteRepository open(File dir)
        throws IOException
    {
        return open(dir, -1);
    }

    /**
     * Deletes this remote repository, including the directory.
     */
    public void delete() throws IOException {
        Files.deleteIfExists(catFile.toPath());
        Files.deleteIfExists(metaFile.toPath());
        Files.deleteIfExists(dir.toPath());
    }

    private RepositoryCatalog cat = null;

    private boolean fetchCatalog(boolean head, boolean force)
        throws IOException
    {

        URI u = uri.resolve("%25catalog");
        if (tracing)
            trace(1, "fetching catalog %s (head %s, force %s)", u, head, force);

        // special-case file protocol for faster copy
        if (u.getScheme().equalsIgnoreCase("file")) {
            Path newfn = dir.toPath().resolve("catalog.new");
            try {
                Files.copy(Paths.get(u), newfn);
                Files.move(newfn, catFile.toPath(), ATOMIC_MOVE);
            } catch (IOException x) {
                Files.deleteIfExists(newfn);
                throw x;
            }
        } else {
            URLConnection uc = u.toURL().openConnection();
            if (uc instanceof HttpURLConnection) {
                HttpURLConnection http = (HttpURLConnection)uc;
                http.setInstanceFollowRedirects(true);
                if (!force) {
                    if (mtime != 0)
                        uc.setIfModifiedSince(mtime);
                    if (etag != null)
                        uc.setRequestProperty("If-None-Match", etag);
                    if (tracing)
                        trace(2, "old mtime %d, etag %s", mtime, etag);
                }
                if (head)
                    http.setRequestMethod("HEAD");
                http.connect();

                int rc = http.getResponseCode();
                if (tracing)
                    trace(2, "response: %s", http.getResponseMessage());
                if (rc == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    return false;
                }
                if (rc != HttpURLConnection.HTTP_OK)
                    throw new IOException(u + ": " + http.getResponseMessage());
            }

            Path newfn = dir.toPath().resolve("catalog.new");
            try (InputStream in = uc.getInputStream()) {
                long t = Files.copy(in, newfn);
                if (tracing)
                    trace(2, "%d catalog bytes read", t);
                Files.move(newfn, catFile.toPath(), ATOMIC_MOVE);
            } catch (IOException x) {
                Files.deleteIfExists(newfn);
                throw x;
            }

            mtime = uc.getHeaderFieldDate("Last-Modified", 0);
            etag = uc.getHeaderField("ETag");
            if (tracing)
                trace(2, "new mtime %d, etag %s", mtime, etag);
        }

        cat = null;
        storeMeta();

        return true;

    }

    // HTTP HEAD
    //
    public boolean isCatalogStale() throws IOException {
        if (!catFile.exists())
            return true;
        return fetchCatalog(true, false);
    }

    // HTTP GET (conditional)
    //
    public boolean updateCatalog(boolean force) throws IOException {
        return fetchCatalog(false, force);
    }

    private RepositoryCatalog catalog()
        throws IOException
    {
        if (!catFile.exists())
            throw new IOException("No catalog yet");
        if (cat == null)
            cat = RepositoryCatalog.load(new FileInputStream(catFile));
        return cat;
    }

    @Override
    protected void gatherLocalModuleIds(String moduleName,
                                        Set<ModuleId> mids)
        throws IOException
    {
        catalog().gatherModuleIds(moduleName, mids);
    }

    @Override
    protected void gatherLocalDeclaringModuleIds(Set<ModuleId> mids)
        throws IOException
    {
        catalog().gatherDeclaringModuleIds(mids);
    }

    @Override
    protected ModuleInfo readLocalModuleInfo(ModuleId mid)
        throws IOException
    {
        byte[] bs = catalog().readModuleInfoBytes(mid);
        return jms.parseModuleInfo(bs);
    }

    @Override
    public InputStream fetch(ModuleId mid) throws IOException {
        ModuleMetaData mmd = fetchMetaData(mid);
        URI u = uri.resolve(mid.toString() + mmd.getType().getFileNameSuffix());
        if (tracing)
            trace(1, "fetching module %s", u);

        // special case file protocol for faster access
        if (u.getScheme().equalsIgnoreCase("file")) {
            return Files.newInputStream(Paths.get(u));
        } else {
            URLConnection uc = u.toURL().openConnection();
            if (uc instanceof HttpURLConnection) {
                HttpURLConnection http = (HttpURLConnection)uc;
                http.setInstanceFollowRedirects(true);
                http.connect();
                int rc = http.getResponseCode();
                if (tracing)
                    trace(2, "response: %s", http.getResponseMessage());
                if (rc != HttpURLConnection.HTTP_OK)
                    throw new IOException(u + ": " + http.getResponseMessage());
            }
            return uc.getInputStream();
        }
    }

    @Override
    public ModuleMetaData fetchMetaData(ModuleId mid) throws IOException {
        RepositoryCatalog.Entry e = catalog().get(mid);
        if (e == null)
            throw new IllegalArgumentException(mid.toString());
        return new ModuleMetaData(e.type, e.csize, e.usize);
    }


    /**
     * Attempts to delete {@code f}. If the delete fails then the exception is
     * added as a suppressed exception to the given exception.
     */
    private static void deleteAfterException(File f, Exception x) {
        try {
            Files.deleteIfExists(f.toPath());
        } catch (IOException x2) {
            x.addSuppressed(x2);
        }
    }
}
