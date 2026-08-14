# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

DIST_SUBDIRS = audit_bridge
SUBDIRS = audit_bridge

if CONFIGURE_KERNEL_MODULES
DIST_SUBDIRS += kernel_modules
SUBDIRS += kernel_modules
endif

if HAVE_FUSE
DIST_SUBDIRS += fuse
SUBDIRS += fuse
endif

if HAVE_LLVM
DIST_SUBDIRS += llvm
SUBDIRS += llvm
endif

maintainer-clean-local:
	rm -rf aclocal.m4 \
	       autom4te.cache \
	       build-aux \
	       configure \
	       conftest.* \
	       Makefile.in
