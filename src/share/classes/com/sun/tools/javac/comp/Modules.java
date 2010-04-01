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


package com.sun.tools.javac.comp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.lang.model.element.ModuleElement;
import javax.lang.model.util.ModuleResolver;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.ModuleFileManager;
import javax.tools.ModuleFileManager.InvalidFileObjectException;
import javax.tools.ModuleFileManager.ModuleMode;

import static javax.tools.StandardLocation.*;

import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.CompletionFailure;
import com.sun.tools.javac.code.Symbol.ModuleRequires;
import com.sun.tools.javac.code.Symbol.ModuleSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.jvm.ClassFile;
import com.sun.tools.javac.jvm.ClassFile.ModuleId;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.main.OptionName;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCModuleClass;
import com.sun.tools.javac.tree.JCTree.JCModuleDecl;
import com.sun.tools.javac.tree.JCTree.JCModuleId;
import com.sun.tools.javac.tree.JCTree.JCModulePermits;
import com.sun.tools.javac.tree.JCTree.JCModuleRequires;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Options;

import static com.sun.tools.javac.main.OptionName.*;

/**
 * <p><b>This is NOT part of any API supported by Sun Microsystems.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class Modules extends JCTree.Visitor {
    ClassReader reader;
    Log log;
    JavaFileManager fileManager;
    ModuleFileManager moduleFileManager;
    Names names;
    Symtab syms;
    boolean enabled;

    ModuleMode mode;

    /** The set of locations for entered trees */
    Set<Location> rootLocns = new LinkedHashSet<Location>();

    // The following should be moved to Symtab, with possible reference in ClassReader
    Map<Location,ModuleSymbol> allModules = new LinkedHashMap<Location,ModuleSymbol>();

    /** The current top level tree */
    JCCompilationUnit currTopLevel;

    /** The symbol currently being analyzed. */
    ModuleSymbol currSym;

    static class ModuleContext {
        ModuleContext(JCModuleDecl decl) {
            this.decl = decl;
        }
        final JCModuleDecl decl;
        boolean seenPlatformRequires;
    }

    Env<ModuleContext> env;
    Map<ModuleSymbol, Env<ModuleContext>> moduleEnvs = new HashMap<ModuleSymbol, Env<ModuleContext>>();

    /** True if file manager is not a ModuleFileManager and we have
     * seen module declaration in input trees. */
    boolean moduleFileManagerUnavailable;

    public static Modules instance(Context context) {
        Modules instance = context.get(Modules.class);
        if (instance == null)
            instance = new Modules(context);
        return instance;
    }

    protected Modules(Context context) {
        context.put(Modules.class, this);
        log = Log.instance(context);
        reader = ClassReader.instance(context);
        fileManager = context.get(JavaFileManager.class);
        names = Names.instance(context);
        syms = Symtab.instance(context);

        Options options = Options.instance(context);
        // module system features enabled unless -XDnomodules is set
        enabled = (options.get("nomodules") == null);

        if (!enabled)
            return;
        
        initModuleResolver(context);

        if (fileManager instanceof ModuleFileManager) {
            moduleFileManager = (ModuleFileManager) fileManager;
            mode = moduleFileManager.getModuleMode();
        } else
            mode = ModuleMode.SINGLE;
    }

////////// TO BE DELETED
//////////    private boolean isEnabled(Options options) {
//////////        OptionName[] disablingOptions = {
////////////            XBOOTCLASSPATH_PREPEND,
//////////            ENDORSEDDIRS,
////////////            BOOTCLASSPATH,
////////////            XBOOTCLASSPATH_APPEND,
//////////            EXTDIRS
//////////        };
//////////        for (OptionName n: disablingOptions) {
//////////            if (options.get(n) != null) {
//////////                DEBUG("Modules: disabled by " + n);
//////////                return false;
//////////            }
//////////        }
//////////        if (options.get("nomodules") != null) {
//////////            DEBUG("Modules: disabled by -XDnomodules");
//////////            return false;
//////////        }
//////////
//////////        DEBUG("Modules: enabled");
//////////        return true;
//////////    }

    <T extends JCTree> void acceptAll(List<T> trees) {
        for (List<T> l = trees; l.nonEmpty(); l = l.tail)
            l.head.accept(this);
    }



    @Override
    public void visitModuleDef(JCModuleDecl tree) {
        DEBUG("Modules.visitModuleDef " + tree.id);

        if (mode == ModuleMode.SINGLE) {
            if (moduleFileManager == null) {
                log.error(tree, "mdl.module.file.manager.required");
                moduleFileManagerUnavailable = true;
            } else {
                currTopLevel.locn = moduleFileManager.join(List.of(CLASS_PATH, SOURCE_PATH));
                if (state == State.INITIAL)
                    rootLocns.add(currTopLevel.locn);
            }
        }

        ModuleSymbol sym = enterModule(currTopLevel.locn);
        if (sym.name != null) {
            log.error(tree.pos(), "mdl.already.defined", sym.module_info.sourcefile);
            sym = new ModuleSymbol(TreeInfo.fullName(tree.id.qualId), syms.rootModule);
        } else {
            sym.name = sym.fullname = TreeInfo.fullName(tree.id.qualId);
            sym.module_info.fullname = ClassSymbol.formFullName(sym.module_info.name, sym);
            sym.module_info.flatname = ClassSymbol.formFlatName(sym.module_info.name, sym);
            sym.module_info.sourcefile = currTopLevel.sourcefile;
            sym.module_info.members_field = new Scope(sym.module_info);
            sym.completer = null;
        }

        DEBUG("Modules.visitModuleDef name "         + sym.name);
        DEBUG("Modules.visitModuleDef fullname "     + sym.fullname);
        DEBUG("Modules.visitModuleDef flatName() "   + sym.flatName());
        DEBUG("Modules.visitModuleDef m-i name "     + sym.module_info.name);
        DEBUG("Modules.visitModuleDef m-i fullname " + sym.module_info.fullname);
        DEBUG("Modules.visitModuleDef m-i flatname " + sym.module_info.flatname);

        sym.location = currTopLevel.locn;
        tree.sym = sym;

        sym.version = tree.getId().version;
        sym.permits = new ListBuffer<Name>();
        sym.provides = new ListBuffer<ModuleId>();
        sym.requires = new LinkedHashMap<ModuleId,ModuleRequires>();
        for (List<JCModuleId> l = tree.provides; l.nonEmpty(); l = l.tail) {
            JCModuleId moduleId = l.head;
            sym.provides.append(new ModuleId(TreeInfo.fullName(moduleId.qualId), moduleId.version));
        }

        currSym = sym;
        Env<ModuleContext> menv = env.dup(tree, new ModuleContext(tree));
        moduleEnvs.put(sym, menv);
        Env<ModuleContext> prev = env;
        env = menv;
        try {
            acceptAll(tree.metadata);

            if (!env.info.seenPlatformRequires) {
                DEBUG("Modules.visitModuleDef seenPlatformRequires:" + env.info.seenPlatformRequires);
                ModuleId mid = getDefaultPlatformModule();
                sym.requires.put(mid, new ModuleRequires(mid, List.of(names.synthetic)));
            }
        } finally {
            currSym = null;
            env = prev;
        }
    }

    @Override
    public void visitTopLevel(JCCompilationUnit tree) {
        DEBUG("Modules.visitTopLevel " + tree.sourcefile);
        env = new Env<ModuleContext>(tree, null);
        env.toplevel = tree;
        currTopLevel = tree;
        JavaFileObject prev = log.useSource(tree.sourcefile);
        try {
            if (mode == ModuleMode.MULTIPLE) {
                assert moduleFileManager != null;
                Location l = moduleFileManager.join(List.of(MODULE_PATH, SOURCE_PATH));
                JCExpression pn = tree.getPackageName();
                String pkgName = (pn == null) ? "" : TreeInfo.fullName(pn).toString();
                try {
                    tree.locn = moduleFileManager.getModuleLocation(l, tree.sourcefile, pkgName);
                    DEBUG("Modules.visitTopLevel MULTIPLE " + tree.locn);
                    if (state == State.INITIAL)
                        rootLocns.add(tree.locn);
                } catch (InvalidFileObjectException e) {
                    log.error(pn, "mdl.file.in.wrong.directory", tree.sourcefile, pkgName);
                }
            }

            if (TreeInfo.isModuleInfo(tree))
                acceptAll(tree.defs);
        } finally {
            currTopLevel = null;
            log.useSource(prev);
            DEBUG("Modules.visitTopLevel EXIT rootLocns=" + rootLocns);
        }
    }

    @Override
    public void visitModuleClass(JCModuleClass tree) {
//        ModuleSymbol sym = currSym;
//        Name className = TreeInfo.fullName(tree.qualId);
//        // JIGSAW TODO check conflicts (at most one class)
//        sym.className = reader.enterClass(className);
//        sym.classFlags = tree.flags;
    }

    @Override
    public void visitModulePermits(JCModulePermits tree) {
        ModuleSymbol sym = currSym;
        for (List<JCExpression> l = tree.moduleNames; l.nonEmpty(); l = l.tail) {
            JCTree qualId = l.head;
            Name moduleName = TreeInfo.fullName(qualId);
            // JIGSAW TODO check duplicates
            sym.permits.add(moduleName);
        }
    }

    @Override
    public void visitModuleRequires(JCModuleRequires tree) {
        ModuleSymbol sym = currSym;
        for (List<JCModuleId> l = tree.moduleIds; l.nonEmpty(); l = l.tail) {
            JCModuleId moduleId = l.head;
            ModuleId mid = new ModuleId(TreeInfo.fullName(moduleId.qualId), moduleId.version);
            // JIGSAW TODO check duplicates
            sym.requires.put(mid, new ModuleRequires(mid, tree.flags));
            ModuleResolver mr = getModuleResolver();
            if (mr.isPlatformName(mid.name))
                env.info.seenPlatformRequires = true;
        }
    }

    @Override
    public void visitTree(JCTree tree) { }

    ModuleSymbol enterModule(Location locn) {
        ModuleSymbol sym = allModules.get(locn);
        if (sym == null) {
            sym = new ModuleSymbol(null, syms.rootModule);
            sym.location = locn;
            sym.module_info = new ClassSymbol(0, names.module_info, sym);
            sym.module_info.modle = sym;
            sym.completer = new Symbol.Completer() {
                public void complete(Symbol sym) throws CompletionFailure {
                    readModule((ModuleSymbol) sym);
                }
            };
            allModules.put(locn, sym);
        }
        return sym;
    }

    void readModule(ModuleSymbol sym) {
        Location locn = sym.location;
        JavaFileObject srcFile = getModuleInfo(locn, JavaFileObject.Kind.SOURCE);
        JavaFileObject classFile = getModuleInfo(locn, JavaFileObject.Kind.CLASS);
        DEBUG("Modules.readModule: src:" + srcFile + " class:" + classFile);
        JavaFileObject file;
        if (srcFile == null) {
            if (classFile == null) {
                sym.name = names.empty; // unnamed module
                return;
            }
            file = classFile;
        } else if (classFile == null)
            file = srcFile;
        else
            file = reader.preferredFileObject(srcFile, classFile);
        DEBUG("Modules.readModule: select:" + file);

        sym.module_info.classfile = file;
        DEBUG("Modules.readModule: complete:" + sym + " " + sym.module_info.classfile);
        reader.complete(sym);
        assert sym.name != null;
        DEBUG("Modules.readModule: finished:" + sym + " name:" + sym.name + " fullname:" + sym.fullname + " flatName():" + sym.flatName());
    }

    JavaFileObject getModuleInfo(Location locn, JavaFileObject.Kind kind) {
        try {
            return fileManager.getJavaFileForInput(locn, "module-info", kind);
        } catch (IOException e) {
            return null;
        }
    }

    ModuleId getDefaultPlatformModule() {
        if (defaultPlatformModule == null) {
            ModuleResolver mr = getModuleResolver();
            String def = mr.getDefaultPlatformModule();
            int at = def.indexOf("@");
            if (at == -1)
                defaultPlatformModule = new ModuleId(names.fromString(def), null);
            else {
                Name name = names.fromString(def.substring(0, at).trim());
                Name version = names.fromString(def.substring(at + 1).trim());
                defaultPlatformModule = new ModuleId(name, version);
            }
        }
        return defaultPlatformModule;
    }
    // where
    private ModuleId defaultPlatformModule;

    private boolean resolve(List<JCCompilationUnit> trees) {
        if (moduleFileManagerUnavailable)
            return false;

        DEBUG("Modules.resolve mode=" + mode + ", rootLocns=" + rootLocns + " (" + rootLocns.size() + ")" );
        if (mode == ModuleMode.SINGLE && rootLocns.isEmpty()) {
            if (moduleFileManager != null) {
                rootLocns.add(moduleFileManager.join(List.of(CLASS_PATH, SOURCE_PATH)));
            } else {
                // TODO: check for module-info.{java,class} and give error if found
                // TODO?? use a custom DEFAULT locn, instead of null??
                return true;
            }
        }

        DEBUG("Modules.resolve: building roots, rootLocns=" + rootLocns);
        List<ModuleSymbol> roots = List.nil();
        for (Location locn: rootLocns) {
            DEBUG("Modules.resolve: building roots: locn: " + locn);
            ModuleSymbol msym = enterModule(locn);
            DEBUG("Modules.resolve: building roots: msym: " + msym);
            msym.complete();
            DEBUG("Modules.resolve: building roots: completed: " + msym);
            if (msym.name != names.empty)
                roots = roots.prepend(msym);
        }
        roots = roots.reverse();
        DEBUG("Modules.resolve: roots: " + roots);

        updateTrees(trees);

        if (roots.isEmpty())
            return true;

        DEBUG("Modules.resolve: modules so far: " + allModules);

        DEBUG("Modules.resolve: reading module-info as needed");
        Location l = (moduleFileManager.getModuleMode() == ModuleMode.SINGLE)
                ? MODULE_PATH : moduleFileManager.join(List.of(MODULE_PATH, SOURCE_PATH));
        for (Location locn: moduleFileManager.getModuleLocations(l)) {
            DEBUG("Modules.resolve: ensuring module-info for " + locn);
            enterModule(locn).complete();
        }

        DEBUG("Modules.resolve: resolve modules");
        ModuleResolver mr = getModuleResolver();
        DEBUG("Modules.resolve: module resolver: " + mr);
        try {
            ListBuffer<ModuleSymbol> namedModules = new ListBuffer<ModuleSymbol>();
            for (ModuleSymbol msym: allModules.values()) {
                if (msym.name != names.empty)
                    namedModules.add(msym);
            }
            Iterable<? extends ModuleElement> modules =
                    mr.resolve(roots, namedModules);

            ListBuffer<Location> locns = new ListBuffer<Location>();
            for (ModuleElement me: modules) {
                ModuleSymbol msym = (ModuleSymbol) me;
                DEBUG("Modules.resolve: msym: " + msym);
                DEBUG("Modules.resolve: msym.location: " + msym.location);
                locns.add(msym.location);
            }
            Location merged = moduleFileManager.join(locns);
            DEBUG("Modules.resolve: merged result: " + merged);
            reader.setPathLocation(merged);
        } catch (ModuleResolver.ResolutionException e) {
            DEBUG("Modules.resolve: resolution error " + e);
            e.printStackTrace();
            return false;
        }
        return true;

    }

    protected ModuleResolver getModuleResolver() {
        return moduleResolver;
    }

    protected void initModuleResolver(Context context) {
        Options options = Options.instance(context);
        boolean useZeroMod = (options.get("zeroMod") != null);

        if (!useZeroMod) {
//            ServiceLoader<ModuleResolver> loader = ServiceLoader.load(ModuleResolver.class);
//            // for now, use the first available, if any
//            for (Iterator<ModuleResolver> iter = loader.iterator(); iter.hasNext(); ) {
//                moduleResolver = iter.next();
//                moduleResolver.init(options);
//                return;
//            }

            String library = options.get(OptionName.L);
            if (library != null || !isLegacyRuntime()) {
                // use Class.forName on jigsaw module resolver, to avoid bootstrap dependency
                try {
                    String jigsawModuleResolver = "com.sun.tools.javac.jigsaw.JigsawModuleResolver";
                    Class<? extends ModuleResolver> c =
                            Class.forName(jigsawModuleResolver).asSubclass(ModuleResolver.class);
                    Constructor<? extends ModuleResolver> constr =
                            c.getDeclaredConstructor(new Class<?>[] { Context.class });
                    moduleResolver = constr.newInstance(context);
                    DEBUG("Modules.initModuleResolver: " + moduleResolver);

                    OptionName[] unsupportedOptions = {
            //            XBOOTCLASSPATH_PREPEND,
                        ENDORSEDDIRS,
            //            BOOTCLASSPATH,
            //            XBOOTCLASSPATH_APPEND,
                        EXTDIRS
                    };
                    for (OptionName o: unsupportedOptions) {
                        if (options.get(o) != null) {

                        }
                    }
                    return;
                } catch (ClassNotFoundException e) {
                    // running in JDK 6 mode
                    DEBUG("Modules.initModuleResolver: " + e);
                } catch (IllegalAccessException e) {
                    // FIXME: fall through for now; should report error
                    DEBUG("Modules.initModuleResolver: " + e);
                } catch (NoSuchMethodException e) {
                    // FIXME: fall through for now; should report error
                    DEBUG("Modules.initModuleResolver: " + e);
                } catch (InstantiationException e) {
                    // FIXME: fall through for now; should report error
                    DEBUG("Modules.initModuleResolver: " + e);
                } catch (InvocationTargetException e) {
                    DEBUG("Modules.initModuleResolver: " + e);
                    Throwable t = e.getTargetException();
                    if (t instanceof FileNotFoundException)
                        log.error("module.library.not.found", t.getMessage());
                    else if (t instanceof IOException)
                        log.error("canot.open.module.library", t.getMessage()); // FIXME, t.getMessage is a barely helpful string
                    else if (t instanceof RuntimeException)
                        throw new RuntimeException(t);
                    else if (t instanceof Error)
                        throw new Error(t);
                    else
                        throw new AssertionError(t);
                }
            }
        }

        // use ZeroMod
        moduleResolver = new ZeroMod(new ErrorHandler() {
            public void report(ModuleSymbol msym, ModuleId mid, String key, Object... args) {
                error(msym, mid, key, args);
            }
        });
        DEBUG("Modules.initModuleResolver: zeromod: " + moduleResolver);
    }
    // where
    private ModuleResolver moduleResolver;

    private boolean isLegacyRuntime() {
        File javaHome = new File(System.getProperty("java.home"));
        File rt_jar = new File(new File(javaHome, "lib"), "rt.jar");
        return rt_jar.exists();
    }

    private void error(ModuleSymbol msym, ModuleId id, String key, Object... args) {
        // TODO, determine error location from msym, mid
        ClassSymbol minfo = msym.module_info;

        Env<ModuleContext> menv = moduleEnvs.get(msym);
        DEBUG("Modules.error " + msym + " -- " + moduleEnvs.get(msym));
        JavaFileObject fo;
        JCDiagnostic.DiagnosticPosition pos;
        if (menv == null) {
            fo = (minfo.sourcefile != null ? minfo.sourcefile : minfo.classfile);
            if (fo == null)
                fo = msym.module_info.classfile;
            pos = null;
        } else {
            fo = menv.toplevel.sourcefile;
            pos = treeFinder.find(menv.info.decl, id);
        }

        JavaFileObject prev = log.useSource(fo);
        try {
            log.error(pos, key, args);
        } finally {
            log.useSource(prev);
        }
    }

    class TreeFinder extends JCTree.Visitor {
        ModuleId mid;
        JCTree result;

        JCTree find(JCTree tree, ModuleId mid) {
            DEBUG("Modules.TreeFinder.find mid=" + mid);
            this.mid = mid;
            result = null;
            tree.accept(this);
            DEBUG("Modules.TreeFinder.find result=" + result);
            return result;
        }

        @Override
        public void visitModuleDef(JCModuleDecl tree) {
            search(tree.id);
            search(tree.provides);
            search(tree.metadata);
        }

        @Override
        public void visitModuleRequires(JCModuleRequires tree) {
            search(tree.moduleIds);
        }

        @Override
        public void visitModuleId(JCModuleId tree) {
            DEBUG("Modules.treeFinder.visitModuleId " + tree + " " + mid);
            if (equal(TreeInfo.fullName(tree.qualId), mid.name) && equal(tree.version, mid.version))
                result = tree;
            DEBUG("Modules.treeFinder.visitModuleId result " + result);
        }

        void search(JCTree tree) {
            if (result != null)
                return;

            tree.accept(this);
        }

        void search(List<? extends JCTree> trees) {
            if (result != null)
                return;

            for (List<? extends JCTree> l = trees; l.nonEmpty(); l = l.tail) {
                l.head.accept(this);
                if (result != null)
                    return;
            }
        }

        <T> boolean equal(T t1, T t2) {
            DEBUG("Modules.treeFinder.equal " + t1 + " " + t2 + (t1 == null ? t2 == null : t1.equals(t2)));
            return t1 == null ? t2 == null : t1.equals(t2);
        }
    }
    TreeFinder treeFinder = new TreeFinder();

    private void updateTrees(List<JCCompilationUnit> trees) {
        if (mode == ModuleMode.SINGLE) {
            assert rootLocns.size() == 1;
            Location locn = rootLocns.iterator().next();
            locn.getClass(); // debug
            ModuleSymbol msym = allModules.get(locn);
            DEBUG("Modules.updateTrees: update SINGLE trees: " + locn + " " + msym);
            if (msym == null) {
                msym = syms.unnamedModule;
                DEBUG("Modules.updateTrees: using unnamed module " + syms.unnamedModule.completer);
            }
            for (List<JCCompilationUnit> l = trees; l.nonEmpty(); l = l.tail) {
                JCCompilationUnit tree = l.head;
                assert tree.locn == null || tree.locn == locn;
                tree.locn = locn;
                assert tree.modle == null || tree.modle == msym;
                tree.modle = msym;
            }
        } else {
            for (List<JCCompilationUnit> l = trees; l.nonEmpty(); l = l.tail) {
                JCCompilationUnit t = l.head;
                ModuleSymbol msym = allModules.get(t.locn);
                if (msym == null) {
                    msym = syms.unnamedModule;
                    DEBUG("Modules.updateTrees: using unnamed module " + syms.unnamedModule.completer);
                }
                t.modle = msym;
                DEBUG("Modules.updateTrees: update MULTIPLE trees: " + t.locn + " " + t.modle);
            }
        }
    }

    private enum State { INITIAL, RESOLVING, RESOLVED};
    private State state = State.INITIAL;

    private int enterCount = 0; // debug only

    public boolean enter(List<JCCompilationUnit> trees) {
        if (!enabled) {
            CheckNoModulesVisitor v = new CheckNoModulesVisitor(log);
            return v.checkNoModules(trees);
        }

        int count = enterCount++;
        DEBUG("Modules.enter " + count + " " + state);

        DEBUG("Modules.enter " + count + ": acceptAll");
        acceptAll(trees);

        try { // debug

        switch (state) {
            case INITIAL:
                state = State.RESOLVING;
                try {
                    return resolve(trees);
                } finally {
                    state = State.RESOLVED;
                }

            case RESOLVING:
                updateTrees(trees);
                return true;

            case RESOLVED:
                return true;

            default:
                throw new AssertionError();
        }

        // debug
        } finally {
             DEBUG("Modules.enter " + count + ": exit " + state);
        }
    }
    // where
    private static class CheckNoModulesVisitor extends TreeScanner {
        private final Log log;
        private boolean result = true;
        CheckNoModulesVisitor(Log log) {
            this.log = log;
        }
        boolean checkNoModules(List<? extends JCTree> trees) {
            scan(trees);
            return result;
        }
        @Override
        public void visitClassDef(JCClassDecl tree) {
        }
        @Override
        public void visitModuleDef(JCModuleDecl tree) {
            result = false;
            log.error(tree, "module.decl.not.permitted");
        }
        @Override
        public void visitTopLevel(JCCompilationUnit tree) {
            JavaFileObject prev = log.useSource(tree.sourcefile);
            try {
                 super.visitTopLevel(tree);
            } finally {
                log.useSource(prev);
            }
        }
    }

    // Quick and dirty temporary debug printing;
    // this should all be removed prior to final integration
    boolean DEBUG = (System.getProperty("javac.debug.modules") != null);
    void DEBUG(String s) {
        if (DEBUG)
            System.err.println(s);
    }

    private static <T> String toString(Iterable<T> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        String sep = "";
        for (T t: items) {
            sb.append(sep);
            sb.append(t);
            sep = ",";
        }
        sb.append("]");
        return sb.toString();
    }

    // should be static within ZeroMod
    enum VersionErrorKind {
        NO_VERSION_AVAILABLE("mdl.no.version.available"),
        NO_UNIQUE_VERSION_AVAILABLE("mdl.no.unique.version.available"),
        REQUIRED_VERSION_NOT_AVAILABLE("mdl.required.version.not.available");
        VersionErrorKind(String key) { this.key = key; }
        final String key;
    };

    interface ErrorHandler {
        void report(ModuleSymbol msym, ModuleId mid, String key, Object... args);
    }

    class ZeroMod implements ModuleResolver {
        private ErrorHandler errorHandler;

        ZeroMod(ErrorHandler e) {
            errorHandler = e;
        }

        public Iterable<? extends ModuleElement> resolve(
                Iterable<? extends ModuleElement> roots,
                Iterable<? extends ModuleElement> modules)
        {
            DEBUG("ZeroMod: roots: " + Modules.toString(roots));
            DEBUG("ZeroMod: modules: " + Modules.toString(modules));
            roots.getClass();
            modules.getClass();

            moduleTable = buildModuleTable(modules);
            List<Node> rootNodes = getNodes(roots);
            tarjan(rootNodes);

            DEBUG("ZeroMod.NODE MAP {");
            for (Node n: nodeMap.values())
                DEBUG("  Node " + n + " " + n.scc);
            DEBUG("}");

            ListBuffer<ModuleSymbol> results = new ListBuffer<ModuleSymbol>();
            for (Node node: rootNodes) {
                if (!results.contains(node.sym))
                    getVisibleModules(node.scc, results);
            }

            DEBUG("ZeroMod: results: " + Modules.toString(results));
            return results;
        }

        public Iterable<? extends ModuleElement> getVisibleModules(ModuleElement elem) {
            ModuleSymbol sym = (ModuleSymbol) elem;
            DEBUG("ZeroMod getVisibleModules " + getNode(sym));
            SCC scc = getNode(sym).scc;
            ListBuffer<ModuleSymbol> results = new ListBuffer<ModuleSymbol>();
            getVisibleModules(scc, results);
            return results.toList();
        }

        private void getVisibleModules(SCC scc, ListBuffer<ModuleSymbol> results) {
            for (Node n: scc.nodes)
                results.add(n.sym);
            for (SCC child: scc.getChildren())
                getVisibleModules(child, results);
        }

        private Map<Name, Map<Name, ModuleSymbol>> buildModuleTable(
                Iterable<? extends ModuleElement> modules) {
            Map<Name, Map<Name, ModuleSymbol>> table = new HashMap<Name, Map<Name, ModuleSymbol>>();
            // build module index
            for (ModuleElement elem: modules) {
                ModuleSymbol sym = (ModuleSymbol) elem;
                add(table, sym, new ClassFile.ModuleId(sym.name, sym.version));
                for (List<ClassFile.ModuleId> l = sym.provides.toList(); l.nonEmpty(); l = l.tail)
                    add(table, sym, l.head);
            }
            return table;
        }

        private void add(Map<Name, Map<Name, ModuleSymbol>> table,
                ModuleSymbol sym, ModuleId mid) {
            Map<Name, ModuleSymbol> versions = table.get(mid.name);
            if (versions == null)
                table.put(mid.name, versions = new HashMap<Name, ModuleSymbol>());
            ModuleSymbol m = versions.get(mid.version);
            if (m != null)
                // TODO ?? enhance error to disambiguate between define and provides
                errorHandler.report(sym, mid, "mdl.duplicate.definition", m);
            else
                versions.put(mid.version, sym);
        }

        private ModuleSymbol getModule(ModuleId mid) throws ModuleException {
            Map<Name, ModuleSymbol> versions = moduleTable.get(mid.name);
            if (versions == null)
                throw new ModuleException("mdl.no.version.available", mid);
            if (mid.version == null) {
                if (versions.size() > 1)
                    throw new ModuleException("mdl.no.unique.version.available", mid);
                return versions.values().iterator().next();
            } else {
                ModuleSymbol sym = versions.get(mid.version);
                if (sym == null)
                    throw new ModuleException("mdl.required.version.not.available", mid);
                return sym;
            }
        }
        // where
        private Map<Name, Map<Name, ModuleSymbol>> moduleTable;

        public boolean isPlatformName(CharSequence name) {
            String n = name.toString();
            return n.equals("jdk") || n.startsWith("jdk.");  // for now
        }

        public String getDefaultPlatformModule() {
            return "jdk@7-ea"; // for now
        }

        private class ModuleException extends Exception {
            private static final long serialVersionUID = 0;
            ModuleException(String key, ModuleId moduleId) {
                this.key = key;
                this.moduleId = moduleId;
            }
            final String key;
            final ModuleId moduleId;
        }

        List<Node> getNodes(Iterable<? extends ModuleElement> elems) {
            ListBuffer<Node> lb = new ListBuffer<Node>();
            for (ModuleElement elem: elems) {
                ModuleSymbol sym = (ModuleSymbol) elem;
                lb.add(getNode(sym));
            }
            return lb.toList();
        }

        Node getNode(ModuleSymbol sym) {
            Node node = nodeMap.get(sym);
            if (node == null)
                nodeMap.put(sym, (node = new Node(sym)));
            return node;
        }
        // where
        private Map<ModuleSymbol, Node> nodeMap= new HashMap<ModuleSymbol, Node>();

        private class Node implements Comparable<Node> {
            final ModuleSymbol sym;
            SCC scc;
            int index = -1;
            int lowlink;
            boolean active;

            Node(ModuleSymbol sym) {
                this.sym = sym;
            }

            Iterable<Node> getDependencies() {
                DEBUG("ZeroMod.Node.getDependencies: " + sym + " " + sym.requires);
                ListBuffer<Node> nodes = new ListBuffer<Node>();
                for (ModuleRequires mr: sym.requires.values()) {
                    if (isPlatformName(mr.moduleId.name)) {
                        DEBUG("ZeroMod.Node.getDependencies: ignore platform module " + mr.moduleId.name);
                        continue;
                    }
                    try {
                        nodes.add(getNode(getModule(mr.moduleId)));
                    } catch (ModuleException e) {
                        errorHandler.report(sym, e.moduleId, e.key, e.moduleId);
                    }
                }
                return nodes.toList();
            }

            @Override
            public String toString() {
                return sym.name + "@" + sym.version + "(index:" + index +",low:" + lowlink + ",active:" + active + ")" ;
            }

            public int compareTo(Node o) {
                return (index < o.index) ? -1 : (index == o.index) ? 0 : 1;
            }
        }

        private class SCC {
            void add(Node node) {
                nodes.add(node);
                node.scc = this;
            }

            Set<SCC> getChildren() {
                if (children == null) {
                    children = new LinkedHashSet<SCC>();
                    for (Node node: nodes) {
                        for (Node n: node.getDependencies()) {
                            n.scc.getClass(); // nullcheck
                            if (n.scc != this)
                                children.add(n.scc);
                        }
                    }
                }
                return children;
            }

            @Override
            public String toString() {
                return nodes.toString();
            }

            private SortedSet<Node> nodes = new TreeSet<Node>();
            private Set<SCC> children;
        }

        // Tarjan's algorithm to determine strongly connected components of a
        // directed graph in linear time.

        void tarjan(Iterable<? extends Node> nodes) {
            for (Node node: nodes) {
                if (node.index == -1)
                    tarjan(node);
            }
        }

        void tarjan(Node v) {
            v.index = index;
            v.lowlink = index;
            index++;
            stack.add(0, v);
            v.active = true;
            for (Node n: v.getDependencies()) {
                if (n.index == -1) {
                    tarjan(n);
                    v.lowlink = Math.min(v.lowlink, n.lowlink);
                } else if (stack.contains(n)) {
                    v.lowlink = Math.min(v.lowlink, n.index);
                }
            }
            if (v.lowlink == v.index) {
                Node n;
                SCC scc = new SCC();
                do {
                    n = stack.remove(0);
                    n.active = false;
                    scc.add(n);
                } while (n != v);
            }
        }

        private int index = 0;
        private ArrayList<Node> stack = new ArrayList<Node>();
    }
}
