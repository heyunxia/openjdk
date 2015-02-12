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

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.openjdk.jigsaw.FileConstants.ModuleFile.SectionType;
import org.openjdk.jigsaw.ModuleFile.SectionHeader;
import static org.openjdk.jigsaw.ModuleFileParser.Event.*;;

/*package*/ class ValidatingModuleFileParserImpl
    extends ModuleFileParserDelegate
    implements ValidatingModuleFileParser
{
    private final byte[] headerHash;
    private final Map<FileConstants.ModuleFile.SectionType,byte[]> hashes;
    private byte[] fileHash;

    /*package*/ ValidatingModuleFileParserImpl(InputStream in) {
        this(ModuleFile.newParser(in));
    }

    /*package*/ ValidatingModuleFileParserImpl(ModuleFileParser parser) {
        super(parser);
        if (event() != START_FILE)
            throw new ModuleFileParserException(event() +
                    ": event, should be START_FILE");   // IAE???
        headerHash = parser.getHash();
        hashes = new LinkedHashMap<>();
    }

    @Override
    public byte[] getHeaderHash() {
        return headerHash.clone();
    }

    @Override
    public Map<SectionType,byte[]> getHashes(){
        // protect hash byte arrays.
        Map<SectionType,byte[]> retHashes = new LinkedHashMap<>();
        for (Entry<SectionType,byte[]> entry : hashes.entrySet())
            retHashes.put(entry.getKey(), entry.getValue().clone());
        return retHashes;
    }

    @Override
    public byte[] getFileHash() {
        if (event() != Event.END_FILE)
            throw new ModuleFileParserException(event() +
                    ": file hash is only available at END_FILE");

        return fileHash;
    }

    @Override
    public Event next() {
        Event event = super.next();

        if (event == END_SECTION) {
            SectionHeader header = getSectionHeader();
            byte[] expected = header.getHash();
            byte[] computed = getHash();
            checkHashMatch(expected, computed, header.getType().toString());
            hashes.put(getSectionHeader().getType(), computed);
        } else if (event == END_FILE) {
            byte[] expected = fileHeader().getHash();
            byte[] computed = getHash();
            checkHashMatch(expected, computed, "file hash");
            fileHash = expected;
        }
        return event;
    }

    private static void checkHashMatch(byte[] expected, byte[] computed,
                                       String section) {
        if (!MessageDigest.isEqual(expected, computed))
            throw new ModuleFileParserException(section + ": expected hash "
                                    + hashHexString(expected)
                                    + " instead of "
                                    + hashHexString(computed));
    }

    private static String hashHexString(byte[] hash) {
        StringBuilder hex = new StringBuilder("0x");
        for (int i = 0; i < hash.length; i++) {
            int val = (hash[i] & 0xFF);
            if (val <= 16)
                hex.append("0");
            hex.append(Integer.toHexString(val));
        }
        return hex.toString();
    }
}
