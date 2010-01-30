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

import java.lang.module.*;
import java.io.*;
import java.net.URI;
import java.util.*;

import static org.openjdk.jigsaw.Trace.*;


/**
 * ## Update all earlier code to use this class for headers
 */

final class FileHeader {

    private int maxMajorVersion;
    private int maxMinorVersion;
    private int majorVersion;
    private int minorVersion;
    private FileConstants.Type type;

    int majorVersion() { return majorVersion; }

    int minorVersion() { return minorVersion; }

    FileHeader() { }

    FileHeader majorVersion(int v) {
        majorVersion = v;
        maxMajorVersion = v;
        return this;
    }

    FileHeader minorVersion(int v) {
        minorVersion = v;
        maxMinorVersion = v;
        return this;
    }

    FileHeader type(FileConstants.Type t) {
        type = t;
        return this;
    }

    void write(DataOutputStream out) throws IOException {
        out.writeInt(FileConstants.MAGIC);
        out.writeShort(type.value());
        out.writeShort(majorVersion);
        out.writeShort(minorVersion);
    }

    void write(OutputStream out) throws IOException {
        write(new DataOutputStream(out));
    }

    FileHeader read(DataInputStream in) throws IOException {
        try {
            int m = in.readInt();
            if (m != FileConstants.MAGIC)
                throw new IOException("Invalid magic number");
            int typ = in.readShort();
            if (typ != type.value())
                throw new IOException("Invalid file type");
            int maj = in.readShort();
            int min = in.readShort();
            if (   maj > maxMajorVersion
                   || (maj == maxMajorVersion && min > maxMinorVersion)) {
                throw new IOException("Futuristic version number");
            }
            majorVersion = maj;
            minorVersion = min;
            return this;
        } catch (EOFException x) {
            throw new IOException("File header truncated", x);
        }
    }

}
