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
import java.lang.module.*;
import java.util.*;
import java.net.*;

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

    public String name() { return null; }

    public RemoteRepository parent() { return null; }

    private URI uri;

    public URI location() {
        return uri;
    }

    static URI canonicalize(URI u)
        throws IOException
    {
        InetAddress ia = InetAddress.getByName(u.getHost());
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
    }

    private File dir;
    private long id;
    private File metaFile;
    private File catFile;

    private RemoteRepository(File d, long i) {
        dir = d;
        id = i;
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
        DataInputStream in
            = new DataInputStream(new BufferedInputStream(fin));
        try {
            FileHeader fh = fileHeader();
            fh.read(in);
            uri = URI.create(in.readUTF());
            mtime = in.readLong();
            String et = in.readUTF();
            etag = (et.length() == 0) ? null : et;
        } finally {
            in.close();
        }
    }

    private void storeMeta()
        throws IOException
    {
        File newfn = new File(dir, "meta.new");
        FileOutputStream fout = new FileOutputStream(newfn);
        DataOutputStream out
            = new DataOutputStream(new BufferedOutputStream(fout));
        try {
            try {
                fileHeader().write(out);
                out.writeUTF(uri.toString());
                out.writeLong(mtime);
                out.writeUTF(etag == null ? "" : etag);
            } finally {
                out.close();
            }
        } catch (IOException x) {
            newfn.delete();
            throw x;
        }
        if (!newfn.renameTo(metaFile))  // ## Not guaranteed atomic
            throw new IOException(newfn + ": Cannot rename to " + metaFile);
    }

    public static RemoteRepository create(File dir, URI u, long id)
        throws IOException
    {
        if (u.isOpaque())
            throw new IllegalArgumentException(u + ": Opaque URIs not supported");
        RemoteRepository rr = new RemoteRepository(dir, id);
        if (dir.exists())
            throw new IllegalStateException(dir + ": Already exists");
        if (!dir.mkdir())
            throw new IOException(dir + ": Cannot create directory");
        rr.uri = canonicalize(u.normalize());
        rr.storeMeta();
        return rr;
    }

    public static RemoteRepository create(File dir, URI u)
        throws IOException
    {
        return create(dir, u, -1);
    }

    public static RemoteRepository open(File dir, long id)
        throws IOException
    {
        RemoteRepository rr = new RemoteRepository(dir, id);
        if (!dir.exists())
            throw new IllegalStateException(dir + ": No such directory");
        if (!dir.isDirectory())
            throw new IOException(dir + ": Not a directory");
        rr.loadMeta();
        return rr;
    }

    public static RemoteRepository open(File dir)
        throws IOException
    {
        return open(dir, -1);
    }

    private RepositoryCatalog cat = null;

    private boolean fetchCatalog(boolean head, boolean force)
        throws IOException
    {

        URI u = uri.resolve("%25catalog");
        if (tracing)
            trace(1, "fetching catalog %s (head %s, force %s)", u, head, force);
        HttpURLConnection co = (HttpURLConnection)u.toURL().openConnection();
        co.setFollowRedirects(true);
        if (!force) {
            if (mtime != 0)
                co.setIfModifiedSince(mtime);
            if (etag != null)
                co.setRequestProperty("If-None-Match", etag);
            if (tracing)
                trace(2, "old mtime %d, etag %s", mtime, etag);
        }
        if (head)
            co.setRequestMethod("HEAD");
        co.connect();

        int rc = co.getResponseCode();
        if (tracing)
            trace(2, "response: %s", co.getResponseMessage());
        if (rc == HttpURLConnection.HTTP_NOT_MODIFIED)
            return false;
        if (rc != HttpURLConnection.HTTP_OK)
            throw new IOException(u + ": " + co.getResponseMessage());

        File newfn = new File(dir, "catalog.new");
        try {
            InputStream in = co.getInputStream();
            try {
                FileOutputStream out = new FileOutputStream(newfn);
                try {
                    byte[] buf = new byte[8192];
                    int n, t = 0;
                    while ((n = in.read(buf)) > 0) {
                        t += n;
                        out.write(buf, 0, n);
                    }
                    if (tracing)
                        trace(2, "%d catalog bytes read", t);
                } finally {
                    out.close();
                }
            } finally {
                in.close();
            }
        } catch (IOException x) {
            newfn.delete();
            throw x;
        }
        if (!newfn.renameTo(catFile))   // ## Not guaranteed atomic
            throw new IOException(newfn + ": Cannot rename to " + metaFile);
        cat = null;

        mtime = co.getHeaderFieldDate("Last-Modified", 0);
        etag = co.getHeaderField("ETag");
        if (tracing)
            trace(2, "new mtime %d, etag %s", mtime, etag);
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

    protected void gatherLocalModuleIds(String moduleName,
                                        Set<ModuleId> mids)
        throws IOException
    {
        catalog().gatherModuleIds(moduleName, mids);
    }

    protected ModuleInfo readLocalModuleInfo(ModuleId mid)
        throws IOException
    {
        byte[] bs = catalog().readModuleInfoBytes(mid);
        return jms.parseModuleInfo(bs);
    }

    public InputStream fetch(ModuleId mid) throws IOException {
        URI u = uri.resolve(mid.toString() + ".jmod");
        if (tracing)
            trace(1, "fetching module %s", u);
        HttpURLConnection co = (HttpURLConnection)u.toURL().openConnection();
        co.setFollowRedirects(true);
        co.connect();
        int rc = co.getResponseCode();
        if (tracing)
            trace(2, "response: %s", co.getResponseMessage());
        if (rc != HttpURLConnection.HTTP_OK)
            throw new IOException(u + ": " + co.getResponseMessage());
        return co.getInputStream();
    }

}
