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

# Create component modules from the classes and resources left
# in $CLASSBINDIR by the usual build process, and install them
# into $LIBDIR/modules
#
# export MODULARIZE_DEV=1 to avoid building the big jdk module

set -e

MODULES="$*"
JMS="$(echo $MODULES | sed -e 's/\(^\| \)/ jdk./g')"

MCLS=$TMP/module-classes
MRES=$TMP/module-resources

RSYNC="rsync -amO --delete --delete-excluded"

if [ -e $MLIB ]; then RSYNC="$RSYNC -i"; fi ## --out-format='%n%L'"; fi

mkdir -p $MCLS $MRES

for m in $JMS; do
  echo "-- $m"
  mc=$MCLS/$m
  mr=$MRES/$m
  mkdir -p $mc $mr
  if [ -e $m.ls ]; then
    sed -e '/^\(#\|$\)/d' -e 's!\.!/!g' -e 's/$/.class/' <$m.ls >$TMP/$m.cf
    $RSYNC --include '*/' --include-from=$TMP/$m.cf --exclude '*' $CLASSES/ $mc
  else
    touch $TMP/$m.cf
  fi
  if [ $m = jdk.boot ]; then
    ## Do this for boot module only (it gets all resources, for now)
    $RSYNC --exclude-from=$TMP/$m.cf --exclude '*.class' $CLASSES/ $MRES/$m
  fi
  $BIN/javac -source 7 -d $MCLS -modulepath $SRC $SRC/$m/module-info.java
done

if ! [ "$MODULARIZE_DEV" ]; then
  echo "-- jdk"
  m=jdk
  mc=$MCLS/$m
  cat $TMP/*.cf \
  | $RSYNC --exclude-from=- --include '*/' --include '*.class' --exclude '*' $CLASSES/ $mc
  $BIN/javac -source 7 -d $MCLS -modulepath $SRC $SRC/$m/module-info.java
fi

if ! [ "$MODULARIZE_DEV" ]; then JMS="$JMS jdk"; fi

if [ -e $MLIB ]; then
  mids=
  ## jmod ls should take multiple module queries
  for m in $JMS; do
    mid=$($BIN/jmod ls $m | grep -v '#')
    if [ "x$mid" = x ]; then echo "ERROR: No version for $m"; exit 9; fi
    v=$(echo $mid | cut -d@ -f2)
    mc=$MCLS/$m
    mr=$MRES/$m
    $RSYNC --exclude module-info.class $mc/ $MLIB/$m/$v/classes
    cp -p $mc/module-info.class $MLIB/$m/$v/info
    if [ -e $mr ]; then $RSYNC $mr/ $MLIB/$m/$v/resources; fi
    mids="$mids $mid"
  done
  $BIN/jmod reindex $mids
else
  $BIN/jmod create -N
  $BIN/jmod install $MCLS -r $MRES/jdk.boot $JMS
fi
