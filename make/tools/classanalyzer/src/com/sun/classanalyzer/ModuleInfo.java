/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.classanalyzer;

import java.util.*;

/**
 * Information about a module.  ModuleInfo.toString() returns
 * a string representation of the module-info.java source file.
 *
 */
public class ModuleInfo {

    private final Module module;
    private final Set<Dependence> requires;
    private final Map<String,Boolean> requiresServices;
    private final Map<String,List<String>> providesServices;

    ModuleInfo(Module m,
               Collection<Dependence> reqs,
               Map<String,Boolean> requiresServices,
               Map<String,Set<String>> providesServices)
    {
        this.module = m;
        this.requires = new TreeSet<>(reqs);
        this.requiresServices = new TreeMap<>(requiresServices);
        this.providesServices = new TreeMap<>();
        for (Map.Entry<String,Set<String>> entry: providesServices.entrySet()) {
            String sn = entry.getKey();
            // preserve order, assume no dups in input
            List<String> impls = new ArrayList<>(entry.getValue());
            this.providesServices.put(sn, impls);
        }
    }

    public Module getModule() {
        return module;
    }

    /**
     * This module's identifier
     */
    public String name() {
        return module.name();
    }

    public String id() {
        return module.name() + " @ " + module.version();
    }

    /**
     * The dependences of this module
     */
    public Set<Dependence> requires() {
        return Collections.unmodifiableSet(requires);
    }

    void visitDependence(Dependence.Filter filter, Set<Module> visited, Set<Module> result) {
        if (!visited.contains(module)) {
            visited.add(module);

            for (Dependence d : requires()) {
                if (filter == null || filter.accept(d)) {
                    ModuleInfo dmi = d.getModule().getModuleInfo();
                    dmi.visitDependence(filter, visited, result);
                }
            }
            result.add(module);
        }
    }

    Set<Module> dependences(Dependence.Filter filter) {
        Set<Module> visited = new TreeSet<Module>();
        Set<Module> result = new LinkedHashSet<Module>();

        visitDependence(filter, visited, result);
        return result;
    }

    private Set<String> reexports;

    private synchronized Set<String> reexports() {
        if (reexports != null) {
            return reexports;
        }

        final Module m = module;
        Set<Module> deps = dependences(new Dependence.Filter() {

            @Override
            public boolean accept(Dependence d) {
                // filter itself
                return d.isPublic();
            }
        });

        reexports = new TreeSet<String>();
        for (Module dm : deps) {
            if (dm != module) {
                // exports all local packages
                for (PackageInfo p : dm.packages()) {
                    if (p.isExported) {
                        reexports.add(p.pkgName);
                    }
                }
                reexports.addAll(dm.getModuleInfo().reexports());
            }
        }
        return reexports;
    }

    private static final boolean noRequiresPublic =
        Boolean.getBoolean("classanalyzer.useExports.reexport");
    private static final boolean useCommaSeparator =
        Boolean.getBoolean("classanalyzer.permits.list");

    private static final String INDENT = "    ";

    /**
     * Returns a string representation of module-info.java for
     * this module.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("module %s {%n", id()));

        for (Dependence d : requires()) {
            String mods = "";
            for (Dependence.Modifier mod : d.modifiers()) {
                if (!noRequiresPublic || mod != Dependence.Modifier.PUBLIC) {
                    mods += mod.toString() + " ";
                }
            }
            Module.View v = d.getModuleView();
            if (v == null)
                throw new RuntimeException("module " + module + " requires " + d + " has null view");
            sb.append(format(1, "requires %s%s;%n", mods, d.getModuleView().id()));
        }

        for (Map.Entry<String,Boolean> entry: requiresServices.entrySet()) {
            String s = entry.getKey();
            boolean optional = entry.getValue();
            sb.append(String.format("%srequires %sservice %s;%n",
                      INDENT,
                      (optional ? "optional " : ""),
                      s));
        }

        for (Map.Entry<String,List<String>> entry: providesServices.entrySet()) {
            String sn = entry.getKey();
            for (String cn: entry.getValue()) {
                sb.append(String.format("%sprovides service %s with %s;%n",
                          INDENT,
                          sn,
                          cn));
            }
        }

        for (Module.View v : module.views()) {
            printModuleView(v == module.defaultView() ? 0 : 1, sb, v);
        }

        Set<Module> reexportedModules = dependences(new Dependence.Filter() {
            @Override
            public boolean accept(Dependence d) {
                // filter itself
                return d.isPublic();
            }
        });

        if (noRequiresPublic) {
            printReexports(reexportedModules, sb);
        }

        sb.append("}\n");
        return sb.toString();
    }

    private void printCommaSepPermits(StringBuilder sb, int level, Set<Module> permits) {
        assert useCommaSeparator == true;
        if (permits.isEmpty())
            return;

        Set<Module> list = new TreeSet<>(permits);
        sb.append(format(level, "permits "));
        int i = 0;
        for (Module pm : list) {
            if (i > 0) {
                sb.append(", ");
                if ((i % 5) == 0) {
                    sb.append("\n");
                    sb.append(format(level, "       "));
                }
            }
            sb.append(pm.name());
            i++;
        }
        sb.append(";\n");
    }

    private void printReexports(Set<Module> modules, StringBuilder sb) {
        // reexports
        if (reexports().size() > 0) {
            Set<String> rexports = new TreeSet<>();
            if (modules.size() == 2) {
                // special case?
                rexports.addAll(reexports());
            } else {
                for (String e : reexports()) {
                    int j = e.indexOf('.');
                    rexports.add(e.substring(0, j) + ".**");
                }
            }
            sb.append("\n" + INDENT + "// reexports\n");
            for (String p : rexports) {
                sb.append(String.format("%sexports %s;%n", INDENT, p));
            }
        }
    }

    private String format(String fmt, Object... args) {
        return format(0, fmt, args);
    }

    private String format(int level, String fmt, Object... args) {
        String s = "";
        for (int i=0; i < level; i++) {
            s += INDENT;
        }
        return s + String.format(fmt, args);
    }

    private StringBuilder formatList(StringBuilder sb, int level, String fmt, Collection<?> c) {
        return formatList(sb, level, fmt, c, false);
    }

    private StringBuilder formatList(StringBuilder sb, int level, String fmt, Collection<?> c, boolean newline) {
        if (c.isEmpty())
            return sb;

        if (newline)
            sb.append("\n");

        TreeSet<?> ls = new TreeSet<>(c);
        for (Object o : ls) {
            sb.append(format(level, fmt, o));
        }
        return sb;
    }

    private void printModuleView(int level, StringBuilder sb, Module.View view) {
        if (view.isEmpty())
            return;

        if (level > 0) {
            // non-default view
            sb.append("\n");
            sb.append(format(level, "view %s {%n", view.name));
        }

        formatList(sb, level+1, "provides %s;%n", view.aliases());
        if (view.mainClass() != null) {
            sb.append(format(level+1, "class %s;%n", view.mainClass()));
        }

        boolean newline = !view.aliases().isEmpty() || view.mainClass() != null;
        if (!view.exports().isEmpty()) {
            if (level == 0) {
                sb.append(newline ? "\n" : "");
                sb.append(format(level+1, "// default view exports%n"));
                newline = false;
            }
            Set<String> exports = view.exports();
            String s = exports.iterator().next();
            if (s.equals("*")) {
                // exports all public types that are not exported in the default view
                exports = new TreeSet<>();
                for (PackageInfo pi : module.packages()) {
                    String pn = pi.pkgName;
                    if (pi.publicClassCount > 0 &&
                            !module.defaultView().exports().contains(pn)) {
                        exports.add(pn);
                    }
                }
            }
            formatList(sb, level+1, "exports %s;%n", exports, newline);
            newline = true;
        }

        if (useCommaSeparator) {
            printCommaSepPermits(sb, level + 1, view.permits());
        } else {
            formatList(sb, level + 1, "permits %s;%n", view.permits(), newline);
        }

        if (level > 0)
            sb.append(format(level, "}%n"));
    }
}

