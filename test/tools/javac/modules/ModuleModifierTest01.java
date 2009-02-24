/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6802521
 * @summary add support for modules: test basic use of module access modifier
 */

import java.io.*;
import java.util.*;

/*
 * Test correct handling of module modifiers, including the cases
 * involving lookahead, using combinations of
 * -- class, interface or enum container
 * -- different items to go in the container
 */
public class ModuleModifierTest01
{
    enum ItemKind {
        FIELD("module int field", " = 0;", ";"),
        ARRAY1("module int array[]", " = { };", ";"),
        ARRAY2("module int[] array", " = { };", ";"),
        METHOD("module int method()", ";", " { return 0; }"),
        AMBIG1("module Test()", ";", " { }"),                     // constr for class, err for enum
        AMBIG2("module Test()", ";", " { return null; }"),        // error for class, method for enum
        AMBIG3("public module Test()", ";", " { return null; }"); // method for all
        ItemKind(String decl, String intfTail, String classTail) {
            this.decl = decl;
            this.intfTail = intfTail;
            this.classTail = classTail;
        }
        final String decl;
        final String intfTail;
        final String classTail;
    };

    enum ClassKind {
        CLASS("class Test {"),
        INTERFACE("interface Test {"),
        ENUM("enum Test { e1 ;");
        ClassKind(String s) {
            text = s;
        }
        final String text;
    };

    public static void main(String... args) throws Exception {
        new ModuleModifierTest01().run();
    }

    public void run() throws Exception {
        for (ClassKind ck: ClassKind.values()) {
            for (ItemKind ik: ItemKind.values()) {
                test(ck, ik);
            }
        }

        if (errors == 0)
            System.out.println(count + " tests passed");
        else
            throw new Exception(errors + "/" + count + " tests failed");
    }

    void test(ClassKind ck, ItemKind ik) throws Exception {
        System.out.println("Test " + (++count) + ": " + ck + " " + ik);
        resetDirs(srcDir, classesDir);

        boolean needModule;
        switch (ik) {
        case AMBIG1: needModule = (ck == ClassKind.INTERFACE); break;
        case AMBIG2: needModule = (ck != ClassKind.CLASS);     break;
        case AMBIG3: needModule = true;  break;
        default:     needModule = false; break;
        }

        String[] testBody = {
            "module m; package p;",
            "    " + ck.text,
            "    " + ik.decl + (ck == ClassKind.INTERFACE ? ik.intfTail : ik.classTail),
            "}",
            (needModule ? "class module { }" : "")
        };

        File test = createFile(srcDir, "Test.java", join(testBody));
        boolean expectError =
            (ck == ClassKind.ENUM && ik == ItemKind.AMBIG1)
            || (ck == ClassKind.CLASS && ik == ItemKind.AMBIG2);

        compile(Arrays.asList(test), classesDir, null, expectError);
    }

    void compile(List<File> files, File classOutDir, List<String> extraOpts, boolean expectError) {
        List<String> options = new ArrayList<String>();
        options.add("-XDrawDiagnostics");
        options.addAll(Arrays.asList("-source", "7", "-d", classOutDir.getPath()));
        if (extraOpts != null)
            options.addAll(extraOpts);
        for (File f: files)
            options.add(f.getPath());

        String[] opts = options.toArray(new String[options.size()]);
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        int rc = com.sun.tools.javac.Main.compile(opts, out);
        out.close();

        if (expectError) {
            if (rc == 0)
                error(files, "compilation succeeded unexpectedly");
            else {
                //log(files, "compilation failed as expected", sw.toString());
                //log(files, "OK");
            }
        }
        else {
            if (rc != 0)
                error(files, "compilation failed unexpectedly", sw.toString());
            else {
                //log(files, "OK");
            }
        }
    }

    /**
     * Join lines with newline.
     */
    String join(String... lines) {
        StringBuilder sb = new StringBuilder();
        for (String s: lines) {
            sb.append(s);
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Create a test file with given content.
     */
    File createFile(File dir, String path, String body) throws IOException {
        File file = new File(dir, path);
        file.getAbsoluteFile().getParentFile().mkdirs();
        FileWriter out = new FileWriter(file);
        out.write(body);
        out.close();
        return file;
    }

    /**
     * Set up empty directories.
     */
    void resetDirs(File... dirs) {
        for (File dir: dirs) {
            if (dir.exists())
                deleteAll(dir);
            dir.mkdirs();
        }
    }

    /**
     * Delete a file or a directory (including all its contents).
     */
    boolean deleteAll(File file) {
        if (file.isDirectory()) {
            for (File f: file.listFiles())
                deleteAll(f);
        }
        return file.delete();
    }

    /**
     * Report an error.
     */
    void error(List<File> files, String msg, String... more) {
        System.out.println("test " + files);
        System.out.println("error: " + msg);
        for (String s: more)
            System.out.println(s);
        errors++;
        //throw new Error(msg);
    }

    /**
     * Report an error.
     */
    void error(String msg, String... more) {
        System.out.println("error: " + msg);
        for (String s: more)
            System.out.println(s);
        errors++;
        throw new Error(msg);
    }

    /**
     * Log a message.
     */
    void log(List<File> files, String... msgs) {
        System.out.println("test " + files);
        for (String s: msgs)
            System.out.println(s);
    }

    int count;
    int errors;
    File srcDir = new File("tmp", "src"); // use "tmp" to help avoid accidents
    File classesDir = new File("tmp", "classes");
}

