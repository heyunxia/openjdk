/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.Set;

/**
 * Information about a module view.
 */
public interface ModuleView  {
    /**
     * {@link ModuleInfo} of this view's containing module.
     */
    public ModuleInfo moduleInfo();

    /**
     * <p> This view's identifier.</p>
     * This view's identifier has the view's name and its containing
     * module's {@linkplain ModuleId#version() version}.
     */
    public ModuleId id();

    /**
     * <p> The identifiers of the virtual modules provided by this view </p>
     *
     * @return  A possibly-empty unmodifiable set of {@link ModuleId ModuleIds}
     */
    public Set<ModuleId> aliases();

    /**
     * <p> The exported packages of this view </p>
     *
     * @return  A possibly-empty unmodifiable set of exported packages
     */
    public Set<String> exports();

    /**
     * <p> The names of modules that are permitted to require this view </p>
     *
     * @return  A possibly-empty unmodifiable set of module names
     */
    public Set<String> permits();

    /**
     * <p> The services that this view provides </p>
     *
     * @return  A possibly-empty unmodifiable map of a fully-qualified
     *          name of a service type to the class names of its providers
     *          provided by this view.
     */
    public Map<String,Set<String>> services();

    /**
     * <p> The fully qualified name of the main class of this view </p>
     *
     * @return  The fully qualified name of the main class of this module, or {@code null}
     *          if this module does not have a main class
     */
    public String mainClass();
}
