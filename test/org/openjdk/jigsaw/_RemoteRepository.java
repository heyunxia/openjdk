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

// Compiled and invoked by remrepo.sh

import java.io.*;
import java.util.*;
import java.lang.module.*;
import java.net.*;
import org.openjdk.jigsaw.*;

import static java.lang.System.out;

public class _RemoteRepository {

    private static ModuleSystem ms = ModuleSystem.base();

    private static <T> boolean equals(Collection<T> c1, Collection<T> c2) {
        if (c1 == null)
            return c2 == null;
        if (c2 == null)
            return false;
        return c1.containsAll(c2) && c2.containsAll(c1);
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

    static void testRemove(RemoteRepository rr, PublishedRepository pr)
        throws Exception
    {
        check(rr, pr);
        ModuleId mid = mids.iterator().next();
        mids.remove(mid);
        // Ensure time stamp of catalog after removal is at least one second >
        // This is the minimum increment represented by the Last-modified header
        Thread.sleep(2000);
        pr.remove(mid);
        rr.updateCatalog(false);
        check(rr, pr);
    }

    static void test(int port) throws Exception {

        URI u = URI.create("http://localhost:" + port);

        RemoteRepository rr = RemoteRepository.create(REM_REPO, u);
        out.format("Remote %s %s%n", REM_REPO, u);

        assert rr.isCatalogStale();
        assert rr.updateCatalog(false);
        assert !rr.isCatalogStale();
        assert !rr.updateCatalog(false);
        assert rr.updateCatalog(true);

        PublishedRepository pr = _PublishedRepository.open();

        testRemove(rr, pr);
        rr = RemoteRepository.open(REM_REPO);
        testRemove(rr, pr);

    }

    public static void main(String[] args)
        throws Exception
    {
        _PublishedRepository.create();
        mids = _PublishedRepository.add(args, true);
        TrivialWebServer tws
            = TrivialWebServer.create(_PublishedRepository.REPO, out);
        out.format("Port %d%n", tws.port());
        try {
            test(tws.port());
        } finally {
            tws.stop();
        }
    }

}
