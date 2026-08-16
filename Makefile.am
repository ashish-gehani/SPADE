# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


# DIST included to enable maintainer targets like maintainer-clean-local.
DIST_SUBDIRS = pkg/java

# For general build targets.
SUBDIRS = pkg/java

if HOST_OS_MAC
DIST_SUBDIRS += pkg/mac
SUBDIRS += pkg/mac
endif

if HOST_OS_LINUX
DIST_SUBDIRS += pkg/linux
SUBDIRS += pkg/linux
endif

# pkg/java needs the keys generatekeys.sh produces to run its tests during build,
# so setup must run before package recursion, not just before all-local.
# For future additions that must also run before any package is built, add them to the
# setup target itself -- do not add more targets to BUILT_SOURCES directly.
BUILT_SOURCES = setup

setup:
	cd "$(SPADE_ROOT)" && bin/manage-neo4j.sh install
	cd "$(SPADE_ROOT)" && bin/keys/generatekeys.sh

.PHONY: setup

all-local:

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
