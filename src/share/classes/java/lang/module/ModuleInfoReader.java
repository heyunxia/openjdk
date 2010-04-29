/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.lang.module;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.module.Dependence;
import java.lang.module.ModuleId;
import java.lang.module.ModuleIdQuery;
import java.lang.module.ModuleInfo;
import java.lang.module.VersionQuery;
import java.security.AccessController;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.EnumSet;
import java.util.Set;
import java.util.Map;


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
    private Set<ModuleId> provides = new LinkedHashSet<ModuleId>();
    private Set<Dependence> requires = new LinkedHashSet<Dependence>();
    private Set<String> permits = new LinkedHashSet<String>();
    private String mainClass;
    private Map<String, ModuleInfoAnnotation> annotationTypes = new LinkedHashMap<String, ModuleInfoAnnotation>();

    // ## Not surfaced in ModuleInfo interface; should probably be removed
    private Set<String> mainClassModifiers = new LinkedHashSet<String>();

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

            moduleInfo = new ModuleInfoImpl(moduleId, provides,
                    requires, permits, mainClass,
                    annotationTypes);

        } catch (IOException e) {
            throw new Error(e);
        }
    }

    /* package */ ModuleInfo moduleInfo() { return moduleInfo; }

    private static final String MODULE = "Module";
    private static final String MODULE_PROVIDES = "ModuleProvides";
    private static final String MODULE_REQUIRES = "ModuleRequires";
    private static final String MODULE_PERMITS = "ModulePermits";
    private static final String MODULE_CLASS = "ModuleClass";
    private static final String RUNTIME_VISABLE_ANNOTATION = "RuntimeVisibleAnnotations";
    private static final String RUNTIME_INVISABLE_ANNOTATION = "RuntimeInvisibleAnnotations";

    private void readAttributes() throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            int nameIndex = in.readUnsignedShort();
            String name = cpool.getUtf8(nameIndex);
            int length = in.readInt();
            if (name.equals(MODULE))
                readModule();
            else if (name.equals(MODULE_PROVIDES))
                readModuleProvides();
            else if (name.equals(MODULE_REQUIRES))
                readModuleRequires();
            else if (name.equals(MODULE_PERMITS))
                readModulePermits();
            else if (name.equals(MODULE_CLASS))
                readModuleClass();
            else if (name.equals(RUNTIME_VISABLE_ANNOTATION)) {
                readAnnotations();
            } else {
                in.skip(length);
            }
        }
    }

    private void readModule() throws IOException {
        int index = in.readUnsignedShort();
        moduleId = cpool.getModuleId(index);
    }

    private void readModuleProvides() throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            provides.add(cpool.getModuleId(in.readUnsignedShort()));
        }
    }

    private void readModuleRequires() throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            int index = in.readUnsignedShort();
            int length = in.readUnsignedShort();
            EnumSet<Dependence.Modifier> mods = EnumSet.noneOf(Dependence.Modifier.class);
            for (int q = 0; q < length; q++) {
                mods.add(Enum.valueOf(Dependence.Modifier.class,
                                      cpool.getUtf8(in.readUnsignedShort()).toUpperCase()));
            }
            requires.add(new Dependence(mods, cpool.getModuleIdQuery(index)));
        }
    }

    private void readModulePermits() throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            permits.add(cpool.getUtf8(in.readUnsignedShort()));
        }
    }

    private void readModuleClass() throws IOException {
        int index = in.readUnsignedShort();
        mainClass = cpool.getClassName(index).replace('/', '.');
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            mainClassModifiers.add(cpool.getUtf8(in.readUnsignedShort()));
        }
    }

    private void readAnnotations() throws IOException {
        int count = in.readUnsignedShort(); 
        for (int i=0; i < count; i++) {
            ModuleInfoAnnotation at = new ModuleInfoAnnotation(in, cpool);
            annotationTypes.put(at.getName(), at);
        }
    }

    public static class ModuleInfoImpl
        implements ModuleInfo
    {

        private ModuleId id;
        private Set<Dependence> requires;
        private Set<ModuleId> provides;
        private Set<String> permits;
        private String mainClass;
        private Map<String, ModuleInfoAnnotation> annotationTypes;

        ModuleInfoImpl(ModuleId id,
                Set<ModuleId> provides,
                Set<Dependence> requires,
                Set<String> permits,
                String mainClass,
                Map<String, ModuleInfoAnnotation> annotationTypes)
        {
            this.id = id;
            this.provides = provides;
            this.requires = requires;
            this.permits = permits;
            this.mainClass = mainClass;
            this.annotationTypes = annotationTypes;
        }

        public ModuleId id() {
            return id;
        }

        public Set<Dependence> requires() {
            // ## Temporarily allow this to be modifiable so that
            // ## platform-default dependences can be added at
            // ## configuration time.  Undo this once we start
            // ## adding those defaults at compile time.
            //return Collections.unmodifiableSet(requires);
            return requires;
        }

        public Set<ModuleId> provides() {
            return Collections.unmodifiableSet(provides);
        }

        public Set<String> permits() {
            return Collections.unmodifiableSet(permits);
        }

        public String mainClass() {
            return mainClass;
        }

        public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
            if (annotationClass == null)
                throw new NullPointerException("Argument annotationClass is null");
            return annotationTypes.containsKey(annotationClass.getName()); 
        }

        public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
            if (annotationClass == null)
                throw new NullPointerException("Argument annotationClass is null");
            ModuleInfoAnnotation at = annotationTypes.get(annotationClass.getName());
            if (at == null) {
                return null;
            }
            return at.generateAnnotation(annotationClass);
        }

        @Override
        public String toString() {
            return "ModuleInfo { id: " + id
                    + ", " + requires
                    + ", provides: " + provides
                    + ", permits: " + permits
                    + ", mainClass: " + mainClass
                    + " }";
        }

        Iterable<ModuleInfoAnnotation> getAnnotationTypes() {
            return annotationTypes.values();
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
        private static final int CONSTANT_ModuleId = 13;

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
                    case CONSTANT_ModuleId:
                    case CONSTANT_NameAndType:
                        int index1 = in.readUnsignedShort();
                        int index2 = in.readUnsignedShort();
                        pool[i] = new Index2Entry(tag, index1, index2);
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
            // ## Why do module names use '/' instead of '.'?
            String name = getUtf8(i2e.index1).replace('/', '.');
            String version = (i2e.index2 == 0 ? null : getUtf8(i2e.index2));
            return new ModuleId(name, ms.parseVersion(version));
        }

        private ModuleIdQuery getModuleIdQuery(int index) {
            Entry e = pool[index];
            assert e.tag == CONSTANT_ModuleId;
            Index2Entry i2e = (Index2Entry) e;
            String name = getUtf8(i2e.index1);
            VersionQuery vq
                = (i2e.index2 == 0 ? null : ms.parseVersionQuery(getUtf8(i2e.index2)));
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

    private static void setJavaLangModuleAccess() {
        // Allow privileged classes outside of java.lang
        sun.misc.SharedSecrets.setJavaLangModuleAccess(new sun.misc.JavaLangModuleAccess() {
            public Iterable<Annotation> getAnnotations(ModuleInfo mi, java.lang.reflect.Module m) {
                Set<Annotation> result = new LinkedHashSet<Annotation>();
                ModuleInfoImpl miImpl = (ModuleInfoImpl) mi;
                for (ModuleInfoAnnotation mia : miImpl.getAnnotationTypes()) {
                    result.add(mia.getAnnotation(m));
                }
                return result;
            }
        });
    }

    static {
        setJavaLangModuleAccess();
    }
}
