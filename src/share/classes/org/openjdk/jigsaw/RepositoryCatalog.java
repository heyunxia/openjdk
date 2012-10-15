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

import static org.openjdk.jigsaw.Repository.ModuleType;
import static org.openjdk.jigsaw.FileConstants.ModuleFile.HashType;


/**
 * <p> A {@linkplain Repository module repository's} catalog </p>
 */

public abstract class RepositoryCatalog {

    // ## Elements in this class are public only to enable unit tests

    private static final JigsawModuleSystem jms
        = JigsawModuleSystem.instance();

    public abstract void gatherDeclaringModuleIds(Set<ModuleId> mids)
        throws IOException;

    public abstract void gatherModuleIds(String moduleName, Set<ModuleId> mids)
        throws IOException;

    public abstract byte[] readModuleInfoBytes(ModuleId mid)
        throws IOException;

    static class Entry {

        final ModuleType type;
        final byte[] mibs;
        final long csize;
        final long usize;
        final HashType hashType;
        final byte[] hash;

        Entry(ModuleType t, byte[] m, long cs, long us, HashType ht, byte[] h) {
            type = t;
            mibs = m;
            csize = cs;
            usize = us;
            hashType = ht;
            hash = h;
        }

    }

    abstract void add(Entry e);

    public void add(ModuleType t, byte[] mibs, long cs, long us,
                    HashType hashType, byte[] hash)
    {
        add(new Entry(t, mibs, cs, us, hashType, hash));
    }

    public abstract boolean remove(ModuleId mid);

    abstract Entry get(ModuleId mid);


    /**
     * <p> A {@linkplain RepositoryCatalog repository catalog} which can be
     * stored to, and then loaded from, a byte stream </p>
     */
    public static class StreamedRepositoryCatalog
        extends RepositoryCatalog
    {

        static final int MAJOR_VERSION = 0;
        static final int MINOR_VERSION = 0;

        private Map<ModuleId,Entry> modules = new HashMap<>();
        private Map<ModuleId,ModuleId> moduleForViewId= new HashMap<>();

        @Override
        public void gatherDeclaringModuleIds(Set<ModuleId> mids) {
            mids.addAll(modules.keySet());
        }

        @Override
        public void gatherModuleIds(String moduleName, Set<ModuleId> mids) {
            for (ModuleId mid : moduleForViewId.keySet()) {
                if (moduleName == null || mid.name().equals(moduleName))
                    mids.add(mid);
            }
        }

        @Override
        public byte[] readModuleInfoBytes(ModuleId mid) {
            Entry e = modules.get(moduleForViewId.get(mid));
            return (e != null) ? e.mibs : null;
        }

        @Override
        void add(Entry e) {
            ModuleInfo mi = jms.parseModuleInfo(e.mibs); // ## Need fast path
            modules.put(mi.id(), e);
            for (ModuleView mv : mi.views()) {
                moduleForViewId.put(mv.id(), mi.id());
                for (ModuleId alias : mv.aliases()) {
                    moduleForViewId.put(alias, mi.id());
                }
            }
        }

        @Override
        public boolean remove(ModuleId mid) {
            for (Iterator<ModuleId> i = moduleForViewId.values().iterator();
                 i.hasNext();)
            {
                // remove views/aliases defined in the module be removed
                ModuleId id = i.next();
                if (id.equals(mid)) {
                    i.remove();
                }
            }
            return modules.remove(mid) != null;
        }

        @Override
        Entry get(ModuleId mid) {
            return modules.get(moduleForViewId.get(mid));
        }

        /* ##
        public boolean remove(ModuleIdQuery midq) {
            int nd = 0;
            for (Iterator<ModuleId> i = modules.keySet().iterator();
                 i.hasNext();)
            {
                ModuleId mid = i.next();
                if (midq.matches(mid)) {
                    i.remove();
                    nd++;
                }
            }
            return nd != 0;
        }
        */

        private StreamedRepositoryCatalog() { }

        private FileHeader fileHeader() {
            return (new FileHeader()
                    .type(FileConstants.Type.STREAM_CATALOG)
                    .majorVersion(MAJOR_VERSION)
                    .minorVersion(MINOR_VERSION));
        }

        public void store(OutputStream os) throws IOException {
            OutputStream bos = new BufferedOutputStream(os);
            try (DataOutputStream out = new DataOutputStream(bos)) {
                fileHeader().write(out);
                out.writeInt(modules.size());
                for (Map.Entry<ModuleId,Entry> me : modules.entrySet()) {
                    out.writeUTF(me.getKey().toString()); // ## Redundant
                    Entry e = me.getValue();
                    out.writeUTF(e.type.getFileNameExtension());
                    out.writeLong(e.csize);
                    out.writeLong(e.usize);
                    out.writeShort(e.hashType.value());
                    out.writeShort(e.hash.length);
                    out.write(e.hash);
                    out.writeShort(e.mibs.length);
                    out.write(e.mibs);
                }
                out.writeInt(moduleForViewId.size());
                for (Map.Entry<ModuleId,ModuleId> me : moduleForViewId.entrySet()) {
                    out.writeUTF(me.getKey().toString());
                    out.writeUTF(me.getValue().toString());
                }
            }
        }

        public StreamedRepositoryCatalog loadStream(InputStream is)
            throws IOException
        {
            BufferedInputStream bis = new BufferedInputStream(is);
            DataInputStream in = new DataInputStream(bis);
            FileHeader fh = fileHeader();
            fh.read(in);
            int nms = in.readInt();
            for (int i = 0; i < nms; i++) {
                ModuleId mid = jms.parseModuleId(in.readUTF());
                ModuleType t = ModuleType.fromFileNameExtension(in.readUTF());
                long cs = in.readLong();
                long us = in.readLong();
                HashType ht = HashType.valueOf(in.readShort());
                int nb = in.readShort();
                byte[] hash = new byte[nb];
                in.readFully(hash);
                nb = in.readShort();
                byte[] mibs = new byte[nb];
                in.readFully(mibs);
                modules.put(mid, new Entry(t, mibs, cs, us, ht, hash));
            }
            int nmids = in.readInt();
            for (int i = 0; i < nmids; i++) {
                ModuleId id = jms.parseModuleId(in.readUTF());
                ModuleId mid = jms.parseModuleId(in.readUTF());
                moduleForViewId.put(id, mid);
            }
            return this;
        }

    }

    public static StreamedRepositoryCatalog load(InputStream in)
        throws IOException
    {
        StreamedRepositoryCatalog src = new StreamedRepositoryCatalog();
        if (in != null) {
            try {
                src.loadStream(in);
            } finally {
                in.close();
            }
        }
        return src;
    }


    /*

    private static class IndexedRepositoryCatalog {  } // ## Later

    static IndexedRepositoryCatalog open(File fn) throws IOException {
        return new IndexedRepositoryCatalog(...);
    }

    */

}
