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

package org.openjdk.jigsaw.cli;

import java.io.*;
import java.util.*;
import org.openjdk.internal.joptsimple.OptionSet;


/* package */ abstract class Command<C> {

    static class Exception
	extends java.lang.Exception
    {

	public Exception(String fmt, Object ... args) {
	    super(String.format(fmt, (java.lang.Object[])args));
	}

	public Exception(IOException x) {
	    super(String.format("I/O error: %s", x.getMessage()));
	}

    }

    protected boolean verbose;
    protected String command;
    protected LinkedList<String> args;
    protected OptionSet opts;

    final void run(C context, OptionSet opts) throws Command.Exception {
	verbose = opts.has("verbose");
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

}
