/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import com.sun.source.util.JavacTask;
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.ModuleProvides_attribute;
import com.sun.tools.classfile.ModuleProvides_attribute.View;
import com.sun.tools.javac.api.ClientCodeWrapper;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javap.JavapTask;

/* Utilities for module directives tests. */
abstract class DirectiveTest {

    protected DirectiveTest() {
        javac = JavacTool.create();
        fm = javac.getStandardFileManager(null, null, null);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Test { }
    
    void run() throws Exception {
        for (Method m: getClass().getDeclaredMethods()) {
            Annotation a = m.getAnnotation(Test.class);
            if (a != null) {
                init(m.getName());
                try {
                    m.invoke(this, new Object[] { });
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    throw (cause instanceof Exception) ? ((Exception) cause) : e;
                }
                System.err.println();
            }
        }
        System.err.println(testCount + " tests" + ((errorCount == 0) ? "" : ", " + errorCount + " errors"));
        if (errorCount > 0)
            throw new Exception(errorCount + " errors found");
    }

    void init(String name) throws IOException {
        System.err.println("Test " + name);
        testCount++;

        srcDir = new File(name, "src");
        srcDir.mkdirs();
        classesDir = new File(name, "classes");
        classesDir.mkdirs();
        
        fm.setLocation(StandardLocation.SOURCE_PATH, Arrays.asList(srcDir));
        fm.setLocation(StandardLocation.MODULE_PATH, Collections.<File>emptyList());
        fm.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(classesDir));
    }

    void compile(List<JavaFileObject> files) throws Exception {
        JavacTask task = javac.getTask(null, fm, null, null, null, files);
        if (!task.call())
            throw new Exception("compilation failed");
    }

    void compile(List<JavaFileObject> files, List<String> expectDiags) throws Exception {
        class DiagListener implements DiagnosticListener<JavaFileObject> {
            List<String> diags = new ArrayList<String>();
            @Override
            public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
                System.err.println(diagnostic);
                JCDiagnostic d = ((ClientCodeWrapper.DiagnosticSourceUnwrapper) diagnostic).d;
                diags.add(d.getKind() + ": " + d.getCode() + " " + Arrays.asList(d.getArgs()));
            }
        }

        DiagListener dl = new DiagListener();
        JavacTask task = javac.getTask(null, fm, dl, null, null, files);
        boolean ok = task.call();
        System.err.println(ok ? "Compilation succeeded" : "Compilation failed");

        List<String> foundDiags = dl.diags;

        checkEqual("diags", expectDiags, foundDiags);
    }

    void javap(String path) {
        List<String> opts = Arrays.asList("-v");
        List<String> files = Arrays.asList(new File(classesDir, path).getPath());
        JavapTask t = new JavapTask(null, fm, null, opts, files);
        t.call();
    }

    View getView(ClassFile cf, String name) throws ConstantPoolException {
        ConstantPool cp = cf.constant_pool;
        ModuleProvides_attribute attr = (ModuleProvides_attribute) cf.getAttribute(Attribute.ModuleProvides);
        Set<String> found = new HashSet<String>();
        for (View v: attr.view_table) {
            if (v.view_name_index == 0 && name == null
                    || v.view_name_index != 0 && cp.getUTF8Value(v.view_name_index).equals(name)) {
                return v;
            }
        }
        return null;
    }

    JavaFileObject createFile(String path, final String body) throws IOException {
        File f = new File(srcDir, path);
        f.getParentFile().mkdirs();
        try (FileWriter out = new FileWriter(f)) {
            out.write(body);
        }
        return fm.getJavaFileObjects(f).iterator().next();
    }

    <T> Set<T> createSet(T... items) {
        return new HashSet<T>(Arrays.asList(items));
    }

    <T> void checkEqual(String label, T expect, T found) {
        if ((found == null) ? (expect == null) : found.equals(expect))
            return;
        System.err.println("Error: mismatch");
        System.err.println("  expected: " + expect);
        System.err.println("     found: " + found);
        errorCount++;
    }

    <T> void checkEqual(String label, Collection<T> expect, Collection<T> found) {
        if ((found == null) ? (expect == null) : found.equals(expect))
            return;
        System.err.println("Error: mismatch");
        System.err.println("  expected: " + expect);
        System.err.println("     found: " + found);
        errorCount++;
    }

    JavacTool javac;
    StandardJavaFileManager fm;
    File srcDir;
    File classesDir;
    int testCount;
    int errorCount;

}
