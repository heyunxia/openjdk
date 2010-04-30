/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @summary add support for modules: test basic use of ModuleRequires attribute
 * @run main ModuleRequiresAttributeTest01
 */

import java.io.*;
import java.util.*;
import com.sun.tools.classfile.*;

public class ModuleRequiresAttributeTest01 {
    enum Flag { PUBLIC, OPTIONAL, LOCAL;
        static Flag of(String s) {
            for (Flag f: values()) {
                if (f.toString().toLowerCase().equals(s))
                    return f;
            }
            return null;
        }};
    enum MultiKind { LIST, DISTINCT };

    public static void main(String[] args) throws Exception {
        new ModuleRequiresAttributeTest01().run(args);
    }

    void run(String... args) throws Exception {
        if (args.length > 0) {
            selectedTestCases = new HashSet<Integer>();
            for (String arg: args) {
                selectedTestCases.add(Integer.parseInt(arg));
            }
        }

        for (int i = 0; i < (1 << Flag.values().length); i++) {
            Set<Flag> flags = new LinkedHashSet<Flag>();
            if ((i & 4) != 0) flags.add(Flag.PUBLIC);
            if ((i & 2) != 0) flags.add(Flag.OPTIONAL);
            if ((i & 1) != 0) flags.add(Flag.LOCAL);
            for (MultiKind k: MultiKind.values()) {
                try {
                    test(flags, k);
                } catch (Throwable t) {
                    t.printStackTrace();
                    errors++;
                }
            }
        }

        if (errors == 0)
            System.out.println(count + " tests passed");
        else
            throw new Exception(errors + "/" + count + " tests failed");
    }

    void test(Set<Flag> flags, MultiKind kind) throws Exception {
        System.err.println("Test group " + (++group) + " " + flags + " " + kind);
        srcDir = new File("group" + group + "/src");
        srcDir.mkdirs();
        modulesDir = new File("group" + group + "/modules");
        modulesDir.mkdirs();
        try {
            String[] modules = { "M1", "M2.N2", "M3.N3.O3@1.0", "M4@4.0" };
            List<String> permitsList = new ArrayList<String>();
            for (String m: modules) {
                permitsList.add(getModuleName(m));
            }
            List<String> requiresList = new ArrayList<String>();
            for (String m: modules) {
                permitsList.remove(getModuleName(m));
                test(flags, kind, m, requiresList, permitsList);
                requiresList.add(m);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            errors++;
        }

    }

    void test(Set<Flag> flags, MultiKind kind, String moduleId, List<String> requiresList, List<String> permitsList) throws Exception {
        // do not reset on each test case so that we can reuse the previously
        // generated module classes
        ++count;
        if (selectedTestCases != null && !selectedTestCases.contains(count)) {
            System.err.println("Skip test " + count + " " + moduleId + " " + requiresList + " " + permitsList);
            return;
        }
        System.err.println("Test " + count + " " + moduleId + " " + requiresList + " " + permitsList);
        File f = createFile(flags, kind, moduleId, requiresList, permitsList);
        compile(Arrays.asList(f));
        checkRequiresAttribute(getModuleName(moduleId), flags, requiresList);
    }

    File createFile(Set<Flag> flags, MultiKind kind, String moduleId, List<String> requiresList, List<String> permitsList) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("module " + moduleId + " {");
        if (requiresList.size() > 0) {
            switch (kind) {
                case DISTINCT:
                    for (String p: requiresList) {
                        sb.append(" requires " + flags2String(flags) + p + ";");
                    }
                    break;
                case LIST:
                    String sep = " requires " + flags2String(flags);
                    for (String p: requiresList) {
                        sb.append(sep);
                        sb.append(p);
                        sep = ", ";
                    }
                    sb.append("; ");
            }
        }
        if (permitsList.size() > 0) {
            String sep = " permits " ;
            for (String p: permitsList) {
                sb.append(sep);
                sb.append(p);
                sep = ", ";
            }
            sb.append("; ");
        }
        sb.append(" }");
        return createFile("test" + count + "/module-info.java", sb.toString());
    }

    static String flags2String(Set<Flag> flags) {
        StringBuilder sb = new StringBuilder();
        for (Flag f: flags) {
            sb.append(f.toString().toLowerCase() + " ");
        }
        return sb.toString();
    }

    private static final char FS = File.separatorChar;

    void checkRequiresAttribute(String moduleName, Set<Flag> flags, List<String> requiresList) {
        String file = "test" + count + "/module-info.class";
        System.err.println("Checking " + file);
        try {
            ClassFile cf = ClassFile.read(new File(modulesDir, file));
            ModuleRequires_attribute attr =
                    (ModuleRequires_attribute) cf.getAttribute(Attribute.ModuleRequires);
            if (attr == null) {
                if (requiresList.size() > 0)
                    error("ModuleRequires attribute not found; expected " + flags + " " + requiresList);
            } else {
                ConstantPool cp = cf.constant_pool;
                List<String> attrList = new ArrayList<String>();
                for (int i = 0; i < attr.requires_length; i++) {
                    ModuleRequires_attribute.Entry e = attr.requires_table[i];
                    if (isSynthetic(e, cp))
                        continue;
                    ConstantPool.CONSTANT_ModuleId_info mid = cp.getModuleIdInfo(e.requires_index);
                    String mn = cp.getUTF8Value(mid.name_index);
                    String mvq = (mid.version_index == 0 ? null : cp.getUTF8Value(mid.version_index));
                    attrList.add(getModuleId(mn, mvq));
                    Set<Flag> attrFlags = new HashSet<Flag>();
                    for (int f = 0; f < e.attributes_length; f++)
                        attrFlags.add(Flag.of(cp.getUTF8Value(e.attributes[f])));
                    checkEqual("flags", flags, attrFlags);
                }
                checkEqual("requires", requiresList, attrList);
            }
        } catch (ConstantPoolException e) {
            error("Error accessing constant pool " + file + ": " + e);
        } catch (IOException e) {
            error("Error reading " + file + ": " + e);
        }
    }

    static boolean isSynthetic(ModuleRequires_attribute.Entry e, ConstantPool cp)
                throws ConstantPoolException {
        for (int f = 0; f < e.attributes_length; f++) {
            if (cp.getUTF8Value(e.attributes[f]).equals("synthetic"))
                return true;
        }
        return false;
    }

    static String getModuleId(String name, String version) {
        return (version == null ? name : name + "@" + version);
    }

    static String getModuleName(String moduleId) {
        int sep = moduleId.indexOf("@");
        return (sep == -1 ? moduleId : moduleId.substring(0, sep).trim());
    }

    static String getModuleVersion(String moduleId) {
        int sep = moduleId.indexOf("@");
        return (sep == -1 ? moduleId : moduleId.substring(sep+1).trim());
    }

    /**
     * Create a test file with given content if the content is not null.
     */
    File createFile(String path, String body) throws IOException {
        if (body == null)
            return null;
        File file = new File(srcDir, path);
        file.getAbsoluteFile().getParentFile().mkdirs();
        FileWriter out = new FileWriter(file);
        out.write(body);
        out.close();
        return file;
    }

    /**
     * Compile a list of files.
     */
    void compile(List<File> files) {
        List<String> argList = new ArrayList<String>();
        argList.addAll(Arrays.asList("-source", "7", "-d", modulesDir.getPath(), "-modulepath", modulesDir.getPath()));
        for (File f: files)
            argList.add(f.getPath());
        String[] args = argList.toArray(new String[argList.size()]);
        System.err.println("compile: " + Arrays.asList(args));

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        int rc = com.sun.tools.javac.Main.compile(args, pw);
        pw.close();

        String out = sw.toString();
        if (out.trim().length() > 0)
            System.err.println(out);
        if (rc != 0)
            throw new Error("compilation failed: rc=" + rc);
    }

    <T> void checkEqual(String tag, T expect, T found) {
        if (expect == null ? found == null : expect.equals(found))
            return;
        error(tag + " mismatch", "expected " + expect, "found: " + found);
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
     * Set up an empty directory.
     */
    void resetDir(File dir) {
        if (dir.exists())
            deleteAll(dir);
        dir.mkdirs();
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
    void error(String msg, String... more) {
        System.err.println("error: " + msg);
        for (String s: more)
            System.err.println(s);
        errors++;
        throw new Error();
    }

    int group;
    int count;
    int errors;
    File srcDir;
    File modulesDir;
    Set<Integer> selectedTestCases;
}
