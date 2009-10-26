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

import static java.lang.module.Dependence.Modifier;


public class ConfigurationBuilder {

    private static JigsawModuleSystem jms = JigsawModuleSystem.instance();

    private ModuleId root;
    private Set<Context> contexts
	= new HashSet<Context>();
    private Map<String,Context> contextForModule
	= new HashMap<String,Context>();

    private Configuration cf;

    private ConfigurationBuilder(String rmid) {
	root = jms.parseModuleId(rmid);
    }

    public static ConfigurationBuilder config(String rmid) {
	return new ConfigurationBuilder(rmid);
    }

    public ConfigurationBuilder add(ContextBuilder cb) {
	Context cx = cb.build();
	contexts.add(cx);
	for (ModuleId mid : cx.modules())
	    contextForModule.put(mid.name(), cx);
	return this;
    }

    public Configuration build() {
	return new Configuration(root, contexts, contextForModule);
    }

    public boolean isEmpty() {
	return contexts.isEmpty();
    }

}
