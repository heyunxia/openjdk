/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;


/**
 * <p> Constants used in various types of files </p>
 */

public final class FileConstants {

    private FileConstants() { }

    public static final int MAGIC = 0xcafe00fa;

    public static final String META_PREFIX = "%";

    /**
     * <p> File types, as used in file headers </p>
     */
    public static enum Type {

        LIBRARY_HEADER(0),
        LIBRARY_MODULE_INDEX(1),
        LIBRARY_MODULE_CONFIG(2),
        MODULE_FILE(3),
        STREAM_CATALOG(4),
        REMOTE_REPO_META(5),
        REMOTE_REPO_LIST(6),
        LIBRARY_MODULE_SIGNER(7),
        LIBRARY_MODULE_IDS(8);

        private final int value;
        public int value() { return value; }

        private Type(int v) {
            value = v;
        }

    }

    /**
     * <p> Module-file constants </p>
     */
    public static final class ModuleFile {

        public static final int MAJOR_VERSION = 0;
        public static final int MINOR_VERSION = 0;

        /**
         * <p> Module-file section types </p>
         */
        public static enum SectionType {

            MODULE_INFO(0, false),
            SIGNATURE(1, false),
            CLASSES(2, false),
            RESOURCES(3, true),
            NATIVE_LIBS(4, true),
            NATIVE_CMDS(5, true),
            CONFIG(6, true);

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

        /**
         * <p> Module-file sub-section types </p>
         */
        public static enum SubSectionType {

            FILE(0);

            private final int value;
            public int value() { return value; }

            private SubSectionType(int v) {
                value = v;
            }

        }

        /**
         * <p> Module-file compressor types </p>
         */
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

        /**
         * <p> Module-file hash types </p>
         */
        public static enum HashType {

            SHA256(0, "SHA-256", 32);

            private final int value;
            public int value() { return value; }

            public static HashType valueOf(int v) {
                if (v == SHA256.value)
                    return SHA256;
                throw new IllegalArgumentException();
            }

            private final String algorithm;
            private final int length;
            public String algorithm() { return algorithm; }
            public int length() { return length; }

            private HashType(int v, String a, int l) {
                value = v;
                algorithm = a;
                length = l;
            }

        }

        /**
         * <p> Module-file signature type </p>
         */
        public static enum SignatureType {

            PKCS7(0);

            private final int value;
            public int value() { return value; }

            public static SignatureType valueOf(int v) {
                for (SignatureType st : values()) {
                    if (st.value() == v)
                        return st;
                }
                throw new IllegalArgumentException();
            }

            private SignatureType(int v) {
                value = v;
            }
        }

    }

}
