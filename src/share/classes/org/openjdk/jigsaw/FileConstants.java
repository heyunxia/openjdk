/*
 * Copyright 2009-2010 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.util.*;


public final class FileConstants {

    private FileConstants() { }

    public static final int MAGIC = 0xcafe00fa;

    public static final String META_PREFIX = "%";

    public static enum Type {

        LIBRARY_HEADER(0),
        LIBRARY_MODULE_INDEX(1),
        LIBRARY_MODULE_CONFIG(2),
        MODULE_FILE(3),
        STREAM_CATALOG(4),
        REMOTE_REPO_META(5),
        REMOTE_REPO_LIST(6);

        private final int value;
        public int value() { return value; }

        private Type(int v) {
            value = v;
        }

    }

    public static final class ModuleFile {

        public static final int MAJOR_VERSION = 0;
        public static final int MINOR_VERSION = 0;

        public static enum SectionType {

            MODULE_INFO(0, false),
            CLASSES(1, false),
            RESOURCES(2, true),
            NATIVE_LIBS(3, true),
            NATIVE_CMDS(4, true),
            CONFIG(5, true);

            private final int value;
            public int value() { return value; }

            public static SectionType valueOf(int v) {
                for (SectionType st : values()) {
                    if (st.value() == v)
                        return st;
                }
                throw new IllegalArgumentException();
            }

            private final boolean hasFiles;
            public boolean hasFiles() { return hasFiles; }

            private SectionType(int v, boolean hf) {
                value = v;
                hasFiles = hf;
            }

        }

        public static enum SubSectionType {

            FILE(0);

            private final int value;
            public int value() { return value; }

            private SubSectionType(int v) {
                value = v;
            }

        }

        public static enum Compressor {

            NONE(0),
            GZIP(1),
            PACK200_GZIP(2);

            private final int value;
            public int value() { return value; }

            public static Compressor valueOf(int v) {
                for (Compressor ct : values()) {
                    if (ct.value() == v)
                        return ct;
                }
                throw new IllegalArgumentException();
            }

            private Compressor(int v) {
                value = v;
            }

        }

        public static enum HashType {

            SHA256(0, "SHA-256");

            private final int value;
            public int value() { return value; }

            public static HashType valueOf(int v) {
                if (v == SHA256.value)
                    return SHA256;
                throw new IllegalArgumentException();
            }

            private final String algorithm;
            public String algorithm() { return algorithm; }

            private HashType(int v, String a) {
                value = v;
                algorithm = a;
            }

        }

    }

}
