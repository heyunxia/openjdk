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


/**
 * <p> Information about a module, as found in a {@code module-info.java}
 * source file or a {@code module-info.class} class file </p>
 */

public interface ModuleInfo {

    /**
     * This module's identifier
     */
    public ModuleId id();

    /**
     * <p> The identifiers of the virtual modules provided by this module </p>
     *
     * @return  A possibly-empty set of {@link ModuleId ModuleIds}
     */
    public Set<ModuleId> provides();

    /**
     * <p> The dependences of this module </p>
     *
     * @return  A possibly-empty set of {@link Dependence Dependences}
     */
    public Set<Dependence> requires();

    /**
     * <p> The names of modules that are permitted to require this module </p>
     *
     * @return  A possibly-empty set of module names
     */
    public Set<String> permits();

    /**
     * <p> The name of the main class of this module </p>
     *
     * @return  The name of the main class of this module, or {@code null}
     *          if this module does not have a main class
     */
    public String mainClass();

}
