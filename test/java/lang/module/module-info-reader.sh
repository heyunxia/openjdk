#! /bin/bash

# Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.

# @test
# @summary java.lang.module.ModuleInfoReader unit test

set -e

BIN=${TESTJAVA:-../../../../build}/bin
SRC=${TESTSRC:-.}

rm -rf z.*

sh $SRC/../../../org/openjdk/jigsaw/tester.sh $0

mkdir z.classes
$BIN/javac -d z.classes $SRC/_ModuleInfoReader.java
$BIN/java -cp z.classes _ModuleInfoReader
exit 0

: setup pass compile

module M @ 1.0
    provides M1 @ 2.0, M2 @ 2.1
{
    requires optional local N @ 9.0, P @ 9.1;
    requires public Q @ 5.11;
    permits A, B;
    class act M.X.Y.Main;
}

package M.X.Y;
public class Main { }

module N @ 9.0 { }

module P @ 9.1 { }

module Q @ 5.11 { }
