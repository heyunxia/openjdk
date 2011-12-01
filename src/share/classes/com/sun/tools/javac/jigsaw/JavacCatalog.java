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
import java.lang.module.Dependence;
import java.lang.module.ModuleId;
import java.lang.module.ModuleIdQuery;
import java.lang.module.ModuleInfo;
import java.lang.module.ServiceDependence;
import java.lang.module.Version;
import java.lang.module.VersionQuery;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
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
import com.sun.tools.javac.code.Directive.ProvidesServiceDirective;
import com.sun.tools.javac.code.Directive.RequiresFlag;
import com.sun.tools.javac.code.Directive.RequiresModuleDirective;
import com.sun.tools.javac.code.Directive.ViewDeclaration;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
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
                if (v.name != null) {
                    addModule(v.name, msym.version, msym);
                }
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
        if (moduleName != null) {
            Map<Version,ModuleSymbol> syms = moduleMap.get(moduleName);
            if (syms == null)
                return;
            addModuleIds(syms, moduleName, mids);
        } else {
            for (String mn : moduleMap.keySet()) {
                addModuleIds(moduleMap.get(mn), moduleName, mids);
            }
        }
        DEBUG("JavacCatalog.gatherLocalModuleIds: moduleName:" + moduleName + "--" + mids);
    }
    
    // add all ModuleIds of the given name
    private void addModuleIds(Map<Version,ModuleSymbol> map,
                              String mn, Set<ModuleId> mids) {
        for (ModuleSymbol sym : map.values()) {
            ModuleId mid = getModuleId(sym);
            if (mn == null || mid.name().equals(mn)) {
                mids.add(mid);
            }
            for (ViewDeclaration v : sym.getViews()) {
                if (v.name == null)
                    continue;
                
                if (mn == null || mn.equals(v.name.toString())) {
                    mids.add(getModuleId(v.name, sym.version));
                }
            }
        }
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
        Map<ModuleId, ModuleInfo.View> views;

        JavacModuleInfo(ModuleSymbol msym) {
            msym.getClass(); // null check
            DEBUG("JavacModuleInfo: msym: " + msym);

            this.msym = msym;
            this.views = new HashMap<ModuleId, ModuleInfo.View>();

            this.id = getModuleId(msym); // FIXME -- throws IllegalArgumentException

            for (ViewDeclaration v : msym.getViews()) {
                String mainClass = null;

                if (v.hasEntrypoint()) {
                    mainClass = new String(ClassFile.externalize(v.getEntrypoint().flatname));
                }

                Set<String> permits = new LinkedHashSet<String>();
                for (PermitsDirective d : v.getPermits()) {
                    permits.add(d.moduleId.name.toString()); // FIXME: validate name?
                }

                Set<String> exports = new LinkedHashSet<String>();

                Set<ModuleId> provides = new LinkedHashSet<ModuleId>();
                for (ProvidesModuleDirective d : v.getAliases()) {
                    provides.add(getModuleId(d.moduleId));
                }
                
                Set<ModuleInfo.Service> services = new LinkedHashSet<ModuleInfo.Service>();
                for (ProvidesServiceDirective s : v.getServices()) {
                    services.add(new JavacServiceImpl(s.service, s.impl));
                }
                
                ModuleId vid = (v.name == null)
                                   ? id
                                   : getModuleId(v.name, msym.version);
                ModuleInfo.View view = new JavacModuleView(vid.name(),
                        vid,
                        exports,
                        provides,
                        services,
                        permits,
                        mainClass);
                views.put(vid, view);
            }
            
            if (!views.containsKey(id)) {
                views.put(id, new JavacModuleView(id.name(), id));
            }

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
            DEBUG("JavacModuleInfo: msym: " + msym + "[id:" + id + " views:" + views + " requires:" + requires + "]");
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
            return views.get(id);
        }

        public Set<View> views() {
            return Collections.unmodifiableSet(new HashSet<ModuleInfo.View>(views.values()));
        }
        
        public View viewFor(ModuleId mid) {
            return views.get(mid);
        }
        
        public View viewFor(String mn) {
            return views.get(new ModuleId(mn, id.version()));
        }

        class JavacModuleView implements ModuleInfo.View {

            private String name;
            private ModuleId vid;
            private Set<String> exports;
            private Set<ModuleId> aliases;
            private Set<ModuleInfo.Service> services;
            private Set<String> permits;
            private String mainClass;
            
            JavacModuleView(String name,
                            ModuleId id) {
                this.name = name;
                this.vid = id;
                this.exports = Collections.emptySet();
                this.aliases = Collections.emptySet();
                this.services = Collections.emptySet();
                this.permits = Collections.emptySet();
                this.mainClass = null;
                DEBUG("JavacModuleView: [name:" + name + " id:" + vid +
                      " permits:" + permits + " provides:" + aliases +
                      " requires service:" + services + " mainClass:" + mainClass + "]");
            }
            JavacModuleView(String name,
                    ModuleId id,
                    Set<String> exports,
                    Set<ModuleId> provides,
                    Set<ModuleInfo.Service> services,
                    Set<String> permits,
                    String mainClass) {
                this.name = name;
                this.vid = id;
                this.exports = exports;
                this.aliases = provides;
                this.services = services;
                this.permits = permits;
                this.mainClass = mainClass;
                DEBUG("JavacModuleView: [name:" + name + " id:" + vid +
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
            public Set<ModuleId> aliases() {
                return aliases;
            }

            public String name() {
                return name;
            }
            
            public ModuleId id() {
                return vid;
            }

            public Set<String> exports() {
                return exports;
            }

            public Set<Service> providesServices() {
                return services;
            }
        }

        class JavacServiceImpl implements ModuleInfo.Service {

            private String name;
            private String implClassName;

            JavacServiceImpl(ClassSymbol service, ClassSymbol impl) {
                this.name = new String(ClassFile.externalize(service.flatname));
                this.implClassName = new String(ClassFile.externalize(impl.flatname));
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public String providerClassName() {
                return implClassName;
            }
        }
    }
}
