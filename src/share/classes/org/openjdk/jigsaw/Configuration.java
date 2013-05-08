/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.jigsaw;

import java.lang.module.*;
import java.io.*;
import java.util.*;


/**
 * <p> A set of named {@linkplain Context contexts}, together with a map from
 * module names to contexts </p>
 *
 * <p> Configurations are the result of the {@linkplain Configurator module
 * configuration process}; they are computed and stored in a {@linkplain
 * Library module library} during installation, and retrieved at run time
 * when an application is launched. </p>
 *
 * @see Context
 * @see Library
 * @see Configurator
 */

public final class Configuration<Cx extends BaseContext> {

    private final Set<ModuleId> roots;

    /**
     * Return the root modules of this configuration.
     */
    public Set<ModuleId> roots() { return roots; }

    private Set<Cx> contexts;
    private Map<String,Cx> contextForName;

    /**
     * Add the given context to this configuration.
     */
    protected void add(Cx cx) {
        contexts.add(cx);
        contextForName.put(cx.name(), cx);
    }

    /**
     * Return the set of contexts in this configuration.
     */
    public Set<Cx> contexts() {
        return contexts;
    }

    /**
     * Get the context of the given name.
     *
     * @throws  IllegalArgumentException
     *          If there is no context of that name in this configuration
     */
    public Cx getContext(String cxn) {
        Cx cx = contextForName.get(cxn);
        if (cx == null)
            throw new IllegalArgumentException(cxn + ": Unknown context");
        return cx;
    }

    private Map<String,Cx> contextForModuleView;

    /**
     * Associate the given context with the given module name.
     */
    protected void put(String mn, Cx cx) {
        contextForModuleView.put(mn, cx);
    }

    /**
     * Find the context for the given module name.
     *
     * @return  The found context, or {@code null} if no such
     *          context exists in this configuration
     */
    public Cx findContextForModuleName(String mn) {
        return contextForModuleView.get(mn);
    }

    /**
     * Get the context for the named module.
     *
     * @throws  IllegalArgumentException
     *          If there is no context for that module name
     *          in this configuration
     */
    public Cx getContextForModuleName(String mn) {
        Cx cx = contextForModuleView.get(mn);
        if (cx == null)
            throw new IllegalArgumentException(mn + ": Unknown module");
        return cx;
    }

    /**
     * Construct a new configuration from an existing context set and
     * module-view-name-to-context map.
     */
    public Configuration(Collection<ModuleId> roots,
                         Set<? extends Cx> contexts,
                         Map<String,? extends Cx> contextForModuleView)
    {
        this.roots = new HashSet<>(roots);
        this.contexts = new HashSet<>(contexts);
        this.contextForModuleView = new HashMap<>(contextForModuleView);
        this.contextForName = new HashMap<>();
        for (Cx cx : contexts) {
            this.contextForName.put(cx.name(), cx);
        }
    }

    /**
     * Construct a new, empty configuration for the given root module.
     */
    public Configuration(Collection<ModuleId> roots) {
        this.roots = new HashSet<>(roots);
        this.contexts = new HashSet<Cx>();
        this.contextForModuleView = new HashMap<String,Cx>();
        this.contextForName = new HashMap<String,Cx>();
    }

    private void dumpServices(String title, Map<String,Set<String>> services,
                              PrintStream out)
    {
        if (!services.isEmpty()) {
            out.format("    %s (%d)%n", title, services.size());

            for (Map.Entry<String, Set<String>> service : services.entrySet()) {
                Set<String> names = service.getValue();
                out.format("      %s (%d)%n", service.getKey(), names.size());
                for (String name : names) {
                    out.format("        %s%n", name);
                }
            }
        }
    }

    private void dump(Context cx, boolean all, PrintStream out) {
        dumpServices("service providers", cx.services(), out);

        if (!cx.localClasses().isEmpty()) {
            Set<String> classes = new TreeSet<>(cx.localClasses());
            out.format("    local (%d)", classes.size());
            if (!all && Platform.isPlatformContext(cx)) {
                out.format(" ...%n");
            } else {
                out.format("%n");
                Map<String,ModuleId> mflcm = cx.moduleForLocalClassMap();
                for (String cn : classes)
                    out.format("      %s:%s%n", cn, mflcm.get(cn));
            }
        }
        if (!cx.remotePackages().isEmpty()) {
            Set<String> rpkgs = new TreeSet<>(cx.remotePackages());
            out.format("    remote (%d)%n", rpkgs.size());
            Map<String,String> cfrpm = cx.contextForRemotePackageMap();
            for (String pn : rpkgs) {
                String cxn = cfrpm.get(pn);
                Cx dcx = getContext(cxn);
                if (!all && Platform.isPlatformContext(dcx))
                    continue;
                out.format("      %s=%s%n", pn, cxn);
            }
        }
    }

    private void dump(PathContext cx, PrintStream out) {
        if (!cx.localPath().isEmpty())
            out.format("    local  %s%n", cx.localPath());
        if (!cx.remoteContexts().isEmpty())
            out.format("    remote %s%n", cx.remoteContexts());
    }

    /**
     * Write a diagnostic summary of this configuration to the given stream.
     */
    public void dump(PrintStream out, boolean all) {
        boolean isPath = contexts().iterator().next() instanceof PathContext;
        out.format("%sconfiguration roots = %s%n",
                   isPath ? "path " : "", roots());
        for (Cx cx : contexts()) {
            out.format("  context %s%n", cx);
            for (ModuleId mid : cx.modules()) {
                out.format("    module %s", mid);
                if (cx instanceof Context) {
                    File lp = ((Context)cx).findLibraryPathForModule(mid);
                    if (lp != null)
                        out.format(" [%s]", lp);
                }
                out.format("%n");
                for (ModuleId id : cx.views(mid)) {
                    out.format("      view %s%n", id);
                }
            }

            if (cx instanceof Context)
                dump((Context)cx, all, out);
            else if (cx instanceof PathContext)
                dump((PathContext)cx, out);
        }
    }

    public void dump(PrintStream out) {
        dump(out, false);
    }

    public int hashCode() {
        int hc = roots.hashCode();
        hc = hc * 43 + contexts.hashCode();
        hc = hc * 43 + contextForModuleView.hashCode();
        return hc;
    }

    public boolean equals(Object ob) {
        if (!(ob instanceof Configuration))
            return false;
        Configuration that = (Configuration)ob;
        return (roots.equals(that.roots)
                && contexts.equals(that.contexts)
                && contextForModuleView.equals(that.contextForModuleView));
    }

}
