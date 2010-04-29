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

import org.openjdk.jigsaw.JigsawModuleSystem;


public abstract class ModuleSystem {

    static class ModuleSystemHolder {
        // lazy initialize this static field to eliminate the 
        // class initialization cycle 
        static ModuleSystem base = JigsawModuleSystem.instance();
    }

    public static ModuleSystem base() {
       return ModuleSystemHolder.base;
    }

    // Module names must be legal Java identifiers
    //
    public static final String checkModuleName(String nm) {
        if (nm == null)
            throw new IllegalArgumentException();
        int n = nm.length();
        if (n == 0 || !Character.isJavaIdentifierStart(nm.codePointAt(0)))
            throw new IllegalArgumentException();

        int cp = nm.codePointAt(0);
        for (int i = Character.charCount(cp);
                i < n;
                i += Character.charCount(cp)) {
            cp = nm.codePointAt(i);
            if (!Character.isJavaIdentifierPart(cp) && nm.charAt(i) != '.') {
                throw new IllegalArgumentException(nm
                                                   + ": Illegal module-name"
                                                   + " character"
                                                   + " at index " + i);
            }
        }
        return nm;
    }

    public abstract Version parseVersion(String v);

    public abstract VersionQuery parseVersionQuery(String vq);

    public final ModuleInfo parseModuleInfo(byte[] bs) {
        return ModuleInfoReader.read(this, bs);
    }

    public final ModuleId parseModuleId(String mid) {
        return ModuleId.parse(this, mid);
    }

    public final ModuleId parseModuleId(String name, String version) {
        return ModuleId.parse(this, name, version);
    }

    public final ModuleIdQuery parseModuleIdQuery(String midq) {
	int i = midq.indexOf('@');
	String mn;
	VersionQuery vq = null;
	if (i < 0) {
	    mn = midq;
	} else {
	    mn = midq.substring(0, i);
	    vq = parseVersionQuery(midq.substring(i + 1));
	}
	return new ModuleIdQuery(mn, vq);
    }
}
