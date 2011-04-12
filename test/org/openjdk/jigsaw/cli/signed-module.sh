#! /bin/sh

# Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
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
# @summary run a signed module with a SecurityManager and check that permission
#    is granted by the policy file

set -e

SRC=${TESTSRC:-.}
BIN=${TESTJAVA:-../../../../../build}/bin

mk() {
  d=`dirname $1`
  if [ ! -d $d ]; then mkdir -p $d; fi
  cat - >$1
}

rm -rf z.src keystore.jks

# Create the keystore file and import the root CA cert
$BIN/keytool -import -keystore keystore.jks -file ${TESTSRC}/ca-cert.pem \
             -noprompt -storepass test123 -alias ca 

# Import the signer's private key and cert
$BIN/javac -source 7 -d  . ${TESTSRC}/ImportPrivateKey.java
$BIN/java -Dtest.src=${TESTSRC} ImportPrivateKey signer signer-prikey.pem \
          RSA signer-cert.pem

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

mk signed-module.policy <<EOF
keystore "keystore.jks";
keystorePasswordURL "${SRC}/keystore.pw";
grant signedBy "signer" {
    permission java.util.PropertyPermission "user.home", "read";
};
grant signedBy "expired-signer" {
    permission java.util.PropertyPermission "user.home", "read";
};
EOF

rm -rf z.modules && mkdir z.modules
$BIN/javac -source 7 -d z.modules -modulepath z.modules `find z.src -name '*.java'`

rm -f test.security@0.1.jmod
# Create signed module with "signer" alias
$BIN/jpkg -v --sign --alias signer --storetype JKS \
          --keystore keystore.jks -L z.lib \
          -m z.modules/test.security jmod test.security < ${SRC}/keystore.pw
# Install and run the signed module
rm -rf z.lib
$BIN/jmod -L z.lib create
$BIN/jmod -J-Dorg.openjdk.system.security.cacerts=keystore.jks \
          -L z.lib install test.security@0.1.jmod
$BIN/java -L z.lib -m test.security signed-module.policy
