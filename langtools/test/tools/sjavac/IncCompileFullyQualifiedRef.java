/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Verify that "alfa.omega.A a" does create a proper dependency
 * @bug 8054689
 * @author Fredrik O
 * @author sogoel (rewrite)
 * @library /tools/lib
 * @build Wrapper ToolBox
 * @run main Wrapper IncCompileFullyQualifiedRef
 */

import java.util.*;
import java.nio.file.*;

public class IncCompileFullyQualifiedRef extends SJavacTester {
    public static void main(String... args) throws Exception {
        IncCompileFullyQualifiedRef fr = new IncCompileFullyQualifiedRef();
        fr.test();
    }

    void test() throws Exception {
        ToolBox tb = new ToolBox();
        tb.writeFile(GENSRC.resolve("alfa/omega/A.java"),
                 "package alfa.omega; public class A { "+
                 "  public final static int DEFINITION = 18; "+
                 "  public void hello() { }"+
                 "}");
        tb.writeFile(GENSRC.resolve("beta/B.java"),
                 "package beta; public class B { "+
                 "  public void world() { alfa.omega.A a; }"+
                 "}");

        compile("gensrc", "-d", "bin", "-j", "1",
                SERVER_ARG, "--log=debug");
        Map<String,Long> previous_bin_state = collectState(BIN);

        // Change pubapi of A, this should trigger a recompile of B.
        tb.writeFile(GENSRC.resolve("alfa/omega/A.java"),
                 "package alfa.omega; public class A { "+
                 "  public final static int DEFINITION = 19; "+
                 "  public void hello() { }"+
                 "}");

        compile("gensrc", "-d", "bin", "-j", "1",
                SERVER_ARG, "--log=debug");
        Map<String,Long> new_bin_state = collectState(BIN);

        verifyNewerFiles(previous_bin_state, new_bin_state,
                         "bin/alfa/omega/A.class",
                         "bin/beta/B.class",
                         "bin/javac_state");
        clean(GENSRC,BIN);
    }
}
