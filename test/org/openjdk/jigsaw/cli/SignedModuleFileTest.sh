#! /bin/sh

# Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
# @compile ImportPrivateKey.java ModuleFileTest.java
# @run shell SignedModuleFileTest.sh
# @summary Unit test for jpkg command

set -e

SRC=${TESTSRC:-.}
BIN=${TESTJAVA:-../../../../../build}/bin
VMOPTS="${TESTVMOPTS} -esa -ea"

rm -rf keystore.jks

# Create the keystore file and import the root CA cert
$BIN/keytool -import -keystore keystore.jks -file ${TESTSRC}/ca-cert.pem \
             -noprompt -storepass test123 -alias ca-cert

# Import the signer's private key and cert
$BIN/java ${VMOPTS} -Dtest.src=${TESTSRC} -cp ${TESTCLASSES} \
          ImportPrivateKey signer \
          signer-prikey.pem RSA signer-cert.pem

OS=`uname -s`
case "$OS" in
  SunOS | Linux | Darwin )
    PS=":"
    FS="/"
    ;;
  Windows* )
    PS=";"
    FS="\\"
    ;;
  CYGWIN* )
    PS=";"
    FS="\\"
    isCygwin=true
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

# ModuleFileTest has a dependency on javac and so include tools.jar
# in the classpath.
#
$BIN/java ${VMOPTS} -Dorg.openjdk.system.security.cacerts=keystore.jks \
          -Dtest.src=${TESTSRC} \
          -cp ${TESTCLASSES}${PS}${TESTJAVA}/lib/tools.jar \
          ModuleFileTest "test signed module file" \
          < ${TESTSRC}/keystore.pw
