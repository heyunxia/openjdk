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

# @test
# @summary Zork

exec /bin/sh ${TESTSRC:-.}/tester.sh $0

: zork pass

module you @ 1 {
    requires in @ 1;
    class you.Are;
}

package you;
public class Are {
    public static void main(String[] args) {
	StringBuilder sb = new StringBuilder("You are");
	sb.append(in.A.text())
	  .append(maze.Of.text())
	  .append(twisty.Little.text());
	System.out.println(sb.toString());
    }
}

module in @ 1 {
    requires public maze @ 1;
}

package in;
public class A {
    public static String text() { return " in a"; }
}

module maze @ 1 {
    requires public twisty @ 1;
}

package maze;
public class Of {
    public static String text() { return " maze of"; }
}

package maze;
public class Alike {
    public static String text() { return " alike"; }
}

module twisty @ 1 {
    requires local passages @ 1;
    requires all @ 1;
}

package twisty;
public class Little {
    public static String text() {
        return " twisty little" + Passages.text() + all.Alike.text();
    }
}

module passages @ 1 {
    permits twisty;
}

package twisty;
/* package */ class Passages {
    public static String text() { return " passages"; }
}

module all @ 1 {
    requires in @ 1;
}

package all;
public class Alike {
    public static String text() {
        return ", all" + maze.Alike.text() + ".";
    }
}
