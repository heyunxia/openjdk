#
# Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

BUILDDIR = ..
include $(BUILDDIR)/common/Defs.gmk

#
# Modularizing the JDK
# - Post jdk build process until the source tree is restructured
#   for modules build
# - <outputdir>/modules/<module> will be created for each module.
#
# Steps:
# 0. During jdk build before this makefile is invoked, classes, 
#    resource files, and other non-class files such as native libraries,
#    properties file, images, etc are created.
#
#    Non-class files are copied to <outputdir>/tmp/modules/<MODULE>
#    directory in this step to prepare for the post-build modularization. 
#
#    The MODULE variable defined in other makefiles specifies 
#    the lowest-level module that the non-class files belong to.
#    The name might or might not be the same as the name of the modules 
#    in the resulting <outputdir>/modules directory.
#    
# 1. Unpack all jars in the <builddir>/lib directory to a temporary 
#    location (<outputdir>/tmp/modules/classes) to prepare for modules
#    creation.
#
# 2. Run ClassAnalyzer tool to analyze all jdk classes and generate
#    class list for all modules and also perform dependency analysis. 
#
#    Input configuration files :-
#
#    modules.config : defines the low-level modules and specifies 
#       what classes and resource files each module includes.
#    modules.group  : defines the module groups and its members.
#    jdk7.depconfig : lists the dynamic dependencies including
#       use of reflection Class.forName and JNI FindClass and
#       service provider.
#    optional.depconfig : lists the optional dependencies
#
# 3. Create one directory for each module (<outputdir>/modules/<module>)
#    based on the output files from (2).
#       
#    modules.list lists the modules to be created for the modules
#    build and its members. For each module (m) in modules.list,
#    a. copy classes listed in $m.classlist and $m.resources to
#       <outputdir>/modules/$m/classes and <outputdir>/modules/$m/resources
#       respectively.
#    b. copy all other files from its members (such as bin, lib, include)
#       to <outputdir>/modules/$m.
#    c. move module-info.class from modules/$m directory to 
#       modules/$m/classes. Default module-info destination for javac 
#       multi-module path is <modulepath>/<module>.
#       

TMP = $(ABS_MODULES_TEMPDIR)
MODULE_CLASSLIST = $(ABS_MODULES_TEMPDIR)/classlist
MODULE_CLASSES = $(ABS_MODULES_TEMPDIR)/classes
MODULES_LIST = $(MODULE_CLASSLIST)/modules.list
MODULEINFO_SRC = $(MODULES_TEMPDIR)/src
JIGSAW_MODULE_LIB = $(ABS_OUTPUTDIR)/lib/modules

# FIXME: need to determine at build time
MODULE_VERSION = 7-ea 

all:: unpack-jars gen-classlist modularize install-modules

JAR_LIST := $(shell $(FIND) $(ABS_OUTPUTDIR)/lib -name \*.jar -print) 
unpack-jars:
	@$(ECHO) ">>>Making "$@" @ `$(DATE)` ..."
	$(RM) -rf $(MODULE_CLASSES)
	$(MKDIR) -p $(MODULE_CLASSES)
	$(CP) -rf $(CLASSBINDIR)/* $(MODULE_CLASSES)
	@for jf in  $(JAR_LIST) ; do \
           if [ -f $$jf ] ; then \
	       $(CD) $(MODULE_CLASSES) && $(BOOT_JAR_CMD) xf $$jf $(BOOT_JAR_JFLAGS); \
           fi ; \
	done
	@$(ECHO) ">>>Finished making "$@" @ `$(DATE)` ..."

gen-classlist:
	$(CD) tools && $(MAKE) all

modularize:
	@$(ECHO) ">>>Making "$@" @ `$(DATE)` ..."
	$(RM) -rf $(MODULES_DIR)
	@$(RM) -rf $(JIGSAW_MODULE_LIB)
	@$(MKDIR) -p $(MODULES_DIR)
	@# Compile module-info.java for all modules
	$(BINDIR)/javac $(JAVACFLAGS) -d $(MODULES_DIR) \
            -Xbootclasspath:$(MODULE_CLASSES)\
	    -modulepath $(MODULES_DIR) \
            -sourcepath $(MODULEINFO_SRC) \
            $(MODULEINFO_SRC)/*/module-info.java
	@# Create modules listed in the modules.list output by the ClassAnalyzer
	@# 1. Merge all non-class and non-resource files from module members
	@#    to modules/$m
	@# 2. Copy all class files into modules/$m/classes
	@# 3. Copy all resource files into modules/$m/resources
	@# 4. Move module-info.class from modules/$m directory to 
	@#    modules/$m/classes. Default module-info destination for 
	@#    javac multi-module path is <modulepath>/<module>.
	@#    jmod only takes a directory with classes only.  
	@#    What should be the modules directory hierachy for packaging?
	$(CD) $(MODULE_CLASSES) && \
	for m in `$(NAWK) '{print $$1}' $(MODULES_LIST)` ; do \
   	   $(ECHO) "Creating module $$m" ; \
	   mlib=$(MODULES_DIR)/$$m ; \
           $(MKDIR) -p $$mlib ; \
	   for s in `$(GREP) "^$$m" $(MODULES_LIST)` ; do \
               if [ -d $(TMP)/$$s ] ; then \
               	   $(CP) -rf $(TMP)/$$s/*  $$mlib; \
		   $(RM) -rf $$mlib/classes; \
	       fi \
	   done ; \
           $(MKDIR) -p $$mlib/classes ; \
	   $(MV) $$mlib/module-info.class $$mlib/classes ; \
	   if [ -f $(MODULE_CLASSLIST)/$$m.classlist ] ; then \
   	       $(SED) -e 's%\\%\/%g' < $(MODULE_CLASSLIST)/$$m.classlist \
	           | $(CPIO) -pdum $$mlib/classes ; \
           fi ; \
	   if [ -f $(MODULE_CLASSLIST)/$$m.resources ] ; then \
               $(MKDIR) -p $$mlib/resources ; \
   	       $(SED) -e 's%\\%\/%g' < $(MODULE_CLASSLIST)/$$m.resources \
	           | $(CPIO) -pdum $$mlib/resources ; \
           fi ; \
        done
	@$(CD) $(MODULE_CLASSES) && $(java-vm-cleanup)
	@$(ECHO) ">>>Finished making "$@" @ `$(DATE)` ..."

install-modules:
	@$(ECHO) ">>>Making "$@" @ `$(DATE)` ..."
	$(RM) -rf $(JIGSAW_MODULE_LIB)
	@$(BINDIR)/jmod create -N
	$(CD) $(MODULES_DIR) && \
	for m in `$(NAWK) '{print $$1}' $(MODULES_LIST)` ; do \
   	   $(ECHO) "Installing module $$m" ; \
	   if [ -f $(MODULE_CLASSLIST)/$$m.resources ] ; then \
	       $(BINDIR)/jmod install $$m/classes -r $$m/resources $$m || exit 1; \
           else \
	       $(BINDIR)/jmod install $$m/classes $$m || exit 1; \
	   fi ; \
        done
	@$(ECHO) ">>>Finished making "$@" @ `$(DATE)` ..."

clean clobber::
	$(RM) -rf $(MODULE_CLASSLIST)
	$(RM) -rf $(MODULES_DIR)
	$(RM) -rf $(MODULE_LIB)
