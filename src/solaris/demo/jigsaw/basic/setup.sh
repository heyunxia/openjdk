#! /bin/bash -e

#
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
#

if ! (set -B >/dev/null 2>&1); then
  exec /bin/bash -e $0          # True bash, please
fi

[ $JIGSAW_DESTDIR ] || source ./env.sh

rm -rf classes; mkdir -p classes
/usr/bin/javac -d classes src/org/hello{,/swing}/Main.java

$SU dpkg -r jdk.{boot,base,awt,swing,tools} jdk org.hello
$SU apt-get clean

mkdir -p pkgs
rm -f pkgs/org.hello*.deb org.hello*.deb
rsync -av $BUILD/jigsaw-pkgs/ pkgs

if ! [ -d bin.nojava ]; then
  # Fake bin directory so that we can pretend
  # that we don't actually have java installed
  mkdir bin.nojava
  echo '#! /bin/bash' >bin.nojava/java
  echo 'echo -bash: $(basename $0): command not found; exit 1' >>bin.nojava/java
  chmod +x bin.nojava/java
  ln -s java bin.nojava/javac
  echo '#! /bin/bash' >bin.nojava/ls
  echo 'exec /bin/ls -CFh $*' >>bin.nojava/ls
  chmod +x bin.nojava/ls
fi
