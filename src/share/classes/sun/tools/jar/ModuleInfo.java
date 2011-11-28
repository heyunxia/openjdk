/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package sun.tools.jar;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.lang.module.*;
import java.lang.module.Dependence.Modifier;
import java.util.*;
import com.sun.tools.classfile.*;
import static com.sun.tools.classfile.ConstantPool.*;

/**
 * Helper class to generate a module-info.class for a modular JAR.
 */
class ModuleInfo {
    private static ModuleSystem ms = ModuleSystem.base();
    private final ModuleId moduleId;
    private final String mainClass;
    private final Set<Dependence> requires;
    private final ModuleInfoWriter writer;
    private byte[] bytes;

    ModuleInfo(String mid, String mainclass) {
        this.moduleId = ms.parseModuleId(mid);
        this.mainClass = mainclass;
        this.requires = new HashSet<>();
        this.writer = new ModuleInfoWriter();
        addRequire(org.openjdk.jigsaw.Platform.defaultPlatformModule(),
                   EnumSet.of(Modifier.SYNTHETIC));
    }

    ModuleId id() {
        return moduleId;
    }

    String mainClass() {
        return mainClass;
    }

    void addRequire(ModuleId mid) {
        addRequire(mid, Collections.EMPTY_SET);
    }

    void addRequire(ModuleId mid, Set<Modifier> mods) {
        ModuleIdQuery midq = ms.parseModuleIdQuery(mid.name() + "@" + mid.version());
        requires.add(new Dependence(mods, midq));
    }

    void addRequire(Dependence d) {
        requires.add(d);
    }

    void write(OutputStream os) throws IOException {
        if (bytes == null) {
            // cache the bytes as this method may be called
            // twice for a compressed entry to calculate CRC
            // and then write it to the jar file.
            bytes = writer.getModuleInfoBytes();
        }
        os.write(bytes);
    }

    class ModuleInfoWriter {
        final List<CPInfo> cpinfos = new ArrayList<>();
        final List<Attribute> attrs = new ArrayList<>();
        int cpidx = 1;
        int this_class_idx;

        ModuleInfoWriter() {
            cpinfos.add(0, new CONSTANT_Utf8_info("dummy"));
        }

        void addModuleAttribute() {
            String mname = moduleId.name();
            this_class_idx = cpidx;
            cpinfos.add(cpidx, new CONSTANT_Class_info(null, cpidx+3));
            cpinfos.add(cpidx+1, new CONSTANT_Utf8_info(mname));
            cpinfos.add(cpidx+2, new CONSTANT_Utf8_info(moduleId.version().toString()));
            cpinfos.add(cpidx+3, new CONSTANT_Utf8_info(mname + "/module-info"));
            cpinfos.add(cpidx+4, new CONSTANT_ModuleId_info(null, cpidx+1, cpidx+2));
            cpinfos.add(cpidx+5, new CONSTANT_Utf8_info(Attribute.Module));
            Attribute attr = new Module_attribute(cpidx+5, cpidx+4);
            attrs.add(attr);
            cpidx += 6;
        }

        void addModuleRequireAttribute() {
            // add constant pool entries for the modifiers
            List<Modifier> modifiers = new ArrayList<>();
            int modifierIdx = cpidx;
            for (Dependence d: requires) {
                for (Modifier m : d.modifiers()) {
                    int i = modifiers.indexOf(m);
                    if (i >= 0)
                        continue;

                    modifiers.add(m);
                    String s = m.name().toLowerCase(Locale.ENGLISH);
                    cpinfos.add(cpidx++, new CONSTANT_Utf8_info(s));
                }
            }

            ModuleRequires_attribute.Entry[] reqs =
                new ModuleRequires_attribute.Entry[requires.size()];
            int i = 0, j = 0;
            for (Dependence d: requires) {
                // ## specify a version range in CONSTANT_ModuleId_info? 
                String version = d.query().versionQuery().toString();
                cpinfos.add(cpidx, new CONSTANT_Utf8_info(d.query().name()));
                cpinfos.add(cpidx+1, new CONSTANT_Utf8_info(version));
                cpinfos.add(cpidx+2, new CONSTANT_ModuleId_info(null, cpidx, cpidx+1));
                int[] attrs = new int[d.modifiers().size()];
                j = 0;
                for (Modifier m : d.modifiers()) {
                    attrs[j++] = modifierIdx + modifiers.indexOf(m);
                }
                reqs[i++] = new ModuleRequires_attribute.Entry(cpidx+2, attrs);
                cpidx += 3;
            }
            cpinfos.add(cpidx, new CONSTANT_Utf8_info(Attribute.ModuleRequires));
            Attribute attr = new ModuleRequires_attribute(cpidx, reqs);
            attrs.add(attr);
            cpidx++;
        }
 
        void addModuleClassAttribute() {
            String cname = mainClass.replace('.', '/');
            cpinfos.add(cpidx, new CONSTANT_Utf8_info(Attribute.ModuleClass));
            cpinfos.add(cpidx+1, new CONSTANT_Utf8_info(cname));
            cpinfos.add(cpidx+2, new CONSTANT_Class_info(null, cpidx+1));
            Attribute attr = new ModuleClass_attribute(cpidx, cpidx+2, new int[0]);
            attrs.add(attr);
            cpidx += 3;
        }

        void addModuleExportAttribute() {
            cpinfos.add(cpidx, new CONSTANT_Utf8_info(Attribute.ModuleExport));
            cpinfos.add(cpidx+1, new CONSTANT_Utf8_info("**"));
            cpinfos.add(cpidx+2, new CONSTANT_Class_info(null, cpidx+1));
            ModuleExport_attribute.Entry[] entry = 
                new ModuleExport_attribute.Entry[] {
                    new ModuleExport_attribute.Entry(cpidx+2, 0)
                };
            Attribute attr = new ModuleExport_attribute(cpidx, entry);
            attrs.add(attr);
            cpidx += 3;
        }

        byte[] getModuleInfoBytes() throws IOException {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ClassWriter cw = new ClassWriter();
            addModuleAttribute();
            addModuleRequireAttribute();
            addModuleExportAttribute();

            if (mainClass != null)
                addModuleClassAttribute();

            ConstantPool cpool = new ConstantPool(cpinfos.toArray(new CPInfo[0]));
            Attributes attributes = new Attributes(cpool, attrs.toArray(new Attribute[0]));
            ClassFile cf = new ClassFile(0xCAFEBABE, 0, 51, cpool,
                                         new AccessFlags(AccessFlags.ACC_MODULE),
                                         this_class_idx, 0, new int[0], new Field[0], new Method[0],
                                         attributes);
            cw.write(cf, os);
            return os.toByteArray();
        }
    }
}
