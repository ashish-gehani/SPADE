# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

FUSE_BINARY         			= libLinuxFUSE.so
FUSE_C_SRC          			= $(SPADE_ROOT)/src/spade/reporter/libLinuxFUSE.c
FUSE_INSTALL_LIBDIR 			= $(DESTDIR)$(prefix)/lib

all-local:
	@$(MKDIR_P) "$(SPADE_FUSE_LIB_DIR)"
	$(CC) -fPIC -shared \
		-Wl,-soname,$(FUSE_BINARY) \
		-I"$(JAVA_HOME)/include" \
		-I"$(JAVA_HOME)/include/linux" \
		-I"$(SPADE_JAVA_BUILD_NATIVE_INCLUDE_DIR)" \
		-Wall \
		"$(FUSE_C_SRC)" \
		$(FUSE_CFLAGS) \
		-o "$(SPADE_FUSE_LIB_DIR)/$(FUSE_BINARY)"

install-exec-local:
	$(MKDIR_P) "$(FUSE_INSTALL_LIBDIR)"
	$(INSTALL_DATA) "$(SPADE_FUSE_LIB_DIR)/$(FUSE_BINARY)" \
		"$(FUSE_INSTALL_LIBDIR)/$(FUSE_BINARY)"

uninstall-local:
	rm -f "$(FUSE_INSTALL_LIBDIR)/$(FUSE_BINARY)"

clean-local:
	rm -f "$(SPADE_FUSE_LIB_DIR)/$(FUSE_BINARY)"

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf aclocal.m4 \
	       autom4te.cache \
	       build-aux \
	       configure \
	       Makefile.in
