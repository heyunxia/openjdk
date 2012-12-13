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
# @summary Test service instance iteration when a ServiceConfigurationError
#          occurs
# @run shell iteration-error.sh

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

mk z.src/si/si/ServiceInterface.java <<EOF
package si;
public interface ServiceInterface {
}
EOF

# First service provider module

mk z.src/sp/module-info.java <<EOF
module sp@1.0 {
  requires si;

  provides service si.ServiceInterface with sp.ServiceProviderImpl1;
  provides service si.ServiceInterface with sp.ServiceProviderImpl2;
  provides service si.ServiceInterface with sp.ServiceProviderImpl3;
  provides service si.ServiceInterface with sp.ServiceProviderImpl4;
}
EOF

mk z.src/sp/sp/ServiceProviderImpl1.java <<EOF
package sp;
import si.ServiceInterface;
public class ServiceProviderImpl1 implements ServiceInterface {
}
EOF

mk z.src/sp/sp/ServiceProviderImpl2.java <<EOF
package sp;
import si.ServiceInterface;
public class ServiceProviderImpl2 implements ServiceInterface {
    public ServiceProviderImpl2() throws Exception {
        throw new Exception();
    }
}
EOF

mk z.src/sp/sp/ServiceProviderImpl3.java <<EOF
package sp;
import si.ServiceInterface;
public class ServiceProviderImpl3 implements ServiceInterface {
}
EOF

mk z.src/sp/sp/ServiceProviderImpl4.java <<EOF
package sp;
import si.ServiceInterface;
public class ServiceProviderImpl4 implements ServiceInterface {
    public ServiceProviderImpl4() throws Exception {
        throw new Exception();
    }
}
EOF

# Test service consumer and application module

mk z.src/app/module-info.java <<EOF
module app@1.0 {
  requires si;

  requires service si.ServiceInterface;

  class app.ServiceLoaderTest;
}
EOF

mk z.src/app/app/ServiceLoaderTest.java <<EOF
package app;

import java.util.*;
import si.ServiceInterface;

public class ServiceLoaderTest {
    public static void main(String... args) {
        ServiceLoader<ServiceInterface> sl = ServiceLoader.load(ServiceInterface.class);
       
        List<ServiceInterface> services = new ArrayList<>();
        List<ServiceConfigurationError> exceptions = new ArrayList<>();

        // First iteration

        Iterator<ServiceInterface> i = sl.iterator();
        while (i.hasNext()) {
            try {
                services.add(i.next());
            } catch (ServiceConfigurationError sce) {
                exceptions.add(sce);                         
            }
        }

        assertEquals("Service instances on first iteration", services.size(), 2);
        assertEquals("Service configuration errors on first iteration", exceptions.size(), 2);

        assertEquals("Cause of service configuration error", 
            exceptions.get(0).getCause().getClass(), Exception.class);
        assertEquals("Cause of service configuration error", 
            exceptions.get(1).getCause().getClass(), Exception.class);

        // Reset state

        services.clear();
        exceptions.clear();

        // Second iteration

        i = sl.iterator();
        while (i.hasNext()) {
            try {
                services.add(i.next());
            } catch (ServiceConfigurationError sce) {
                exceptions.add(sce);                         
            }
        }

        assertEquals("Service instances on second iteration", services.size(), 2);
        assertEquals("Service configuration errors on second iteration", exceptions.size(), 0);
    }

    static <T> void assertEquals(String msg, T actual, T expected) {
        if (!expected.equals(actual)) {
            fail("%s expected=%s actual=%s", msg, expected, actual);
        }
    }

    static void fail(String message, Object... args) {
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
