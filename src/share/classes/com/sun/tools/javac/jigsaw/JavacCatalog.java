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

package com.sun.tools.javac.jigsaw;

import java.io.File;
import java.io.IOException;
import java.lang.module.Dependence;
import java.lang.module.ModuleId;
import java.lang.module.ModuleInfo;
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

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ModuleSymbol;
import com.sun.tools.javac.jvm.ClassFile;
import com.sun.tools.javac.util.Name;
import java.lang.module.ModuleIdQuery;

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
            for (ClassFile.ModuleId mid: msym.provides) {
                addModule(mid.name, mid.version, msym);
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
	DEBUG("JavacCatalog.gatherLocalModuleIds");
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

    ModuleId getModuleId(ClassFile.ModuleId mid) {
        return getModuleId(mid.name, mid.version); // FIXME -- throws IllegalArgumentException
    }

    ModuleId getModuleId(Name n, Name v) {
        String mid = (v == null) ? n.toString() : (n + "@" + v);
        return jigsaw.parseModuleId(mid); // FIXME -- throws IllegalArgumentException
    }

    ModuleIdQuery getModuleIdQuery(ClassFile.ModuleId midq) {
        return getModuleIdQuery(midq.name, midq.version); // FIXME -- throws IllegalArgumentException
    }

    ModuleIdQuery getModuleIdQuery(Name n, Name vq) {
        String midq = (vq == null) ? n.toString() : (n + "@" + vq);
        return jigsaw.parseModuleIdQuery(midq); // FIXME -- throws IllegalArgumentException
    }

    Dependence.Modifier getModifier(Name n) {
        String s = n.toString();  // FIXME: use names, but that requires Context
        if (s.equals("local"))
            return Dependence.Modifier.LOCAL;
        if (s.equals("optional"))
            return Dependence.Modifier.OPTIONAL;
        if (s.equals("public"))
            return Dependence.Modifier.PUBLIC;
        if (s.equals("synthetic"))
            return Dependence.Modifier.SYNTHETIC;
        throw new IllegalArgumentException(s);  // FIXME -- throws IllegalArgumentException
    }

    class JavacModuleInfo implements ModuleInfo {
        ModuleSymbol msym;

        ModuleId id;
        Set<String> permits;
        Set<ModuleId> provides;
        Set<Dependence> requires;
        String mainClass;

        JavacModuleInfo(ModuleSymbol msym) {
            msym.getClass(); // null check
            DEBUG("JavacModuleInfo: msym: " + msym);

            this.msym = msym;

            id = getModuleId(msym); // FIXME -- throws IllegalArgumentException

            mainClass = (msym.className == null) ? null : msym.className.toString();

            permits = new LinkedHashSet<String>();
            for (Name p: msym.permits)
                permits.add(p.toString()); // FIXME: validate name?
            permits = Collections.unmodifiableSet(permits);

            provides = new LinkedHashSet<ModuleId>();
            for (ClassFile.ModuleId p: msym.provides)
                provides.add(getModuleId(p));
            provides = Collections.unmodifiableSet(provides);

            requires = new LinkedHashSet<Dependence>();
            for (Symbol.ModuleRequires r: msym.requires.values()) {
                DEBUG("JavacModuleInfo: require " + r);
                ModuleIdQuery q = getModuleIdQuery(r.moduleId);
                EnumSet<Dependence.Modifier> mods = EnumSet.noneOf(Dependence.Modifier.class);
                for (Name n: r.flags) {
                    mods.add(getModifier(n));  // FIXME -- throws IllegalArgumentException
                }
                requires.add(new Dependence(mods, q));
            }
            DEBUG("JavacModuleInfo: msym: " + msym + "[id:" + id + " permits:" + permits + " provides:" + provides + " requires:" + requires + " mainClass:" + mainClass + "]");
        }

        public ModuleId id() {
            return id;
        }

        public String mainClass() {
            return mainClass;
        }

        public Set<String> permits() {
            return permits;
        }

        public Set<ModuleId> provides() {
            return provides;
        }

        public Set<Dependence> requires() {
            return requires;
        }
    }

}
