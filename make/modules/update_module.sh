#!/bin/sh

#
# Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Sun designates this
# particular file as subject to the "Classpath" exception as provided
# by Sun in the LICENSE file that accompanied this code.
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

#
# A script to assist incremental jdk development to update a specified
# module in the module library of the build outputdir or a specified
# module image (such as jdk-module-image, jdk-base-image, etc).
# 
# You can find out which module a class or resource file belongs 
# from the classlist files generated by the class analyzer tool:
#   $outputdir/moduleinfo/classlist/*.classlist
#   $outputdir/moduleinfo/classlist/*.resources
#
# Details:
# 
# After the jdk is built, a module library, $outputdir/lib/modules, 
# with jdk modules preinstalled will be created.
#
# When classes are modified and recompiled, an additional step is 
# required to update the module library; otherwise, module mode 
# will not run with the latest recompiled classes.  
#
# To modularize the jdk and recreate the module library:
#   cd jdk/make/modules; make all
#
# To avoid reinstalling all jdk modules, you can use this script
# to update one or more modules.
#
# Examples:
# Reinstall jdk.boot module in the build outputdir 
#    $ update_module jdk.boot

# Reinstall jdk.boot module in the build outputdir and the jdk-module-image
#    $ update_module -image jdk-module-image jdk.boot
#
# Reinstall javac module in a specified jdkhome from the langtools buildarea
#    $ update_module -jdkhome mymoduleimage -classes langtools/build/classes javac
#

do_usage() {
  printf "Usage: $0 [module name]+\n"
  printf "   options\n"
  printf "     -builddir : build outputdir\n"
  printf "     -image    : module image name (e.g. jdk-module-image)\n"
  printf "     -jdkhome  : jdk module image to be updated\n"
  printf "                 If specified, only its module library is updated\n"
  printf "     -classes  : directory from which classes are copied\n"
  printf "                 Must be used with -jdkhome\n"
  printf "     -d64      : 64-bit build\n"
  printf "     -h        : this help message\n"
  printf "   \n"
  printf "   Note: By default it will update the module library\n"
  printf "         in the build outputdir.\n"
  exit 1
}

if [ $# -eq 0 ] ; then
   do_usage;
   exit 1
fi

#
# Directory build outputdir directory is under the top forest repo
#
builddir=
image=
classesdir=
jdkhome=
quick=0
lp64=0
modules=
for i in $*; do
   case $i in
        -builddir)  builddir="$2"; shift 2;;
        -image)     image="$2"; shift 2;;
        -jdkhome)   jdkhome="$2"; shift 2;;
        -classes)   classesdir="$2"; shift 2;;
        -d64)       lp64=1; shift;;
        -h)         do_usage ; break ;;
        --)         shift; break;;
   esac
done

modules=$@

ARCH=i586
OS=`uname -s`
case "$OS" in
  SunOS )
    PLATFORM=solaris
    proc=`uname -p`
    case "$proc" in 
       i[3-9]86) 
           ARCH=i586
           ;;
       sparc*)
           ARCH=sparc
           ;;
       *)
           ARCH=$proc
           ;;
    esac
    ;;
  Linux )
    PLATFORM=linux
    mach=`uname -m`
    case "$mach" in
        i[3-9]86)
            ARCH=i586
            ;;
        ia64)
            ARCH=ia64
            ;;
        x86_64)
            ARCH=amd64
            ;;
        sparc*)
            ARCH=sparc
            ;;
        arm*)
            ARCH=arm
            ;;
        *)
            ARCH=$mach
            ;;
      esac
    ;;
  Windows* )
    PLATFORM=windows
    ;;
  CYGWIN* )
    PLATFORM=windows
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

if [ ${lp64} = 1 -a ${PLATFORM} = "solaris" ] ; then
    if [ ${ARCH} = "sparc" ] ; then 
        ARCH=sparcv9
    else
        ARCH=`isainfo -n`
    fi
fi

# set default build output dir to the top forest build directory
if [ "x${builddir}" = x ] ; then
    builddir=`dirname $0`/../../../build/${PLATFORM}-${ARCH}
    echo "Build outputdir : ${builddir}" 
fi


if [ ! -d ${builddir} ] ; then
   echo "Build outputdir $builddir doesn't exist"
   do_usage
   exit 1
fi

if [ "x${jdkhome}" != x ] ; then
   if [ ! -d ${jdkhome} ] ; then
      echo "JDK home ${jdkhome} doesn't exist"
      do_usage
      exit 1
   fi
fi

if [ "x${classesdir}" != x ] ; then
   if [ ! -d ${classesdir} ] ; then
      echo "Class directory $classesdir doesn't exist"
      do_usage
      exit 1
   fi
fi

if [ "x${classesdir}" != x -a "x${jdkhome}" = x ] ; then
   echo "-jdkhome must be set"
   do_usage
   exit 1
fi

if [ "x${jdkhome}" != x -a "x${image}" != x ] ; then
   echo "Both -jdkhome and -image set is not supported"
   do_usage
   exit 1
fi

abs_builddir=`cd $builddir; pwd`

module_image_dir=${abs_builddir}/${image}
if [ "x${image}" != x -a ! -d ${module_image_dir} ] ; then
   echo "Module image ${image} doesn't exist"
   do_usage
   exit 1
fi

if [ "x${modules}" = x ] ; then
   echo "Must specify a module"
   do_usage;
   exit 1
fi

submodule_dir=${abs_builddir}/submodules
classlist_dir=${abs_builddir}/moduleinfo/classlist
modules_list=${classlist_dir}/modules.list

copy_module() {
   m=$1
   mroot=$2
   classes=$3
   mcontent=${abs_builddir}/modules/$m
   if [ ! -d ${mcontent} ] ; then
      echo "module $m doesn't exist"
      exit 1
   fi

   echo "Syncing module $m in the build outputdir"
   for s in `grep "^$m " ${modules_list}` ; do 
      if [ -d ${submodule_dir}/$s ] ; then 
          for d in bin lib etc include ; do 
              if [ -d ${submodule_dir}/$s/$d ] ; then 
                  cp -rf ${submodule_dir}/$s/$d  ${mcontent}
              fi ;
          done 
      fi ;
   done ;
   
   cd ${classes}
   if [ -f ${classlist_dir}/$m.classlist ] ; then 
      sed -e 's%\\%\/%g' < ${classlist_dir}/$m.classlist \
                      | cpio -pdum ${mcontent}/classes 
   fi
   if [ -f ${classlist_dir}/$m.resources ] ; then 
      sed -e 's%\\%\/%g' < ${classlist_dir}/$m.resources \
                      | cpio -pdum ${mcontent}/resources
   fi
}

reinstall_module() {
   m=$1
   mroot=$2
   classes=$3
   mlib=$mroot/lib/modules
   mcontent=${abs_builddir}/modules/$m

   JMOD_OPTION=
   if [ -f ${classlist_dir}/$m.resources ] ; then 
      JMOD_OPTION="-r ${mcontent}/resources"
   fi

   echo "Reinstalling module $m in $4"
   rm -rf ${mlib}/$m
   cd ${mcontent}
   ${abs_builddir}/bin/jmod -L ${mlib} install classes ${JMOD_OPTION} $m || exit 1 
}

quick_update() {
   m=$1
   mroot=$2
   classes=$3
   mlib=$mroot/lib/modules
   mcontent=${abs_builddir}/modules/$m
   if [ ! -d ${mlib} ] ; then
      echo "module $m doesn't exist in ${mroot}"
      exit 1
   fi

   cd ${classes}
   if [ -f ${classlist_dir}/$m.classlist ] ; then 
       filelist=`cat ${classlist_dir}/$m.classlist`
       ${mroot}/bin/jar uf $mlib/$m/7-ea/classes $filelist
   fi
   if [ -f ${classlist_dir}/$m.resources ] ; then 
       filelist=`cat ${classlist_dir}/$m.resources`
       ${mroot}/bin/jar uf $mlib/$m/7-ea/classes $filelist
   fi

   echo "Reindexing module $m in $4"
   ${abs_builddir}/bin/jmod -L ${mlib} reindex $m@7-ea || exit 1 
}

update() {
   if [ ${quick} = 0 ] ; then
       reinstall_module $@
   else
       quick_update $@
   fi
}

for m in ${modules} ; do
   if [ "x${jdkhome}" = x ] ; then
       # update the module content in the build directory once
       copy_module $m ${abs_builddir} ${abs_builddir}/classes ${abs_builddir}
       update $m ${abs_builddir} ${abs_builddir}/classes "the build outputdir"
       if [ "x${image}" != x ] ; then
           cp -r ${abs_builddir}/lib/modules/$m ${module_image_dir}/lib/modules
       fi
   else 
       mroot=${jdkhome}
       dest=${jdkhome}
       classes=${abs_builddir}/classes
       if [ "x${classesdir}" != x ] ; then
           classes=${classesdir}
       fi
       copy_module $m ${abs_builddir} ${classes} ${abs_builddir}
       update $m ${mroot} ${classes} ${mroot} ${dest}
   fi
done
