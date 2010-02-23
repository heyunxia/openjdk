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

package org.openjdk.jigsaw;

import java.lang.module.*;
import java.util.*;
import java.net.URI;


public final class Resolution {

    final Collection<ModuleIdQuery> rootQueries;

    final Set<ModuleInfo> modules;

    final Map<String,ModuleInfo> moduleForName;

    final Map<String,URI> locationForName;

    private final Set<ModuleId> modulesNeeded;

    /**
     * <p> The ids of the modules that must be installed in order for this
     * resolution to be configured </p>
     */
    public Set<ModuleId> modulesNeeded() { return modulesNeeded; }

    final long downloadRequired;

    /**
     * <p> The total number of bytes that must be downloaded in order to
     * install the needed modules </p>
     */
    public long downloadRequired() { return downloadRequired; }

    final long spaceRequired;

    /**
     * <p> An upper bound on the total number of bytes of storage required to
     * install the needed modules </p>
     */
    public long spaceRequired() { return spaceRequired; }

    Resolution(Collection<ModuleIdQuery> rqs,
               Set<ModuleInfo> mis,
               Map<String,ModuleInfo> mfn,
               Map<String,URI> lfn,
               long dr, long sr)
    {
        rootQueries = rqs;
        modules = mis;
        moduleForName = mfn;
        locationForName = lfn;
        downloadRequired = dr;
        spaceRequired = sr;
        Set<ModuleId> nmids = new HashSet<>();
        for (ModuleInfo mi : modules) {
            URI u = locationForName.get(mi.id().name());
            if (u != null && !u.getScheme().equals("file"))
                nmids.add(mi.id());
        }
        modulesNeeded = Collections.unmodifiableSet(nmids);
    }

}
