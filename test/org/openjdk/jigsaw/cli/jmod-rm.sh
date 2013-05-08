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
# @summary jmod remove tests

set -e

BIN=${TESTJAVA:-../../../../../build}/bin
SRC=${TESTSRC:-.}
VMOPTS="${TESTVMOPTS} -esa -ea"
test=000jmod-rm

mk() {
  d=`dirname $1`
  if [ ! -d $d ]; then mkdir -p $d; fi
  cat - >$1
}

compare() {
  if [ "$1" != "$2" ]; then
    echo "FAIL: expected [$1], got [$2]"
    exit 1
  fi
}

rm -rf $test/z.*

mk $test/z.src/foo/module-info.java <<EOF
module foo@1.0 {
    exports com.foo;
    class com.foo.Main;
    view foo.internal {
        exports com.foo.internal;
        permits qux;
    }
}
EOF

mk $test/z.src/foo/com/foo/Main.java <<EOF
package com.foo;
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello from " + name());
    }

    public static String name() {
        return "foo";
    }
}
EOF

mk $test/z.src/foo/com/foo/internal/Hello.java <<EOF
package com.foo.internal;
public class Hello {
    public static String name() {
        return "foo.internal";
    }
}
EOF

mk $test/z.src/foo/natlib/libfoo.so <<EOF
just some random lib data
EOF

mk $test/z.src/foo/natcmd/foo <<EOF
just some random cmd data
EOF

mk $test/z.src/foo/config/fooProps.txt <<EOF
just some random config data
EOF

mk $test/z.src/bar/module-info.java <<EOF
module bar@1.0 {
    class com.bar.Main;
}
EOF

mk $test/z.src/bar/com/bar/Main.java <<EOF
package com.bar;
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello from bar");
    }
}
EOF

mk $test/z.src/baz/module-info.java <<EOF
module baz@1.0 {
    requires foo @ 1.0;
    class com.baz.Main;
}
EOF

mk $test/z.src/baz/com/baz/Main.java <<EOF
package com.baz;
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello from baz, and hello from " + com.foo.Main.name());
    }
}
EOF

mk $test/z.src/qux/module-info.java <<EOF
module qux@1.0 {
    requires foo.internal @ 1.0;
    class com.qux.Main;
}
EOF

mk $test/z.src/qux/com/qux/Main.java <<EOF
package com.qux;
import com.foo.internal.Hello;
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello from qux, and hello from " + Hello.name());
    }
}
EOF

mkdir $test/z.modules $test/z.classes

$BIN/javac -source 8 -d $test/z.modules -modulepath $test/z.modules \
    `find $test/z.src -name '*.java'`
$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib create
$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib install $test/z.modules foo
## Check foo@1.0 is installed
compare foo@1.0 `$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib list | tr -d ' \n\r'`
## Check jmod remove -d (--dry)
$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib remove -d foo@1.0
compare foo@1.0 `$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib list | tr -d ' \n\r'`
## Check jmod remove (not a dry run!)
$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib remove foo@1.0
compare "" `$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib list | tr -d ' \n\r'`
rm -rf $test/z.lib

## Check jmod remove with additional non dependent root modules
$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib create
$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib install $test/z.modules foo bar
$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib rm foo@1.0
compare bar@1.0 `$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib list | tr -d ' \n\r'`
rm -rf $test/z.lib

## Check with foo and dependent baz, should fail to remove foo
$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib create
$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib install $test/z.modules foo baz
## We expect the remove to fail. Temporarily disable '-e' ( script exits
## immediately if a command exits with a  non-zero exit status ).
set +e
if `$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib rm foo@1.0 > /dev/null 2>&1`; then
  echo "FAIL: foo@1.0 should not be removed as baz@1.0 depends on it"
  exit 1
fi
set -e
$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib rm foo@1.0 baz@1.0
compare "" `$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib list | tr -d ' \n\r'`
rm -rf $test/z.lib

## Check views, foo.internal and dependent qux, should fail to remove foo
$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib create
$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib install $test/z.modules foo qux
## Expect next command to fail
set +e
if `$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib rm foo@1.0 > /dev/null 2>&1`; then
  echo "FAIL: foo@1.0 should not be removed as qux@1.0 depends on it"
  exit 1
fi
set -e
$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib rm qux@1.0 foo@1.0
compare "" `$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib list | tr -d ' \n\r'`
rm -rf $test/z.lib

## Check -f (--force), foo and dependent baz, should remove foo
$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib create
$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib install $test/z.modules foo baz
$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib rm -f foo@1.0
compare baz@1.0 `$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.lib list | tr -d ' \n\r'`
rm -rf $test/z.lib


## Check out of module content
assertExists() {
  if [ ! -f $1 ]; then
    echo "ERROR: $1 does not exist"
    exit 1
  fi
}
assertDoesNotExist() {
  if [ -f $1 ]; then
    echo "FAILED: $1 should have been removed"
    exit 1
  fi
}

mkdir $test/z.image $test/z.jmods
$BIN/jpkg ${TESTTOOLVMOPTS} -m $test/z.modules/foo -d $test/z.jmods \
                            --natcmd $test/z.src/foo/natcmd \
                            --natlib $test/z.src/foo/natlib \
                            --config $test/z.src/foo/config \
                            jmod foo
$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.image/lib/z.lib create \
                            --natcmd $test/z.image/bin \
                            --natlib $test/z.image/lib \
                            --config $test/z.image/config
$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.image/lib/z.lib install \
                            $test/z.jmods/foo@1.0.jmod
compare foo@1.0 `$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.image/lib/z.lib list | tr -d ' \n\r'`
assertExists $test/z.image/bin/foo
assertExists $test/z.image/lib/libfoo.so
assertExists $test/z.image/config/fooProps.txt ]
$BIN/jmod ${TESTTOOLVMOPTS} -L $test/z.image/lib/z.lib rm foo@1.0
assertDoesNotExist $test/z.image/bin/foo
assertDoesNotExist $test/z.image/lib/libfoo.so
assertDoesNotExist $test/z.image/config/fooProps.txt
