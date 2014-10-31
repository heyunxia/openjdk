/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6964768 6964461 6964469 6964487 6964460 6964481 6980021
 * @summary need test program to validate javac resource bundles
 */

import java.io.*;
import java.util.*;
import javax.tools.*;
import com.sun.tools.classfile.*;
import com.sun.tools.javac.code.Lint.LintCategory;

/**
 * Compare string constants in javac classes against keys in javac resource bundles.
 */
public class CheckResourceKeys {
    /**
     * Main program.
     * Options:
     * -finddeadkeys
     *      look for keys in resource bundles that are no longer required
     * -findmissingkeys
     *      look for keys in resource bundles that are missing
     *
     * @throws Exception if invoked by jtreg and errors occur
     */
    public static void main(String... args) throws Exception {
        CheckResourceKeys c = new CheckResourceKeys();
        if (c.run(args))
            return;

        if (is_jtreg())
            throw new Exception(c.errors + " errors occurred");
        else
            System.exit(1);
    }

    static boolean is_jtreg() {
        return (System.getProperty("test.src") != null);
    }

    /**
     * Main entry point.
     */
    boolean run(String... args) throws Exception {
        boolean findDeadKeys = false;
        boolean findMissingKeys = false;

        if (args.length == 0) {
            if (is_jtreg()) {
                findDeadKeys = true;
                findMissingKeys = true;
            } else {
                System.err.println("Usage: java CheckResourceKeys <options>");
                System.err.println("where options include");
                System.err.println("  -finddeadkeys      find keys in resource bundles which are no longer required");
                System.err.println("  -findmissingkeys   find keys in resource bundles that are required but missing");
                return true;
            }
        } else {
            for (String arg: args) {
                if (arg.equalsIgnoreCase("-finddeadkeys"))
                    findDeadKeys = true;
                else if (arg.equalsIgnoreCase("-findmissingkeys"))
                    findMissingKeys = true;
                else
                    error("bad option: " + arg);
            }
        }

        if (errors > 0)
            return false;

        Set<String> codeStrings = getCodeStrings();
        Set<String> resourceKeys = getResourceKeys();

        if (findDeadKeys)
            findDeadKeys(codeStrings, resourceKeys);

        if (findMissingKeys)
            findMissingKeys(codeStrings, resourceKeys);

        return (errors == 0);
    }

    /**
     * Find keys in resource bundles which are probably no longer required.
     * A key is probably required if there is a string fragment in the code
     * that is part of the resource key, or if the key is well-known
     * according to various pragmatic rules.
     */
    void findDeadKeys(Set<String> codeStrings, Set<String> resourceKeys) {
        String[] prefixes = {
            "compiler.err.", "compiler.warn.", "compiler.note.", "compiler.misc.",
            "javac."
        };
        for (String rk: resourceKeys) {
            // some keys are used directly, without a prefix.
            if (codeStrings.contains(rk))
                continue;

            // remove standard prefix
            String s = null;
            for (int i = 0; i < prefixes.length && s == null; i++) {
                if (rk.startsWith(prefixes[i])) {
                    s = rk.substring(prefixes[i].length());
                }
            }
            if (s == null) {
                error("Resource key does not start with a standard prefix: " + rk);
                continue;
            }

            if (codeStrings.contains(s))
                continue;

            // keys ending in .1 are often synthesized
            if (s.endsWith(".1") && codeStrings.contains(s.substring(0, s.length() - 2)))
                continue;

            // verbose keys are generated by ClassReader.printVerbose
            if (s.startsWith("verbose.") && codeStrings.contains(s.substring(8)))
                continue;

            // mandatory warning messages are synthesized with no characteristic substring
            if (isMandatoryWarningString(s))
                continue;

            // check known (valid) exceptions
            if (knownRequired.contains(rk))
                continue;

            // check known suspects
            if (needToInvestigate.contains(rk))
                continue;

            //check lint description keys:
            if (s.startsWith("opt.Xlint.desc.")) {
                String option = s.substring(15);
                boolean found = false;

                for (LintCategory lc : LintCategory.values()) {
                    if (option.equals(lc.option))
                        found = true;
                }

                if (found)
                    continue;
            }

            error("Resource key not found in code: " + rk);
        }
    }

    /**
     * The keys for mandatory warning messages are all synthesized and do not
     * have a significant recognizable substring to look for.
     */
    private boolean isMandatoryWarningString(String s) {
        String[] bases = { "deprecated", "unchecked", "varargs", "sunapi" };
        String[] tails = { ".filename", ".filename.additional", ".plural", ".plural.additional", ".recompile" };
        for (String b: bases) {
            if (s.startsWith(b)) {
                String tail = s.substring(b.length());
                for (String t: tails) {
                    if (tail.equals(t))
                        return true;
                }
            }
        }
        return false;
    }

    Set<String> knownRequired = new TreeSet<String>(Arrays.asList(
        // See Resolve.getErrorKey
        "compiler.err.cant.resolve.args",
        "compiler.err.cant.resolve.args.params",
        "compiler.err.cant.resolve.location.args",
        "compiler.err.cant.resolve.location.args.params",
        "compiler.misc.cant.resolve.location.args",
        "compiler.misc.cant.resolve.location.args.params",
        // JavaCompiler, reports #errors and #warnings
        "compiler.misc.count.error",
        "compiler.misc.count.error.plural",
        "compiler.misc.count.warn",
        "compiler.misc.count.warn.plural",
        // Used for LintCategory
        "compiler.warn.lintOption",
        // Other
        "compiler.misc.base.membership"                                 // (sic)
        ));


    Set<String> needToInvestigate = new TreeSet<String>(Arrays.asList(
        "compiler.misc.fatal.err.cant.close.loader",        // Supressed by JSR308
        "compiler.err.cant.read.file",                      // UNUSED
        "compiler.err.illegal.self.ref",                    // UNUSED
        "compiler.err.io.exception",                        // UNUSED
        "compiler.err.limit.pool.in.class",                 // UNUSED
        "compiler.err.name.reserved.for.internal.use",      // UNUSED
        "compiler.err.no.match.entry",                      // UNUSED
        "compiler.err.not.within.bounds.explain",           // UNUSED
        "compiler.err.signature.doesnt.match.intf",         // UNUSED
        "compiler.err.signature.doesnt.match.supertype",    // UNUSED
        "compiler.err.type.var.more.than.once",             // UNUSED
        "compiler.err.type.var.more.than.once.in.result",   // UNUSED
        "compiler.misc.ccf.found.later.version",            // UNUSED
        "compiler.misc.non.denotable.type",                 // UNUSED
        "compiler.misc.unnamed.package",                    // should be required, CR 6964147
        "compiler.misc.verbose.retro",                      // UNUSED
        "compiler.misc.verbose.retro.with",                 // UNUSED
        "compiler.misc.verbose.retro.with.list",            // UNUSED
        "compiler.warn.proc.type.already.exists",           // TODO in JavacFiler
        "javac.err.invalid.arg",                            // UNUSED ??
        "javac.opt.arg.class",                              // UNUSED ??
        "javac.opt.arg.pathname",                           // UNUSED ??
        "javac.opt.moreinfo",                               // option commented out
        "javac.opt.nogj",                                   // UNUSED
        "javac.opt.printflat",                              // option commented out
        "javac.opt.printsearch",                            // option commented out
        "javac.opt.prompt",                                 // option commented out
        "javac.opt.retrofit",                               // UNUSED
        "javac.opt.s",                                      // option commented out
        "javac.opt.scramble",                               // option commented out
        "javac.opt.scrambleall"                             // option commented out
        ));

    /**
     * For all strings in the code that look like they might be fragments of
     * a resource key, verify that a key exists.
     */
    void findMissingKeys(Set<String> codeStrings, Set<String> resourceKeys) {
        for (String cs: codeStrings) {
            if (cs.matches("[A-Za-z][^.]*\\..*")) {
                // ignore filenames (i.e. in SourceFile attribute
                if (cs.matches(".*\\.java"))
                    continue;
                // ignore package and class names
                if (cs.matches("(com|java|javax|sun)\\.[A-Za-z.]+"))
                    continue;
                // explicit known exceptions
                if (noResourceRequired.contains(cs))
                    continue;
                // look for matching resource
                if (hasMatch(resourceKeys, cs))
                    continue;
                error("no match for \"" + cs + "\"");
            }
        }
    }
    // where
    private Set<String> noResourceRequired = new HashSet<String>(Arrays.asList(
            // system properties
            "application.home", // in Paths.java
            "env.class.path",
            "line.separator",
            "os.name",
            "user.dir",
            // file names
            "ct.sym",
            "rt.jar",
            // -XD option names
            "process.packages",
            "ignore.symbol.file",
            // prefix/embedded strings
            "compiler.",
            "compiler.misc.",
            "opt.Xlint.desc.",
            "count.",
            "illegal.",
            "javac.",
            "verbose."
    ));

    /**
     * Look for a resource that ends in this string fragment.
     */
    boolean hasMatch(Set<String> resourceKeys, String s) {
        for (String rk: resourceKeys) {
            if (rk.endsWith(s))
                return true;
        }
        return false;
    }

    /**
     * Get the set of strings from (most of) the javac classfiles.
     */
    Set<String> getCodeStrings() throws IOException {
        Set<String> results = new TreeSet<String>();
        JavaCompiler c = ToolProvider.getSystemJavaCompiler();
        JavaFileManager fm = c.getStandardFileManager(null, null, null);
        JavaFileManager.Location javacLoc = findJavacLocation(fm);
        String[] pkgs = {
            "javax.annotation.processing",
            "javax.lang.model",
            "javax.tools",
            "com.sun.source",
            "com.sun.tools.javac"
        };
        for (String pkg: pkgs) {
            for (JavaFileObject fo: fm.list(javacLoc,
                    pkg, EnumSet.of(JavaFileObject.Kind.CLASS), true)) {
                String name = fo.getName();
                // ignore resource files, and files which are not really part of javac
                if (name.matches(".*resources.[A-Za-z_0-9]+\\.class.*")
                        || name.matches(".*CreateSymbols\\.class.*"))
                    continue;
                scan(fo, results);
            }
        }
        return results;
    }

    // depending on how the test is run, javac may be on bootclasspath or classpath
    JavaFileManager.Location findJavacLocation(JavaFileManager fm) {
        JavaFileManager.Location[] locns =
            { StandardLocation.PLATFORM_CLASS_PATH, StandardLocation.CLASS_PATH };
        try {
            for (JavaFileManager.Location l: locns) {
                JavaFileObject fo = fm.getJavaFileForInput(l,
                    "com.sun.tools.javac.Main", JavaFileObject.Kind.CLASS);
                if (fo != null)
                    return l;
            }
        } catch (IOException e) {
            throw new Error(e);
        }
        throw new IllegalStateException("Cannot find javac");
    }

    /**
     * Get the set of strings from a class file.
     * Only strings that look like they might be a resource key are returned.
     */
    void scan(JavaFileObject fo, Set<String> results) throws IOException {
        InputStream in = fo.openInputStream();
        try {
            ClassFile cf = ClassFile.read(in);
            for (ConstantPool.CPInfo cpinfo: cf.constant_pool.entries()) {
                if (cpinfo.getTag() == ConstantPool.CONSTANT_Utf8) {
                    String v = ((ConstantPool.CONSTANT_Utf8_info) cpinfo).value;
                    if (v.matches("[A-Za-z0-9-_.]+"))
                        results.add(v);
                }
            }
        } catch (ConstantPoolException ignore) {
        } finally {
            in.close();
        }
    }

    /**
     * Get the set of keys from the javac resource bundles.
     */
    Set<String> getResourceKeys() {
        Set<String> results = new TreeSet<String>();
        for (String name : new String[]{"javac", "compiler"}) {
            ResourceBundle b =
                    ResourceBundle.getBundle("com.sun.tools.javac.resources." + name);
            results.addAll(b.keySet());
        }
        return results;
    }

    /**
     * Report an error.
     */
    void error(String msg) {
        System.err.println("Error: " + msg);
        errors++;
    }

    int errors;
}
