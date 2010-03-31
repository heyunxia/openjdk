#
# Copyright 2004-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
# Makefile for building simple launchers
#

#
# make-launcher creates a launcher in dual mode when running from
# a modules image.
# By default, it will run in  module mode. You can specify 
#   -J-Xmode:legacy to force it to run in legacy mode
#   -J-Xmode:module to force it to run in module mode
#
# Each tool is packaged as a module whose name is same as the binary name.
# make/modules/modules.group contains a module definition
# that specifies its entry point for each binary.
# The launcher (java.c) will launch the tool in module mode 
# or legacy mode as how it is built.
#
# NOTE: when a new launcher is added, a module definition
# for this launcher needs to be added in make/modules/modules.group. 

BUILDDIR = ..
PACKAGE = launchers
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

# Run MAKE $@ for all generic launchers

define make-all-launchers
$(call make-launcher, appletviewer, sun.applet.Main, , )
$(call make-launcher, apt, com.sun.tools.apt.Main, , )
$(call make-launcher, extcheck, com.sun.tools.extcheck.Main, , )
$(call make-launcher, idlj, com.sun.tools.corba.se.idl.toJavaPortable.Compile, , )
$(call make-launcher, jar, sun.tools.jar.Main, , )
$(call make-launcher, pack200, com.sun.java.util.jar.pack.Driver, , --pack)
$(call make-launcher, jmod, org.openjdk.jigsaw.cli.Librarian, -J-Xmode:legacy, )
$(call make-launcher, jpkg, org.openjdk.jigsaw.cli.Packager, , )
$(call make-launcher, jarsigner, sun.security.tools.JarSigner, , )
$(call make-launcher, javac, com.sun.tools.javac.Main, -J-Xmode:legacy, )
$(call make-launcher, javadoc, com.sun.tools.javadoc.Main, -J-Xmode:legacy, )
$(call make-launcher, javah, com.sun.tools.javah.Main, -J-Xmode:legacy, )
$(call make-launcher, javap, com.sun.tools.javap.Main, -J-Xmode:legacy, )
$(call make-launcher, jconsole, sun.tools.jconsole.JConsole, \
  -J-Djconsole.showOutputViewer, )
$(call make-launcher, jdb, com.sun.tools.example.debug.tty.TTY, , )
$(call make-launcher, jhat, com.sun.tools.hat.Main, , )
$(call make-launcher, jinfo, sun.tools.jinfo.JInfo, \
  -J-Dsun.jvm.hotspot.debugger.useProcDebugger \
  -J-Dsun.jvm.hotspot.debugger.useWindbgDebugger, )
$(call make-launcher, jmap, sun.tools.jmap.JMap, \
  -J-Dsun.jvm.hotspot.debugger.useProcDebugger \
  -J-Dsun.jvm.hotspot.debugger.useWindbgDebugger, )
$(call make-launcher, jps, sun.tools.jps.Jps, , )
$(call make-launcher, jrunscript, com.sun.tools.script.shell.Main, , )
$(call make-launcher, jsadebugd, sun.jvm.hotspot.jdi.SADebugServer, , )
$(call make-launcher, jstack, sun.tools.jstack.JStack, \
  -J-Dsun.jvm.hotspot.debugger.useProcDebugger \
  -J-Dsun.jvm.hotspot.debugger.useWindbgDebugger, )
$(call make-launcher, jstat, sun.tools.jstat.Jstat, , )
$(call make-launcher, jstatd, sun.tools.jstatd.Jstatd, , )
$(call make-launcher, native2ascii, sun.tools.native2ascii.Main, , )
$(call make-launcher, rmic, sun.rmi.rmic.Main, -J-Xmode:legacy, )
$(call make-launcher, schemagen, com.sun.tools.internal.jxc.SchemaGenerator, , )
$(call make-launcher, serialver, sun.tools.serialver.SerialVer, , )
$(call make-launcher, xjc, com.sun.tools.internal.xjc.Driver, , )
$(call make-launcher, wsgen, com.sun.tools.internal.ws.WsGen, , )
$(call make-launcher, wsimport, com.sun.tools.internal.ws.WsImport, , )
$(call make-launcher, orbd, com.sun.corba.se.impl.activation.ORBD, \
  -J-Dcom.sun.CORBA.activation.DbDir=./orb.db \
  -J-Dcom.sun.CORBA.activation.Port=1049 \
  -J-Dcom.sun.CORBA.POA.ORBServerId=1, )
$(call make-launcher, servertool, com.sun.corba.se.impl.activation.ServerTool, , )
$(call make-launcher, tnameserv, com.sun.corba.se.impl.naming.cosnaming.TransientNameServer, \
  -J-Dcom.sun.CORBA.activation.DbDir=./orb.db \
  -J-Djava.util.logging.LoggingPermission=contol \
  -J-Dcom.sun.CORBA.POA.ORBServerId=1, )
endef

build:
	$(make-all-launchers)

clean clobber::
	$(make-all-launchers)

include $(BUILDDIR)/common/Rules.gmk
