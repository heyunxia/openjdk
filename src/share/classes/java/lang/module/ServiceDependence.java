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

import java.lang.module.Dependence.Modifier;
import java.util.Set;

/**
 * Information about a service dependence specified in a {@link ModuleInfo}.
 */
public final class ServiceDependence extends Dependence {
    private final String service;

    /**
     * Constructs a {@code ServiceDependence} on the given service.
     *
     * @param mods    modifiers
     * @param service the fully-qualified name of a service
     *
     * @throws IllegalArgumentException if mods contains the {@link Modifier#LOCAL
     *         LOCAL} or {@link Modifier#PUBLIC PUBLIC} modifier which is
     *         invalid for a {@code ServiceDependence}.
     */
    public ServiceDependence(Set<Modifier> mods, String service) {
        super(mods);
        this.service = service;
    }

    /**
     * Returns the fully qualified name of the service.
     * @return the fully qualified name of the service
     */
    public String service() {
        return service;
    }

    @Override
    public boolean equals(Object ob) {
        if (!(ob instanceof ServiceDependence)) {
            return false;
        }
        ServiceDependence that = (ServiceDependence) ob;
        return (service.equals(that.service)
                && modifiers().equals(that.modifiers()));
    }

    @Override
    public int hashCode() {
        return service.hashCode() * 43 + modifiers().hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("requires service");
        for (Modifier m : modifiers()) {
            sb.append(' ');
            sb.append(m.toString().toLowerCase());
        }
        sb.append(" ").append(service);
        return sb.toString();
    }
}
