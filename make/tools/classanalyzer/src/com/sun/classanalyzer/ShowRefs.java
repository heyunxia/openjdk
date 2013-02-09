/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.sun.classanalyzer;

import java.io.IOException;
import java.util.*;

/**
 * A simple tool to print out the reverse dependencies.
 */
public class ShowRefs {

    static void usage() {
        System.out.println("java ShowRefs -classpath <paths> classname ....");
        System.out.println("Example usages:");
        System.out.println("  java ShowRefs -classpath Foo.jar com.foo.Foo");
        System.out.println("  java ShowRefs sun.misc.VM");
        System.exit(-1);
    }

    public static void main(String[] args) throws IOException {
        String classpath = null;
        List<String> classnames = new ArrayList<String>();

        int i = 0;
        while (i < args.length) {
            String arg = args[i++];
            if (arg.equals("-classpath")) {
                if (i == args.length) {
                    System.err.println("Invalid option -classpath");
                    usage();
                }

                classpath = args[i++];
            } else {
                classnames.add(arg);
            }
        }

        ClassPath cpath = classpath == null ?
            ClassPath.newJDKClassPath(System.getProperty("java.home")) :
            ClassPath.newInstance(classpath);
        cpath.parse();

        for (String c : classnames) {
            Klass k = Klass.findKlass(c);
            if (k == null) {
                System.err.println(c + " not found");
            } else {
                showRefs(k);
            }
        }
    }

    static void showRefs(Klass k) {
        System.out.format("References to %s:%n", k.getClassName());
        for (Klass ref : k.getReferencingClasses()) {
            System.out.format("  <- %s%n", ref.getClassName());
        }
        System.out.println();
    }

}
