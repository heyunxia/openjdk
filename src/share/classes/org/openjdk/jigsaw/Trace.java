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

package org.openjdk.jigsaw;

import java.security.AccessController;
import java.security.PrivilegedAction;


class Trace {

    static int traceLevel = 0;
    static boolean tracing = false;

    static {
        PrivilegedAction<String> pa = new PrivilegedAction<String>() {
            public String run() { return System.getenv("JIGSAW_TRACE"); }};
        String v = AccessController.doPrivileged(pa);
        if (v != null) {
            traceLevel = Integer.parseInt(v);
            tracing = traceLevel > 0;
        }
    }

    static void trace(int level, int depth, String fmt, Object ... args) {
        if (level >= traceLevel)
            return;
        StringBuilder sb = new StringBuilder();
        sb.append("| ");
        for (int i = 0; i < level; i++)
            sb.append("  ");
        if (depth > 0) {
            for (int i = 0; i < depth; i++)
                sb.append("-");
            sb.append(" ");
        }
        System.out.format(sb.toString() + fmt + "%n", args);
    }

    static void trace(int level, String fmt, Object ... args) {
        trace(level, 0, fmt, args);
    }

}
