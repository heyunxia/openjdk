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

import java.util.Set;
import java.lang.annotation.*;

/**
 * <p> Information about a module, as found in a {@code module-info.java}
 * source file or a {@code module-info.class} class file </p>
 *
 */

public interface ModuleInfo {

    /**
     * This module's identifier
     */
    public ModuleId id();

    /**
     * <p> The identifiers of the virtual modules provided by this module </p>
     *
     * @return  A possibly-empty unmodifiable set of {@link ModuleId ModuleIds}
     */
    public Set<ModuleId> provides();

    /**
     * <p> The dependences of this module </p>
     *
     * @return  A possibly-empty unmodifiable set of {@link Dependence Dependences}
     */
    public Set<Dependence> requires();

    /**
     * <p> The names of modules that are permitted to require this module </p>
     *
     * @return  A possibly-empty unmodifiable set of module names
     */
    public Set<String> permits();

    /**
     * <p> The fully qualified name of the main class of this module </p>
     *
     * @return  The fully qualified name of the main class of this module, or {@code null}
     *          if this module does not have a main class
     */
    public String mainClass();

    //  -- AnnotatedElement methods --

    /**
     * Returns true if an annotation for the specified type
     * is present on this module, else false.
     *
     * @param annotationClass the Class object corresponding to the
     *        annotation type
     * @return true if an annotation for the specified annotation
     *     type is present on this module, else false
     *
     * @see java.lang.reflect.AnnotatedElement#isAnnotationPresent
     */
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass);

    /**
     * Returns an annotation for the specified type on this module,
     * if such an annotation is present, else {@code null}.
     *
     * <p> The annotation returned by this method could contain an element
     * whose value is of type {@code Class}.
     * This value cannot be returned directly:  information necessary to
     * locate and load a class (such as the class loader to use) is
     * not available, and the class might not be loadable at all.
     * Attempting to read a {@code Class} object by invoking the relevant
     * method on the returned annotation
     * will result in a {@link UnsupportedElementTypeException},
     * from which the corresponding type may be extracted.
     * Similarly, attempting to read a {@code Class[]}-valued element
     * will result in a {@link UnsupportedElementTypeException},
     *
     * <p> Calling methods on the returned annotation object 
     * can throw many of the exceptions that can be thrown when calling 
     * methods on an annotation object returned by {@link 
     * java.lang.reflect.AnnotatedElement core reflection}.
     *
     * @param <A>  the annotation type
     * @param annotationType  the {@code Class} object corresponding to
     *          the annotation type
     * @return this module's annotation for the 
     *         specified annotation type if present on this element, 
     *         else {@code null}
     *
     * @see java.lang.reflect.AnnotatedElement#getAnnotation
     * @see EnumConstantNotPresentException
     * @see AnnotationTypeMismatchException
     * @see IncompleteAnnotationException
     */
    public <A extends Annotation> A getAnnotation(Class<A> annotationType);

}
