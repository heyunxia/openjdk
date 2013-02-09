/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashSet;
import java.util.Set;

/**
 * Package Information
 */
public class PackageInfo implements Comparable<PackageInfo> {

    final Module module;
    final String pkgName;
    final boolean isExported;
    int  classCount;
    long classBytes;
    int  publicClassCount;
    int  innerClassCount;
    int  resourceCount;
    long resourceBytes;

    PackageInfo(Module m, String name) {
        this.module = m;
        this.pkgName = name;
        this.isExported = isExportedPackage(name);
        this.classCount = 0;
        this.classBytes = 0;
        this.publicClassCount = 0;
        this.innerClassCount = 0;
        this.resourceCount = 0;
        this.resourceBytes = 0;
    }

    void add(PackageInfo pkg) {
        this.classCount += pkg.classCount;
        this.classBytes += pkg.classBytes;
        this.publicClassCount += pkg.publicClassCount;
        this.innerClassCount += pkg.innerClassCount;
        this.resourceCount += pkg.resourceCount;
        this.resourceBytes += pkg.resourceBytes;
    }

    void addKlass(Klass k) {
        classCount++;
        classBytes += k.getFileSize();
        if (k.isPublic()) {
            publicClassCount++;
        }
        if (k.getClassName().contains("$")) {
            innerClassCount++;
        }
    }

    void addResource(ResourceFile r) {
        resourceCount++;
        resourceBytes += r.getFileSize();
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 59 * hash + (this.module != null ? this.module.hashCode() : 0);
        hash = 59 * hash + (this.pkgName != null ? this.pkgName.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PackageInfo) {
            PackageInfo p = (PackageInfo) o;
            return p.module.equals(this.module) && p.pkgName.equals(this.pkgName);
        }
        return false;
    }

    @Override
    public int compareTo(PackageInfo p) {
        if (this.equals(p)) {
            return 0;
        } else if (pkgName.compareTo(p.pkgName) == 0) {
            return module.compareTo(p.module);
        } else {
            return pkgName.compareTo(p.pkgName);
        }
    }

    final static Set<String> exportedPackages = new HashSet<String>();

    static {
        // if exported.packages property is not set,
        // exports all packages
        String apis = Module.getModuleProperty("exported.packages");
        if (apis != null) {
            for (String s : apis.split("\\s+")) {
                exportedPackages.add(s.trim());
            }
        }
    }

    static boolean isExportedPackage(String pkg) {
        return exportedPackages.isEmpty() || exportedPackages.contains(pkg);
    }
}
