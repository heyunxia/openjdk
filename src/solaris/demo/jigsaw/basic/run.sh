#! /bin/bash

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
  exec /bin/bash $0             # True bash, please
fi

if [ `uname` != Linux ] || ! which dpkg >&/dev/null; then
  echo "This demo only works on Debian-based Linux systems"
  exit 1
fi

source ./env.sh

if ! bash -e setup.sh >&setup.log; then
  cat setup.log
  exit 1
fi

# ANSI escape sequences to highlight commands and comments, if on a tty
if stty 9>&1- -F /dev/fd/9 >&/dev/null; then
  BD='[1m'                    # Bold
  HI='[32m'                   # Green
  LO='[0m'                    # Default
else
  HI=
  LO=
fi

@ () { echo "$HI# $@$LO"; }
. () { echo "$BD"'$' "$@""$LO"; "$@" 2>&1; if [ $? != 0 ]; then echo ERROR; exit 1; fi; }
: () { echo "$BD"'$' "$@""$LO"; "$@" 2>&1; }

cat() {
  egrep -v '^[/ ]\*' $@         # No need to display legal notices
}

set +o hashall                  # To make the bin.nojava subterfuge work

@ "Hmm, do we have java on this machine?"
: java -version
@ "Oops, no ... let's see if we have some packages we can install"
. ls -l pkgs/jdk.{boot,base}*
@ "Let's try those:"
. $SU dpkg -i pkgs/jdk.{boot,base}*
. java -version
@ "Ah, much better -- exactly which modules did that install?"
. jmod ls
@ "Good -- boot and base, as expected"

@ "Now let's run the primordial test program:"
. cat src/org/hello/Main.java
. java -cp classes org.hello.Main

@ "Good, that worked -- how about a simple Swing program?"
. cat src/org/hello/swing/Main.java
: java -cp classes org.hello.swing.Main
@ "Uh-oh, looks like we don't have the AWT classes,"
@ "so let's install the AWT and Swing modules:"
. ls -l pkgs/jdk.{awt,swing}*
. $SU dpkg -i pkgs/jdk.{awt,swing}*
. jmod ls
@ 'Okay, try again (press the "Good-bye" button when window appears)'
. java -cp classes org.hello.swing.Main
@ "Success!"

@ "Now let's take a look at how to modularize the hello-world program"
@ "We'll need a simple module-info file:"
. cat src/module-info.java
@ "Just compile it, along with the main class:"
: javac -d classes src/module-info.java src/org/hello/Main.java
@ "Oops, we need javac, so let's install the tools module:"
. $SU dpkg -i pkgs/jdk.tools*
. javac -d classes src/module-info.java src/org/hello/Main.java
@ "Okay, we've compiled the modularized hello-world code;"
@ "now we can create a Debian package for it:"
. jpkg -m classes -d . -c hello deb org.hello
. ls -l org.hello*.deb
@ "Install that package:"
. $SU dpkg -i org.hello*.deb
@ 'Now we can run the "hello" module directly:'
. java -m org.hello
@ "More conveniently, we can run the command that jpkg created for us:"
. which hello
. hello

@ "The End"
@ "Thank you for viewing this basic demo of Project Jigsaw"
