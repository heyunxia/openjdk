/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.classfile;

import java.io.IOException;

/**
 * See JSR 294.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ModuleExport_attribute extends Attribute {
    ModuleExport_attribute(ClassReader cr, int name_index, int length) throws IOException {
        super(name_index, length);
        export_length = cr.readUnsignedShort();
        export_table = new Entry[export_length];
        for (int i = 0; i < export_length; i++)
            export_table[i] = new Entry(cr);
    }

    public ModuleExport_attribute(ConstantPool constant_pool, Entry[] export_table)
            throws ConstantPoolException {
        this(constant_pool.getUTF8Index(Attribute.ModuleExport), export_table);
    }

    public ModuleExport_attribute(int name_index, Entry[] export_table) {
        super(name_index, 2 + length(export_table));
        this.export_length = export_table.length;
        this.export_table = export_table;
    }

    public <R, D> R accept(Visitor<R, D> visitor, D data) {
        return visitor.visitModuleExport(this, data);
    }

    public final int export_length;
    public final Entry[] export_table;

    private static int length(Entry[] export_table) {
        return 2 + export_table.length * Entry.length();
    }

    public static class Entry {
        Entry(ClassReader cr) throws IOException {
            export_index = cr.readUnsignedShort();
            flags = cr.readUnsignedByte();
        }

        public Entry(int e, int f) {
            export_index = e;
            flags = f;
        }

        public static int length() {
            return 3;
        }

        public final int export_index;
        public final int flags;
    }
}
