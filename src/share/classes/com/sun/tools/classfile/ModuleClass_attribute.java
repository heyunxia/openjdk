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

import com.sun.tools.classfile.ConstantPool.*;

/**
 * See JSR294.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ModuleClass_attribute extends Attribute {
    ModuleClass_attribute(ClassReader cr, int name_index, int length) throws IOException {
        super(name_index, length);
        class_index = cr.readUnsignedShort();
        attributes_length = cr.readUnsignedShort();
        attributes = new int[attributes_length];
        for (int i = 0; i < attributes.length; i++)
            attributes[i] = cr.readUnsignedShort();
    }

    public ModuleClass_attribute(ConstantPool constant_pool, int class_index, int[] attributes)
            throws ConstantPoolException {
        this(constant_pool.getUTF8Index(Attribute.ModuleRequires), class_index, attributes);
    }

    public ModuleClass_attribute(int name_index, int class_index, int[] attributes) {
        super(name_index, 2 + attributes.length * 2);
        this.class_index = class_index;
        this.attributes_length = attributes.length;
        this.attributes = attributes;
    }

    public CONSTANT_Class_info getClassInfo(ConstantPool constant_pool) throws ConstantPoolException {
        if (class_index == 0)
            return null;
        return constant_pool.getClassInfo(class_index);
    }

    public String getClassName(ConstantPool constant_pool) throws ConstantPoolException {
        if (class_index == 0)
            return null;
        return constant_pool.getClassInfo(class_index).getName();
    }

    public String[] getClassAttributes(ConstantPool constant_pool) throws ConstantPoolException {
        String[] attrs = new String[attributes.length];
        for (int i = 0; i < attrs.length; i++)
            attrs[i] = constant_pool.getUTF8Value(attributes[i]);
        return attrs;
    }

    public <R, D> R accept(Visitor<R, D> visitor, D data) {
        return visitor.visitModuleClass(this, data);
    }

    public final int class_index;
    public final int attributes_length;
    public final int[] attributes;
}
