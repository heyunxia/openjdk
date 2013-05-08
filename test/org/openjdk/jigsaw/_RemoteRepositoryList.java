/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

// Compiled and invoked by repolist.sh

import java.io.*;
import java.util.*;
import java.lang.module.*;
import java.net.*;
import java.nio.file.*;
import org.openjdk.jigsaw.*;
import org.openjdk.jigsaw.SimpleLibrary.StorageOption;

import static java.lang.System.out;

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

    @SuppressWarnings("unchecked")
    private static <T> void assertEquals(Collection<T> c1, T ... xs) {
        Collection<T> c2 = Arrays.asList(xs);
        assertEquals(c1, c2);
    }

    private static boolean equals(ModuleInfo mi1, ModuleInfo mi2) {
        // ## TODO multiple views
        return (mi1.id().equals(mi2.id())
                && mi1.requiresModules().equals(mi2.requiresModules())
                && mi1.requiresServices().equals(mi2.requiresServices())
                && equals(mi1.defaultView(), mi2.defaultView()));
    }

    private static boolean equals(ModuleView mv1, ModuleView mv2) {
        return (mv1.id().equals(mv2.id())
                && mv1.aliases().equals(mv2.aliases())
                && mv1.services().equals(mv2.services())
                && mv1.permits().equals(mv2.permits())
                && ((mv1.mainClass() == mv2.mainClass())
                    || (mv1.mainClass() != null
                        && mv1.mainClass().equals(mv2.mainClass()))));
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

    static String localHost;

    static URI local(int port, String path) throws Exception {
        if (localHost == null)
            localHost = InetAddress.getLocalHost().getCanonicalHostName();
        return URI.create("http://" + localHost + ":" + port + path);
    }

    static void testAddRemove(int port) throws Exception {

        File LIB = new File("z.lib.addrem");
        Set<StorageOption> opts = Collections.emptySet();
        Library lib = SimpleLibrary.create(LIB, opts);
        RemoteRepositoryList rl = lib.repositoryList();
        assert rl.repositories().isEmpty();

        URI u1 = local(port, "/foo");
        RemoteRepository rr = rl.add(u1, 0);
        assert rr != null;
        u1 = URI.create(u1.toString() + "/");
        System.out.printf("url: %s%n", u1);
        System.out.printf("repository: %s%n", rr.location());
        assert rr.location().equals(u1);
        assertEquals(rl.repositories(), rr);

        lib = SimpleLibrary.open(LIB);
        rl = lib.repositoryList();
        List<RemoteRepository> rrs = rl.repositories();
        assert rrs.size() == 1;
        RemoteRepository rr_ = rrs.get(0);
        assert rr_.location().equals(rr.location());

        URI u2 = local(port, "/bar/");
        RemoteRepository rr2 = rl.add(u2, Integer.MAX_VALUE);
        assertEquals(locations(rl), u1, u2);
        assertEquals(rl.repositories(), rr_, rr2);

        URI u3 = local(port, "/baz/");
        RemoteRepository rr3 = rl.add(u3, 0);
        assertEquals(locations(rl), u3, u1, u2);
        assertEquals(rl.repositories(), rr3, rr_, rr2);

        URI u4 = local(port, "/qux/");
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

        Set<StorageOption> opts = Collections.emptySet();
        Library lib = SimpleLibrary.create(LIB, opts);
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
