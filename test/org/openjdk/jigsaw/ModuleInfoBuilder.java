/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import java.lang.module.*;
import org.openjdk.jigsaw.*;

import static java.lang.module.Dependence.Modifier;


public class ModuleInfoBuilder {

    private static JigsawModuleSystem jms = JigsawModuleSystem.instance();

    private static class MI
        implements ModuleInfo
    {

        private final ModuleId mid;
        public ModuleId id() { return mid; }

        private MI(ModuleId mid) {
            this.mid = mid;
        }

        private Set<ViewDependence> requires
            // We use a linked hash set so as to guarantee deterministic order
            = new LinkedHashSet<>();
        public Set<ViewDependence> requiresModules() { return requires; }

        private Set<ServiceDependence> requiredServices = new LinkedHashSet<>();
        public Set<ServiceDependence> requiresServices() {
            return Collections.unmodifiableSet(requiredServices);
        }

        Map<String, ModuleViewBuilder> viewBuilders = new HashMap<>();

        ModuleView defaultView;
        Set<ModuleView> moduleViews;
        public ModuleView defaultView() {
            return defaultView;
        }

        public Set<ModuleView> views() {
            return moduleViews;
        }

        ModuleInfo build() {
            moduleViews = new HashSet<>();
            for (ModuleViewBuilder mvb : viewBuilders.values()) {
                ModuleView mv = mvb.build(this);
                moduleViews.add(mv);
                if (mv.id().equals(mid)) {
                    defaultView = mv;
                }
            }
            return this;
        }

        public String toString() { return mid.toString(); }
    }

    private final MI mi;
    private final ModuleViewBuilder defaultView;

    private ModuleInfoBuilder(String id) {
        mi = new MI(jms.parseModuleId(id));
        this.defaultView =  new ModuleViewBuilder(this, mi.mid);
        mi.viewBuilders.put(mi.mid.name(), defaultView);
    }

    public static ModuleInfoBuilder module(String id) {
        return new ModuleInfoBuilder(id);
    }

    public ModuleViewBuilder view(String name) {
         ModuleId id = new ModuleId(name, mi.mid.version());
         ModuleViewBuilder mvb = new ModuleViewBuilder(this, id);
         mi.viewBuilders.put(id.name(), mvb);
         return mvb;
    }

    public ModuleInfoBuilder requires(EnumSet<Modifier> mods, String mnvq) {
        int i = mnvq.indexOf('@');
        String mn;
        VersionQuery vq = null;
        if (i < 0) {
            mn = mnvq;
        } else {
            mn = mnvq.substring(0, i);
            vq = jms.parseVersionQuery(mnvq.substring(i + 1));
        }
        mi.requires.add(new ViewDependence(mods,
                                           new ModuleIdQuery(mn, vq)));
        return this;
    }

    public ModuleInfoBuilder requires(String mnvq) {
        return requires(null, mnvq);
    }

    public ModuleInfoBuilder requiresLocal(String mnvq) {
        return requires(EnumSet.of(Modifier.LOCAL), mnvq);
    }

    public ModuleInfoBuilder requiresOptional(String mnvq) {
        return requires(EnumSet.of(Modifier.OPTIONAL), mnvq);
    }

    public ModuleInfoBuilder requiresPublic(String mnvq) {
        return requires(EnumSet.of(Modifier.PUBLIC), mnvq);
    }

    public ModuleInfoBuilder requiresService(String serviceInteface) {
        mi.requiredServices.add(new ServiceDependence(null, serviceInteface));
        return this;
    }

    public ModuleInfoBuilder requiresOptionalService(String serviceInteface) {
        mi.requiredServices.add(new ServiceDependence(
            EnumSet.of(Modifier.OPTIONAL), serviceInteface));
        return this;
    }

    public ModuleInfoBuilder providesService(String serviceInterface,
                                             String serviceProviderClass) {
        defaultView.providesService(serviceInterface, serviceProviderClass);
        return this;
    }

    public ModuleInfoBuilder alias(String mnv) {
        defaultView.alias(mnv);
        return this;
    }

    public ModuleInfoBuilder exports(String pn) {
        defaultView.exports(pn);
        return this;
    }

    public ModuleInfoBuilder permits(String s) {
        defaultView.permits(s);
        return this;
    }

    public ModuleInfoBuilder mainClass(String cn) {
        defaultView.mainClass = cn;
        return this;
    }

    public ModuleInfo build() {
        return mi.build();
    }

    class ModuleViewBuilder {
        final ModuleInfoBuilder mib;
        final ModuleId id;
        final Set<String> exports = new HashSet<>();
        final Set<ModuleId> aliases = new HashSet<>();
        final Set<String> permits = new HashSet<>();
        final Map<String,Set<String>> services = new LinkedHashMap<>();
        String mainClass;

        private ModuleViewBuilder(ModuleInfoBuilder mib, ModuleId id) {
            this.mib = mib;
            this.id = id;
        }

        public ModuleViewBuilder view(String name) {
            Version version = mib.mi.mid.version();
            ModuleId id = new ModuleId(name, version);
            ModuleViewBuilder mvb = new ModuleViewBuilder(mib, id);
            mib.mi.viewBuilders.put(id.name(), mvb);
            return mvb;
        }

        public ModuleViewBuilder providesService(String serviceInterface,
                                                 String serviceProviderClass) {
            Set<String> spcs = services.get(serviceInterface);
            if (spcs == null) {
                spcs = new LinkedHashSet<>();
                services.put(serviceInterface, spcs);
            }
            spcs.add(serviceProviderClass);
            return this;
        }

        public ModuleViewBuilder alias(String mnv) {
            aliases.add(jms.parseModuleId(mnv));
            return this;
        }

        public ModuleViewBuilder exports(String pn) {
            exports.add(pn);
            return this;
        }

        public ModuleViewBuilder permits(String s) {
            if (s.indexOf('@') >= 0) {
                throw new IllegalArgumentException(s);
            }
            permits.add(s);
            return this;
        }

        public ModuleViewBuilder mainClass(String cn) {
            mainClass = cn;
            return this;
        }

        ModuleView build(ModuleInfo mi) {
            return new ModuleViewImpl(mi,
                                      id,
                                      mainClass,
                                      aliases,
                                      exports,
                                      permits,
                                      services);
        }
    }

    class ModuleViewImpl
        implements ModuleView
    {
        private final ModuleInfo mi;
        private final ModuleId id;
        private final Set<String> exports;
        private final Set<ModuleId> aliases;
        private final Map<String,Set<String>> services;
        private final Set<String> permits;
        private final String mainClass;

        ModuleViewImpl(ModuleInfo mi,
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
            return "view " + id.name() + " {"
                    + ", provides: " + aliases
                    + ", provides service: " + services
                    + ", permits: " + permits
                    + ", mainClass: " + mainClass
                    + " }";
        }
    }

}
