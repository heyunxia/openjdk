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

package org.openjdk.jigsaw;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.ArrayList;

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

    // Trace.trace method may be called while the classes for tracing
    // e.g. java.util.Formatter) are being loaded.  Cache the traces
    // to avoid infinite loop until the very first call completes.
    static class Cache {
        static List<String> formats = new ArrayList<String>();
        static List<Object[]> traceArgs = new ArrayList<Object[]>();
        static boolean caching = false;

        /**
         * Returns true if the trace message is added to the cache.
         */
        static synchronized boolean add(String fmt, Object ... args) {
            if (caching && formats != null) {
                formats.add(fmt);
                traceArgs.add(args);
                return true;
            }
            caching = true;
            return false;
        }

        static synchronized boolean isEmpty() {
            return formats == null;
        }

        static synchronized void printAndClear() {
            if (isEmpty())
                return;

            for (int i=0; i < formats.size(); i++)  {
                System.out.format(formats.get(i), traceArgs.get(i));
            }
            caching = false;
            formats = null;
            traceArgs = null;
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
        sb.append(fmt);
        sb.append("%n");

        // Cache the traces until the first call to the format method
        // returns to avoid recursion while classes are being loaded
        if (Cache.add(sb.toString(), args)) {
            return;
        }

        System.out.format(sb.toString(), args);

        if (!Cache.isEmpty()) {
            // now classes needed by tracing are loaded and initialized
            // print all cached traces
            Cache.printAndClear();
        }
    }

    static void trace(int level, String fmt, Object ... args) {
        trace(level, 0, fmt, args);
    }

}
