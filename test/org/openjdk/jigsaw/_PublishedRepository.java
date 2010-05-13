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

// Compiled and invoked by pubrepo.sh

import java.io.*;
import java.util.*;
import java.lang.module.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import org.openjdk.jigsaw.*;

import static java.lang.System.out;
import static java.nio.file.StandardOpenOption.*;


public class _PublishedRepository {

    private static ModuleSystem ms = ModuleSystem.base();

    private static <T> boolean eq(Collection<T> c1, Collection<T> c2) {
        return c1.containsAll(c2) && c2.containsAll(c1);
    }

    static final Path REPO = Paths.get("z.repo");

    static Set<ModuleId> mids = null;

    static Set<Path> mpaths = null;

    static void check(PublishedRepository pr) throws Exception {
        if (!pr.validate(null))
            throw new Exception("Repo invalid");
        if (mids != null) {
            Collection<ModuleId> fmids = pr.listLocalModuleIds();
            assert eq(mids, fmids)
                : String.format("expected %s; found %s", mids, fmids);
        }
    }

    static void create() throws Exception {
        PublishedRepository pr = PublishedRepository.open(REPO, true);
        check(pr);
    }

    static PublishedRepository open() throws IOException {
        return PublishedRepository.open(REPO, false);
    }

    static ModuleId toModuleId(Path p) {
        return ms.parseModuleId(p.getName().toString().replace(".jmod", ""));
    }

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

    static Set<ModuleId> add(String[] args, boolean checkAll)
        throws Exception
    {
        PublishedRepository pr = PublishedRepository.open(REPO, false);
        mids = new HashSet<>();
        mpaths = new HashSet<>();
        for (String a : args) {
            Path p = Paths.get(a);
            mpaths.add(p);
            pr.publish(p);
            mids.add(toModuleId(p));
        }
        check(pr);
        if (!checkAll)
            return mids;
        for (Path p : mpaths) {
            ModuleId mid = toModuleId(p);
            assert equals(p.newInputStream(), pr.fetch(mid))
                : String.format("%s %s", mid, p);
        }
        return mids;
    }

    static void delete() throws Exception {
        PublishedRepository pr = PublishedRepository.open(REPO, false);
        for (ModuleId mid : new ArrayList<ModuleId>(mids)) {
            pr.remove(mid);
            mids.remove(mid);
            check(pr);
        }
    }

    static void corrupt() throws Exception {

        // Corrupt the catalog: s/twisty/twosty/g
        FileChannel cat = FileChannel.open(REPO.resolve("%catalog"),
                                           READ, WRITE);
        ByteBuffer bb = ByteBuffer.allocate((int)cat.size());
        assert cat.read(bb) == (int)cat.size();
        bb.flip();
        while (bb.hasRemaining()) {
            if (bb.get() == (int)'t') {
                if (bb.hasRemaining() && bb.get() == (int)'w') {
                    if (bb.hasRemaining() && bb.get() == (int)'i') {
                        bb.position(bb.position() - 1);
                        bb.put((byte)'o');
                    }
                }
            }
        }
        bb.flip();
        cat.position(0);
        assert cat.write(bb) == (int)cat.size();

        // Remove a module file
        ModuleId dmid = null;
        for (ModuleId mid : mids) {
            if (!mid.name().equals("twisty")) {
                dmid = mid;
                break;
            }
        }
        Path p = REPO.resolve(dmid.name() + "@1.jmod");
        out.format("Deleting %s%n", p);
        p.delete();
        mids.remove(dmid);

        PublishedRepository pr = PublishedRepository.open(REPO, false);
        assert !pr.validate(null);
    }

    static void recat() throws Exception {
        PublishedRepository pr = PublishedRepository.open(REPO, false);
        pr.reCatalog();
        check(pr);
    }

    public static void main(String[] args)
        throws Exception
    {
        create();
        add(args, true);
        delete();
        add(args, false);
        corrupt();
        recat();
        out.format("All tests passed%n");
    }

}
