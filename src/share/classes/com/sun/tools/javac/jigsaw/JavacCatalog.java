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
import java.lang.module.ModuleView;
import java.lang.module.ServiceDependence;
import java.lang.module.ViewDependence;
import java.lang.module.Version;
import java.lang.module.VersionQuery;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private Version nullVersion;   // used in the moduleMap

    boolean DEBUG = (System.getProperty("javac.debug.modules") != null);
    void DEBUG(String s) {
        if (DEBUG)
            System.err.println(s);
    }

    JavacCatalog(File library) throws IOException/*FIXME*/ {
        jigsaw = JigsawModuleSystem.instance();
        nullVersion = jigsaw.parseVersion("0");
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
                    com.sun.tools.javac.code.ModuleId alias = d.moduleId;
                    addModule(alias.name, alias.version, msym);
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
        if (v != null)
            map.put(v, msym);
        else
            map.put(nullVersion, msym);
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
    
    protected void gatherLocalDeclaringModuleIds(Set<ModuleId> mids) throws IOException {
        DEBUG("JavacCatalog.gatherLocalDeclaringModuleIds");
        for (Map<Version,ModuleSymbol> map : moduleMap.values()) {
            for (ModuleSymbol sym : map.values()) {
                ModuleId mid = getModuleId(sym);
                mids.add(mid);
            }
        }
        DEBUG("JavacCatalog.gatherLocalDeclaringModuleIds: " + "--" + mids);
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
                Name n = v.name == null ? sym.fullname : v.name;
                if (mn == null || mn.equals(n.toString())) {
                    mid = getModuleId(n, sym.version);
                    if (!mids.contains(mid))
                        mids.add(mid);
                }

                for (ProvidesModuleDirective d : v.getAliases()) {
                    mids.add(getModuleId(d.moduleId));
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
        if (map == null)
            return null;
        return (mid.version() == null) ? map.get(nullVersion) : map.get(mid.version());
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

    ModuleIdQuery getModuleQuery(com.sun.tools.javac.code.ModuleQuery midq) {
        return getModuleIdQuery(midq.name, midq.versionQuery); // FIXME -- throws IllegalArgumentException
    }
    
    ModuleIdQuery getModuleIdQuery(ModuleElement.ModuleQuery midq) {
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
            case REEXPORT:
                return Dependence.Modifier.PUBLIC;
            case SYNTHETIC:
                return Dependence.Modifier.SYNTHETIC;
            case SYNTHESIZED:
                return Dependence.Modifier.SYNTHESIZED;
	    default:
                throw new IllegalArgumentException(f.toString());  // FIXME -- throws IllegalArgumentException
        }
    }

    class JavacModuleInfo implements ModuleInfo {
        ModuleSymbol msym;

        ModuleId id;
        Set<ViewDependence> requiresModules;
        Map<ModuleId, ModuleView> views;

        JavacModuleInfo(ModuleSymbol msym) {
            msym.getClass(); // null check
            DEBUG("JavacModuleInfo: msym: " + msym);

            this.msym = msym;
            this.views = new HashMap<ModuleId, ModuleView>();

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
                
                Map<String,Set<String>> services = new LinkedHashMap<String,Set<String>>();
                for (ProvidesServiceDirective s : v.getServices()) {
                    String sn = new String(ClassFile.externalize(s.service.flatname));
                    String pn = new String(ClassFile.externalize(s.impl.flatname));
                    Set<String> providers = services.get(sn);
                    if (providers == null) {
                        providers = new LinkedHashSet<String>();
                        services.put(sn, providers);
                    }
                    providers.add(pn);
                }
                
                ModuleId vid = (v.name == null)
                                   ? id
                                   : getModuleId(v.name, msym.version);
                ModuleView view = new JavacModuleView(this,
                                                      vid,
                                                      mainClass,
                                                      provides,
                                                      exports,
                                                      permits,
                                                      services);
                views.put(vid, view);
            }
            
            if (!views.containsKey(id)) {
                // create the default view if not exists
                views.put(id, new JavacModuleView(this,
                                                  id,
                                                  null,
                                                  Collections.<ModuleId>emptySet(),
                                                  Collections.<String>emptySet(),
                                                  Collections.<String>emptySet(),
                                                  Collections.<String,Set<String>>emptyMap()));
            }

            requiresModules = new LinkedHashSet<ViewDependence>();
            for (RequiresModuleDirective r: msym.getRequiredModules()) {
                DEBUG("JavacModuleInfo: require " + r);
                ModuleIdQuery q = getModuleQuery(r.moduleQuery);
                EnumSet<Dependence.Modifier> mods = EnumSet.noneOf(Dependence.Modifier.class);
                for (com.sun.tools.javac.code.Directive.RequiresFlag f: r.flags) {
                    mods.add(getModifier(f));  // FIXME -- throws IllegalArgumentException
                }
                requiresModules.add(new ViewDependence(mods, q));
            }
            DEBUG("JavacModuleInfo: msym: " + msym + "[id:" + id + " views:" + views + " requires:" + requiresModules + "]");
        }

        @Override
        public ModuleId id() {
            return id;
        }
         
        @Override
        public Set<ViewDependence> requiresModules() {
            return requiresModules;
        }
        
        public Set<ServiceDependence> requiresServices() {
            return Collections.emptySet();
        }

        public ModuleView defaultView() {
            return views.get(id);
        }

        public Set<ModuleView> views() {
            return Collections.unmodifiableSet(new HashSet<ModuleView>(views.values()));
        }
    }
    
    class JavacModuleView
        implements ModuleView
    {
        private final ModuleInfo mi;
        private final ModuleId id;
        private final Set<String> exports;
        private final Set<ModuleId> aliases;
        private final Map<String,Set<String>> services;
        private final Set<String> permits;
        private final String mainClass;

        JavacModuleView(ModuleInfo mi,
                        ModuleId id,
                        String mainClass,
                        Set<ModuleId> aliases,
                        Set<String> exports,
                        Set<String> permits,
                        Map<String,Set<String>> serviceProviders) {
            this.mi = mi;
            this.id = id;
            this.mainClass = mainClass;
            this.aliases = aliases;
            this.exports = exports;
            this.permits = permits;
            this.services = serviceProviders;
        }

        public ModuleInfo moduleInfo() {
            return mi;
        }

        public ModuleId id() {
            return id;
        }

        public Set<ModuleId> aliases() {
            return Collections.unmodifiableSet(aliases);
        }

        public Set<String> exports() {
            return Collections.unmodifiableSet(exports);
        }
        
        public Set<String> permits() {
            return Collections.unmodifiableSet(permits);
        }

        public Map<String,Set<String>> services() {
            return Collections.unmodifiableMap(services);
        }

        public String mainClass() {
            return mainClass;
        }

        @Override
        public String toString() {
            return "View { id: " + id
                    + ", provides: " + aliases
                    + ", provides service: " + services
                    + ", permits: " + permits
                    + ", mainClass: " + mainClass
                    + " }";
        }
    }
}
