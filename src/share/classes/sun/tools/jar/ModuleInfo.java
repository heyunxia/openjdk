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
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.module.*;
import java.lang.module.Dependence.Modifier;
import java.util.*;
import com.sun.tools.classfile.*;
import org.openjdk.jigsaw.ClassInfo;
import static com.sun.tools.classfile.ConstantPool.*;

/**
 * Helper class to generate a module-info.class for a modular JAR.
 */
class ModuleInfo {
    private static ModuleSystem ms = ModuleSystem.base();
    private final ModuleId moduleId;
    private final Set<ViewDependence> requiresModules;
    private final Map<String,Set<String>> providers;
    private final Set<String> exports;
    private final ModuleInfoWriter writer;
    private String mainClass;
    private byte[] bytes;

    ModuleInfo(String mid) {
        this.moduleId = ms.parseModuleId(mid);
        this.requiresModules = new HashSet<>();
        this.providers = new HashMap<>();
        this.exports = new TreeSet<>();
        this.writer = new ModuleInfoWriter();
        addRequires(ms.parseModuleIdQuery("jdk.jre"),
                    EnumSet.of(Modifier.SYNTHESIZED));
    }

    ModuleId id() {
        return moduleId;
    }

    String mainClass() {
        return mainClass;
    }

    void setMainClass(String mainclass) {
        this.mainClass = mainclass;
    }

    void addRequires(ModuleId mid) {
        addRequires(ms.parseModuleIdQuery(mid.toString()));
    }

    void addRequires(ModuleIdQuery midq) {
        addRequires(midq, Collections.EMPTY_SET);
    }

    void addRequires(ModuleIdQuery midq, Set<Modifier> mods) {
        requiresModules.add(new ViewDependence(mods, midq));
    }

    void addProvidesService(String service, String impl) {
        Set<String> impls = providers.get(service);
        if (impls == null) {
            // preserve order, no dups
            impls = new LinkedHashSet<>();
            providers.put(service, impls);
        }
        impls.add(impl);
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

    private void addExports(ClassInfo ci) {
        // exports the package of this class if it's public
        if (ci.isPublic() && !ci.isModuleInfo()) {
            int i = ci.name().lastIndexOf('.');
            if (i > 0) {
                String pn = ci.name().substring(0, i);
                exports.add(pn);
            }
        }
    }

    void addExports(File f) throws IOException {
        ClassInfo ci = ClassInfo.read(f);
        addExports(ci);
    }

    void addExports(InputStream in, long size, String path) throws IOException {
        ClassInfo ci = ClassInfo.read(in, size, path);
        addExports(ci);
    }

    class ModuleInfoWriter {
        final List<CPInfo> cpinfos = new ArrayList<>();
        final List<Attribute> attrs = new ArrayList<>();
        int cpidx = 1;
        int this_class_idx;
        int moduleNameIndex;
        int moduleIndex;

        ModuleInfoWriter() {
            cpinfos.add(0, new CONSTANT_Utf8_info("dummy"));
        }

        void addModuleAttribute() {
            String mname = moduleId.name();
            this_class_idx = cpidx;
            cpinfos.add(cpidx, new CONSTANT_Class_info(null, cpidx+3));
            cpinfos.add(cpidx+1, new CONSTANT_Utf8_info(mname));
            moduleNameIndex = cpidx+1;

            cpinfos.add(cpidx+2, new CONSTANT_Utf8_info(moduleId.version().toString()));
            cpinfos.add(cpidx+3, new CONSTANT_Utf8_info(mname + "/module-info"));
            cpinfos.add(cpidx+4, new CONSTANT_ModuleId_info(null, cpidx+1, cpidx+2));
            cpinfos.add(cpidx+5, new CONSTANT_Utf8_info(Attribute.Module));
            Attribute attr = new Module_attribute(cpidx+5, cpidx+4);
            attrs.add(attr);
            cpidx += 6;
        }


        int addModuleIdQuery(ModuleIdQuery query) {
            int nameIdx, versionIdx;

            nameIdx = cpidx++;
            cpinfos.add(nameIdx, new CONSTANT_Utf8_info(query.name()));
            if (query.versionQuery() == null) {
                versionIdx = 0;
            } else {
                versionIdx = cpidx++;
                cpinfos.add(versionIdx,
                            new CONSTANT_Utf8_info(query.versionQuery().toString()));
            }
            cpinfos.add(cpidx, new CONSTANT_ModuleQuery_info(null, nameIdx, versionIdx));
            return cpidx++;
        }

        void addModuleRequiresAttribute() {
            ModuleRequires_attribute.Entry[] moduleEntries =
                new ModuleRequires_attribute.Entry[requiresModules.size()];
            ModuleRequires_attribute.Entry[] serviceEntries =
                new ModuleRequires_attribute.Entry[0];
            int i=0;
            for (ViewDependence d: requiresModules) {
                // ## specify a version range in CONSTANT_ModuleQuery_info?
                int midq = addModuleIdQuery(d.query());
                int flags = 0;
                for (Modifier m : d.modifiers()) {
                    switch (m) {
                        case OPTIONAL:
                            flags |= ModuleRequires_attribute.ACC_OPTIONAL;
                            break;
                        case LOCAL:
                            flags |= ModuleRequires_attribute.ACC_LOCAL;
                            break;
                        case PUBLIC:
                            flags |= ModuleRequires_attribute.ACC_REEXPORT;
                            break;
                        case SYNTHETIC:
                            flags |= ModuleRequires_attribute.ACC_SYNTHETIC;
                            break;
                        case SYNTHESIZED:
                            flags |= ModuleRequires_attribute.ACC_SYNTHESIZED;
                            break;
                    }
                }
                moduleEntries[i++] = new ModuleRequires_attribute.Entry(midq, flags);
            }
            cpinfos.add(cpidx, new CONSTANT_Utf8_info(Attribute.ModuleRequires));
            Attribute attr = new ModuleRequires_attribute(cpidx, moduleEntries, serviceEntries);
            attrs.add(attr);
            cpidx++;
        }

        void addModuleProvidesAttribute() {
            // ## multiple views support
            ModuleProvides_attribute.View[] views =
                new ModuleProvides_attribute.View[1];

            int entryPointIndex = mainClass() == null ? 0 : addClassInfo(mainClass());
            int[] exportsCpIds = new int[exports.size()];

            ModuleProvides_attribute.Service[] service_table =
                new ModuleProvides_attribute.Service[providers.size()];
            int i = 0;
            for (Map.Entry<String,Set<String>> entry: providers.entrySet()) {
                String sn = entry.getKey();
                for (String impl: entry.getValue()) {
                    int service_index = addClassInfo(sn);
                    int impl_index = addClassInfo(impl);
                    service_table[i++] =
                        new ModuleProvides_attribute.Service(service_index, impl_index);
                }
            }

            i = 0;
            for (String pn : exports) {
                int index = cpidx++;
                cpinfos.add(index, new CONSTANT_Utf8_info(pn));
                exportsCpIds[i++] = index;
            }

            views[0] =
                new ModuleProvides_attribute.View(0,
                                                  entryPointIndex,
                                                  new int[0],
                                                  service_table,
                                                  exportsCpIds,
                                                  new int[0]);

            cpinfos.add(cpidx, new CONSTANT_Utf8_info(Attribute.ModuleProvides));
            Attribute attr = new ModuleProvides_attribute(cpidx, views);
            attrs.add(attr);
            cpidx++;
        }

        int addClassInfo(String cn) {
            String cname = cn.replace('.', '/');
            cpinfos.add(cpidx, new CONSTANT_Utf8_info(cname));
            cpinfos.add(cpidx+1, new CONSTANT_Class_info(null, cpidx));
            int index = cpidx+1;
            cpidx += 2;
            return index;
        }

        byte[] getModuleInfoBytes() throws IOException {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ClassWriter cw = new ClassWriter();
            addModuleAttribute();
            addModuleRequiresAttribute();
            addModuleProvidesAttribute();

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
