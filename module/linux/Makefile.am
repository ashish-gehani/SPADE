# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

DIST_SUBDIRS = audit_bridge kernel_module
SUBDIRS = audit_bridge kernel_module
export KERNEL_MODULES

if HAVE_FUSE
DIST_SUBDIRS += fuse
SUBDIRS += fuse
endif

if HAVE_LLVM
DIST_SUBDIRS += llvm
SUBDIRS += llvm
endif

maintainer-clean-local:
	rm -rf configure Makefile.in aclocal.m4 build-aux autom4te.cache conftest.*
