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

BUILDDIR=..
include $(BUILDDIR)/common/Defs.gmk

SRC = $(SHARE_SRC)/modules
BIN = $(BINDIR)
CLASSES = $(CLASSBINDIR)
TMP=$(TEMPDIR)/jigsaw
MLIB = $(LIBDIR)/modules
PKGS = $(OUTPUTDIR)/jigsaw-pkgs

MODULES = boot base awt swing

all: mods pkgs

# For faster builds, during development only
dev-%: ; MODULARIZE_DEV=1 $(MAKE) --no-print-directory $*

mods:
	SRC=$(SRC) BIN=$(BIN) CLASSES=$(CLASSES) TMP=$(TMP) MLIB=$(MLIB) \
	JIGSAW_TRACE=3 sh modularize.sh $(MODULES)

%-image:
	OUTPUTDIR=$(OUTPUTDIR) TMP=$(TMP) sh imagine.sh $*

images: $(MODULES:%=%-image) jdk-image

$(PKGS): ; mkdir -p $@

# The boot package gets special treatment,
# both here and in {imagine,modularize}.sh
#
boot-deb: boot-image $(PKGS)
	fakeroot dpkg-deb -Zlzma --build \
	  $(OUTPUTDIR)/jigsaw-images/boot $(PKGS)

%-deb: %-image $(PKGS)
	mkdir -p $(OUTPUTDIR)/jigsaw-images/$*
	(M=$$(if [ $* != jdk ]; then echo jdk.$*; else echo jdk; fi); \
	 PATH=$(BINDIR):$(PATH) $(BINDIR)/jpkg -v -m $(TMP)/module-classes \
	   -i $(OUTPUTDIR)/jigsaw-images/$* \
           -L /usr/local/jigsaw/lib/modules -d $(PKGS) -c jigsaw-jdk-$* deb $$M)

debs: $(MODULES:%=%-deb)

all-debs: debs jdk-deb

pkgs: all-debs

clean-image:
	rm -rf $(OUTPUTDIR)/jigsaw-images

clean-pkgs: clean-image
	rm -rf $(OUTPUTDIR)/jigsaw-pkgs

clean: clean-pkgs
	rm -rf $(MLIB) $(TMP)
