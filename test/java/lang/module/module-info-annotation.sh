#! /bin/bash

# Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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
mk() {
  d=$(dirname $1)
  if ! [ -d $(dirname $1) ]; then mkdir -p $d; fi
  cat - >$1
}


mk z.src.v1/test.foo.bar/module-info.java <<EOF
@org.bar.ScalarTypesWithDefault (
)
@com.foo.ArrayTypesWithDefault (
)
module test.foo.bar @ 1 {
    requires foo;
    requires bar;
}
EOF

mk z.src.v2/test.foo.bar/module-info.java <<EOF
import java.util.*;
import com.foo.*;
import org.bar.*;
@org.bar.ScalarTypes (
        b =    1,
        s =    2,
        i =    3,
        l =    4L,
        c =    '5',
        f =    6.0f,
        d =    7.0,
        bool = true,
        str =  "custom",
        cls =  Map.class,
        e =    Stooge.MOE,
        a =    @Point(x = 1, y = 2)
)
@com.foo.ArrayTypes (
        b =    { 1, 2 },
        s =    { 2, 3 },
        i =    { 3, 4 },
        l =    { 4L, 5L },
        c =    { '5', '6' },
        f =    { 6.0f, 7.0f },
        d =    { 7.0, 8.0 },
        bool = { true, false },
        str =  { "custom", "paint" },
        cls =  { Map.class, Set.class },
        e =    { Stooge.MOE, Stooge.CURLY },
        a =    { @Point(x = 1, y = 2),  @Point(x = 3, y = 4) }
)
module test.foo.bar @ 2 {
    requires foo;
    requires bar;
}
EOF

mk z.src/foo/module-info.java <<EOF
module foo @ 1 {
  requires bar @ 1;
  class com.foo.Foo;
}
EOF

mk z.src/foo/com/foo/Stooge.java <<EOF
package com.foo;
public enum Stooge { LARRY, MOE, CURLY }
EOF

mk z.src/foo/com/foo/ArrayTypes.java <<EOF
package com.foo;
import static java.lang.annotation.RetentionPolicy.*;
import java.lang.annotation.*;
import org.bar.Point;
@Retention(RUNTIME)
public @interface ArrayTypes {
    byte[]     b();
    short[]    s();
    int[]      i();
    long[]     l();
    char[]     c();
    float[]    f();
    double[]   d();
    boolean[]  bool();
    String[]   str();
    Class[]    cls();
    Stooge[]   e();
    Point[]    a();
}
EOF

mk z.src/foo/com/foo/Foo.java <<EOF
package com.foo;
public class Foo {
    public static void main(String[] args) {
        for (Stooge s : Stooge.class.getEnumConstants()) {
            System.out.println(s);
        }
    }
}
EOF

mk z.src/foo/com/foo/ArrayTypesWithDefault.java <<EOF
package com.foo;
import static java.lang.annotation.RetentionPolicy.*;
import java.lang.annotation.*;
import org.bar.Point;
import java.util.*;
@Retention(RUNTIME)
public @interface ArrayTypesWithDefault {
    byte[]    b()    default { 11 };
    short[]   s()    default { 12 };
    int[]     i()    default { 13 };
    long[]    l()    default { 14L };
    char[]    c()    default { 'V' };
    float[]   f()    default { 16.0f };
    double[]  d()    default { 17.0 };
    boolean[] bool() default { false };
    String[]  str()  default { "default" };
    Class[]   cls()  default { Deque.class, Queue.class };
    Stooge[]  e()    default { Stooge.LARRY };
    Point[]   a()    default { @Point(x = 11, y = 12) };
}
EOF

mk z.src/bar/module-info.java <<EOF
module bar @ 1 { }
EOF

mk z.src/bar/org/bar/Point.java <<EOF
package org.bar;
import java.lang.annotation.*;
@Target({})
public @interface Point { int x(); int y(); }
EOF

mk z.src/bar/org/bar/ScalarTypes.java <<EOF
package org.bar;
import static java.lang.annotation.RetentionPolicy.*;
import java.lang.annotation.*;
import com.foo.Stooge;
@Retention(RUNTIME)
public @interface ScalarTypes {
    byte     b();
    short    s();
    int      i();
    long     l();
    char     c();
    float    f();
    double   d();
    boolean  bool();
    String   str();
    Class    cls();
    Stooge   e();
    Point    a();
}
EOF

mk z.src/bar/org/bar/ScalarTypesWithDefault.java <<EOF
package org.bar;
import static java.lang.annotation.RetentionPolicy.*;
import java.lang.annotation.*;
import com.foo.Stooge;
import java.util.*;
@Retention(RUNTIME)
public @interface ScalarTypesWithDefault {
    byte     b()    default 11;
    short    s()    default 12;
    int      i()    default 13;
    long     l()    default 14;
    char     c()    default 'V';
    float    f()    default 16.0f;
    double   d()    default 17.0;
    boolean  bool() default false;
    String   str()  default "default";
    Class    cls()  default Deque.class;
    Stooge   e()    default Stooge.LARRY;
    Point    a()    default @Point(x = 11, y = 12);
}
EOF

mkdir z.modules z.classes

$BIN/javac -d z.classes $SRC/ModuleAnnotationTest.java \
     $(find z.src/foo/com/foo -name '*.java') \
     $(find z.src/bar/org/bar -name '*.java') || exit 1
$BIN/javac -d z.modules -modulepath z.modules  \
    $(find z.src -name '*.java') || exit 2

$BIN/jmod -L z.lib create  || exit 3
$BIN/jmod -L z.lib install z.modules foo bar || exit 4
$BIN/java -L z.lib -m foo || exit 5

run() {
    ver=$1
    $BIN/javac -d z.modules -modulepath z.modules \
        z.src.$ver/test.foo.bar/module-info.java || exit 5
    $BIN/java -cp z.classes ModuleAnnotationTest z.modules/test.foo.bar/module-info.class || exit 6
}

# Test ScalarTypes and ArrayTypes with default values
run v1

# Test ScalarTypes and ArrayTypes with override values
run v2

exit 0
