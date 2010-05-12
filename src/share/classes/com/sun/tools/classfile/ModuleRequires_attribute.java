/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.classfile;

import java.io.IOException;

/**
 * See JSR294.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ModuleRequires_attribute extends Attribute {
    ModuleRequires_attribute(ClassReader cr, int name_index, int length) throws IOException {
        super(name_index, length);
        requires_length = cr.readUnsignedShort();
        requires_table = new Entry[requires_length];
        for (int i = 0; i < requires_length; i++)
            requires_table[i] = new Entry(cr);
    }

    public ModuleRequires_attribute(ConstantPool constant_pool, Entry[] requires_table)
            throws ConstantPoolException {
        this(constant_pool.getUTF8Index(Attribute.ModuleRequires), requires_table);
    }

    public ModuleRequires_attribute(int name_index, Entry[] requires_table) {
        super(name_index, 2 + length(requires_table));
        this.requires_length = requires_table.length;
        this.requires_table = requires_table;
    }

    public <R, D> R accept(Visitor<R, D> visitor, D data) {
        return visitor.visitModuleRequires(this, data);
    }

    public final int requires_length;
    public final Entry[] requires_table;

    private static int length(Entry[] requires_table) {
        int n = 0;
        for (int i = 0; i < requires_table.length; i++)
            n += requires_table[i].length();
        return n;
    }

    public static class Entry {
        Entry(ClassReader cr) throws IOException {
            requires_index = cr.readUnsignedShort();
            attributes_length = cr.readUnsignedShort();
            attributes = new int[attributes_length];
            for (int i = 0; i < attributes_length; i++)
                attributes[i] = cr.readUnsignedShort();
        }

        public int length() {
            return 4 + attributes_length * 2;
        }

        public final int requires_index;
        public final int attributes_length;
        public final int[] attributes;
    }
}
