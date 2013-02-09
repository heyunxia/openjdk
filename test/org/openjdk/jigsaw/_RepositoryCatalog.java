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

// Compiled and invoked by repocat.sh

import java.io.*;
import java.util.*;
import java.lang.module.*;
import java.nio.*;
import java.nio.channels.*;
import org.openjdk.jigsaw.*;
import org.openjdk.jigsaw.RepositoryCatalog.StreamedRepositoryCatalog;

import static org.openjdk.jigsaw.Repository.ModuleType;
import static org.openjdk.jigsaw.FileConstants.ModuleFile.HashType;


public class _RepositoryCatalog {

    private static ModuleSystem ms = ModuleSystem.base();

    private static <T> boolean eq(Collection<T> c1, Collection<T> c2) {
        return c1.containsAll(c2) && c2.containsAll(c1);
    }

    static File CAT_FILE = new File("z.scat");

    static void writeStreamed(Map<ModuleId,byte[]> modules, String[] args)
        throws Exception
    {
        StreamedRepositoryCatalog rc = RepositoryCatalog.load(null);
        ByteBuffer bb = ByteBuffer.allocate(8192);
        for (int i = 0; i < args.length; i++) {
            bb.clear();
            try (FileChannel fc = new FileInputStream(args[i]).getChannel()) {
                int s = (int)fc.size();
                if (bb.capacity() < s)
                    bb = ByteBuffer.allocate(s);
                int n = fc.read(bb);
                if (n != s)
                    throw new IOException("Mis-sized read");
                rc.add(ModuleType.JMOD, Arrays.copyOfRange(bb.array(), 0, n),
                       42, 93,
                       HashType.SHA256, new byte[0]);
                modules.put(ms.parseModuleInfo(bb.array()).id(),
                            Arrays.copyOfRange(bb.array(), 0, n));
            }
        }
        try (OutputStream rco = new FileOutputStream(CAT_FILE)) {
            rc.store(rco);
        }
    }

    static StreamedRepositoryCatalog readStreamed(Map<ModuleId,byte[]> modules)
        throws Exception
    {
        InputStream in = new FileInputStream(CAT_FILE);
        StreamedRepositoryCatalog rc = null;
        try {
            rc = RepositoryCatalog.load(in);
        } finally {
            in.close();
        }
        Set<ModuleId> mids = new HashSet<>();
        rc.gatherDeclaringModuleIds(mids);
        assert eq(mids, modules.keySet());
        for (ModuleId mid : mids) {
            assert Arrays.equals(rc.readModuleInfoBytes(mid),
                                 modules.get(mid))
                : mid;
        }
        return rc;
    }

    static void deleteStreamed(Map<ModuleId,byte[]> modules,
                               StreamedRepositoryCatalog rc)
        throws Exception
    {
        ModuleId dmid = ms.parseModuleId("twisty@1");
        assert rc.remove(dmid);
        OutputStream out = new FileOutputStream(CAT_FILE);
        rc.store(out);
        Map<ModuleId,byte[]> mods = new HashMap<>(modules);
        for (Iterator<ModuleId> i = mods.keySet().iterator(); i.hasNext();) {
            ModuleId mid = i.next();
            if (dmid.equals(mid))
                i.remove();
        }
        readStreamed(mods);
    }

    public static void main(String[] args)
        throws Exception
    {
        Map<ModuleId,byte[]> modules = new HashMap<>();
        writeStreamed(modules, args);
        StreamedRepositoryCatalog rc = readStreamed(modules);
        deleteStreamed(modules, rc);
    }

}
