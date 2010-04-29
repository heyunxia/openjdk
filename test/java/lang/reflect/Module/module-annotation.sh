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
# @summary Test to access annotations in java.lang.reflect.Module

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

mk z.src.v1/test.foo.bar/foo/bar/Main.java <<EOF
package foo.bar;
public class Main {};
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

mk z.src.v2/test.foo.bar/foo/bar/Main.java <<EOF
package foo.bar;
public class Main {};
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
module bar @ 1 {
    requires foo;
}
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

mk z.src/test/module-info.java <<EOF
module test @ 1 {
  requires foo;
  requires bar;
  requires test.foo.bar;
  class test.ModuleAnnotationTest;
}
EOF

mk z.src/test/test/ModuleAnnotationTest.java <<EOF
package test;
public class ModuleAnnotationTest {
   // content to be copied from the test source
}
EOF
cp -f $SRC/ModuleAnnotationTest.java z.src/test/test

mkdir z.modules

OS=`uname -s`
case "$OS" in
  SunOS )
    PS=":"
    FS="/"
    ;;
  Linux )
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

run() {
   ver=$1
   lib=z.lib.$1
   mkdir z.modules.$ver

   # when javac supports -L option, we can replace
   #    -modulepath z.modules.$ver${PS}z.modules
   # with:
   #    -modulepath z.modules.$ver -L z.lib 
   $BIN/javac -d z.modules.$ver -modulepath z.modules.$ver${PS}z.modules \
        $(find z.src.$ver -name '*.java') || exit 10

   $BIN/jmod -P z.lib -L $lib create  || exit 11
   $BIN/jmod -L $lib install z.modules.$ver test.foo.bar || exit 12
   $BIN/jmod -L $lib install z.modules test || exit 13
   $BIN/java -L $lib -m test $lib || exit 14
}

$BIN/javac -d z.modules -modulepath z.modules -sourcepath z.src.v1 \
    $(find z.src -name '*.java') || exit 1

$BIN/jmod -L z.lib create  || exit 2
$BIN/jmod -L z.lib install z.modules foo bar || exit 3
$BIN/java -L z.lib -m foo || exit 4

# Test ScalarTypes and ArrayTypes with default values
run v1

# Test ScalarTypes and ArrayTypes with override values
run v2

exit 0
