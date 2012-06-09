#! /bin/bash

# Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
# @summary java.lang.module.ModuleInfoReader unit test
# @key modules

set -e

BIN=${TESTJAVA:-../../../../build}/bin
SRC=${TESTSRC:-.}
VMOPTS="${TESTVMOPTS} -esa -ea"

rm -rf z.*

sh $SRC/../../../org/openjdk/jigsaw/tester.sh $0

mkdir z.classes
$BIN/javac -d z.classes $SRC/_ModuleInfoReader.java
$BIN/java ${VMOPTS} -cp z.classes _ModuleInfoReader
exit 0

: setup pass compile

module M @ 1.0 {
    provides M1 @ 2.0;
    provides M2 @ 2.1;
    requires optional local N @ 9.0;
    requires optional local P @ 9.1;
    requires public Q @ 5.11;
    permits A;
    permits B;
    class M.X.Y.Main;
}

package M.X.Y;
public class Main {
    public static void main(String[] args) { }
}

module N @ 9.0 { 
    permits M;
}

module P @ 9.1 {
    permits M;
}

module Q @ 5.11 { }
