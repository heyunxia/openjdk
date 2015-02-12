/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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

// Compiled and invoked by pubrepo.sh

import java.io.*;
import java.util.*;
import java.lang.module.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.*;
import org.openjdk.jigsaw.*;

import static java.lang.System.out;
import static java.nio.file.StandardOpenOption.*;
import org.openjdk.jigsaw.Repository.ModuleType;


public class _PublishedRepository {

    private static ModuleSystem ms = ModuleSystem.base();

    private static <T> boolean eq(Collection<T> c1, Collection<T> c2) {
        return c1.containsAll(c2) && c2.containsAll(c1);
    }

    static final Path REPO = Paths.get("z.repo");

    static Set<ModuleId> mids = null;

    static Set<Path> mpaths = null;

    static void check(PublishedRepository pr) throws Exception {
        if (!pr.validate(null)) {
            throw new Exception("Repo invalid");
        }
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
        String fn = p.getFileName().toString();
        if (fn.endsWith(ModuleType.JAR.getFileNameSuffix())) {
            return ms.parseModuleId(fn.replace(ModuleType.JAR.getFileNameSuffix(), ""));
        } else {
            return ms.parseModuleId(fn.replace(ModuleType.JMOD.getFileNameSuffix(), ""));
        }
    }

    static Path toModulePath(Path repo, ModuleId mid) {
        Path m = toModulePath(repo, mid, ModuleType.JAR);
        if (m == null) {
            m = toModulePath(repo, mid, ModuleType.JMOD);
        }
        return m;
    }

    private static Path toModulePath(Path repo, ModuleId mid, ModuleType type) {
        Path m = repo.resolve(mid.toString() + type.getFileNameSuffix());
        return m.toFile().exists() ? m : null;
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
            try (InputStream ia = Files.newInputStream(p);
                 InputStream ib = pr.fetch(mid)) {
                assert equals(ia, ib)
                    : String.format("%s %s", mid, p);
            }
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
        cat.close();

        // Remove a module file
        ModuleId dmid = null;
        for (ModuleId mid : mids) {
            if (!mid.name().equals("twisty")) {
                dmid = mid;
                break;
            }
        }
        Path p = toModulePath(REPO, dmid);
        out.format("Deleting %s%n", p);
        Files.delete(p);
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
