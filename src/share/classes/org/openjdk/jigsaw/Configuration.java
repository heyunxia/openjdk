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

package org.openjdk.jigsaw;

import java.lang.module.*;
import java.io.*;
import java.util.*;


/**
 * <p> A set of named {@linkplain Context contexts}, together with a map from
 * module names to contexts. </p>
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

    private Map<String,Cx> contextForModule;

    /**
     * Associate the given context with the given module name.
     */
    protected void put(String mn, Cx cx) {
        contextForModule.put(mn, cx);
    }

    /**
     * Find the context for the given module name.
     *
     * @return  The found context, or {@code null} if no such
     *          context exists in this configuration
     */
    public Cx findContextForModuleName(String mn) {
        return contextForModule.get(mn);
    }

    /**
     * Get the context for the named module.
     *
     * @throws  IllegalArgumentException
     *          If there is no context for that module name
     *          in this configuration
     */
    public Cx getContextForModuleName(String mn) {
        Cx cx = contextForModule.get(mn);
        if (cx == null)
            throw new IllegalArgumentException(mn + ": Unknown module");
        return cx;
    }

    /**
     * Construct a new configuration from an existing context set and
     * module-name-to-context map.
     */
    public Configuration(Collection<ModuleId> roots,
                         Set<? extends Cx> contexts,
                         Map<String,? extends Cx> contextForModule)
    {
        this.roots = new HashSet<>(roots);
        this.contexts = new HashSet<Cx>(contexts);
        this.contextForModule = new HashMap<String,Cx>(contextForModule);
        this.contextForName = new HashMap<String,Cx>();
        for (Cx cx : contexts) {
            this.contextForName.put(cx.name(), cx);
        }
    }

    /**
     * Construct a new, empty configuration for the given root module.
     */
    public Configuration(ModuleId root) {
        this.roots = Collections.singleton(root);
        this.contexts = new HashSet<Cx>();
        this.contextForModule = new HashMap<String,Cx>();
        this.contextForName = new HashMap<String,Cx>();
    }

    private void dump(Context cx, PrintStream out) {
        if (!cx.localClasses().isEmpty()) {
            out.format("    local");
            for (Map.Entry<String,ModuleId> me
                 : cx.moduleForLocalClassMap().entrySet())
                out.format(" %s:%s", me.getKey(), me.getValue());
            out.format("%n");
        }
        if (!cx.remotePackages().isEmpty()) {
            out.format("    remote {");
            boolean first = true;
            for (Map.Entry<String,String> me
                 : cx.contextForRemotePackageMap().entrySet())
            {
                Cx dcx = getContext(me.getValue());
                if (Platform.isPlatformContext(dcx))
                    continue;
                if (!first)
                    out.format(", ");
                else
                    first = false;
                out.format("%s=%s", me.getKey(), me.getValue());
            }
            out.format("}%n");
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
    public void dump(PrintStream out) {
        boolean isPath = contexts().iterator().next() instanceof PathContext;
        out.format("%sconfiguration roots = %s%n",
                   isPath ? "path " : "", roots());
        for (Cx cx : contexts()) {
            out.format("  context %s %s%n", cx, cx.modules());
            if (Platform.isPlatformContext(cx))
                continue;
            if (cx instanceof Context)
                dump((Context)cx, out);
            else if (cx instanceof PathContext)
                dump((PathContext)cx, out);
        }
    }

    public int hashCode() {
        int hc = roots.hashCode();
        hc = hc * 43 + contexts.hashCode();
        hc = hc * 43 + contextForModule.hashCode();
        return hc;
    }

    public boolean equals(Object ob) {
        if (!(ob instanceof Configuration))
            return false;
        Configuration that = (Configuration)ob;
        return (roots.equals(that.roots)
                && contexts.equals(that.contexts)
                && contextForModule.equals(that.contextForModule));
    }

}
