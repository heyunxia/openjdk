/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.module;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.EnumSet;
import java.util.Set;
import java.util.Map;

import java.lang.module.Dependence.Modifier;

/**
 * Read a module-info class file.
 *
 * This class is module-system agnostic.  It delegates the parsing of versions
 * and version queries to a given module system.
 */

/* package */  class ModuleInfoReader {

    /* package */ static ModuleInfo
        read(ModuleSystem ms, byte[] data)
    {
        return new ModuleInfoReader(ms, data).moduleInfo;
    }

    private ModuleSystem ms;
    private ModuleInfo moduleInfo;
    private DataInputStream in;
    private ConstantPool cpool;
    private ModuleId moduleId;
    private Set<ViewDependence> requiresModules = new LinkedHashSet<>();
    private Set<ServiceDependence> requiresServices = new LinkedHashSet<>();
    private Set<ModuleView> views = new LinkedHashSet<>();
    private ModuleView defaultView;

    private ModuleInfoReader(ModuleSystem ms, byte[] data) {

        this.ms = ms;

        try {
            in = new DataInputStream(new ByteArrayInputStream(data));
            int magic = in.readInt();
            if (magic != 0xCAFEBABE)
                throw new IllegalArgumentException("bad magic");

            int minorVersion = in.readUnsignedShort();
            int majorVersion = in.readUnsignedShort();
            if (majorVersion < 51)
                throw new IllegalArgumentException("bad major version");

            cpool = new ConstantPool(ms, in);

            int accessFlags = in.readUnsignedShort();
            int thisClass = in.readUnsignedShort();
            int superClass = in.readUnsignedShort();

            int interfacesCount = in.readUnsignedShort();
            if (interfacesCount > 0)
                throw new IllegalArgumentException("bad #interfaces");

            int fieldsCount = in.readUnsignedShort();
            if (fieldsCount > 0)
                throw new IllegalArgumentException("bad #fields");

            int methodsCount = in.readUnsignedShort();
            if (methodsCount > 0)
                throw new IllegalArgumentException("bad #methods");

            readAttributes();

            if (defaultView == null) {
                defaultView =
                    new ModuleViewImpl(moduleId,
                                       null,
                                       Collections.<ModuleId>emptySet(),
                                       Collections.<String>emptySet(),
                                       Collections.<String>emptySet(),
                                       Collections.<String, Set<String>>emptyMap());
                views.add(defaultView);
            }
            moduleInfo = new ModuleInfoImpl(moduleId,
                                            defaultView,
                                            views,
                                            requiresModules,
                                            requiresServices);
            for (ModuleView mv : views) {
                ((ModuleViewImpl)mv).mi = moduleInfo;
            }

        } catch (IOException e) {
            throw new Error(e);
        }
    }

    /* package */ ModuleInfo moduleInfo() { return moduleInfo; }

    private static final String MODULE = "Module";
    private static final String MODULE_PROVIDES = "ModuleProvides";
    private static final String MODULE_REQUIRES = "ModuleRequires";
    private static final String MODULE_DATA = "ModuleData";
    private static final int ACC_OPTIONAL    = 0x1;
    private static final int ACC_LOCAL       = 0x2;
    private static final int ACC_REEXPORT    = 0x4;
    private static final int ACC_SYNTHETIC   = 0x1000;
    private static final int ACC_SYNTHESIZED = 0x10000;

    private void readAttributes() throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            int nameIndex = in.readUnsignedShort();
            String name = cpool.getUtf8(nameIndex);
            int length = in.readInt();
            switch (name) {
                case MODULE:
                    readModule();
                    break;
                case MODULE_PROVIDES:
                    readModuleProvides();
                    break;
                case MODULE_REQUIRES:
                    readModuleRequires();
                    break;
                default:
                    in.skip(length);
            }
        }
    }

    private void readModule() throws IOException {
        int index = in.readUnsignedShort();
        moduleId = cpool.getModuleId(index);
    }

    private void readModuleRequires() throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            int index = in.readUnsignedShort();
            int flags = in.readInt();
            EnumSet<Modifier> mods = EnumSet.noneOf(Modifier.class);
            if ((flags & ACC_OPTIONAL) != 0) {
                mods.add(Modifier.OPTIONAL);
            }
            if ((flags & ACC_LOCAL) != 0) {
                mods.add(Modifier.LOCAL);
            }
            if ((flags & ACC_REEXPORT) != 0) {
                mods.add(Modifier.PUBLIC);
            }
            if ((flags & ACC_SYNTHETIC) != 0) {
                mods.add(Modifier.SYNTHETIC);
            }
            if ((flags & ACC_SYNTHESIZED) != 0) {
                mods.add(Modifier.SYNTHESIZED);
            }
            requiresModules.add(new ViewDependence(mods, cpool.getModuleIdQuery(index)));
        }

        count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            String cn = readClassName();
            int flags = in.readInt();
            EnumSet<Modifier> mods = EnumSet.noneOf(Modifier.class);
            if ((flags & ACC_OPTIONAL) != 0) {
                mods.add(Modifier.OPTIONAL);
            }
            if ((flags & ACC_SYNTHETIC) != 0) {
                mods.add(Modifier.SYNTHETIC);
            }
            if ((flags & ACC_SYNTHESIZED) != 0) {
                mods.add(Modifier.SYNTHESIZED);
            }
            requiresServices.add(new ServiceDependence(mods, cn));
        }
    }

    private void readModuleProvides() throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            Set<String> exports = new LinkedHashSet<>();
            Set<ModuleId> aliases = new LinkedHashSet<>();
            Map<String,Set<String>> services = new LinkedHashMap<>();
            Set<String> permits = new LinkedHashSet<>();

            String viewname = readViewName();
            ModuleId id = new ModuleId(viewname, moduleId.version());
            String mainClass = readClassName();

            readModuleAliases(aliases);
            readModuleServices(services);
            readModuleExports(exports);
            readModulePermits(permits);

            ModuleView view = new ModuleViewImpl(id,
                                                 mainClass,
                                                 aliases,
                                                 exports,
                                                 permits,
                                                 services);
            views.add(view);
            if (id.equals(moduleId)) {
                defaultView = view;
            }
        }
    }

    private String readClassName() throws IOException {
        int index = in.readUnsignedShort();
        if (index == 0)
            return null;

        return cpool.getClassName(index).replace('/', '.');
    }

    private String readViewName() throws IOException {
        int index = in.readUnsignedShort();
        if (index == 0)
            return moduleId.name();

        return cpool.getUtf8(index).replace('/', '.');
    }

    private void readModuleExports(Set<String> exports) throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            int index = in.readUnsignedShort();
            exports.add(cpool.getUtf8(index).replace('/', '.'));
        }
    }

    private void readModuleServices(Map<String,Set<String>> services) throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            String sn = readClassName();
            String impl = readClassName();
            if (sn == null || impl == null)
                throw new NullPointerException("Service name: " + sn +
                        " Implementation class: " + impl);
            Set<String> providers = services.get(sn);
            if (providers == null) {
                providers = new LinkedHashSet<>();
                services.put(sn, providers);
            }
            providers.add(impl);
        }
    }

    private void readModulePermits(Set<String> permits) throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            ModuleId mid = cpool.getModuleId(in.readUnsignedShort());
            permits.add(mid.name());
        }
    }

    private void readModuleAliases(Set<ModuleId> aliases) throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            aliases.add(cpool.getModuleId(in.readUnsignedShort()));
        }
    }

    private String readModuleData() throws IOException {
        int index = in.readUnsignedShort();
        return cpool.getUtf8(index);
    }

    static class ModuleInfoImpl
        implements ModuleInfo
    {

        private final ModuleId id;
        private final ModuleView defaultView;
        private final Set<ModuleView> views;
        private final Set<ViewDependence> requiresModules;
        private final Set<ServiceDependence> requiresServices;

        ModuleInfoImpl(ModuleId id,
                       ModuleView defaultView,
                       Set<ModuleView> views,
                       Set<ViewDependence> viewDeps,
                       Set<ServiceDependence> serviceDeps)
        {
            this.id = id;
            this.defaultView = defaultView;
            this.views = Collections.unmodifiableSet(views);
            this.requiresModules = Collections.unmodifiableSet(viewDeps);
            this.requiresServices = Collections.unmodifiableSet(serviceDeps);
        }

        public ModuleId id() {
            return id;
        }

        public Set<ViewDependence> requiresModules() {
            return requiresModules;
        }

        public Set<ServiceDependence> requiresServices() {
            return requiresServices;
        }

        public ModuleView defaultView() {
            return defaultView;
        }

        public Set<ModuleView> views() {
            return views;
        }

        @Override
        public String toString() {
            Set<String> names = new LinkedHashSet<>();
            for (ModuleView mv : views) {
                names.add(mv.id().name());
            }
            return "ModuleInfo { id: " + id
                    + ", requires: " + requiresModules
                    + ", requires service:" + requiresServices
                    + ", views: " + names
                    + " }";
        }
    }

    static class ModuleViewImpl
        implements ModuleView
    {
        private final ModuleId id;
        private final Set<String> exports;
        private final Set<ModuleId> aliases;
        private final Map<String,Set<String>> services;
        private final Set<String> permits;
        private final String mainClass;
        ModuleInfo mi;

        ModuleViewImpl(ModuleId id,
                       String mainClass,
                       Set<ModuleId> aliases,
                       Set<String> exports,
                       Set<String> permits,
                       Map<String,Set<String>> serviceProviders) {
            this.id = id;
            this.mainClass = mainClass;
            this.aliases = Collections.unmodifiableSet(aliases);
            this.exports = Collections.unmodifiableSet(exports);
            this.permits = Collections.unmodifiableSet(permits);
            this.services = Collections.unmodifiableMap(serviceProviders);
        }

        public ModuleInfo moduleInfo() {
            return mi;
        }

        public ModuleId id() {
            return id;
        }

        public Set<ModuleId> aliases() {
            return aliases;
        }

        public Set<String> exports() {
            return exports;
        }

        public Set<String> permits() {
            return permits;
        }

        public Map<String,Set<String>> services() {
            return services;
        }

        public String mainClass() {
            return mainClass;
        }

        @Override
        public String toString() {
            return "View { id: " + id
                    + ", provides: " + aliases
                    + ", provides service: " + services
                    + ", permits: " + permits
                    + ", mainClass: " + mainClass
                    + " }";
        }
    }

    static class ConstantPool {

        private static class Entry {
            protected Entry(int tag) {
                this.tag = tag;
            }
            final int tag;
        }

        private static class IndexEntry extends Entry {

            IndexEntry(int tag, int index) {
                super(tag);
                this.index = index;
            }
            final int index;
        }

        private static class Index2Entry extends Entry {

            Index2Entry(int tag, int index1, int index2) {
                super(tag);
                this.index1 = index1;
                this.index2 = index2;
            }
            final int index1,  index2;
        }

        private static class ValueEntry extends Entry {

            ValueEntry(int tag, Object value) {
                super(tag);
                this.value = value;
            }
            final Object value;
        }

        private static final int CONSTANT_Utf8 = 1;
        private static final int CONSTANT_Integer = 3;
        private static final int CONSTANT_Float = 4;
        private static final int CONSTANT_Long = 5;
        private static final int CONSTANT_Double = 6;
        private static final int CONSTANT_Class = 7;
        private static final int CONSTANT_String = 8;
        private static final int CONSTANT_Fieldref = 9;
        private static final int CONSTANT_Methodref = 10;
        private static final int CONSTANT_InterfaceMethodref = 11;
        private static final int CONSTANT_NameAndType = 12;
        private final static int CONSTANT_MethodHandle = 15;
        private final static int CONSTANT_MethodType = 16;
        private final static int CONSTANT_InvokeDynamic = 18;
        private final static int CONSTANT_ModuleId = 19;
        private final static int CONSTANT_ModuleQuery = 20;

        private final ModuleSystem ms;

        ConstantPool(ModuleSystem ms, DataInputStream in)
            throws IOException
        {
            this.ms = ms;
            int count = in.readUnsignedShort();
            pool = new Entry[count];

            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case CONSTANT_Class:
                    case CONSTANT_String:
                        int index = in.readUnsignedShort();
                        pool[i] = new IndexEntry(tag, index);
                        break;

                    case CONSTANT_Double:
                        double dvalue = in.readDouble();
                        pool[i] = new ValueEntry(tag, dvalue);
                        i++;
                        break;

                    case CONSTANT_Fieldref:
                    case CONSTANT_InterfaceMethodref:
                    case CONSTANT_Methodref:
                    case CONSTANT_InvokeDynamic:
                    case CONSTANT_ModuleId:
                    case CONSTANT_ModuleQuery:
                    case CONSTANT_NameAndType:
                        int index1 = in.readUnsignedShort();
                        int index2 = in.readUnsignedShort();
                        pool[i] = new Index2Entry(tag, index1, index2);
                        break;

                    case CONSTANT_MethodHandle:
                        int refKind = in.readUnsignedByte();
                        index = in.readUnsignedShort();
                        pool[i] = new Index2Entry(tag, refKind, index);
                        break;

                    case CONSTANT_MethodType:
                        index = in.readUnsignedShort();
                        pool[i] = new IndexEntry(tag, index);
                        break;

                    case CONSTANT_Float:
                        float fvalue = in.readFloat();
                        pool[i] = new ValueEntry(tag, fvalue);
                        break;

                    case CONSTANT_Integer:
                        int ivalue = in.readInt();
                        pool[i] = new ValueEntry(tag, ivalue);
                        break;

                    case CONSTANT_Long:
                        long lvalue = in.readLong();
                        pool[i] = new ValueEntry(tag, lvalue);
                        i++;
                        break;

                    case CONSTANT_Utf8:
                        String svalue = in.readUTF();
                        pool[i] = new ValueEntry(tag, svalue);
                        break;

                    default:
                        throw new IllegalArgumentException("bad constant pool entry " + i);
                }
            }
        }

        private Entry[] pool;

        String getClassName(int index) {
            Entry e = pool[index];
            assert e.tag == CONSTANT_Class;
            return getUtf8(((IndexEntry) e).index);
        }

        private ModuleId getModuleId(int index) {
            Entry e = pool[index];
            assert e.tag == CONSTANT_ModuleId;
            Index2Entry i2e = (Index2Entry) e;
            // module name is in internal form (JVMS 4.2.1)
            String name = getUtf8(i2e.index1).replace('/', '.');
            String version = (i2e.index2 == 0 ? null :getUtf8(i2e.index2));
            return new ModuleId(name, ms.parseVersion(version));
        }

        private ModuleIdQuery getModuleIdQuery(int index) {
            Entry e = pool[index];
            assert e.tag == CONSTANT_ModuleQuery;
            Index2Entry i2e = (Index2Entry) e;
            // module name is in internal form (JVMS 4.2.1)
            String name = getUtf8(i2e.index1).replace('/', '.');
            VersionQuery vq = (i2e.index2 == 0
                                   ? null
                                   : ms.parseVersionQuery(getUtf8(i2e.index2)));
            return new ModuleIdQuery(name, vq);
        }

        String getUtf8(int index) {
            Entry e = pool[index];
            assert e.tag == CONSTANT_Utf8;
            return (String) (((ValueEntry) e).value);
        }

        Object getValue(int index) {
            Entry e = pool[index];
            assert e.tag == CONSTANT_Double ||
                   e.tag == CONSTANT_Float ||
                   e.tag == CONSTANT_Integer ||
                   e.tag == CONSTANT_Long;
            return ((ValueEntry) e).value;
        }
    }
}
