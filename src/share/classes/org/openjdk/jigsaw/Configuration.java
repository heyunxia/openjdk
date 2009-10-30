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
 * <p> A set of named {@linkplain Contexts contexts}, together with a map from
 * module names to contexts. </p>
 *
 * <p> Configurations are the result of {@linkplain Resolver module
 * resolution}; they are computed and stored in a {@linkplain Library module
 * library} during the installation process, and retrieved at run time when an
 * application is launched. </p>
 *
 * @see Context
 * @see Library
 * @see Resolver
 */

public final class Configuration {

    private final ModuleId root;

    /**
     * Return the root module of this configuration.
     */
    public ModuleId root() { return root; }

    private Set<Context> contexts;
    private Map<String,Context> contextForName;

    /**
     * Add the given context to this configuration.
     */
    protected void add(Context cx) {
        contexts.add(cx);
        contextForName.put(cx.name(), cx);
    }

    /**
     * Return the set of contexts in this configuration.
     */
    public Set<Context> contexts() {
        return contexts;
    }

    /**
     * Get the context of the given name.
     *
     * @throws  IllegalArgumentException
     *          If there is no context of that name in this configuration
     */
    public Context getContext(String cxn) {
        Context cx = contextForName.get(cxn);
        if (cx == null)
            throw new IllegalArgumentException(cxn + ": Unknown context");
        return cx;
    }

    private Map<String,Context> contextForModule;

    /**
     * Associate the given context with the given module name.
     */
    protected void put(String mn, Context cx) {
        contextForModule.put(mn, cx);
    }

    /**
     * Find the context for the given module name.
     *
     * @return  The found context, or {@code null} if no such
     *          context exists in this configuration
     */
    public Context findContextForModuleName(String mn) {
        return contextForModule.get(mn);
    }

    /**
     * Get the context for the named module.
     *
     * @throws  IllegalArgumentException
     *          If there is no context for that module name
     *          in this configuration
     */
    public Context getContextForModuleName(String mn) {
        Context cx = contextForModule.get(mn);
        if (cx == null)
            throw new IllegalArgumentException(mn + ": Unknown module");
        return cx;
    }

    /**
     * Construct a new configuration from an existing context set and
     * module-name-to-context map.
     */
    public Configuration(ModuleId root,
                         Set<? extends Context> contexts,
                         Map<String,? extends Context> contextForModule)
    {
        this.root = root;
        this.contexts = new HashSet<Context>(contexts);
        this.contextForModule = new HashMap<String,Context>(contextForModule);
        this.contextForName = new HashMap<String,Context>();
        for (Context cx : contexts) {
            this.contextForName.put(cx.name(), cx);
        }
    }

    /**
     * Construct a new, empty configuration for the given root module.
     */
    public Configuration(ModuleId root) {
        this.root = root;
        this.contexts = new HashSet<Context>();
        this.contextForModule = new HashMap<String,Context>();
        this.contextForName = new HashMap<String,Context>();
    }

    /**
     * Write a diagnostic summary of this configuration to the given stream.
     */
    public void dump(PrintStream out) {
        out.format("configuration root = %s%n", root());
        for (Context cx : contexts()) {
            out.format("  context %s %s%n", cx, cx.modules());
            if (Platform.isPlatformContext(cx))
                continue;
            if (!cx.remotePackages().isEmpty()) {
                out.format("    remote {");
                boolean first = true;
                for (Map.Entry<String,String> me
                         : cx.contextForRemotePackageMap().entrySet())
                {
                    Context dcx = getContext(me.getValue());
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
            if (!cx.localClasses().isEmpty()) {
                out.format("    local");
                for (Map.Entry<String,ModuleId> me
                         : cx.moduleForLocalClassMap().entrySet())
                    out.format(" %s:%s", me.getKey(), me.getValue());
                out.format("%n");
            }
        }
    }

    public int hashCode() {
        int hc = root.hashCode();
        hc = hc * 43 + contexts.hashCode();
        hc = hc * 43 + contextForModule.hashCode();
        return hc;
    }

    public boolean equals(Object ob) {
        if (!(ob instanceof Configuration))
            return false;
        Configuration that = (Configuration)ob;
        return (root.equals(that.root)
                && contexts.equals(that.contexts)
                && contextForModule.equals(that.contextForModule));
    }

}
