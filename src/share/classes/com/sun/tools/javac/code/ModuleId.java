/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.code;

import com.sun.tools.javac.api.Formattable;
import com.sun.tools.javac.api.Messages;
import com.sun.tools.javac.util.Name;
import java.util.Locale;

/**
 * Representation of a module id, {@literal module-name[@version]}
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ModuleId implements javax.lang.model.element.ModuleElement.ModuleId, Formattable {
    public final Name name;
    public final Name version;

    public ModuleId(Name name) {
        this.name = name;
        this.version = null;
    }

    public ModuleId(Name name, Name version) {
        this.name = name;
        this.version = version;
    }

    public CharSequence getName() {
        return name;
    }

    public CharSequence getVersion() {
        return version;
    }

    public ModuleQuery toQuery() {
        return new ModuleQuery(name, version);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ModuleId))
            return false;

        ModuleId m = (ModuleId) other;
        if (name != m.name)
            return false;

        return (version == null ? m.version == null : version.equals(m.version));
    }

    @Override
    public int hashCode() {
        if (version == null) {
            return name.hashCode();
        } else {
            return name.hashCode() * version.hashCode();
        }
    }

    // for debugging
    @Override
    public String toString() {
        return "ModuleId[" + name + (version == null ? "" : "@" + version) + "]";
    }

    // for use in diagnostics
    @Override
    public String toString(Locale locale, Messages messages) {
        return version == null ? name.toString() : name + "@" + version;
    }

    public String getKind() {
        return "ModuleId";
    }

}
