/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug      8008768 8026567
 * @summary  Using {@inheritDoc} in simple tag defined via -tag fails
 * @library  ../lib
 * @build    JavadocTester
 * @run main TestSimpleTagInherit
 */

public class TestSimpleTagInherit extends JavadocTester {

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {

    };

    //Input for string search tests.
    private static final String[][] TEST = {
        {  }
    };

    public static void main(String... args) throws Exception {
        TestSimpleTagInherit tester = new TestSimpleTagInherit();
        tester.runTests();
    }

    @Test
    void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "-tag", "custom:optcm:<em>Custom:</em>",
                "p");
        checkExit(Exit.OK);

        checkOutput("p/TestClass.html", true,
                "<dt><span class=\"simpleTagLabel\"><em>Custom:</em></span></dt>\n"
                + "<dd>doc for BaseClass class</dd>",
                "<dt><span class=\"simpleTagLabel\"><em>Custom:</em></span></dt>\n"
                + "<dd>doc for BaseClass method</dd>");
    }
}
