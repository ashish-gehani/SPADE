# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


# DIST included to enable maintainer targets like maintainer-clean-local.
DIST_SUBDIRS = subpackage/java

# For general build targets.
SUBDIRS = subpackage/java

if HOST_OS_MAC
DIST_SUBDIRS += subpackage/mac
SUBDIRS += subpackage/mac
endif

if HOST_OS_LINUX
DIST_SUBDIRS += subpackage/linux
SUBDIRS += subpackage/linux
endif

setup:
	cd "$(SPADE_ROOT)" && bin/manage-neo4j.sh install
	cd "$(SPADE_ROOT)" && bin/keys/generatekeys.sh

.PHONY: setup

all-local: setup

clean-local:
	rm -rf "$(SPADE_ROOT)/cfg/ssl"
	rm -rf "$(SPADE_ROOT)/log"
	rm -rf "$(SPADE_ROOT)/tmp"

maintainer-clean-local:
	rm -rf aclocal.m4 \
	       autom4te.cache \
	       build-aux \
	       confdefs.h \
	       conftest.* \
	       configure \
	       configure~ \
	       Makefile.in
