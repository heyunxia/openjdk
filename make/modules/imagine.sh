#! /bin/sh

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

# Create images for the non-Java parts of the component modules

set -e

if [ $# != 1 ]; then
  echo "Usage: $0 module-suffix"
  exit 9
fi
MOD=$1

if [ $MOD != jdk -a \! -f image.$MOD.ls ]; then
  # Nothing to do
  exit 0
fi

BUILD=$OUTPUTDIR
IMAGE_ROOT=$BUILD/jigsaw-images/$MOD
IMAGE_DEST=$IMAGE_ROOT/usr/local/jigsaw

echo "-- $IMAGE_DEST"
mkdir -p $IMAGE_DEST

RSYNC='rsync -amOi --delete'

if [ $MOD = jdk ]; then ls=image.rest.ls; else ls=image.$MOD.ls; fi

$RSYNC --exclude /lib/modules \
       --include '*/' --include-from=$ls \
       --exclude '*' $BUILD/ $IMAGE_DEST

if [ $MOD != boot ]; then exit 0; fi

for f in LICENSE ASSEMBLY_EXCEPTION; do
  cp -p ../../$f $IMAGE_DEST
done

mkdir -p $IMAGE_DEST/lib/modules
$RSYNC --include '/%*' --include '*/' --include '/jdk.boot/***' --exclude '*' \
       $BUILD/lib/modules/ $IMAGE_DEST/lib/modules

mkdir -p $IMAGE_DEST/lib/i386
cat >$IMAGE_DEST/lib/i386/jvm.cfg <<EOF
-client KNOWN
-server KNOWN
EOF

ISZ=$(du -sk $IMAGE_DEST | cut -f1)

mkdir -p $IMAGE_ROOT/DEBIAN
cat >$IMAGE_ROOT/DEBIAN/control <<EOF
Package: jdk.$MOD
Installed-Size: $ISZ
Version: 7_ea
Maintainer: Jigsaw Team <jigsaw-dev@openjdk.java.net>
Description: Jigsaw JDK 7 boot module
 No long description.
Architecture: i386
Section: misc
Priority: optional
EOF
