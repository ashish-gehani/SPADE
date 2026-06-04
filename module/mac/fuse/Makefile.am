# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

SPADE_BUILD_NATIVE_INCLUDE_DIR = $(SPADE_ROOT)/build/native/include
FUSE_BUILD_DIR      = @FUSE_BUILD_DIR@
FUSE_BINARY         = libMacFUSE.jnilib
FUSE_C_SRC          = $(SPADE_ROOT)/src/spade/reporter/libMacFUSE.c
FUSE_INSTALL_LIBDIR = $(DESTDIR)$(prefix)/lib

all-local:
	@$(MKDIR_P) "$(FUSE_BUILD_DIR)"
	$(CC) -dynamiclib \
		-I"$(JAVA_HOME)/include" \
		-I"$(JAVA_HOME)/include/darwin" \
		-I"$(SPADE_BUILD_NATIVE_INCLUDE_DIR)" \
		-Wall \
		"$(FUSE_C_SRC)" \
		$(FUSE_CFLAGS) \
		-o "$(FUSE_BUILD_DIR)/$(FUSE_BINARY)"

install-exec-local:
	$(MKDIR_P) "$(FUSE_INSTALL_LIBDIR)"
	$(INSTALL_DATA) "$(FUSE_BUILD_DIR)/$(FUSE_BINARY)" \
		"$(FUSE_INSTALL_LIBDIR)/$(FUSE_BINARY)"

uninstall-local:
	rm -f "$(FUSE_INSTALL_LIBDIR)/$(FUSE_BINARY)"

clean-local:
	rm -f "$(FUSE_BUILD_DIR)/$(FUSE_BINARY)"

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf configure Makefile.in aclocal.m4 build-aux autom4te.cache
