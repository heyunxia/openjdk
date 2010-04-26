/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.javac.util;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

public class Debug {
    static final boolean ALLOW_OPTION = true;
    static final boolean ALLOW_SYSPROP = true;
    static final boolean ALLOW_ENVVAR = true;

    public final PrintWriter out;
    Set<String> opts;

    public static void main(String... args) {
        Context c = new Context();
        Debug d = new Debug("test", Options.instance(c), new PrintWriter(System.out, true));
        for (String arg: args) {
            System.out.print("<<" + arg + "|");
            String[] lines = arg.split("n", -1);
            for (int i = 0; i < lines.length - 1; i++)
                d.out.println(lines[i]);
            d.out.print(lines[lines.length - 1]);
            d.out.flush();
            System.out.println(">>");
        }
    }

    public Debug(String name, Options options, PrintWriter out) {
        this.out = new DebugPrinter(name, out);

        if (ALLOW_OPTION) {
            String v = options.get(name + ".debug");
            if (v != null)
                setOpts("all");
            String prefix = name + ".debug:";
            for (String k: options.keySet()) {
                if (k.startsWith(prefix))
                    setOpts(k.substring(prefix.length()));
            }
        }

        try {
            if (opts == null && ALLOW_SYSPROP)
                setOpts(System.getProperty("javac." + name + ".debug"));
        } catch (SecurityException e) {
            // ignore
        }

        try {
            if (opts == null && ALLOW_ENVVAR)
                setOpts(System.getenv("_JAVAC_" + name.toUpperCase() + "_DEBUG"));
        } catch (SecurityException e) {
            // ignore
        }
    }

    public boolean isEnabled() {
        return (opts != null);
    }

    public boolean isEnabled(String opt) {
        if (opts == null)
            return false;

        return opts.contains(opt) ||
                (opts.contains("all") && !opts.contains("-" + opt));
    }

    public void print(Object o) {
        out.print(o);
    }

    public void print(String s) {
        out.print(s);
    }

    public void println(Object o) {
        out.println(o);
    }

    public void println(String s) {
        out.println(s);
    }

    public void println() {
        out.println();
    }

    void setOpts(String list) {
        if (list == null)
            return;
        if (list.equals("true")) // common value for sys props and env vars
            list = "all";
        for (String opt: list.split("[\\s,]+")) {
            if (opt.isEmpty())
                continue;
            if (opts == null)
                opts = new HashSet<String>();
            opts.add(opt);
        }
    }

    static class DebugPrinter extends PrintWriter {
        final String name;
        boolean needLinePrefix = true;

        DebugPrinter(String name, PrintWriter out) {
            super(out);
            this.name = name;
        }

        @Override
        public void write(int c) {
            checkLinePrefix();
            super.write(c);
        }

        @Override
        public void write(String s, int off, int len) {
            if (len > 0) {
                checkLinePrefix();
                super.write(s, off, len);
            }
        }

        @Override
        public void println() {
            super.println();
            needLinePrefix = true;
        }

        private void checkLinePrefix() {
            if (needLinePrefix) {
                needLinePrefix = false;
                write("[" + name + "] ");
            }
        }
    }
}
