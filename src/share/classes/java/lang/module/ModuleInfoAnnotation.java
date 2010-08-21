/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.lang.annotation.Annotation;
import java.lang.annotation.AnnotationFormatError;
import java.lang.annotation.AnnotationTypeMismatchException;
import java.lang.module.ModuleInfoReader.ConstantPool;
import java.lang.reflect.*;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import sun.reflect.annotation.*;
import sun.reflect.generics.parser.SignatureParser;
import sun.reflect.generics.factory.GenericsFactory;
import sun.reflect.generics.factory.CoreReflectionFactory;
import sun.reflect.generics.visitor.Reifier;
import sun.reflect.generics.visitor.TypeTreeVisitor;
import sun.reflect.generics.tree.*;

/**
 * Represents an annotation in module-info.class. This class is
 * based on the com.sun.tools.classfile library.
 *
 */
/* package */ class ModuleInfoAnnotation {
    private final int typeIndex;
    private final int numElements;
    private final String typeSig;
    private final String typeName;
    private final Set<Element> elements;

    /**
     * Constructs a ModuleInfoAnnotation by parsing the bytes from
     * the specified input stream and resolving constant references in
     * the specified constant pool.
     *
     * @params in    Data input stream for reading an annotation
     * @params cpool Constant pool of module-info.class
     */
    ModuleInfoAnnotation(DataInputStream in, ConstantPool cpool) throws IOException {
        this.typeIndex = in.readUnsignedShort();
        this.numElements = in.readUnsignedShort();

        this.typeSig = cpool.getUtf8(typeIndex);
        // convert the field descriptor to the fully qualified class name
        this.typeName = typeSig.substring(1, typeSig.length()-1).replace('/', '.');
        this.elements = new HashSet<Element>();
        for (int i = 0; i < numElements; i++)
            elements.add(new Element(in, cpool));
    }

    /**
     * Returns the annotation type name.
     */
    String getName() {
        return typeName;
    }

    /**
     * Returns an Annotation object that doesn't support element of type
     * Class; attempting to read a Class object by invoking the relevant
     * method on the returned annotation will result in a
     * UnsupportedElementTypeException.
     *
     * This method is called by the  ModuleInfo.getAnnotation() method;
     */
    <T extends Annotation> T generateAnnotation(Class<T> annotationClass) {
        return generateAnnotation(annotationClass, false);
    }

    /**
     * Returns an Annotation object created by the core reflection.
     * If supportClassElementType is false, attempting to read a Class
     * object by invoking the relevant method on the returned annotation
     * will result in a UnsupportedElementTypeException.
     *
     * @param annotationClass annotation class
     * @param supportClassElementType supports element of Class type
     */
    <T extends Annotation> T generateAnnotation(Class<T> annotationClass,
                                                boolean supportClassElementType) {
        AnnotationType type = AnnotationType.getInstance(annotationClass);
        Map<String, Class<?>> memberTypes = type.memberTypes();
        Map<String, Object> memberValues =
            new LinkedHashMap<String, Object>(type.memberDefaults());

        // A visitor to obtain the value of an element
        ElementValueVisitor elementVisitor =
            new ElementValueVisitor(annotationClass, supportClassElementType);

        for (Element e : elements) {
            String memberName = e.elementName;
            Class<?> memberType = memberTypes.get(memberName);
            if (memberType != null) {
                Object value = elementVisitor.getValue(e, memberType);
                memberValues.put(memberName, value);
            }
        }

        // replace the default value of elements of Class type
        // to UnsupportedElementTypeExceptionProxy if it doesn't support
        // elements of Class type
        if (!supportClassElementType) {
            for (Map.Entry<String, Class<?>> e : memberTypes.entrySet()) {
                String memberName = e.getKey();
                Class<?> memberType = e.getValue();
                Object value = memberValues.get(memberName);
                if (memberType == Class.class && !(value instanceof ExceptionProxy)) {
                    value = new DefaultValueExceptionProxy(Class.class.cast(value));
                    memberValues.put(memberName, value);
                } else if (memberType.isArray() &&
                           memberType.getComponentType() == Class.class) {
                    if (!(value instanceof ExceptionProxy)) {
                        value = new DefaultValueExceptionProxy((Class<?>[]) value);
                        memberValues.put(memberName, value);
                    }
                }
            }
        }

        Annotation a = AnnotationParser.annotationForMap(annotationClass, memberValues);
        return annotationClass.cast(a);
    }

    /**
     * Returns Annotation object for a java.lang.reflect.Module
     */
    Annotation getAnnotation(Module module) {
        Class<? extends Annotation> annotationClass = null;
        try {
            annotationClass = (Class<? extends Annotation>) parseSig(typeSig, module);
        } catch (NoClassDefFoundError e) {
            throw new TypeNotPresentException(typeSig, e);
        }
        return generateAnnotation(annotationClass, true);
    }

    /**
     * Returns a Class object of the given signature loaded by
     * the module class loader of the given Module.
     */
    private static Class<?> parseSig(String sig, Module module) {
        if (sig.equals("V")) return void.class;
        SignatureParser parser = SignatureParser.make();
        TypeSignature typeSig = parser.parseTypeSig(sig);
        GenericsFactory factory = CoreReflectionFactory.make(module);
        Reifier reify = Reifier.make(factory);
        typeSig.accept(reify);
        Type result = reify.getResult();
        return toClass(result);
    }

    private static Class<?> toClass(Type o) {
        if (o instanceof GenericArrayType)
            return Array.newInstance(toClass(((GenericArrayType)o).getGenericComponentType()),
                                     0)
                .getClass();
        return (Class)o;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("@").append(typeName).append("(");
        String sep = "";
        for (Element e : elements) {
           sb.append(sep);
           sb.append(e.elementName).append("=").append(e.value.toString());
           sep = ", ";
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * element_value_pair structure
     */
    static class Element {
        Element (DataInputStream in, ConstantPool cpool)
                throws IOException {
            this.elementNameIndex = in.readUnsignedShort();
            this.elementName = cpool.getUtf8(elementNameIndex);
            this.value = ElementValue.read(in, cpool);
        }

        final int elementNameIndex;
        final String elementName;
        final ElementValue value;
    }

    /**
     * element_value structure
     */
    static abstract class ElementValue {
        static ElementValue read(DataInputStream in, ConstantPool cpool)
                throws IOException {
            int tag = in.readUnsignedByte();
            switch (tag) {
            case 'B':
            case 'C':
            case 'D':
            case 'F':
            case 'I':
            case 'J':
            case 'S':
            case 'Z':
            case 's':
                return new PrimitiveElementValue(in, tag, cpool);

            case 'e':
                return new EnumElementValue(in, tag, cpool);

            case 'c':
                return new ClassElementValue(in, tag, cpool);

            case '@':
                return new AnnotationElementValue(in, tag, cpool);

            case '[':
                return new ArrayElementValue(in, tag, cpool);

            default:
                throw new ClassFormatError("unrecognized tag: " + tag);
            }
        }

        protected ElementValue(int tag) {
            this.tag = tag;
        }

        abstract <R,P> R accept(Visitor<R,P> visitor, P p);

        interface Visitor<R,P> {
            R visitPrimitive(PrimitiveElementValue ev, P p);
            R visitEnum(EnumElementValue ev, P p);
            R visitClass(ClassElementValue ev, P p);
            R visitAnnotation(AnnotationElementValue ev, P p);
            R visitArray(ArrayElementValue ev, P p);
        }

        final int tag;
    }

    static class PrimitiveElementValue extends ElementValue {
        PrimitiveElementValue(DataInputStream in, int tag, ConstantPool cpool) throws IOException {
            super(tag);
            this.constValueIndex = in.readUnsignedShort();
            Integer v;
            switch(tag) {
              case 'B':
                v = (Integer) cpool.getValue(constValueIndex);
                this.constValue = new Byte(v.byteValue());
                break;
              case 'C':
                v = (Integer) cpool.getValue(constValueIndex);
                this.constValue = new Character((char) v.shortValue());
                break;
              case 'S':
                v = (Integer) cpool.getValue(constValueIndex);
                this.constValue = new Short(v.shortValue());
                break;
              case 'Z':
                v = (Integer) cpool.getValue(constValueIndex);
                this.constValue = v.intValue() == 1 ? Boolean.TRUE : Boolean.FALSE;
                break;
              case 'D':
              case 'F':
              case 'I':
              case 'J':
                this.constValue = cpool.getValue(constValueIndex);
                break;
              case 's':
                this.constValue = cpool.getValue(constValueIndex);
                break;
              default:
                throw new AnnotationFormatError(
                    "Invalid member-value tag in annotation: " + (char) tag);
            }
        }

        <R,P> R accept(Visitor<R,P> visitor, P p) {
            return visitor.visitPrimitive(this, p);
        }

        @Override
        public String toString() {
            return constValue.getClass().getName() + " value " + constValue;
        }

        final int constValueIndex;
        final Object constValue;
    }

    static class EnumElementValue extends ElementValue {
        EnumElementValue(DataInputStream in, int tag, ConstantPool cpool) throws IOException {
            super(tag);
            typeNameIndex = in.readUnsignedShort();
            constNameIndex = in.readUnsignedShort();
            this.typeName = cpool.getUtf8(typeNameIndex);
            this.constName = cpool.getUtf8(constNameIndex);
        }

        public <R,P> R accept(Visitor<R,P> visitor, P p) {
            return visitor.visitEnum(this, p);
        }

        @Override
        public String toString() {
            return typeName + "." + constName;
        }

        final int typeNameIndex;
        final int constNameIndex;
        final String typeName;
        final String constName;
    }

    static class ClassElementValue extends ElementValue {
        ClassElementValue(DataInputStream in, int tag, ConstantPool cpool) throws IOException {
            super(tag);
            this.classInfoIndex = in.readUnsignedShort();
            this.className = cpool.getUtf8(classInfoIndex);
        }

        <R,P> R accept(Visitor<R,P> visitor, P p) {
            return visitor.visitClass(this, p);
        }

        final int classInfoIndex;
        final String className;

        @Override
        public String toString() {
            return className;
        }
    }

    static class AnnotationElementValue extends ElementValue {
        AnnotationElementValue(DataInputStream in, int tag, ConstantPool cpool)
                throws IOException {
            super(tag);
            annotationValue = new ModuleInfoAnnotation(in, cpool);
        }

        <R,P> R accept(Visitor<R,P> visitor, P p) {
            return visitor.visitAnnotation(this, p);
        }

        final ModuleInfoAnnotation annotationValue;

        @Override
        public String toString() {
            return annotationValue.toString();
        }
    }

    static class ArrayElementValue extends ElementValue {
        ArrayElementValue(DataInputStream in, int tag, ConstantPool cpool)
                throws IOException {
            super(tag);
            numValues = in.readUnsignedShort();
            values = new ElementValue[numValues];
            for (int i = 0; i < values.length; i++)
                values[i] = ElementValue.read(in, cpool);
        }

        <R,P> R accept(Visitor<R,P> visitor, P p) {
            return visitor.visitArray(this, p);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (ElementValue v : values) {
                sb.append(v.toString()).append(" ");
            }
            sb.append("]");
            return sb.toString();
        }

        final int numValues;
        final ElementValue[] values;
    }

    // proxy exception
    private static class UnsupportedElementTypeExceptionProxy extends ExceptionProxy {
        private List<String> typeSigs;

        UnsupportedElementTypeExceptionProxy(String sig) {
            this.typeSigs = Collections.singletonList(sig);
        }

        UnsupportedElementTypeExceptionProxy(List<String> sigs) {
            this.typeSigs = Collections.unmodifiableList(sigs);
        }

        protected RuntimeException generateException() {
            List<String> names = new ArrayList<String>();
            for (String sig : typeSigs) {
                names.add(parseTypeSignature(sig));
            }
            return new UnsupportedElementTypeException(names);
        }

        /*
         * Returns a string representing the type in the source code if possible.
         */
        private String parseTypeSignature(String sig) {
            if (sig.equals("V")) return "Void";
            SignatureParser parser = SignatureParser.make();
            TypeSignature typeSig = parser.parseTypeSig(sig);
            TypeVisitor visitor = new TypeVisitor();
            typeSig.accept(visitor);
            return visitor.getResult();
        }
    }

    private static class DefaultValueExceptionProxy extends ExceptionProxy {
        private List<String> typeNames = new ArrayList<String>();

        DefaultValueExceptionProxy(Class<?> cls) {
            typeNames.add(cls.getName());
        }

        DefaultValueExceptionProxy(Class<?>[] classes) {
            for (Class<?> c : classes) {
               typeNames.add(c.getName());
            }
        }

        protected RuntimeException generateException() {
            return new UnsupportedElementTypeException(typeNames);
        }
    }

    // Visitor to get the reified type name
    private static class TypeVisitor implements TypeTreeVisitor<String> {
        private String resultType = "";
        public String getResult() {
            return resultType;
        }

        // Visitor methods, per node type
        public void visitFormalTypeParameter(FormalTypeParameter ftp) {
            throw new AssertionError("Should not reach here");
        }

        // copy from sun.reflect.generics.visitor.Reifier
        public void visitClassTypeSignature(ClassTypeSignature ct) {
            // extract iterator on list of simple class type sigs
            List<SimpleClassTypeSignature> scts = ct.getPath();
            assert(!scts.isEmpty());
            Iterator<SimpleClassTypeSignature> iter = scts.iterator();
            SimpleClassTypeSignature sc = iter.next();
            StringBuilder n = new StringBuilder(sc.getName());
            boolean dollar = sc.getDollar();

            // phase 1: iterate over simple class types until
            // we are either done or we hit one with non-empty type parameters
            while (iter.hasNext() && sc.getTypeArguments().length == 0) {
                sc = iter.next();
                dollar = sc.getDollar();
                n.append(dollar?"$":".").append(sc.getName());
            }

            // Now, either sc is the last element of the list, or
            // it has type arguments (or both)
            assert(!(iter.hasNext()) || (sc.getTypeArguments().length > 0));
            // if there are no type arguments
            if (sc.getTypeArguments().length == 0) {
                //we have surely reached the end of the path
                assert(!iter.hasNext());
                resultType = n.toString(); // the result is the raw type
            } else {
                assert(sc.getTypeArguments().length > 0);

                // phase 2: iterate over remaining simple class types
                dollar =false;
                while (iter.hasNext()) {
                    sc = iter.next();
                    dollar = sc.getDollar();
                    n.append(dollar?"$":".").append(sc.getName()); // build up raw class name
                }
                resultType = n.toString();
            }
        }

        public void visitArrayTypeSignature(ArrayTypeSignature a) {
            // extract and reify component type
            a.getComponentType().accept(this);
            resultType = resultType + "[]";
        }

        public void visitTypeVariableSignature(TypeVariableSignature tv) {
            throw new AssertionError("Should not reach here");
        }
        public void visitWildcard(Wildcard w) {
            throw new AssertionError("Should not reach here");
        }

        public void visitSimpleClassTypeSignature(SimpleClassTypeSignature sct) {
            resultType = sct.getName();
        }

        public void visitBottomSignature(BottomSignature b) {
            throw new AssertionError("Should not reach here");
        }

        //  Primitives and Void
        public void visitByteSignature(ByteSignature b) {
            resultType = "byte";
        }
        public void visitBooleanSignature(BooleanSignature b) {
            resultType = "boolean";
        }
        public void visitShortSignature(ShortSignature s) {
            resultType = "short";
        }
        public void visitCharSignature(CharSignature c) {
            resultType = "char";
        }
        public void visitIntSignature(IntSignature i) {
            resultType = "int";
        }
        public void visitLongSignature(LongSignature l) {
            resultType = "long";
        }
        public void visitFloatSignature(FloatSignature f) {
            resultType = "float";
        }
        public void visitDoubleSignature(DoubleSignature d) {
            resultType = "double";
        }

        public void visitVoidDescriptor(VoidDescriptor v) {
            resultType = "Void";
        }
    }

    private static class ElementValueVisitor implements ElementValue.Visitor<Object, Class<?>> {
        private final Class<? extends Annotation> annotationClass;
        private final boolean supportClassElementType;
        private String mismatchType = "unknown";
        ElementValueVisitor(Class<? extends Annotation> annotationClass,
                            boolean supportClassElementType) {
            this.annotationClass = annotationClass;
            this.supportClassElementType = supportClassElementType;
        }

        Object getValue(Element e, Class<?> elementType) {
            Object value;
            try {
                mismatchType = "unknown";
                value = e.value.accept(this, elementType);
            } catch (IllegalArgumentException ex) {
                // indicates a type mismatch
                Method method = null;
                try {
                    method = annotationClass.getMethod(e.elementName);
                } catch (NoSuchMethodException x) {
                    throw new AssertionError("should not reach here");
                }
                final Method m = method;
                value = new ExceptionProxy() {
                    static final long serialVersionUID = 269;
                    public String toString() {
                        return "<error>";   // eg:  @Anno(value=<error>)
                    }
                    protected RuntimeException generateException() {
                        return new AnnotationTypeMismatchException(m, mismatchType);
                    }
                };
            }
            return value;
        }

        private Object parse(ElementValue value, Class<?> elementType) {
            return value.accept(this, elementType);
        }

        public Object visitPrimitive(PrimitiveElementValue ev, Class<?> elementType) {
            return ev.constValue;
        }

        public Object visitEnum(EnumElementValue ev, Class<?> elementType) {
            Class<? extends Enum> enumType = (Class<? extends Enum>) elementType;
            Object value;
            try {
                value = Enum.valueOf(enumType, ev.constName);
            } catch (IllegalArgumentException ex) {
                value = new EnumConstantNotPresentExceptionProxy(enumType, ev.constName);
            }
            return value;
        }

        public Object visitClass(ClassElementValue ev, Class<?> elementType) {
            if (supportClassElementType) {
                try {
                    return parseSig(ev.className, annotationClass.getModule());
                } catch (NoClassDefFoundError e) {
                    return new TypeNotPresentExceptionProxy(ev.className, e);
                }
            } else {
                return new UnsupportedElementTypeExceptionProxy(ev.className);
            }
        }

        public Object visitAnnotation(AnnotationElementValue ev, Class<?> elementType) {
            Class<? extends Annotation> type = (Class<? extends Annotation>) elementType;
            return ev.annotationValue.generateAnnotation(type);
        }

        public Object visitArray(ArrayElementValue ev, Class<?> elementType) {
            Class<?> componentType = elementType.getComponentType();
            int length = ev.numValues;
            if (componentType == Class.class && !supportClassElementType) {
                List<String> elems = new ArrayList<String>();
                for (int i = 0; i < length; i++) {
                    ClassElementValue e = (ClassElementValue) ev.values[i];
                    elems.add(e.className);
                }
                return new UnsupportedElementTypeExceptionProxy(elems);
            } else {
                Object result = Array.newInstance(componentType, length);
                for (int i = 0; i < length; i++) {
                    Object value = parse(ev.values[i], componentType);
                    if (value == null || value instanceof ExceptionProxy) {
                        return value;
                    }
                    try {
                        Array.set(result, i, value);
                    } catch (IllegalArgumentException e) {
                        // type mismatch
                        mismatchType = value.getClass().getName();
                        return e;
                    }
                }
                return result;
            }
        }
    }
}
