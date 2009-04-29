#! /bin/sh

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

# @test Basic resolver tests

exec /bin/sh ${TESTSRC:-.}/tester.sh $0

: trivial pass

module x @ 1 {
  requires y @ 1;
  class x.X;
}

package x;
public class X {
    public static void main(String[] args) {
        System.exit(y.Y.zero());
    }
}

module y @ 1 { }

package y;
public class Y {
    public static int zero() { return 0; }
}

: trivial2 fail install

module x @ 1 {
  requires y @ 1;
  class x.X;
}

package x;
public class X { }

module y @ 2 { }

: package-private fail invoke

module x @ 1 {
  requires y @ 1;
  class x.X;
}

package x;
public class X {
    public static void main(String[] args) throws Exception {
        Class.forName("y.Y");
    }
}

module y @ 1 { }

package y;
class Y { }
