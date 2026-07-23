# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

SRC_DIR = src
SRC_C_MAIN_FILE = $(SRC_DIR)/libLinuxFUSE.c

BUILD_DIR                                    = build
BUILD_SHARED_LIB_NAME                        = libLinuxFUSE.so
BUILD_SHARED_LIB_FILE                        = $(BUILD_DIR)/$(BUILD_SHARED_LIB_NAME)
INPUT_SPADE_PACKAGE_HEADER_INCLUDE_JAVA_DIR  = $(SPADE_PACKAGE_HEADER_INCLUDE_JAVA_DIR)
OUTPUT_SPADE_PACKAGE_FUSE_SHARED_LIB_FILE    = $(SPADE_PACKAGE_LIB_DIR)/$(SPADE_PACKAGE_LINUX_FUSE_SHARED_LIB_NAME)

all-local:
	@$(MKDIR_P) "$(BUILD_DIR)"
	$(CC) -fPIC -shared \
		-Wl,-soname,$(BUILD_SHARED_LIB_NAME) \
		-I"$(JAVA_HOME)/include" \
		-I"$(JAVA_HOME)/include/linux" \
		-I"$(INPUT_SPADE_PACKAGE_HEADER_INCLUDE_JAVA_DIR)" \
		-Wall \
		"$(SRC_C_MAIN_FILE)" \
		$(FUSE_CFLAGS) \
		-o "$(BUILD_SHARED_LIB_FILE)"
	cp "$(BUILD_SHARED_LIB_FILE)" "$(OUTPUT_SPADE_PACKAGE_FUSE_SHARED_LIB_FILE)"

clean-local:
	rm -rf "$(BUILD_DIR)"
	rm -f "$(OUTPUT_SPADE_PACKAGE_FUSE_SHARED_LIB_FILE)"

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf aclocal.m4 \
	       autom4te.cache \
	       build-aux \
	       configure \
	       Makefile.in
