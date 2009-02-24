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
import com.sun.tools.classfile.ConstantPool.CONSTANT_ModuleId_info;

/**
 * See JSR294.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ModuleProvides_attribute extends Attribute {
    ModuleProvides_attribute(ClassReader cr, int name_index, int length) throws IOException {
        super(name_index, length);
        provides_length = cr.readUnsignedShort();
        provides_table = new int[provides_length];
        for (int i = 0; i < provides_length; i++)
            provides_table[i] = cr.readUnsignedShort();
    }

    public ModuleProvides_attribute(ConstantPool constant_pool, int[] provides_table)
            throws ConstantPoolException {
        this(constant_pool.getUTF8Index(Attribute.ModuleProvides), provides_table);
    }

    public ModuleProvides_attribute(int name_index, int[] provides_table) {
        super(name_index, 2 + 2 * provides_table.length);
        this.provides_length = provides_table.length;
        this.provides_table = provides_table;
    }

    public CONSTANT_ModuleId_info getProvides(int index, ConstantPool constant_pool) throws ConstantPoolException {
        int provides_index = provides_table[index];
        return constant_pool.getModuleIdInfo(provides_index);
    }

    public <R, D> R accept(Visitor<R, D> visitor, D data) {
        return visitor.visitModuleProvides(this, data);
    }

    public final int provides_length;
    public final int[] provides_table;
}
