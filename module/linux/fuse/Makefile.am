# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

FUSE_BUILD_DIR      = build
FUSE_BINARY         = libLinuxFUSE.so
FUSE_JAVA_SRC       = $(SPADE_SRC_DIR)/spade/reporter/LinuxFUSE.java
FUSE_C_SRC          = $(SPADE_SRC_DIR)/spade/reporter/libLinuxFUSE.c
FUSE_HEADER_DIR     = $(FUSE_BUILD_DIR)
FUSE_INSTALL_LIBDIR = $(DESTDIR)$(prefix)/lib

all-local:
	@$(MKDIR_P) "$(FUSE_BUILD_DIR)"
	$(JAVAC) $(JAVAC_HEADER_GEN_OPTIONS) \
		-h "$(FUSE_HEADER_DIR)" \
		"$(FUSE_JAVA_SRC)"
	$(CC) -fPIC -shared \
		-Wl,-soname,$(FUSE_BINARY) \
		-I"$(JAVA_HOME)/include" \
		-I"$(JAVA_HOME)/include/linux" \
		-I"$(FUSE_HEADER_DIR)" \
		-Wall \
		"$(FUSE_C_SRC)" \
		$(FUSE_CFLAGS) \
		-o "$(FUSE_BUILD_DIR)/$(FUSE_BINARY)"

install-exec-local:
	$(MKDIR_P) "$(FUSE_INSTALL_LIBDIR)"
	$(INSTALL_PROGRAM) "$(FUSE_BUILD_DIR)/$(FUSE_BINARY)" \
		"$(FUSE_INSTALL_LIBDIR)/$(FUSE_BINARY)"

uninstall-local:
	rm -f "$(FUSE_INSTALL_LIBDIR)/$(FUSE_BINARY)"

clean-local:
	rm -f "$(FUSE_BUILD_DIR)/$(FUSE_BINARY)"
	rm -f "$(FUSE_HEADER_DIR)"/*.h

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf configure Makefile.in aclocal.m4 build-aux autom4te.cache
