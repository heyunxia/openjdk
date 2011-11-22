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

package com.sun.tools.javac.jigsaw;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.module.Dependence;
import java.lang.module.ModuleId;
import java.lang.module.ModuleIdQuery;
import java.lang.module.ModuleInfo;
import java.lang.module.ServiceDependence;
import java.lang.module.Version;
import java.lang.module.VersionQuery;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.ModuleElement;

import org.openjdk.jigsaw.Catalog;
import org.openjdk.jigsaw.JigsawModuleSystem;
import org.openjdk.jigsaw.Library;
import org.openjdk.jigsaw.SimpleLibrary;

import com.sun.tools.javac.code.Directive.PermitsDirective;
import com.sun.tools.javac.code.Directive.ProvidesModuleDirective;
import com.sun.tools.javac.code.Directive.RequiresFlag;
import com.sun.tools.javac.code.Directive.RequiresModuleDirective;
import com.sun.tools.javac.code.Directive.ViewDeclaration;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ModuleSymbol;
import com.sun.tools.javac.jvm.ClassFile;
import com.sun.tools.javac.util.Name;

/*
 * Implementation of a Jigsaw catalog providing access to the modules
 * in a compilation found with command line options such as modulepath,
 * classpath and sourcepath.
 */
public class JavacCatalog  extends Catalog {
    final JigsawModuleSystem jigsaw;
    final Library library;

    private Map<String, Map<Version, ModuleSymbol>> moduleMap =
            new HashMap<String, Map<Version, ModuleSymbol>>();

    boolean DEBUG = (System.getProperty("javac.debug.modules") != null);
    void DEBUG(String s) {
        if (DEBUG)
            System.err.println(s);
    }

    JavacCatalog(File library) throws IOException/*FIXME*/ {
        jigsaw = JigsawModuleSystem.instance();
        if (library == null)
            this.library = Library.openSystemLibrary();
        else
            this.library = SimpleLibrary.open(library);
        DEBUG("JavacCatalog: library:" + library + " this.library:" + this.library);
    }

    @Override
    public String name() {
        return "javac"; // can we do anything better here?
    }

    @Override
    public Catalog parent() {
        return library;
    }

    void init(Iterable<? extends ModuleElement> modules) {
        DEBUG("JavacCatalog.init: " + modules);
        for (ModuleElement me: modules) {
            ModuleSymbol msym = (ModuleSymbol) me;
            DEBUG("JavacCatalog.init: msym:" + msym + " msym.fullname:" + msym.fullname + " msym.version:" + msym.version);
            addModule(msym.fullname, msym.version, msym);
            for (ViewDeclaration v: msym.getViews()) {
                for (ProvidesModuleDirective d: v.getAliases()) {
                    com.sun.tools.javac.code.ModuleId mid = d.moduleId;
                    addModule(mid.name, mid.version, msym);
                }
            }
        }
        DEBUG("JavacCatalog.init: map:" + moduleMap);
    }

    private void addModule(Name name, Name version, ModuleSymbol msym) {
        String n = name.toString();
        Version v = getVersion(version);
        Map<Version,ModuleSymbol> map = moduleMap.get(n);
        if (map == null)
            moduleMap.put(n, map = new HashMap<Version,ModuleSymbol>());
        map.put(v, msym);
    }

    @Override
    protected void gatherLocalModuleIds(String moduleName, Set<ModuleId> mids) throws IOException {
        DEBUG("JavacCatalog.gatherLocalModuleIds: " + moduleName);
        Collection<Map<Version,ModuleSymbol>> maps;
        if (moduleName != null) {
            Map<Version,ModuleSymbol> syms = moduleMap.get(moduleName);
            if (syms == null)
                return;
            maps = Collections.singleton(syms);
        } else {
            maps = moduleMap.values();
        }

        for (Map<Version,ModuleSymbol> map: maps) {
            for (ModuleSymbol sym: map.values())
                mids.add(getModuleId(sym));
        }

        DEBUG("JavacCatalog.gatherLocalModuleIds: moduleName:" + moduleName + "--" + mids);
    }

    @Override
    protected ModuleInfo readLocalModuleInfo(ModuleId mid) throws IOException {
        DEBUG("JavacCatalog.readLocalModuleInfo " + mid);
        ModuleSymbol msym = getModuleSymbol(mid);
        DEBUG("JavacCatalog.readLocalModuleInfo " + mid + "--" + ((msym == null) ? null : new JavacModuleInfo(msym)));
        return (msym == null) ? null : new JavacModuleInfo(msym);
    }

//    private ModuleId getModuleId(ModuleSymbol msym) {
//        return getModuleId(new ClassFile.ModuleId(msym.fullname, msym.version));
//    }
//
//    private ModuleId getModuleId(ClassFile.ModuleId cf_mid) {
//        ModuleId id = moduleIdCache.get(cf_mid);
//        if (id == null) {
//            Name name = cf_mid.name;
//            Name version = cf_mid.version;
//            String s = (version == null) ? name.toString() : (name + "@" + version);
//            moduleIdCache.put(cf_mid, id = jigsaw.parseModuleId(s));
//        }
//        return id;
//    }
//
    ModuleSymbol getModuleSymbol(ModuleId mid) {
        Map<Version,ModuleSymbol> map = moduleMap.get(mid.name());
        return (map == null) ? null : map.get(mid.version());
    }

    Version getVersion(Name v) {
        return (v == null) ? null : jigsaw.parseVersion(v.toString()); // FIXME -- throws IllegalArgumentException
    }

    VersionQuery getVersionQuery(Name vq) {
        return (vq == null) ? null : jigsaw.parseVersionQuery(vq.toString()); // FIXME -- throws IllegalArgumentException
    }

    ModuleId getModuleId(ModuleSymbol sym) {
        return getModuleId(sym.fullname, sym.version); // FIXME -- throws IllegalArgumentException
    }

    ModuleId getModuleId(com.sun.tools.javac.code.ModuleId mid) {
        return getModuleId(mid.name, mid.version); // FIXME -- throws IllegalArgumentException
    }

    ModuleId getModuleId(Name n, Name v) {
        String mid = (v == null) ? n.toString() : (n + "@" + v);
        return jigsaw.parseModuleId(mid); // FIXME -- throws IllegalArgumentException
    }

    ModuleIdQuery getModuleIdQuery(com.sun.tools.javac.code.ModuleIdQuery midq) {
        return getModuleIdQuery(midq.name, midq.versionQuery); // FIXME -- throws IllegalArgumentException
    }
    
    ModuleIdQuery getModuleIdQuery(ModuleElement.ModuleIdQuery midq) {
        return getModuleIdQuery(midq.getName(), midq.getVersionQuery());
    }

    ModuleIdQuery getModuleIdQuery(Name n, Name vq) {
        String midq = (vq == null) ? n.toString() : (n + "@" + vq);
        return jigsaw.parseModuleIdQuery(midq); // FIXME -- throws IllegalArgumentException
    }
    
    ModuleIdQuery getModuleIdQuery(CharSequence n, CharSequence vq) {
        String q = (vq == null || vq.length() == 0) ? String.valueOf(n) : (n + "@" + vq);
        return jigsaw.parseModuleIdQuery(q);
    }

    Dependence.Modifier getModifier(RequiresFlag f) {
        switch (f) {
            case LOCAL:
                return Dependence.Modifier.LOCAL;
            case OPTIONAL:
                return Dependence.Modifier.OPTIONAL;
            case PUBLIC:
                return Dependence.Modifier.PUBLIC;
			default:
                throw new IllegalArgumentException(f.toString());  // FIXME -- throws IllegalArgumentException
        }
    }

    class JavacModuleInfo implements ModuleInfo {
        ModuleSymbol msym;

        ModuleId id;
        Set<Dependence> requires;
        ModuleInfo.View view;


        JavacModuleInfo(ModuleSymbol msym) {
            msym.getClass(); // null check
            DEBUG("JavacModuleInfo: msym: " + msym);

            this.msym = msym;

            id = getModuleId(msym); // FIXME -- throws IllegalArgumentException

            String mainClass = null;
            {   // needs to be updated for multiple views
                ViewDeclaration v = msym.getDefaultView();
                if (v.hasEntrypoint())
                    mainClass = new String(ClassFile.externalize(v.getEntrypoint().flatname));
            }

            Set<String> permits = new LinkedHashSet<String>();
            for (PermitsDirective d: msym.getDefaultView().getPermits()) {
                permits.add(d.moduleId.name.toString()); // FIXME: validate name?
            }
            
            Set<String> exports  = new LinkedHashSet<String>();
            Set<ModuleInfo.Service> services  = new LinkedHashSet<ModuleInfo.Service>();

            Set<ModuleId> provides = new LinkedHashSet<ModuleId>();
            for (ViewDeclaration v: msym.getViews()) {
                for (ProvidesModuleDirective d: v.getAliases()) {
                    provides.add(getModuleId(d.moduleId));
                }
            }
            
            view = new JavacModuleView(id.name(),
                                       exports,
                                       provides,
                                       services,
                                       permits,
                                       mainClass);

            requires = new LinkedHashSet<Dependence>();
            for (RequiresModuleDirective r: msym.getRequiredModules()) {
                DEBUG("JavacModuleInfo: require " + r);
                ModuleIdQuery q = getModuleIdQuery(r.moduleQuery);
                EnumSet<Dependence.Modifier> mods = EnumSet.noneOf(Dependence.Modifier.class);
                for (com.sun.tools.javac.code.Directive.RequiresFlag f: r.flags) {
                    mods.add(getModifier(f));  // FIXME -- throws IllegalArgumentException
                }
                requires.add(new Dependence(mods, q));
            }
            DEBUG("JavacModuleInfo: msym: " + msym + "[id:" + id + " views:" + view + " requires:" + requires + "]");
        }

        @Override
        public ModuleId id() {
            return id;
        }
        
         
        @Override
        public Set<Dependence> requires() {
            return requires;
        }
        public Set<ServiceDependence> requiresServices() {
            return Collections.emptySet();
        }

        public View defaultView() {
            return view;
        }

        public Set<View> views() {
            return Collections.singleton(view);
        }

        class JavacModuleView implements ModuleInfo.View {

            private String name;
            private Set<String> exports;
            private Set<ModuleId> provides;
            private Set<ModuleInfo.Service> services;
            private Set<String> permits;
            private String mainClass;

            JavacModuleView(String name,
                    Set<String> exports,
                    Set<ModuleId> provides,
                    Set<ModuleInfo.Service> services,
                    Set<String> permits,
                    String mainClass) {
                this.name = name;
                this.exports = exports;
                this.provides = provides;
                this.services = services;
                this.permits = permits;
                this.mainClass = mainClass;
                DEBUG("JavacModuleView: [name:" + name + 
                      " permits:" + permits + " provides:" + provides +
                      " requires service:" + services + " mainClass:" + mainClass + "]");
            }

            @Override
            public String mainClass() {
                return mainClass;
            }

            @Override
            public Set<String> permits() {
                return permits;
            }

            @Override
            public Set<ModuleId> provides() {
                return provides;
            }

            public String name() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public Set<String> exports() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public Set<Service> providesServices() {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }
    }
}
