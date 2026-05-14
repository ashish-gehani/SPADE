# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

DIST_SUBDIRS = module/java
SUBDIRS = module/java

if PLATFORM_MAC
DIST_SUBDIRS += module/mac
SUBDIRS += module/mac
else
DIST_SUBDIRS += module/linux
SUBDIRS += module/linux
endif

setup:
	pushd "$(SPADE_ROOT)" && { bin/manage-neo4j.sh install; ret=$$?; popd; exit $$ret; }
	$(SPADE_ROOT)/bin/keys/generatekeys.sh

.PHONY: setup


all-local: setup


clean-local:
	rm -rf "$(SPADE_ROOT)/cfg/ssl"
	rm -rf "$(SPADE_ROOT)/log"
	rm -rf "$(SPADE_ROOT)/tmp"

maintainer-clean-local:
	rm -rf configure configure~ Makefile.in aclocal.m4 build-aux autom4te.cache conftest.* condefs.h
