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


public class ContextBuilder {

    private static JigsawModuleSystem jms = JigsawModuleSystem.instance();

    private static class MockContext extends Context {
        private Map<String,ModuleId> moduleForName
            = new HashMap<String,ModuleId>();
        void add(ModuleId mid) {
            moduleForName.put(mid.name(), mid);
        }
        public void add(ModuleId mid, Set<ModuleId> views) {
            super.add(mid, views);
        }
        public void putModuleForLocalClass(String cn, String mn) {
            super.putModuleForLocalClass(cn, moduleForName.get(mn));
        }
        public void putContextForRemotePackage(String pn, String cxn) {
            super.putContextForRemotePackage(pn, cxn);
        }
        public void putService(String sn, String impl) {
            super.putService(sn, impl);
        }

    }

    private MockContext cx = new MockContext();

    static class MockPathContext extends PathContext {
        private Map<String,ModuleId> moduleForName
            = new HashMap<String,ModuleId>();
        void add(ModuleId mid) {
            moduleForName.put(mid.name(), mid);
        }
        public void add(ModuleId mid, Set<ModuleId> views) {
            super.add(mid, views);
        }
        private void extend(List<ModuleId> pl, ModuleId mid) {
            if (pl.size() == 0 || !pl.get(pl.size() - 1).equals(mid))
                pl.add(mid);
        }
        void extendLocalPath(ModuleId mid) {
            extend(super.localPath(), mid);
        }
        private Set<String> remoteContextNames = new HashSet<>();
        void linkRemoteContexts(Configuration<PathContext> cf) {
            for (String cxn : remoteContextNames)
                super.remoteContexts().add(cf.getContext(cxn));
            remoteContextNames = null;
        }
    }

    private MockPathContext pcx = new MockPathContext();

    private Map<ModuleId,Set<ModuleId>> modules = new HashMap<>();
    private ContextBuilder(String[] mids) {
        for (String s : mids) {
            ModuleId mid = jms.parseModuleId(s);
            if (modules.containsKey(mid)) {
                throw new IllegalArgumentException(mid + ": Duplicate");
            }

            Set<ModuleId> views = new HashSet<>();
            views.add(mid);
            modules.put(mid, views);

            cx.add(mid);
            pcx.add(mid);
            pcx.extendLocalPath(mid);
        }
    }

    public static ContextBuilder context(String ... mids) {
        return new ContextBuilder(mids);
    }

    public ContextBuilder views(String m, String... vns) {
        ModuleId mid = jms.parseModuleId(m);
        if (!modules.containsKey(mid)) {
            throw new IllegalArgumentException(mid + ": not in this context");
        }

        Set<ModuleId> views = modules.get(mid);
        for (String name : vns) {
            views.add(new ModuleId(name, mid.version()));
        }
        return this;
    }

    public ContextBuilder localClass(String cn, String mn) {
        cx.putModuleForLocalClass(cn, mn);
        return this;
    }

    public ContextBuilder remotePackage(String pn, String cxn) {
        cx.putContextForRemotePackage(pn, cxn);
        return this;
    }

    public ContextBuilder remote(String ... cxns) {
        for (String cxn : cxns) {
            // We don't necessarily have all the actual contexts module
            // at this point, so here we just save the context names
            pcx.remoteContextNames.add(cxn);
        }
        return this;
    }

    public ContextBuilder service(String serviceInteface,
                                  String serviceProviderClass) {
        cx.putService(serviceInteface, serviceProviderClass);
        return this;
    }

    public Context build() {
        for (Map.Entry<ModuleId,Set<ModuleId>> e : modules.entrySet()) {
            cx.add(e.getKey(), e.getValue());
        }
        cx.freeze();
        return cx;
    }

    public PathContext buildPath() {
        for (Map.Entry<ModuleId,Set<ModuleId>> e : modules.entrySet()) {
            pcx.add(e.getKey(), e.getValue());
        }
        pcx.freeze();
        return pcx;
    }

}
