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
# @summary Test service loading with multiple service interfaces,
#   service provider classes, and class loaders
# @run shell loading.sh

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

# Service interface module

mk z.src/si/module-info.java <<EOF
module si@1.0 {
  exports si;
}
EOF

mk z.src/si/si/ServiceInterfaceOne.java <<EOF
package si;
public interface ServiceInterfaceOne {
}
EOF

mk z.src/si/si/ServiceInterfaceTwo.java <<EOF
package si;
public interface ServiceInterfaceTwo {
}
EOF

# First service provider module

mk z.src/sp1/module-info.java <<EOF
module sp1@1.0 {
  requires si;

  provides service si.ServiceInterfaceOne with sp1.ServiceProviderOneImpl1;
  provides service si.ServiceInterfaceOne with sp1.ServiceProviderOneImpl2;

  provides service si.ServiceInterfaceTwo with sp1.ServiceProviderTwoImpl1;
  provides service si.ServiceInterfaceTwo with sp1.ServiceProviderTwoImpl2;
}
EOF

mk z.src/sp1/sp1/ServiceProviderOneImpl1.java <<EOF
package sp1;
import si.ServiceInterfaceOne;
public class ServiceProviderOneImpl1 implements ServiceInterfaceOne {
}
EOF

mk z.src/sp1/sp1/ServiceProviderOneImpl2.java <<EOF
package sp1;
import si.ServiceInterfaceOne;
public class ServiceProviderOneImpl2 implements ServiceInterfaceOne {
}
EOF

mk z.src/sp1/sp1/ServiceProviderTwoImpl1.java <<EOF
package sp1;
import si.ServiceInterfaceTwo;
public class ServiceProviderTwoImpl1 implements ServiceInterfaceTwo {
}
EOF

mk z.src/sp1/sp1/ServiceProviderTwoImpl2.java <<EOF
package sp1;
import si.ServiceInterfaceTwo;
public class ServiceProviderTwoImpl2 implements ServiceInterfaceTwo {
}
EOF

# Second service provider module

mk z.src/sp2/module-info.java <<EOF
module sp2@1.0 {
  requires si;

  provides service si.ServiceInterfaceOne with sp2.ServiceProviderOneImpl1;
  provides service si.ServiceInterfaceOne with sp2.ServiceProviderOneImpl2;

  provides service si.ServiceInterfaceTwo with sp2.ServiceProviderTwoImpl1;
  provides service si.ServiceInterfaceTwo with sp2.ServiceProviderTwoImpl2;
}
EOF

mk z.src/sp2/sp2/ServiceProviderOneImpl1.java <<EOF
package sp2;
import si.ServiceInterfaceOne;
public class ServiceProviderOneImpl1 implements ServiceInterfaceOne {
}
EOF

mk z.src/sp2/sp2/ServiceProviderOneImpl2.java <<EOF
package sp2;
import si.ServiceInterfaceOne;
public class ServiceProviderOneImpl2 implements ServiceInterfaceOne {
}
EOF

mk z.src/sp2/sp2/ServiceProviderTwoImpl1.java <<EOF
package sp2;
import si.ServiceInterfaceTwo;
public class ServiceProviderTwoImpl1 implements ServiceInterfaceTwo {
}
EOF

mk z.src/sp2/sp2/ServiceProviderTwoImpl2.java <<EOF
package sp2;
import si.ServiceInterfaceTwo;
public class ServiceProviderTwoImpl2 implements ServiceInterfaceTwo {
}
EOF

# Test service consumer and application module

mk z.src/app/module-info.java <<EOF
module app@1.0 {
  requires si;

  requires service si.ServiceInterfaceOne;
  requires service si.ServiceInterfaceTwo;

  class app.ServiceLoaderTest;
}
EOF

mk z.src/app/app/ServiceLoaderTest.java <<EOF
package app;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ServiceLoader;
import si.ServiceInterfaceOne;
import si.ServiceInterfaceTwo;

public class ServiceLoaderTest<T> {
    static interface LoaderFactory<T> {
        ServiceLoader<T> load(Class<T> serviceInterface);
    }

    public static void main(String... args) {
        {
            ServiceLoaderTest t = new ServiceLoaderTest(4, ServiceInterfaceOne.class);
            t.test();
        }
        {
            ServiceLoaderTest t = new ServiceLoaderTest(4, ServiceInterfaceTwo.class);
            t.test();
        }
    }

    private final int expectedSize;
    private final Class<T> serviceInterface;

    public ServiceLoaderTest(int expectedSize, Class<T> serviceInterface) {
        this.expectedSize = expectedSize;
        this.serviceInterface = serviceInterface;
    }

    LoaderFactory<T> load(final ClassLoader loader) {
        return new LoaderFactory<T>() {
            public ServiceLoader<T> load(Class<T> serviceInterface) {
                return ServiceLoader.load(serviceInterface, loader);
            }

            public String toString() {
                return "ServiceLoader using load(ClassLoader ) with " + loader;
            }
        };
    }

    LoaderFactory<T> loadInstalled() {
        return new LoaderFactory<T>() {
            public ServiceLoader<T> load(Class<T> serviceInterface) {
                return ServiceLoader.loadInstalled(serviceInterface);
            }

            public String toString() {
                return "ServiceLoader using loadInstalled()";
            }
        };
    }

    LoaderFactory<T> load() {
        return new LoaderFactory<T>() {
            public ServiceLoader<T> load(Class<T> serviceInterface) {
                return ServiceLoader.load(serviceInterface);
            }

            public String toString() {
                return "ServiceLoader using load()";
            }
        };
    }

    public void test() {
        List<LoaderFactory<T>> lfs = Arrays.asList(load(), loadInstalled(),
          load(null), load(ClassLoader.getSystemClassLoader()), load(serviceInterface.getClassLoader()));

        for (LoaderFactory<T> lf: lfs) {
            test(lf);
        }
    }

    public void test(LoaderFactory<T> lf) {
        System.out.println(String.format("Testing %s, expected=%d, loader=\"%s\"",
            serviceInterface, expectedSize, lf));
        ServiceLoader<T> sl = lf.load(serviceInterface);

        Set<T> values = new HashSet<>();
        for (T t: sl) {
            values.add(t);
        }

        // Test size
        if (values.size() != expectedSize) {
            fail("Wrong number of service instances: %d expected, %d found",
                expectedSize, values.size());
        }

        // Test same reference
        for (T t: sl) {
          if (!values.contains(t)) {
              fail("Service instance %s is not a member of existing instances",
                t, values);
          }
        }

        // Test new reference
        for (T t: lf.load(serviceInterface)) {
          if (values.contains(t)) {
              fail("Service instance %s is a member of new instances",
                t, values);
          }
        }
    }

    void fail(String message, Object... args) {
        throw new RuntimeException(String.format(message, args));
    }
}
EOF

mkdir z.modules z.classes

$BIN/javac -source 8 -d z.modules -modulepath z.modules \
    `find z.src -name '*.java'`
$BIN/jmod ${TESTTOOLVMOPTS} -L z.lib create
$BIN/jmod ${TESTTOOLVMOPTS} -L z.lib install z.modules `ls z.src`
$BIN/jmod ${TESTTOOLVMOPTS} -L z.lib ls -v
$BIN/java ${VMOPTS} -L z.lib -m app
