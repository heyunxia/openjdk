#! /bin/sh

# Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
# @summary Unit test for jmod command

set -e

SRC=${TESTSRC:-.}
BIN=${TESTJAVA:-../../../../../build}/bin

mk() {
  d=`dirname $1`
  if [ ! -d `dirname $1` ]; then mkdir -p $d; fi
  cat - >$1
}

rm -rf z.src keystore.jks

# Create the keystore file and import the root CA cert
$BIN/keytool -import -keystore keystore.jks -file ${TESTSRC}/ca-cert.pem \
  -noprompt -storepass test123 -alias ca-cert

# Import the signer's private key and cert
$BIN/javac -source 7 -d  . ${TESTSRC}/ImportPrivateKey.java
$BIN/java -Dtest.src=${TESTSRC} ImportPrivateKey

mk z.src/com.foo.signed/module-info.java <<EOF
module com.foo.signed @ 1.0
{
    permits com.foo.john_hancock;
    class com.foo.signed.Main;
}
EOF

mk z.src/com.foo.signed/com/foo/signed/Main.java <<EOF
package com.foo.signed;
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, signed world!");
    }
}
EOF

rm -rf z.modules && mkdir -p z.modules
$BIN/javac -source 7 -d z.modules -modulepath z.modules `find z.src -name '*.java'`

rm -rf z.lib
JAVA_MODULES=z.lib
export JAVA_MODULES
$BIN/jmod create
$BIN/jmod id

# Test the installation of a signed module
#
$BIN/jpkg \
    -v \
    -m z.modules/com.foo.signed \
    -d z.modules \
    -k keystore.jks \
    jmod com.foo.signed < ${TESTSRC}/keystore.pw
# Skip verification (due to empty 'cacerts' file)
$BIN/jmod install --noverify z.modules/com.foo.signed@1.0.jmod

$BIN/jmod list -v
$BIN/jmod dump-class com.foo.signed@1.0 com.foo.signed.Main z

# Check the class file packaged in the jmod file
# As pack200 modifies the class file during compression, 
# we need to compare with a 'pack200-unpack200' version 
$BIN/jar cfM z.modules/com.foo.signed.jar -C z.modules/com.foo.signed com/foo/signed/Main.class
$BIN/pack200 z.modules/com.foo.signed.pack.gz z.modules/com.foo.signed.jar
$BIN/unpack200 z.modules/com.foo.signed.pack.gz z.jar
$BIN/jar xf z.jar com/foo/signed/Main.class
cmp z com/foo/signed/Main.class
