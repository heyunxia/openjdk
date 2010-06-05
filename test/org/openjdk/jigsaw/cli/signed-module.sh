#! /bin/sh

# Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Sun designates this
# particular file as subject to the "Classpath" exception as provided
# by Sun in the LICENSE file that accompanied this code.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.

# @test
# @summary run a signed module with a SecurityManager and check that permission
#    is granted by the policy file

set -e

SRC=${TESTSRC:-.}
BIN=${TESTJAVA:-../../../../../build}/bin

mk() {
  d=$(dirname $1)
  if ! [ -d $(dirname $1) ]; then mkdir -p $d; fi
  cat - >$1
}

rm -rf z.src

mk z.src/test.security/module-info.java <<EOF
module test.security @ 0.1 {
    class test.security.GetProperty;
}
EOF


mk z.src/test.security/test/security/GetProperty.java <<EOF
package test.security;
import java.io.File;
import java.security.Policy;
import java.security.URIParameter;
public class GetProperty {
    public static void main(String[] args) throws Exception {
        URIParameter up = new URIParameter(new File(args[0]).toURI());
        Policy p = Policy.getInstance("JavaPolicy", up);
        Policy.setPolicy(p);
        System.setSecurityManager(new SecurityManager());
        System.getProperty("user.home");
    }
}
EOF

rm -rf z.modules && mkdir z.modules
$BIN/javac -source 7 -d z.modules -modulepath z.modules $(find z.src -name '*.java')

rm -f test.security@0.1.jmod
$BIN/jpkg -v --sign --signer mykey --storetype JKS \
          --keystore ${SRC}/keystore.jks \
          -m z.modules/test.security jmod test.security < ${SRC}/keystore.pw

rm -rf z.lib
$BIN/jmod -L z.lib create
$BIN/jmod -L z.lib install test.security@0.1.jmod
$BIN/java -L z.lib -m test.security ${SRC}/signed-module.policy
