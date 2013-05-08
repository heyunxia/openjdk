#! /bin/sh

# Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
# @summary package and install signed and timestamped modules

set -e

SRC=${TESTSRC:-.}
BIN=${TESTJAVA:-../../../../../build}/bin
VMOPTS="${TESTVMOPTS} -esa -ea"

mk() {
  d=`dirname $1`
  if [ ! -d $d ]; then mkdir -p $d; fi
  cat - >$1
}

importCert() {
  $BIN/keytool -import -keystore keystore.jks -file ${TESTSRC}/$1-cert.pem \
               -noprompt -storepass test123 -alias $1
}

importPrivateKey() {
  $BIN/java ${VMOPTS} -Dtest.src=${TESTSRC} ImportPrivateKey $1 $1-prikey.pem \
            $2 $1-cert.pem
}

rm -rf z.src keystore.jks

# Create the keystore file and import the root CA and the TSA root CA cert
importCert ca
importCert tsca

# Import the signer's private key and cert
$BIN/javac -source 8 -d . ${TESTSRC}/ImportPrivateKey.java
importPrivateKey signer RSA
# Import the TSA's private key and cert
importPrivateKey tsa DSA
# Import the expired signer's private key and cert
importPrivateKey expired-signer RSA

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
$BIN/javac -source 8 -d z.modules -modulepath z.modules `find z.src -name '*.java'`

# Check timestamping support
$BIN/javac -d . ${TESTSRC}/TimestampTest.java
$BIN/java ${VMOPTS} -Dtest.src=${TESTSRC} TimestampTest < ${TESTSRC}/keystore.pw
