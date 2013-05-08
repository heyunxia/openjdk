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

import java.util.Locale;

import com.sun.tools.javac.api.Formattable;
import com.sun.tools.javac.api.Messages;
import com.sun.tools.javac.util.Name;

/**
 * Representation of a module query, @literal{module-name@version-query}
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ModuleQuery implements javax.lang.model.element.ModuleElement.ModuleQuery, Formattable {
    public final Name name;
    public final Name versionQuery;

    public ModuleQuery(Name name, Name version) {
        this.name = name;
        this.versionQuery = version;
    }

    public CharSequence getName() {
        return name;
    }

    public CharSequence getVersionQuery() {
        return versionQuery;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ModuleQuery))
            return false;

        ModuleQuery m = (ModuleQuery) other;
        if (name != m.name)
            return false;

        return (versionQuery == null) ? (m.versionQuery == null)
                : versionQuery.equals(m.versionQuery);
    }

    @Override
    public int hashCode() {
        if (versionQuery == null) {
            return name.hashCode();
        } else {
            return name.hashCode() * versionQuery.hashCode();
        }
    }

    // for debugging
    @Override
    public String toString() {
        return "ModuleQuery[" + name + (versionQuery == null ? "" : "@" + versionQuery) + "]";
    }

    // for use in diagnostics
    @Override
    public String toString(Locale locale, Messages messages) {
        return versionQuery == null ? name.toString() : name + "@" + versionQuery;
    }

    public String getKind() {
        return "ModuleQuery";
    }

}
