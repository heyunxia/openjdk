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

BUILD = ../../build
BIN = $(BUILD)/bin
CLASSES = $(BUILD)/classes
TMP=$(BUILD)/jigsaw
MODULE_CLASSES = $(TMP)/module-classes
RESOURCES = $(TMP)/resources
MODULES = $(BUILD)/lib/modules
SRC = ../../src/share/modules

all: $(MODULES)/jdk

$(MODULE_CLASSES)/jdk:
	mkdir -p $@
	(cd $(CLASSES); find . -name '*.class' | tar cfT - -) \
         | tar xfC - $(MODULE_CLASSES)/jdk

$(MODULE_CLASSES)/jdk/module-info.class: $(SRC)/jdk/module-info.java \
                                         $(MODULE_CLASSES)/jdk
	$(BIN)/javac -source 7 \
          -d $(MODULE_CLASSES) -modulepath $(MODULE_CLASSES) $<

$(RESOURCES):
	mkdir -p $@
	tar cfC - $(CLASSES) --exclude '*.class' . | tar xfC - $(RESOURCES)

$(MODULES):
	$(BIN)/jmod -L $(MODULES) -N create

$(MODULES)/jdk: $(RESOURCES) $(MODULE_CLASSES)/jdk/module-info.class $(MODULES)
	$(BIN)/jmod -L $(MODULES) \
          install $(MODULE_CLASSES) -r $(RESOURCES) jdk

clean:
	rm -rf $(MODULES) $(TMP)

clean-modules:
	rm -rf $(MODULES)


# Development only
#

update:
	rsync -avO --include '*/' --include '*.class' --exclude '*' \
          $(CLASSES)/ $(MODULES)/jdk/7-ea/classes
