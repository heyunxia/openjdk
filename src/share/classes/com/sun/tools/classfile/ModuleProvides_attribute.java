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
 * See Jigsaw.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ModuleProvides_attribute extends Attribute {
    ModuleProvides_attribute(ClassReader cr, int name_index, int length) throws IOException {
        super(name_index, length);
        view_length = cr.readUnsignedShort();
        view_table = new View[view_length];
        for (int i = 0; i < view_length; i++)
            view_table[i] = new View(cr);
    }

    public ModuleProvides_attribute(ConstantPool constant_pool, View[] provides_table)
            throws ConstantPoolException {
        this(constant_pool.getUTF8Index(Attribute.ModuleProvides), provides_table);
    }

    public ModuleProvides_attribute(int name_index, View[] view_table) {
        super(name_index, 2 + length(view_table));
        this.view_length = view_table.length;
        this.view_table = view_table;
    }

    public <R, D> R accept(Visitor<R, D> visitor, D data) {
        return visitor.visitModuleProvides(this, data);
    }

    public final int view_length;
    public final View[] view_table;
    
    private static int length(View[] view_table) {
        int n = 0;
        for (View v: view_table)
            n += v.length();
        return n;
    }

    public static class View {
        public final int view_name_index;
        public final int entrypoint_index;
        public final int alias_length;
        public final int[] alias_table;
        public final int service_length;
        public final Service[] service_table;
        public final int export_length;
        public final Export[] export_table;
        public final int  permit_length;
        public final int[] permit_table;

        View(ClassReader cr) throws IOException {
            view_name_index = cr.readUnsignedShort();
            entrypoint_index = cr.readUnsignedShort();
            alias_length = cr.readUnsignedShort();
            alias_table = new int[alias_length];
            for (int i = 0; i < alias_table.length; i++)
                alias_table[i] = cr.readUnsignedShort();
            service_length = cr.readUnsignedShort();
            service_table = new Service[service_length];
            for (int i = 0; i < service_table.length; i++)
                service_table[i] = new Service(cr);
            export_length = cr.readUnsignedShort();
            export_table = new Export[export_length];
            for (int i = 0; i < export_table.length; i++)
                export_table[i] = new Export(cr);
            permit_length = cr.readUnsignedShort();
            permit_table = new int[permit_length];
            for (int i = 0; i < permit_table.length; i++)
                permit_table[i] = cr.readUnsignedShort();
        }

        int length() {
            return  2   // view_name_index
                    + 2 // entrypoint_index
                    + 2 + 2 * alias_table.length
                    + 2 + Service.length * service_table.length
                    + 2 + Export.length * export_table.length
                    + 2 + 2 * permit_table.length;

        }
    }

    public static class Service {
        static final int length = 4;

        public final int service_index;
        public final int impl_index;

        Service(ClassReader cr) throws IOException {
            service_index = cr.readUnsignedShort();
            impl_index = cr.readUnsignedShort();
        }
    }

    public static class Export {
        static final int length = 6;

        public final int export_index;
        public final int export_flags;
        public final int source_index;

        Export(ClassReader cr) throws IOException {
            export_index = cr.readUnsignedShort();
            export_flags = cr.readUnsignedShort();
            source_index = cr.readUnsignedShort();
        }
    }
}
