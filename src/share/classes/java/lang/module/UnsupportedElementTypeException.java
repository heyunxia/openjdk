/*
 * Copyright 2005-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;


/**
 * Thrown when an application attempts to read a {@code Class} 
 * object or a Class[]-valued element by invoking the relevant method 
 * on an annotation returned by the {@link java.lang.module.ModuleInfo#getAnnotation}
 * method.
 *
 * @see java.lang.module.ModuleInfo#getAnnotation(Class)
 * @since 1.7
 */
public class UnsupportedElementTypeException extends RuntimeException {

    private static final long serialVersionUID = 6548519553178935875L;
    private List<String> classnames;

    /**
     * Constructs a new UnsupportedElementTypeException.
     *
     * @param classnames a list of fully-qualified class name
     * specified in the element being accessed.
     */
    public UnsupportedElementTypeException(String classname) {
        super("Attempt to access Class object " + classname);
        this.classnames = Collections.singletonList(classname);
    }

    /**
     * Constructs a new UnsupportedElementTypeException.
     *
     * @param classnames a list of fully-qualified class name
     * specified in the element being accessed.
     */
    public UnsupportedElementTypeException(List<String> classnames) {
        super("Attempt to access Class objects " + 
              (classnames = // defensive copy
               new ArrayList<String>(classnames)).toString() );
        this.classnames = Collections.unmodifiableList(classnames);
    }

    /**
     * Returns the list of fully-qualified class name corresponding
     * to the value of the element being accessed.
     *
     * @return the list of fully-qualified class name corresponding 
     * to the value of the element being accessed.
     */
    public List<String> getClassNames() {
        return classnames;
    }
}
