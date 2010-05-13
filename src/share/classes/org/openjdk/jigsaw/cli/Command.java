/*
 * Copyright 2009-2010 Sun Microsystems, Inc.  All Rights Reserved.
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

package org.openjdk.jigsaw.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.openjdk.internal.joptsimple.OptionSet;


/* package */ abstract class Command<C> {

    static class Exception
        extends java.lang.Exception
    {

        private static final long serialVersionUID = 74132770414881L;

        public Exception(String fmt, Object ... args) {
            super(String.format(fmt, args));
        }

        private static String summarize(String what, IOException x) {
            String msg = null;
            if (x instanceof FileSystemException) {
                FileSystemException y = (FileSystemException)x;
                // ## There should be a better way to do this!
                msg = (y.getClass().getName()
                       .replace("Exception", "")
                       .replace("java.nio.file.", "")
                       .replaceAll("(\\p{Lower})(\\p{Upper})",
                                   "$1 $2"));
                msg = msg.charAt(0) + msg.substring(1).toLowerCase();
                if (what == null)
                    what = y.getFile();
            } else {
                msg = x.getMessage();
            }
            if (what != null)
                return String.format("%s: %s", what, msg);
            return String.format("I/O error: %s", x.getMessage());
        }

        public Exception(IOException x) {
            super(summarize(null, x));
            initCause(x);
        }

        public Exception(String what, IOException x) {
            super(summarize(what, x));
            initCause(x);
        }

        public Exception(java.lang.Exception x) {
            super(x);
        }

    }

    protected boolean verbose;
    protected boolean force;
    protected boolean dry;
    protected String command;
    protected LinkedList<String> args;
    protected OptionSet opts;

    final void run(C context, OptionSet opts) throws Command.Exception {
        verbose = opts.has("verbose");
        force = opts.has("force");
        dry = opts.has("dry-run");
        args = new LinkedList<String>(opts.nonOptionArguments());
        command = args.remove();
        this.opts = opts;
        go(context);
    }

    protected abstract void go(C context) throws Command.Exception;

    protected boolean hasArg() {
        return !args.isEmpty();
    }

    protected String takeArg()
        throws Command.Exception
    {
        if (args.isEmpty())
            throw new Command.Exception("%s: Insufficient arguments", command);
        return args.remove();
    }

    protected void finishArgs()
        throws Command.Exception
    {
        if (!args.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String a : args)
                sb.append(" ").append(a);
            throw new Command.Exception("%s: Extraneous arguments:%s",
                                        command, sb.toString());
        }
    }

    protected void noDry()
        throws Command.Exception
    {
        if (dry)
            throw new Command.Exception("%s: Option -n (--dry-run)"
                                        + " not supported",
                                        command);
    }

}
