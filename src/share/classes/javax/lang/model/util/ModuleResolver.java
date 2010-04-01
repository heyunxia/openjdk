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

package javax.lang.model.util;
// TODO: reconsider this package -- perhaps it belongs in com.sun.tools.javac.api

import javax.lang.model.element.ModuleElement;

/**
 * Module resolver for modules defined in the Java&trade; Programming Language.
 *
 * Given a set of root modules, and an overall set of modules, the resolver determines
 * which modules are visible from the root modules.
 */
public interface ModuleResolver {

    class ResolutionException extends Exception {
        private static final long serialVersionUID = -5294493995009985322L;
    }

    /**
     * Resolve a set of modules. The resolution may take additional modules into
     * account, such as may be found in a system module library.
     * @param roots The root modules whose dependencies need to be resolved
     * @param modules A set of modules in which to find any dependencies.
     * @throws ResolutionException if the resolution cannot be successfully completed.
     */
    Iterable<? extends ModuleElement> resolve(
        Iterable<? extends ModuleElement> roots,
        Iterable<? extends ModuleElement> modules)
        throws ResolutionException;

    /**
     * Get the set of visible modules for a module. This method may be called
     * after {@link #resolve} to get more specific information.
     * @param module
     * @return the visible modules
     */
    Iterable<? extends ModuleElement> getVisibleModules(ModuleElement module)
        throws IllegalStateException;

    boolean isPlatformName(CharSequence name);

    String getDefaultPlatformModule(); // should use a "platform" enum
}
