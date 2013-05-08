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

import java.util.Set;

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
     * <p> The module dependences of this module </p>
     *
     * @return  A possibly-empty unmodifiable set of {@link ViewDependence ViewDependences}
     */
    public Set<ViewDependence> requiresModules();

    /**
     * <p> The service dependences of this module </p>
     *
     * @return  A possibly-empty unmodifiable set of
     *          {@link ServiceDependence ServiceDependences}
     */
    public Set<ServiceDependence> requiresServices();

    /**
     * <p> The default view of this module.</p>
     * Each module has a default view whose
     * {@linkplain ModuleView#id() identifier} is the same as
     * its {@linkplain ModuleId module's identifier}.
     *
     * @return A default {@link ModuleView ModuleView}
     */
    public ModuleView defaultView();

    /**
     * <p> The views of this module.</p>
     *
     * @return  An unmodifiable set of {@link ModuleView ModuleViews}
     *          that includes the {@linkplain #defaultView() default view}.
     */
    public Set<ModuleView> views();
}
