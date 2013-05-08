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
# @summary Basic test of repo on file system
# @run shell hello-repo.sh

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

mk z.src/app/module-info.java <<EOF
module app @ 1.0 {
    requires foolib;
    class com.app.Main;
}
EOF

mk z.src/app/com/app/Main.java <<EOF
package com.app;
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
EOF

mk z.src/foolib/module-info.java <<EOF
module foolib @ 1.0 {
}
EOF

mkdir z.mods
$BIN/javac -source 8 -d z.mods -modulepath z.modules \
    `find z.src -name '*.java'`

mkdir z.pkgs
$BIN/jpkg ${TESTTOOLVMOPTS} -d z.pkgs -m z.mods/app jmod app
$BIN/jpkg ${TESTTOOLVMOPTS} -d z.pkgs -m z.mods/foolib jmod foolib

$BIN/jrepo ${TESTTOOLVMOPTS} z.repo create
$BIN/jrepo ${TESTTOOLVMOPTS} z.repo add z.pkgs/*.jmod

$BIN/jmod ${TESTTOOLVMOPTS} create -L z.mlib
$BIN/jmod ${TESTTOOLVMOPTS} add-repo -L z.mlib z.repo
$BIN/jmod ${TESTTOOLVMOPTS} -L z.mlib install -n app
$BIN/jmod ${TESTTOOLVMOPTS} -L z.mlib install app
$BIN/java ${VMOPTS} -L z.mlib -m app
