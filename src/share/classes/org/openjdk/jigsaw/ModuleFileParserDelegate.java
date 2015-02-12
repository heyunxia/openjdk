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
import java.util.Iterator;
import java.util.Map.Entry;
import org.openjdk.jigsaw.ModuleFile.ModuleFileHeader;
import org.openjdk.jigsaw.ModuleFile.SectionHeader;
import org.openjdk.jigsaw.ModuleFile.SubSectionFileHeader;


/**
 * <p> A delegating {@linkplain ModuleFileParser module-file parser} </p>
 *
 * <p> By default each method does nothing but call the corresponding method on
 * the parent parser. </p>
 */

public class ModuleFileParserDelegate
    implements ModuleFileParser
{

    private ModuleFileParser parser;

    ModuleFileParserDelegate(ModuleFileParser parser) {
        this.parser = parser;
    }

    @Override
    public ModuleFileHeader fileHeader() {
        return parser.fileHeader();
    }

    @Override
    public Event event() {
        return parser.event();
    }

    @Override
    public boolean hasNext() {
        return parser.hasNext();
    }

    @Override
    public Event next() {
        return parser.next();
    }

    @Override
    public SectionHeader getSectionHeader() {
        return parser.getSectionHeader();
    }

    @Override
    public SubSectionFileHeader getSubSectionFileHeader() {
        return parser.getSubSectionFileHeader();
    }

    @Override
    public byte[] getHash() {
        return parser.getHash();
    }

    @Override
    public InputStream getContentStream() {
        return parser.getContentStream();
    }
    @Override
    public Iterator<Entry<String,InputStream>> getClasses() {
        return parser.getClasses();
    }

    @Override
    public InputStream getRawStream() {
        return parser.getRawStream();
    }

    @Override
    public boolean skipToNextStartSection() {
        return parser.skipToNextStartSection();
    }

    @Override
    public boolean skipToNextStartSubSection() {
        return parser.skipToNextStartSubSection();
    }

}
