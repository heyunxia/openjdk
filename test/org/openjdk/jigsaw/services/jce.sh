#! /bin/sh

# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.

# @test
# @summary Basic test for loading JCE providers using ServiceLoader
# @run shell jce.sh

set -e

BIN=${TESTJAVA:-../../../../build}/bin
SRC=${TESTSRC:-.}
VMOPTS="${TESTVMOPTS} -esa -ea"

mk() {
  d=`dirname $1`
  if [ ! -d $d ]; then mkdir -p $d; fi
  cat - >$1
}

rm -rf z.*

mk z.src/jce/module-info.java <<EOF
module jce @ 1.0 {
    // This doesn't work yet. It requires making providers not configured in
    // the java.security file available by default. See 7191104 (issue # 5).
    //provides service java.security.Provider with com.jce.DummyProvider;
    class com.jce.Main;
}
EOF

mk z.src/jce/com/jce/Main.java <<EOF
package com.jce;
import java.security.*;
import java.util.*;
public class Main {
    public static void main(String[] args) throws Exception {
        Provider[] providers = Security.getProviders();
        System.out.println("Available JCE Providers:");
        for (Provider p : providers) {
            System.out.println(p);
        }
        System.out.println("Getting SHA-256 from first provider");
        MessageDigest.getInstance("SHA-256");
        System.out.println("Getting SHA-256 from \"SUN\" provider");
        MessageDigest.getInstance("SHA-256", "SUN");
        Provider p = Security.getProvider("SUN");
        if (p == null) {
            throw new Exception("SUN JCE Provider not found");
        }
        System.out.println("Getting SHA-256 from SUN Provider object");
        MessageDigest.getInstance("SHA-256", p);

        // This doesn't work yet. It requires making providers not configured in
        // the java.security file available by default. See 7191104 (issue # 5).
        //System.out.println("Getting SHA-256 from \"Dummy\" provider");
        //MessageDigest.getInstance("SHA-256", "Dummy");
    }
}
EOF

# This doesn't work yet. It requires making providers not configured in
# the java.security file available by default. See 7191104 (issue # 5).
#
# mk z.src/jce/com/jce/DummyProvider.java <<EOF
# package com.jce;
# import java.security.Provider;
# import java.security.ProviderException;
# public class DummyProvider extends Provider {
#    public DummyProvider() {
#        super("Dummy", 0.0, "Do not use");
#        throw new ProviderException("DummyProvider");
#    }
#}
#EOF

mkdir z.modules z.classes

$BIN/javac -source 8 -d z.modules -modulepath z.modules \
    `find z.src -name '*.java'`
$BIN/jmod ${TESTTOOLVMOPTS} -L z.lib create
$BIN/jmod ${TESTTOOLVMOPTS} -L z.lib install z.modules `ls z.src`
$BIN/jmod ${TESTTOOLVMOPTS} -L z.lib ls -v
$BIN/java ${VMOPTS} -Djava.security.debug=jca -L z.lib -m jce 1
