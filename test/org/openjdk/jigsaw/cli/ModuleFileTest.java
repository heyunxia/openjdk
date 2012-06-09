/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @summary test module format writing/reading
 */
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSigner;
import java.security.cert.*;
import java.util.*;
import java.util.zip.*;
import org.openjdk.jigsaw.cli.*;
import org.openjdk.jigsaw.ModuleFile;
import org.openjdk.jigsaw.SignedModule;
import static org.openjdk.jigsaw.FileConstants.ModuleFile.*;

public class ModuleFileTest {

    private static final String MNAME = "hello";
    private static final String MVER = "0.1";

    public static void main(String[] args) throws Exception {
        boolean sign = args.length > 0;
        try {
            test(sign);
        } catch (Throwable t) {
            t.printStackTrace();
            errors++;
        }
        if (errors == 0) {
            System.out.println(count + " tests passed");
        } else {
            throw new Exception(errors + "/" + count + " tests failed");
        }
    }
    final String[] classnames = new String[]{
        "World", "Another"
    };
    final String[] filenames = new String[]{
        "yo", "Hey"
    };
    final int classes;
    final int resources;
    final int nativelibs;
    final int nativecmds;
    final int configs;
    final File jmodfile;
    final File extractedDir;
    String filecontent = "file content";

    ModuleFileTest(int classes, int resources, int configs,
            int nativelibs, int nativecmds) throws Exception {
        count++;
        this.classes = classes;
        this.resources = resources;
        this.nativelibs = nativelibs;
        this.nativecmds = nativecmds;
        this.configs = configs;
        this.jmodfile = new File(moduleDir, MNAME + "@" + MVER + ".jmod");
        this.extractedDir = new File(MNAME);
        createModuleContent();
    }

    void run(boolean sign) throws Exception {
        jpkg(MNAME);
        if (sign) {
            sign(MNAME, MVER);
        }
        checkModuleFileContent(MNAME);
    }

    void setFileContent(String s) {
        filecontent = s;
    }

    private void setup() throws IOException {
        if (tmpDir.exists()) {
            deleteTree(tmpDir.toPath());
        }
        if (extractedDir.exists()) {
            deleteTree(extractedDir.toPath());
        }
        tmpDir.mkdirs();
    }

    private void createModuleContent() throws IOException {
        setup();

        String moduleinfo = "module " + MNAME + " @ " + MVER + " {}";
        List<File> files = new ArrayList<>();
        files.add(createFile(srcDir, "module-info.java", moduleinfo));
        for (int i = 0; i < classes; i++) {
            String cn = classnames[i];
            String code = String.format("public class %s {}", cn);
            files.add(createFile(srcDir, cn + ".java", code));
        }
        compile(files);

        // create resource files
        for (int i = 0; i < resources; i++) {
            createFile(resourcesDir, filenames[i], filecontent);
        }

        // create native libraries
        for (int i = 0; i < nativelibs; i++) {
            createFile(natlibDir, filenames[i] + ".so", filecontent);
        }

        // create native commands
        for (int i = 0; i < nativecmds; i++) {
            createFile(natcmdDir, filenames[i] + ".exe", filecontent);
        }

        // create config files
        for (int i = 0; i < configs; i++) {
            createFile(configDir, filenames[i], filecontent);
        }
    }

    /**
     * Create a test file with given content if the content is not null.
     */
    File createFile(File dir, String path, String body) throws IOException {
        if (body == null) {
            return null;
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, path);
        Files.write(file.toPath(), body.getBytes());
        return file;
    }

    void jpkg(String mname) throws Exception {
        if (!moduleDir.exists()) {
            moduleDir.mkdirs();
        }

        List<String> args = new ArrayList<>();
        args.add("-m");
        args.add(classesDir.getAbsolutePath());
        args.add("-d");
        args.add(moduleDir.getAbsolutePath());
        if (nativelibs > 0) {
            args.add("--natlib");
            args.add(natlibDir.toString());
        }
        if (nativecmds > 0) {
            args.add("--natcmd");
            args.add(natcmdDir.toString());
        }
        if (configs > 0) {
            args.add("--config");
            args.add(configDir.toString());
        }
        args.add("jmod");
        args.add(mname);
        System.out.println("      jpkg arguments: " + args);
        Packager.main(args.toArray(new String[0]));
    }

    /**
     * Extract a module.
     */
    void extract() throws Exception {
        String[] args = {"extract", jmodfile.getAbsolutePath(), "-v"};
        System.out.println("      jmod arguments: " + Arrays.toString(args));
        Librarian.main(args);
    }

    void checkModuleInfo(File mdir) throws IOException {
        String sectionDirname = ModuleFile.getSubdirOfSection(SectionType.MODULE_INFO);
        File copyDir = new File(mdir, sectionDirname);
        compare("module-info.class", classesDir, copyDir);
    }

    void checkModuleFileContent(String mname) throws Exception {
        extract();

        checkModuleInfo(extractedDir);

        checkClasses(extractedDir);
        checkResourceFiles(extractedDir);

        checkFiles(configDir,
                   new File(extractedDir,
                            ModuleFile.getSubdirOfSection(SectionType.CONFIG)),
                   configs, "", false);
        checkFiles(natcmdDir,
                   new File(extractedDir,
                            ModuleFile.getSubdirOfSection(SectionType.NATIVE_CMDS)),
                   nativecmds, ".exe", true);
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        checkFiles(natlibDir,
                   new File(extractedDir,
                            ModuleFile.getSubdirOfSection(SectionType.NATIVE_LIBS)),
                   nativelibs, ".so", windows);
    }

    void checkClasses(File mdir) throws IOException {
        File zipfile = new File(mdir, "classes");
        if (classes == 0) {
            if (zipfile.exists()) {
                throw new RuntimeException("No class in this module but "
                        + zipfile + " exists");
            }
            return;
        }

        List<String> classfiles = new ArrayList<>();
        for (int i = 0; i < classes; i++) {
            classfiles.add(classnames[i] + ".class");
        }
        try (ZipFile content = new ZipFile(zipfile)) {
            compare(classfiles, classesDir, content);
        }
    }

    void checkResourceFiles(File mdir) throws IOException {
        if (resources == 0) {
            return;
        }

        List<String> files = new ArrayList<>();
        String path = resourcesDir.getName();
        for (int i = 0; i < resources; i++) {
            files.add(path + '/' + filenames[i]);
        }
        try (ZipFile content = new ZipFile(new File(mdir, "classes"))) {
            compare(files, classesDir, content);
        }
    }

    void checkFiles(File dir, File copyDir,
                    int count, String suffix,
                    boolean executable) throws IOException {
        List<String> fnames = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            fnames.add(filenames[i] + suffix);
        }
        compare(fnames, dir, copyDir);

        for (String fname : fnames) {
            File file = new File(copyDir, fname);
            if (executable && !file.canExecute()) {
                throw new IOException("File not marked executable: " + file);
            }
        }
    }

    void sign(String name, String version) throws Exception {
        List<String> args = new ArrayList<>();
        args.add("--keystore");
        args.add("keystore.jks");
        args.add(jmodfile.getAbsolutePath());
        args.add("signer");
        args.add("-v");
        System.out.println("      jsign arguments: " + args);
        Signer.main(args.toArray(new String[0]));
        checkSignature(name, jmodfile);
    }

    private static void test(boolean sign) throws Exception {
        testEmptyModule(sign);
        testSingleClassModule(sign);
        testMultiClassModule(sign);
        testSingleResourceModule(sign);
        testMultiResourceModule(sign);
        testSingleNatLibModule(sign);
        testMultiNatLibModule(sign);
        testSingleNatCmdModule(sign);
        testMultiNatCmdModule(sign);
        testSingleConfigModule(sign);
        testMultiConfigModule(sign);
        testBloatedModule(sign);
    }

    private static void testEmptyModule(boolean sign) throws Exception {
        System.out.println("\nTest: Empty module");
        new ModuleFileTest(0, 0, 0, 0, 0).run(sign);
    }

    private static void testSingleClassModule(boolean sign) throws Exception {
        System.out.println("\nTest: Single class module");
        new ModuleFileTest(1, 0, 0, 0, 0).run(sign);
    }

    private static void testMultiClassModule(boolean sign) throws Exception {
        System.out.println("\nTest: Multiple class module");
        new ModuleFileTest(2, 0, 0, 0, 0).run(sign);
    }

    void testSignMultiClassModule() throws Exception {
        System.out.println("\nTest: Multiple class module");
        new ModuleFileTest(2, 0, 0, 0, 0).run(true);
    }

    private static void testSingleResourceModule(boolean sign) throws Exception {
        System.out.println("\nTest: Single resource module");
        new ModuleFileTest(2, 1, 0, 0, 0).run(false);
    }

    private static void testMultiResourceModule(boolean sign) throws Exception {
        System.out.println("\nTest: Multiple resource module");
        new ModuleFileTest(2, 2, 0, 0, 0).run(false);
    }

    private static void testSingleNatLibModule(boolean sign) throws Exception {
        System.out.println("\nTest: Single native library module");
        new ModuleFileTest(0, 0, 0, 1, 0).run(false);
    }

    private static void testMultiNatLibModule(boolean sign) throws Exception {
        System.out.println("\nTest: Multiple native library module");
        new ModuleFileTest(0, 0, 0, 2, 0).run(sign);
    }

    private static void testSingleNatCmdModule(boolean sign) throws Exception {
        System.out.println("\nTest: Single native command module");
        new ModuleFileTest(0, 0, 0, 0, 1).run(sign);
    }

    private static void testMultiNatCmdModule(boolean sign) throws Exception {
        System.out.println("\nTest: Multi native command module");
        new ModuleFileTest(0, 0, 0, 0, 2).run(sign);
    }

    private static void testSingleConfigModule(boolean sign) throws Exception {
        System.out.println("\nTest: Single config module");
        new ModuleFileTest(0, 0, 1, 0, 0).run(sign);
    }

    private static void testMultiConfigModule(boolean sign) throws Exception {
        System.out.println("\nTest: Multi config module");
        new ModuleFileTest(0, 0, 2, 0, 0).run(sign);
    }

    private static void testBloatedModule(boolean sign) throws Exception {
        System.out.println("\nTest: Bloated module");
        String largefile = "0";

        for (int i = 0; i < 10000; i++) {
            largefile += i;
        }
        new ModuleFileTest(2, 2, 2, 2, 2).run(sign);
    }

    void checkSignature(String name, File signedModuleFile) throws Exception {
        try (FileInputStream fis = new FileInputStream(signedModuleFile);
                DataInputStream dis = new DataInputStream(fis);
                ModuleFile.Reader reader = new ModuleFile.Reader(dis)) {
            if (reader.hasSignature()) {
                if (reader.getSignatureType() == SignatureType.PKCS7) {

                    SignedModule sm = new SignedModule(reader);
                    int i = 1;
                    System.out.println("Module '" + name + "' is signed by:");
                    for (CodeSigner signer : sm.verifySignature()) {
                        X509Certificate signerCert =
                                (X509Certificate) signer.getSignerCertPath().getCertificates().get(0);
                        System.out.println("    [" + (i++) + "] "
                                + signerCert.getSubjectX500Principal().getName());
                    }
                    System.out.println();
                } else {
                    throw new Exception("Unsupported signature format");
                }
            } else {
                throw new Exception("Module '" + name + "' is unsigned");
            }
        }
    }

    /**
     * Compare an extracted module with original.
     */
    void compare(String fname, File origDir, File copyDir)
            throws IOException {
        compare(Collections.singletonList(fname), origDir, copyDir);
    }

    void compare(List<String> fnames, File origDir, File copyDir)
            throws IOException {
        for (String fname : fnames) {
            File file = new File(origDir, fname);
            File copy = null;

            // Module-info class is extracted into info file.
            if (fname.equals("module-info.class")) {
                copy = new File(copyDir, "info");
            } else {
                copy = new File(copyDir, fname);
            }
            compare(file, copy);
        }
    }

    void compare(List<String> fnames, File origDir, ZipFile content)
            throws IOException {
        for (String fname : fnames) {
            File file = new File(origDir, fname.replace('/', File.separatorChar));
            ZipEntry ze = content.getEntry(fname);
            if (ze == null) {
                throw new FileNotFoundException(fname);
            }
            compare(file, content.getInputStream(ze));
        }
    }

    /**
     * Compare two files for identity.
     */
    void compare(File f1, InputStream i2) throws IOException {
        // For class files, only compare the ClassFile header as
        // the class file is modified after being compressed by pack200.
        long numBytes = f1.getName().endsWith(".class") ? 10 : f1.length();
        int count = 0;
        int c1, c2;
        try (FileInputStream fis = new FileInputStream(f1);
                InputStream i1 = new BufferedInputStream(fis)) {
            while ((c1 = i1.read()) != -1 && count < numBytes) {
                count++;
                c2 = i2.read();
                if (c1 != c2) {
                    throw new RuntimeException("file compare failed 1");
                }
            }
            if (c1 == -1 && i2.read() != -1) {
                throw new RuntimeException("file compare failed 2");
            }
        } finally {
            i2.close();
        }
    }

    void compare(File f1, File f2) throws IOException {
        if (!f1.exists()) {
            throw new RuntimeException("File not exist: " + f1);
        }
        if (!f2.exists()) {
            throw new RuntimeException("File not exist: " + f2);
        }
        compare(f1, new BufferedInputStream(new FileInputStream(f2)));
    }

    /**
     * Compile a list of files.
     */
    void compile(List<File> files) {
        List<String> options = new ArrayList<>();
        if (!classesDir.exists()) {
            classesDir.mkdirs();
        }
        options.addAll(Arrays.asList("-source", "8", "-d", classesDir.getPath()));
        for (File f : files) {
            options.add(f.getPath());
        }

        String[] opts = options.toArray(new String[options.size()]);
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            int rc = com.sun.tools.javac.Main.compile(opts, pw);
            String out = sw.toString();
            if (out.trim().length() > 0) {
                System.err.println(out);
            }
            if (rc != 0) {
                throw new Error("compilation failed: rc=" + rc);
            }
        }

    }

    /**
     * Add a file to a list if the file is not null.
     */
    void addFile(List<File> files, File file) {
        if (file != null) {
            files.add(file);
        }
    }

    /**
     * Create a test file with given content if the content is not null.
     */
    File createFile(String path, String body) throws IOException {
        if (body == null) {
            return null;
        }
        File file = new File(srcDir, path);
        return createFile(file, body);
    }

    File createFile(File file, String body) throws IOException {
        file.getAbsoluteFile().getParentFile().mkdirs();
        try (FileWriter out = new FileWriter(file)) {
            out.write(body);
        }
        return file;
    }

    void deleteTree(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e)
                    throws IOException {
                if (e == null) {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                } else {
                    // directory iteration failed
                    throw e;
                }
            }
        });

    }

    /**
     * Report an error.
     */
    static void error(String msg, String... more) {
        System.err.println("error: " + msg);
        for (String s : more) {
            System.err.println(s);
        }
        errors++;
    }
    static int count;
    static int errors;
    File tmpDir = new File("tmp");
    File srcDir = new File(tmpDir, "src"); // use "tmp" to help avoid accidents
    File classesDir = new File(tmpDir, "classes");
    File resourcesDir = new File(classesDir, "resources");
    File moduleDir = new File(tmpDir, "modules");
    File natlibDir = new File(srcDir, "natlib");
    File natcmdDir = new File(srcDir, "natcmd");
    File configDir = new File(srcDir, "config");
    String baseDir = System.getProperty("test.src", ".");
}
