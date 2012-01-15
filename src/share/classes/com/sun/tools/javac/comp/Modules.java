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


package com.sun.tools.javac.comp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.WeakHashMap;

import javax.lang.model.element.ModuleElement;
import javax.lang.model.util.ModuleResolver;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.ModuleFileManager;
import javax.tools.ModuleFileManager.InvalidFileObjectException;
import javax.tools.ModuleFileManager.ModuleMode;
import javax.tools.StandardLocation;

import static javax.tools.StandardLocation.*;

import com.sun.source.tree.RequiresFlag;

import com.sun.tools.javac.code.Directive;
import com.sun.tools.javac.code.Directive.PermitsDirective;
import com.sun.tools.javac.code.Directive.ProvidesModuleDirective;
import com.sun.tools.javac.code.Directive.RequiresModuleDirective;
import com.sun.tools.javac.code.Directive.ViewDeclaration;
import com.sun.tools.javac.code.ModuleId;
import com.sun.tools.javac.code.ModuleIdQuery;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.CompletionFailure;
import com.sun.tools.javac.code.Symbol.ModuleSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCEntrypointDirective;
import com.sun.tools.javac.tree.JCTree.JCModuleDecl;
import com.sun.tools.javac.tree.JCTree.JCExportDirective;
import com.sun.tools.javac.tree.JCTree.JCModuleId;
import com.sun.tools.javac.tree.JCTree.JCModuleIdQuery;
import com.sun.tools.javac.tree.JCTree.JCPermitsDirective;
import com.sun.tools.javac.tree.JCTree.JCProvidesModuleDirective;
import com.sun.tools.javac.tree.JCTree.JCProvidesServiceDirective;
import com.sun.tools.javac.tree.JCTree.JCRequiresModuleDirective;
import com.sun.tools.javac.tree.JCTree.JCRequiresServiceDirective;
import com.sun.tools.javac.tree.JCTree.JCViewDecl;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Debug;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Options;

import static com.sun.tools.javac.main.Option.*;

/**
 * <p><b>This is NOT part of any supported API.
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
    Debug debug;
    boolean enabled;
    Map<JCTree.JCModuleDirective, Reference<Directive>> directiveMap =
            new WeakHashMap<JCTree.JCModuleDirective, Reference<Directive>>();

    ModuleMode mode;

    ModuleId baseModule;
    ModuleIdQuery baseModuleQuery;

    /**
     * The set of module locations for entered trees.
     * In single module compilation mode, it is a composite of class path and
     * source path.
     * In multi-module compilation mode, it is the set of "module directories"
     * for the files on the command line (i.e. the directories above each
     * source files package hierarchy.)
     */
    Set<Location> rootLocns = new LinkedHashSet<Location>();

    // The following should be moved to Symtab, with possible reference in ClassReader
    Map<Location,ModuleSymbol> allModules = new LinkedHashMap<Location,ModuleSymbol>();

    /** The current top level tree */
    JCCompilationUnit currTopLevel;

    /** The symbol currently being analyzed. */
    ModuleSymbol currSym;
    
    /** The view currently being analyzed. */
    JCTree.JCViewDecl currView;

    /** The current set of directives being built. */
    ListBuffer<Directive> currDirectives;

    static class ModuleContext {
        ModuleContext(JCModuleDecl decl) {
            this.decl = decl;
            requiresBaseModule = true;
        }
        final JCModuleDecl decl;
        boolean requiresBaseModule;
        boolean isPlatformModule;
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
        debug = new Debug("modules", options, log);

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

        Target target = Target.instance(context);
        Name v = names.fromString(target.name.replaceAll("^1.", ""));
        baseModule = new ModuleId(names.java_base, v);
        Name q = names.fromString(">=" + v);
        baseModuleQuery = new ModuleIdQuery(names.java_base, q);
    }

    <T extends JCTree> void acceptAll(List<T> trees) {
        for (List<T> l = trees; l.nonEmpty(); l = l.tail)
            l.head.accept(this);
    }

    public Directive getDirective(JCTree.JCModuleDirective tree) {
        Reference<Directive> r = directiveMap.get(tree);
        return (r == null) ? null : r.get();
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
            sym.extendedMetadata = tree.metadata;
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
        currSym = sym;
        currDirectives = new ListBuffer<Directive>();
        Env<ModuleContext> menv = env.dup(tree, new ModuleContext(tree));
        moduleEnvs.put(sym, menv);
        Env<ModuleContext> prev = env;
        env = menv;
        try {
            if (isBaseModuleName(sym.name))
                env.info.requiresBaseModule = false;

            acceptAll(tree.directives);

            DEBUG("Modules.visitModuleDef requiresBaseModule:" + env.info.requiresBaseModule);
            if (env.info.requiresBaseModule) {
                currDirectives.add(new RequiresModuleDirective(baseModuleQuery,
                        EnumSet.of(Directive.RequiresFlag.SYNTHESIZED)));
            }
        } finally {
            sym.directives = currDirectives.toList();
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
    public void visitEntrypoint(JCEntrypointDirective tree) {
//        ModuleSymbol sym = currSym;
//        Name className = TreeInfo.fullName(tree.qualId);
//        // JIGSAW TODO check conflicts (at most one class)
//        sym.className = reader.enterClass(className);
//        sym.classFlags = tree.flags;C
    }

    @Override
    public void visitExports(JCExportDirective tree) {
    }

    @Override
    public void visitPermits(JCPermitsDirective tree) {
        JCTree qualId = tree.moduleName;
        Name moduleName = TreeInfo.fullName(qualId);
        // JIGSAW TODO check duplicates
        PermitsDirective d = new PermitsDirective(moduleName);
        directiveMap.put(tree, new WeakReference<Directive>(d));
        currDirectives.add(d);
    }

    @Override
    public void visitProvidesModule(JCProvidesModuleDirective tree) {
        JCModuleId moduleId = tree.moduleId;
        ProvidesModuleDirective d = new ProvidesModuleDirective(
                new ModuleId(TreeInfo.fullName(moduleId.qualId), moduleId.version));
        directiveMap.put(tree, new WeakReference<Directive>(d));
        currDirectives.add(d);
        if (isBaseModuleName(d.moduleId.name))
            env.info.requiresBaseModule = false;
    }

    @Override
    public void visitProvidesService(JCProvidesServiceDirective tree) {
    }

    @Override
    public void visitRequiresModule(JCRequiresModuleDirective tree) {
        JCModuleIdQuery moduleIdQuery = tree.moduleIdQuery;
        ModuleIdQuery mq = new ModuleIdQuery(TreeInfo.fullName(moduleIdQuery.qualId), moduleIdQuery.versionQuery);
        // JIGSAW TODO check duplicates
        Set<Directive.RequiresFlag> flags = EnumSet.noneOf(Directive.RequiresFlag.class);
        for (RequiresFlag f: tree.flags) {
            switch (f) {
                case REEXPORT:
                    flags.add(Directive.RequiresFlag.REEXPORT);
                    break;
                case OPTIONAL:
                    flags.add(Directive.RequiresFlag.OPTIONAL);
                    break;
                case LOCAL:
                    flags.add(Directive.RequiresFlag.LOCAL);
                    break;
            }
        }
        RequiresModuleDirective d = new RequiresModuleDirective(mq, flags);
        directiveMap.put(tree, new WeakReference<Directive>(d));
        currDirectives.add(d);
        if (isBaseModuleName(mq.name))
            env.info.requiresBaseModule = false;
    }

    @Override
    public void visitRequiresService(JCRequiresServiceDirective tree) {
    }

    @Override
    public void visitView(JCViewDecl tree) {
        if (currView == null) {
            ListBuffer<Directive> prev = currDirectives;
            currDirectives = new ListBuffer<Directive>();
            try {
                acceptAll(tree.directives);
            } finally {
                ViewDeclaration v = new ViewDeclaration(TreeInfo.fullName(tree.name),
                        currDirectives.toList());
                if (isBaseModuleName(v.name))
                    env.info.requiresBaseModule = false;
                directiveMap.put(tree, new WeakReference<Directive>(v));
                currDirectives = prev;
                currDirectives.add(v);
            }
        } else {
            log.error(tree, "nested.view.not.allowed");
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
        DEBUG("Modules.readModule: locn:" + locn.getName());
        DEBUG("Modules.readModule: src:" + srcFile + " class:" + classFile);
        JavaFileObject file;
        if (srcFile == null) {
            if (classFile == null) {
                sym.name = sym.fullname = names.empty; // unnamed module
                DEBUG("Modules.readModule: (" + sym.hashCode() + ") no module info found for " + locn );
                RequiresModuleDirective d = new RequiresModuleDirective(baseModuleQuery,
                        EnumSet.of(Directive.RequiresFlag.SYNTHESIZED));
                sym.directives = List.<Directive>of(d);
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

    private boolean resolve(List<JCCompilationUnit> trees) {
        if (moduleFileManagerUnavailable)
            return false;

        DEBUG("Modules.resolve: resolve modules");
        ModuleResolver mr = getModuleResolver();
        DEBUG("Modules.resolve: module resolver: " + mr);

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
        if (debug.isEnabled("initialRootLocns"))
            showRootLocations(rootLocns);

        List<ModuleSymbol> roots = List.nil();
        for (Location locn: rootLocns) {
            DEBUG("Modules.resolve: building roots: locn: " + locn);
            ModuleSymbol msym = enterModule(locn);
            DEBUG("Modules.resolve: building roots: msym: " + msym);
            msym.complete();
            DEBUG("Modules.resolve: building roots: completed: " + msym);
            roots = roots.prepend(msym);
        }
        roots = roots.reverse();
        DEBUG("Modules.resolve: roots: " + roots);

        updateTrees(trees);

        DEBUG("Modules.resolve: modules so far: " + allModules);

        DEBUG("Modules.resolve: reading module-info as needed");
        Location l = (moduleFileManager.getModuleMode() == ModuleMode.SINGLE)
                ? MODULE_PATH : moduleFileManager.join(List.of(MODULE_PATH, SOURCE_PATH));
        for (Location locn: moduleFileManager.getModuleLocations(l)) {
            DEBUG("Modules.resolve: ensuring module-info for " + locn);
            enterModule(locn).complete();
        }

        ListBuffer<ModuleSymbol> namedModules = new ListBuffer<ModuleSymbol>();
        for (ModuleSymbol msym: allModules.values()) {
            if (msym.name != names.empty)
                namedModules.add(msym);
        }

        if (debug.isEnabled("resolve")) {
            debug.println("Module resolver: " + mr.getClass().getSimpleName());
            showModules("Module resolution roots:", roots);
            showModules("Modules in compilation environment:", namedModules);
        }

        Iterable<? extends ModuleElement> modules = mr.resolve(roots, namedModules);
        if (modules == null)
            return false;

        // modules is returned in an order such that if A requires B, then
        // A will be earlier than B. This means that the root modules, such
        // as the unnamed module appear ahead of the platform modules,
        // which will appear last.  This is the reverse of the traditional
        // ordering, in which the platform classes are read ahead of classes
        // on the user class path.   Therefore, we build and use a reversed
        // list of modules before converting the modules to the path used by
        // ClassReader.

        List<ModuleSymbol> msyms = List.nil();
        for (ModuleElement me: modules)
            msyms = msyms.prepend((ModuleSymbol) me);

        if (debug.isEnabled("resolve"))
            showModules("Resolved modules: ", msyms);

        JavacFileManager jfm = (fileManager instanceof JavacFileManager)
                ? (JavacFileManager)fileManager : null;

        ModuleSymbol firstPlatformModule = null;
        ModuleSymbol lastPlatformModule = null;
        for (ModuleSymbol msym: msyms) {
            DEBUG("Modules.resolve: " + msym.fullname + " " + isPlatformModule(msym));
            if (isPlatformModule(msym)) {
                if (firstPlatformModule == null)
                    firstPlatformModule = msym;
                lastPlatformModule = msym;
            }
        }

        ListBuffer<Location> locns = new ListBuffer<Location>();
        for (ModuleSymbol msym: msyms) {
            DEBUG("Modules.resolve: msym: " + msym);
            DEBUG("Modules.resolve: msym.location: " + msym.location);
            if (jfm != null && isPlatformModule(msym)) {
                locns.addAll(jfm.augmentPlatformLocation(msym.location,
                        msym == firstPlatformModule,
                        msym == lastPlatformModule));
            } else
                locns.add(msym.location);
        }
        Location merged = moduleFileManager.join(locns);
        DEBUG("Modules.resolve: merged result: " + merged);
        reader.setPathLocation(merged);

        return true;
    }

    boolean isBaseModuleName(Name name) {
        return name.equals(baseModule.name);
    }

    boolean isPlatformModule(ModuleSymbol msym) {
        return isPlatformModuleName(msym.name) || definesPlatformModule(msym.directives);
    }

    boolean isPlatformModuleName(Name name) {
        return name.toString().startsWith("java.");
    }

    boolean definesPlatformModule(List<Directive> directives) {
        for (Directive d: directives) {
            switch (d.getKind()) {
                case PROVIDES_MODULE:
                    if (isPlatformModuleName(((ProvidesModuleDirective) d).moduleId.name))
                        return true;
                    break;
                case VIEW:
                    ViewDeclaration v = (ViewDeclaration) d;
                    if (isPlatformModuleName(v.name) || definesPlatformModule(v.directives))
                        return true;
                    break;
            }
        }
        return false;
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

            String library = options.get(Option.L);
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

                    Option[] unsupportedOptions = {
            //            XBOOTCLASSPATH_PREPEND,
                        ENDORSEDDIRS,
            //            BOOTCLASSPATH,
            //            XBOOTCLASSPATH_APPEND,
                        EXTDIRS
                    };
                    for (Option o: unsupportedOptions) {
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
            public void report(ModuleSymbol msym, ModuleIdQuery mq, String key, Object... args) {
                error(msym, mq, key, args);
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

    private void error(ModuleSymbol msym, ModuleIdQuery mq, String key, Object... args) {
        error(msym, new ModuleId(mq.name, mq.versionQuery), key, args);
    }

    private void error(ModuleSymbol msym, ModuleId id, String key, Object... args) {
        if (msym == null) {
            log.error(key, args);
        } else {
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
            search(tree.directives);
        }

        @Override
        public void visitExports(JCExportDirective tree) {
        }

        @Override
        public void visitPermits(JCPermitsDirective tree) {
            if (equal(TreeInfo.fullName(tree.moduleName), mid.name) && mid.version == null)
                result = tree;
        }

        @Override
        public void visitRequiresModule(JCRequiresModuleDirective tree) {
            search(tree.moduleIdQuery);
        }

        @Override
        public void visitModuleId(JCModuleId tree) {
            DEBUG("Modules.treeFinder.visitModuleId " + tree + " " + mid);
            if (equal(TreeInfo.fullName(tree.qualId), mid.name) && equal(tree.version, mid.version))
                result = tree;
            DEBUG("Modules.treeFinder.visitModuleId result " + result);
        }

        @Override
        public void visitModuleIdQuery(JCModuleIdQuery tree) {
            DEBUG("Modules.treeFinder.visitModuleId " + tree + " " + mid);
            if (equal(TreeInfo.fullName(tree.qualId), mid.name) && equal(tree.versionQuery, mid.version))
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

    void showModuleResolver(ModuleResolver mr) {
        debug.println("Module resolver: " + mr.getClass().getSimpleName());
    }

    void showRootLocations(Collection<Location> rootLocns) {
        debug.println("Module root locations: (" + (rootLocns == null ? "null" : rootLocns.size()) + ")");
        if (rootLocns == null)
            return;
        int i = 0;
        for (Location l: rootLocns) {
            debug.println("  " + (i++) +": " + l);
        }
    }

    void showModules(String desc, Collection<ModuleSymbol> msyms) {
        boolean showAll = debug.isEnabled("all");
        boolean showLocation = showAll || debug.isEnabled("location");
        boolean showRequires = showAll || debug.isEnabled("requires");
        if (showLocation || showRequires || showAll) {
            debug.println(desc + " (" + msyms.size() + ")");
            for (ModuleSymbol msym: msyms) {
                debug.println("  " + msym);
                if (showLocation) {
                    debug.println("    location: " + msym.location);
                }
                if (showRequires) {
                    // short form only, for now
                    debug.print("    requires: ");
                    String sep = "";
                    for (RequiresModuleDirective d: msym.getRequiredModules()) {
                        debug.print(sep);
                        showNameAndVersion(d.moduleQuery.name, d.moduleQuery.versionQuery);
                        sep = ", ";
                    }
                    debug.println();
                }
            }

        } else {
            debug.print(desc + " (" + msyms.size() + ")");
            debug.print(" ");
            String sep = "";
            for (ModuleSymbol msym: msyms) {
                debug.print(sep + msym);
                sep = ", ";
            }
            debug.println();
        }
    }

    void showModuleIds(String desc, Collection<ModuleId> mids) {
        debug.print(desc + " (" + mids.size() + ")");
        debug.print(" ");
        String sep = "";
        for (ModuleId mid: mids) {
            debug.print(sep);
            showNameAndVersion(mid.name, mid.version);
            sep = ", ";
        }
        debug.println();
    }

    private void showNameAndVersion(Name name, Name version) {
        debug.print(name);
        if (version != null) {
            debug.print("@");
            debug.print(version);
        }
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
        void report(ModuleSymbol msym, ModuleIdQuery mq, String key, Object... args);
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
                add(table, sym, new ModuleId(sym.name, sym.version));
                for (ViewDeclaration v : sym.getViews()) {
                    for (ProvidesModuleDirective d : v.getAliases())
                        add(table, sym, d.moduleId);
                }
            }

            // Add entry for default platform module if needed
            ModuleId p = baseModule;
            Map<Name,ModuleSymbol> versions = table.get(p.name);
            ModuleSymbol psym = (versions == null) ? null : versions.get(p.version);
            if (psym == null) {
                if (versions == null)
                    table.put(p.name, versions = new HashMap<Name,ModuleSymbol>());
                psym = new ModuleSymbol(p.name, syms.rootModule);
                psym.location = StandardLocation.PLATFORM_CLASS_PATH;
                versions.put(p.version, psym);
                psym.directives = List.nil();
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

        private ModuleSymbol getModule(ModuleIdQuery mid) throws ModuleException {
            Map<Name, ModuleSymbol> versions = moduleTable.get(mid.name);
            if (versions == null)
                throw new ModuleException("mdl.no.version.available", mid);
            if (mid.versionQuery == null) {
                if (versions.size() > 1)
                    throw new ModuleException("mdl.no.unique.version.available", mid);
                return versions.values().iterator().next();
            } else {
                Name q = mid.versionQuery;
                Name ge = names.fromString(">=");
                if (q.startsWith(ge)) q = q.subName(2, q.length());
                ModuleSymbol sym = versions.get(q);
                if (sym == null)
                    throw new ModuleException("mdl.required.version.not.available", mid);
                return sym;
            }
        }
        // where
        private Map<Name, Map<Name, ModuleSymbol>> moduleTable;

        private class ModuleException extends Exception {
            private static final long serialVersionUID = 0;
            ModuleException(String key, ModuleIdQuery moduleQuery) {
                this.key = key;
                this.moduleQuery = moduleQuery;
            }
            final String key;
            final ModuleIdQuery moduleQuery;
        }

////////        List<Node> getNodes(Iterable<? extends ModuleElement.ModuleIdQuery> queries) {
////////            ListBuffer<Node> nodes = new ListBuffer<Node>();
////////            for (ModuleElement.ModuleIdQuery midq: queries) {
////////                ModuleId mid = (ModuleId) midq;
////////                try {
////////                    nodes.add(getNode(getModule(mid)));
////////                } catch (ModuleException e) {
////////                    errorHandler.report(null, e.moduleId, e.key, e.moduleId);
////////                }
////////            }
////////            return nodes.toList();
////////        }

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
                DEBUG("ZeroMod.Node.getDependencies: " + sym + " " + sym.getRequiredModules());
                ListBuffer<Node> nodes = new ListBuffer<Node>();
                for (RequiresModuleDirective d: sym.getRequiredModules()) {
                    try {
                        nodes.add(getNode(getModule(d.moduleQuery)));
                    } catch (ModuleException e) {
                        errorHandler.report(sym, e.moduleQuery, e.key, e.moduleQuery);
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
