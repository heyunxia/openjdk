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

import java.util.*;
import java.lang.module.*;
import org.openjdk.jigsaw.*;


public class ContextBuilder {

    private static JigsawModuleSystem jms = JigsawModuleSystem.instance();

    private static class MockContext extends Context {
	private Map<String,ModuleId> moduleForName
	    = new HashMap<String,ModuleId>();
	public void add(ModuleId mid) {
	    super.add(mid);
	    moduleForName.put(mid.name(), mid);
	}
	public void putModuleForLocalClass(String cn, String mn) {
	    super.putModuleForLocalClass(cn, moduleForName.get(mn));
	}
	public void putContextForRemotePackage(String pn, String cxn) {
	    super.putContextForRemotePackage(pn, cxn);
	}
    }

    private MockContext cx = new MockContext();

    private ContextBuilder(String[] mids) {
	for (String s : mids) {
	    ModuleId mid = jms.parseModuleId(s);
	    if (cx.modules().contains(mid))
		throw new IllegalArgumentException(mid + ": Duplicate");
	    cx.add(mid);
	}
    }

    public static ContextBuilder context(String ... mids) {
	return new ContextBuilder(mids);
    }

    public ContextBuilder local(String mn, String cn) {
	cx.putModuleForLocalClass(cn, mn);
	return this;
    }

    public ContextBuilder remote(String pn, String cxn) {
	cx.putContextForRemotePackage(pn, cxn);
	return this;
    }

    public Context build() {
	cx.freeze();
	return cx;
    }

}
