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

// Compiled and invoked by repolist.sh

import java.io.*;
import java.util.*;
import java.lang.module.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import org.openjdk.jigsaw.*;

import static java.lang.System.out;
import static java.nio.file.StandardOpenOption.*;


public class _RemoteRepositoryList {

    private static ModuleSystem ms = ModuleSystem.base();

    private static <T> boolean equals(Collection<T> c1, Collection<T> c2) {
        if (c1 == null)
            return c2 == null;
        if (c2 == null)
            return false;
        return c1.containsAll(c2) && c2.containsAll(c1);
    }

    private static <T> void assertEquals(Collection<T> c1, Collection<T> c2) {
        assert equals(c1, c2) : String.format("%s : %s", c1, c2);
    }

    private static <T> void assertEquals(Collection<T> c1, T ... xs) {
        Collection<T> c2 = Arrays.asList(xs);
        assertEquals(c1, c2);
    }

    private static boolean equals(ModuleInfo mi1, ModuleInfo mi2) {
        return (mi1.id().equals(mi2.id())
                && mi1.provides().equals(mi2.provides())
                && mi1.requires().equals(mi2.requires())
                && mi1.permits().equals(mi2.permits())
                && ((mi1.mainClass() == mi2.mainClass())
                    || (mi1.mainClass() != null
                        && mi1.mainClass().equals(mi2.mainClass()))));
    }

    static final File REM_REPO = new File("z.remote");

    static Set<ModuleId> mids = null;

    static byte[] readStream(InputStream in)
        throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n = 0;
        while ((n = in.read(buf)) > 0)
            out.write(buf, 0, n);
        return out.toByteArray();
    }

    static boolean equals(InputStream ia, InputStream ib)
        throws Exception
    {
        return Arrays.equals(readStream(ia), readStream(ib));
    }

    private static void check(RemoteRepository rr, PublishedRepository pr)
        throws Exception
    {
        assert equals(rr.listModuleIds(), mids)
            : String.format("%s : %s", rr.listModuleIds(), mids);
        assert equals(pr.listModuleIds(), mids)
            : String.format("%s : %s", pr.listModuleIds(), mids);
        out.format("Module ids: %s%n", mids);
        for (ModuleId mid : mids) {
            assert equals(rr.readModuleInfo(mid),
                          pr.readModuleInfo(mid));
            assert equals(rr.fetch(mid), pr.fetch(mid));
        }
    }

    static List<URI> locations(RemoteRepositoryList rl)
        throws IOException
    {
        List<URI> us = new ArrayList<>();
        for (RemoteRepository rr : rl.repositories())
            us.add(rr.location());
        return us;
    }

    static void testAddRemove(int port) throws Exception {

        File LIB = new File("z.lib.addrem");
        Library lib = SimpleLibrary.open(LIB, true);
        RemoteRepositoryList rl = lib.repositoryList();
        assert rl.repositories().isEmpty();

        URI u1 = URI.create("http://localhost:" + port + "/foo");
        RemoteRepository rr = rl.add(u1, 0);
        assert rr != null;
        u1 = URI.create(u1.toString() + "/");
        assert rr.location().equals(u1);
        assertEquals(rl.repositories(), rr);

        lib = SimpleLibrary.open(LIB);
        rl = lib.repositoryList();
        List<RemoteRepository> rrs = rl.repositories();
        assert rrs.size() == 1;
        RemoteRepository rr_ = rrs.get(0);
        assert rr_.location().equals(rr.location());

        URI u2 = URI.create("http://localhost:" + port + "/bar/");
        RemoteRepository rr2 = rl.add(u2, Integer.MAX_VALUE);
        assertEquals(locations(rl), u1, u2);
        assertEquals(rl.repositories(), rr_, rr2);

        URI u3 = URI.create("http://localhost:" + port + "/baz/");
        RemoteRepository rr3 = rl.add(u3, 0);
        assertEquals(locations(rl), u3, u1, u2);
        assertEquals(rl.repositories(), rr3, rr_, rr2);

        URI u4 = URI.create("http://localhost:" + port + "/qux/");
        RemoteRepository rr4 = rl.add(u4, 1);
        assertEquals(locations(rl), u3, u4, u1, u2);
        assertEquals(rl.repositories(), rr3, rr4, rr_, rr2);

        assert rl.remove(rr4);
        assertEquals(locations(rl), u3, u1, u2);
        assertEquals(rl.repositories(), rr3, rr_, rr2);

    }

    static void testFetch(int port) throws Exception {

        File LIB = new File("z.lib.fetch");
        URI u = URI.create("http://localhost:" + port);

        Library lib = SimpleLibrary.open(LIB, true);
        RemoteRepositoryList rl = lib.repositoryList();
        rl.add(u, 0);

        assert !rl.areCatalogsStale();
        assert !rl.updateCatalogs(false);
        assert rl.updateCatalogs(true);

    }

    public static void main(String[] args)
        throws Exception
    {

        TrivialWebServer tws
            = TrivialWebServer.create(Paths.get("z.repos"), out);
        out.format("port %d%n", tws.port());
        try {
            testAddRemove(tws.port());
        } finally {
            tws.stop();
        }

        _PublishedRepository.create();
        mids = _PublishedRepository.add(args, true);
        tws = TrivialWebServer.create(_PublishedRepository.REPO, out);
        out.format("port %d%n", tws.port());
        try {
            testFetch(tws.port());
        } finally {
            tws.stop();
        }

    }

}
