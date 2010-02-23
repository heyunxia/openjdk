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
import java.net.URI;
import java.lang.module.*;
import java.util.*;

import static org.openjdk.jigsaw.FileConstants.ModuleFile.HashType;


/**
 * <p> A module repository's catalog </p>
 */

public abstract class RepositoryCatalog {

    // ## Elements in this class are public only to enable unit tests

    private static final JigsawModuleSystem jms
        = JigsawModuleSystem.instance();

    public abstract void gatherModuleIds(String moduleName, Set<ModuleId> mids)
        throws IOException;

    public abstract byte[] readModuleInfoBytes(ModuleId mid)
        throws IOException;

    static class Entry {

        final byte[] mibs;
        final long csize;
        final long usize;
        final HashType hashType;
        final byte[] hash;

        Entry(byte[] m, long cs, long us, HashType ht, byte[] h) {
            mibs = m;
            csize = cs;
            usize = us;
            hashType = ht;
            hash = h;
        }

    }

    abstract void add(Entry e);

    public void add(byte[] mibs, long cs, long us,
                    HashType hashType, byte[] hash)
    {
        add(new Entry(mibs, cs, us, hashType, hash));
    }

    public abstract boolean remove(ModuleId mid);

    abstract Entry get(ModuleId mid);


    public static class StreamedRepositoryCatalog
        extends RepositoryCatalog
    {

        static final int MAJOR_VERSION = 0;
        static final int MINOR_VERSION = 0;

        private Map<ModuleId,Entry> modules = new HashMap<>();

        public void gatherModuleIds(String moduleName, Set<ModuleId> mids) {
            for (ModuleId mid : modules.keySet()) {
                if (moduleName == null || mid.name().equals(moduleName))
                    mids.add(mid);
            }
        }

        public byte[] readModuleInfoBytes(ModuleId mid) {
            Entry e = modules.get(mid);
            return (e != null) ? e.mibs : null;
        }

        public void add(Entry e) {
            ModuleId mid = jms.parseModuleInfo(e.mibs).id(); // ## Need fast path
            modules.put(mid, e);
        }

        public boolean remove(ModuleId mid) {
            return modules.remove(mid) != null;
        }

        Entry get(ModuleId mid) {
            return modules.get(mid);
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
            DataOutputStream out = new DataOutputStream(bos);
            fileHeader().write(out);
            out.writeInt(modules.size());
            for (Map.Entry<ModuleId,Entry> me : modules.entrySet()) {
                out.writeUTF(me.getKey().toString()); // ## Redundant
                Entry e = me.getValue();
                out.writeLong(e.csize);
                out.writeLong(e.usize);
                out.writeShort(e.hashType.value());
                out.writeShort(e.hash.length);
                out.write(e.hash);
                out.writeShort(e.mibs.length);
                out.write(e.mibs);
            }
            out.close();
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
                long cs = in.readLong();
                long us = in.readLong();
                HashType ht = HashType.valueOf(in.readShort());
                int nb = in.readShort();
                byte[] hash = new byte[nb];
                in.readFully(hash);
                nb = in.readShort();
                byte[] mibs = new byte[nb];
                in.readFully(mibs);
                modules.put(mid, new Entry(mibs, cs, us, ht, hash));
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
