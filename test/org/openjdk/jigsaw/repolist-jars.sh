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
# @summary Unit test for remote-repository lists

set -e

BIN=${TESTJAVA:-../../../../build}/bin
SRC=${TESTSRC:-.}
VMOPTS="${TESTVMOPTS} -esa -ea"

## Share setup code with pubrepo.sh, remrepo.sh

cat $SRC/maze.sh \
| sed -e 's/^: zork pass/: zork pass compile/' \
| sh $SRC/tester.sh -

rm -rf z.classes; mkdir -p z.classes
$BIN/javac -d z.classes \
  $SRC/_PublishedRepository.java \
  $SRC/_RemoteRepositoryList.java \
  $SRC/TrivialWebServer.java

mns=`cd z.test/modules; echo *`
echo $mns

mkdir -p z.test/module-files
for mn in $mns; do
  $BIN/jar cf z.test/module-files/$mn\@1.jar -C z.test/modules/$mn .
done

rm -rf z.repo* z.lib.*
mkdir z.repos
for r in foo bar baz qux; do
  $BIN/jrepo ${TESTTOOLVMOPTS} z.repos/$r create
done

$BIN/java ${VMOPTS} -cp z.classes _RemoteRepositoryList z.test/module-files/*
