/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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

public class ConfigurationBuilder {

    private static JigsawModuleSystem jms = JigsawModuleSystem.instance();

    private List<ModuleId> roots = new ArrayList<>();

    private Set<Context> contexts = new HashSet<>();
    private Map<String,Context> contextForModuleView = new HashMap<>();

    private Set<PathContext> pathContexts = new HashSet<>();
    private Map<String,PathContext> pathContextForModuleView = new HashMap<>();

    private ConfigurationBuilder(String[] rmids) {
        for (String s : rmids)
            roots.add(jms.parseModuleId(s));
    }

    public static ConfigurationBuilder config(String ... rmids) {
        return new ConfigurationBuilder(rmids);
    }

    public ConfigurationBuilder add(ContextBuilder cb) {
        Context cx = cb.build();
        contexts.add(cx);
        for (ModuleId mid : cx.modules()) {
            for (ModuleId id : cx.views(mid)) {
                contextForModuleView.put(id.name(), cx);
            }
        }
        PathContext pcx = cb.buildPath();
        pathContexts.add(pcx);
        for (ModuleId mid : pcx.modules()) {
            for (ModuleId id : pcx.views(mid)) {
                pathContextForModuleView.put(id.name(), pcx);
            }
        }
        return this;
    }

    public Configuration<Context> build() {
        return new Configuration<>(roots, contexts, contextForModuleView);
    }

    public Configuration<PathContext> buildPath() {
        Configuration<PathContext> cf
            = new Configuration<>(roots, pathContexts, pathContextForModuleView);
        for (PathContext pcx : pathContexts)
            ((ContextBuilder.MockPathContext)pcx).linkRemoteContexts(cf);
        return cf;
    }

    public boolean isEmpty() {
        return contexts.isEmpty();
    }

}
