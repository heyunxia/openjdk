/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package org.openjdk.jigsaw;

import java.lang.module.*;
import java.util.*;
import java.net.URI;


/**
 * <p> A result of running the resolver </p>
 */

public final class Resolution {

    final Collection<ModuleIdQuery> rootQueries;

    final Set<ModuleInfo> modules;

    final Map<String,ModuleView> moduleViewForName;

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
               Map<String,ModuleView> mvfn,
               Map<String,URI> lfn,
               Set<ModuleId> needed,
               long dr, long sr)
    {
        rootQueries = rqs;
        modules = mis;
        moduleViewForName = mvfn;
        locationForName = lfn;
        modulesNeeded = needed;
        downloadRequired = dr;
        spaceRequired = sr;
    }

}
