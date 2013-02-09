/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.reflect;

import java.lang.annotation.Annotation;
import java.lang.module.ModuleClassLoader;
import java.lang.module.ModuleId;
import java.lang.module.ModuleInfo;
import java.lang.module.Version;
import java.security.CodeSource;

//
// ## Module is an AccessibleObject but it may not be annotated?
//
public final class Module
    extends AccessibleObject
{

    private ModuleInfo moduleInfo;
    private ModuleClassLoader loader;
    private final CodeSource codeSource;

    /**
     * Package-private constructor used by ReflectAccess to enable
     * instantiation of these objects in Java code from the java.lang
     * package via sun.reflect.LangReflectAccess.
     */
    Module(ModuleInfo mi, ModuleClassLoader ld, CodeSource cs) {
        loader = ld;
        moduleInfo = mi;
        codeSource = cs;
    }

    public ModuleClassLoader getClassLoader() {
        return loader;
    }

    public ModuleInfo getModuleInfo() {
        return moduleInfo;
    }

    public CodeSource getCodeSource() {
        return codeSource;
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
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
        if (annotationClass == null)
            throw new NullPointerException();

        return null;
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
        return new Annotation[0];
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
