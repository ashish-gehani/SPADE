# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

DIST_SUBDIRS = 
SUBDIRS = 

if HAVE_FUSE
DIST_SUBDIRS += fuse
SUBDIRS += fuse
endif

if HAVE_LLVM
DIST_SUBDIRS += llvm
SUBDIRS += llvm
endif

if HAVE_OPENBSM
DIST_SUBDIRS += openbsm
SUBDIRS += openbsm
endif

maintainer-clean-local:
	rm -rf aclocal.m4 \
	       autom4te.cache \
	       build-aux \
	       configure \
	       configure~ \
	       conftest.* \
	       Makefile.in
