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

package java.lang.reflect;

import java.lang.annotation.Annotation;
import java.lang.annotation.AnnotationFormatError;
import java.lang.annotation.RetentionPolicy;
import java.lang.module.ModuleClassLoader;
import java.lang.module.ModuleId;
import java.lang.module.ModuleInfo;
import java.lang.module.Version;
import java.util.Map;
import java.util.LinkedHashMap;
import sun.reflect.annotation.AnnotationType;

public final class Module
    implements AnnotatedElement
{

    private ModuleInfo moduleInfo;
    private ModuleClassLoader loader;

    /**
     * Package-private constructor used by ReflectAccess to enable
     * instantiation of these objects in Java code from the java.lang
     * package via sun.reflect.LangReflectAccess.
     */
    Module(ModuleInfo mi, ModuleClassLoader ld) {
        loader = ld;
        moduleInfo = mi;
    }

    public ModuleClassLoader getClassLoader() {
        return loader;
    }

    public ModuleInfo getModuleInfo() {
        return moduleInfo;
    }

    // Convenience methods

    public ModuleId getModuleId() {
        return moduleInfo.id();
    }

    public String getName() {
        return moduleInfo.id().name();
    }

    public Version getVersion() {
        return moduleInfo.id().version();
    }


    //  -- AnnotatedElement methods --

    /**
     * {@inheritDoc}
     */
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return getAnnotation(annotationClass) != null;
    }

    /**
     * {@inheritDoc}
     */
    public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
        if (annotationClass == null)
            throw new NullPointerException();

        return (A) annotationsMap().get(annotationClass);
    }

    /**
     * {@inheritDoc}
     */
    public Annotation[] getAnnotations() {
        // no inherited annotations
        return getDeclaredAnnotations();
    }

    /**
     * {@inheritDoc}
     */
    public Annotation[] getDeclaredAnnotations() {
        return annotationsMap.values().toArray(new Annotation[0]);
    }

    private transient Map<Class<? extends Annotation>, Annotation> annotationsMap;
    // Returns the cached annotations
    private synchronized  Map<Class<? extends Annotation>, Annotation> annotationsMap() {
        if (annotationsMap != null)
            return annotationsMap;

        // module-info.class is not loaded in the VM as a Class object
        // we can't use sun.reflect.annotation.AnnotationParser here 
        annotationsMap = new LinkedHashMap<Class<? extends Annotation>, Annotation>();
        for (Annotation a: sun.misc.SharedSecrets.
                               getJavaLangModuleAccess().getAnnotations(moduleInfo, this)) {
            Class<? extends Annotation> klass = a.annotationType();
            AnnotationType type = AnnotationType.getInstance(klass);
            if (type.retention() == RetentionPolicy.RUNTIME) {
                if (annotationsMap.put(klass, a) != null) {
                    throw new AnnotationFormatError(
                        "Duplicate annotation for class: "+klass+": " + a);
                }
            }
        }
        return annotationsMap;
    }

    // ## EHS

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName());
        sb.append("(").append(moduleInfo.id()).append(")");
        return sb.toString();
    }

}
