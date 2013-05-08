/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;


/**
 * <p> A fast persistent tree map </p>
 *
 * <p> ... (though not actually a {@link java.util.TreeMap} </p>
 */

public class PersistentTreeMap implements Closeable {

    private final long db;
    private final boolean readOnly;

    protected PersistentTreeMap(long db) {
        this(db, true);
    }

    protected PersistentTreeMap(long db, boolean readOnly) {
        this.db = db;
        this.readOnly = readOnly;
    }

    private static native long create0(String f) throws IOException;

    // creates file if doesn't exist, truncates.
    public static PersistentTreeMap create(File f) throws IOException {
        return new PersistentTreeMap(create0(f.getPath()), false);
    }

    private static native long open0(String f) throws IOException;

    public static PersistentTreeMap open(File f) throws IOException {
        return new PersistentTreeMap(open0(f.getPath()));
    }

    private native void put0(long db, String k, String v)
        throws IOException;

    // overwrites existing value, if there is one
    public void put(String key, String value) throws IOException {
        if (readOnly)
            throw new IOException("attempt to modify a read-only database");

        put0(db, key, value);
    }

    private native String get0(long db, String k)
        throws IOException;

    // null if key not found
    public String get(String key) throws IOException {
        return get0(db, key);
    }

    private native void put1(long db, String k, int v)
        throws IOException;

    // overwrites existing value, if there is one
    public void put(String key, int value) throws IOException {
        if (readOnly)
            throw new IOException("attempt to modify a read-only database");

        put1(db, key, value);
    }

    private native int get1(long db, String k)
        throws IOException;

    // Returns -1 if key not found
    public int getInt(String key) throws IOException {
        return get1(db, key);
    }

    private native void put2(long db, String k, String sv, int iv)
        throws IOException;

    // overwrites existing value, if there is one
    public void put(String key, String sval, int ival) throws IOException {
        if (readOnly)
            throw new IOException("attempt to modify a read-only database");

        put2(db, key, sval, ival);
    }

    /**
     * <p> A {@link String}, and an {@code int} </p>
     */
    public static class StringAndInt {
        public final String s;
        public final int i;
        public StringAndInt(String s, int i) {
            this.s = s;
            this.i = i;
        }
    }

    private native boolean get2(long db, String k, String[] svala, int[] ivala)
        throws IOException;

    // This is simpler than hassling with creating the result object
    // directly in JNI

    private String[] svala = new String[1];
    private int[] ivala = new int[1];

    // null if key not found
    public StringAndInt getStringAndInt(String key)
        throws IOException
    {
        synchronized(this) {
            if (!get2(db, key, svala, ivala))
                return null;
            return new StringAndInt(svala[0], ivala[0]);
        }
    }

    private native void close0(long db) throws IOException;

    @Override
    public void close() throws IOException {
        close0(db);
    }

    private static native void initialize();

    static {
        initialize();
    }

}
