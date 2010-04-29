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
import java.lang.annotation.Annotation;
import org.openjdk.jigsaw.*;

import static java.lang.module.Dependence.Modifier;


public class ModuleInfoBuilder {

    private static JigsawModuleSystem jms = JigsawModuleSystem.instance();

    private static class MI
	implements ModuleInfo
    {

	private ModuleId mid;
	public ModuleId id() { return mid; }

	private MI(ModuleId mid) {
	    this.mid = mid;
	}

	private Set<ModuleId> provides = new HashSet<ModuleId>();
	public Set<ModuleId> provides() { return provides; }

	private Set<Dependence> requires
	    // We use a linked hash set so as to guarantee deterministic order
	    = new LinkedHashSet<Dependence>();
	public Set<Dependence> requires() { return requires; }

	private Set<String> permits = new HashSet<String>();
	public Set<String> permits() { return permits; }

	private String mainClass;
	public String mainClass() { return mainClass; }

        public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
            return false;
        }

        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return null;
        }

        public String toString() { return mid.toString(); }
    }

    private MI mi;

    private ModuleInfoBuilder(String id) {
	mi = new MI(jms.parseModuleId(id));
    }

    public static ModuleInfoBuilder module(String id) {
	return new ModuleInfoBuilder(id);
    }

    public ModuleInfoBuilder requires(EnumSet<Modifier> mods, String mnvq) {
	int i = mnvq.indexOf('@');
	String mn;
	VersionQuery vq = null;
	if (i < 0) {
	    mn = mnvq;
	} else {
	    mn = mnvq.substring(0, i);
	    vq = jms.parseVersionQuery(mnvq.substring(i + 1));
	}
	mi.requires.add(new Dependence(mods,
				       new ModuleIdQuery(mn, vq)));
	return this;
    }

    public ModuleInfoBuilder requires(String mnvq) {
	return requires(null, mnvq);
    }

    public ModuleInfoBuilder requiresLocal(String mnvq) {
	return requires(EnumSet.of(Modifier.LOCAL), mnvq);
    }

    public ModuleInfoBuilder requiresOptional(String mnvq) {
	return requires(EnumSet.of(Modifier.OPTIONAL), mnvq);
    }

    public ModuleInfoBuilder requiresPublic(String mnvq) {
	return requires(EnumSet.of(Modifier.PUBLIC), mnvq);
    }

    public ModuleInfoBuilder provides(String mnv) {
	mi.provides.add(jms.parseModuleId(mnv));
	return this;
    }

    public ModuleInfoBuilder permits(String s) {
	if (s.indexOf('@') >= 0)
	    throw new IllegalArgumentException(s);
	mi.permits.add(s);
	return this;
    }

    public ModuleInfoBuilder mainClass(String cn) {
	mi.mainClass = cn;
	return this;
    }

    public ModuleInfo build() {
	return mi;
    }

}
