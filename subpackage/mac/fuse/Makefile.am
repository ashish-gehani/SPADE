# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

SRC_DIR                                      = src
SRC_C_MAIN_PATH                              = $(SRC_DIR)/libMacFUSE.c

BUILD_DIR                                    = build
BUILD_SHARED_LIB_NAME                        = libMacFUSE.jnilib
BUILD_SHARED_LIB_PATH                        = $(BUILD_DIR)/$(BUILD_SHARED_LIB_NAME)
INPUT_SPADE_PACKAGE_HEADER_INCLUDE_JAVA_DIR  = $(SPADE_PACKAGE_HEADER_INCLUDE_JAVA_DIR)
OUTPUT_SPADE_PACKAGE_FUSE_SHARED_LIB_PATH    = $(SPADE_PACKAGE_FUSE_SHARED_LIB_DIR)/$(SPADE_PACKAGE_FUSE_SHARED_LIB_NAME)

all-local:
	@$(MKDIR_P) "$(BUILD_DIR)"
	$(CC) -dynamiclib \
		-I"$(JAVA_HOME)/include" \
		-I"$(JAVA_HOME)/include/darwin" \
		-I"$(INPUT_SPADE_PACKAGE_HEADER_INCLUDE_JAVA_DIR)" \
		-Wall \
		"$(SRC_C_MAIN_PATH)" \
		$(FUSE_CFLAGS) \
		-o "$(BUILD_SHARED_LIB_PATH)"
	cp "$(BUILD_SHARED_LIB_PATH)" "$(OUTPUT_SPADE_PACKAGE_FUSE_SHARED_LIB_PATH)"

clean-local:
	rm -rf "$(BUILD_DIR)"
	rm -f "$(OUTPUT_SPADE_PACKAGE_FUSE_SHARED_LIB_PATH)"

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf aclocal.m4 \
	       autom4te.cache \
	       build-aux \
	       configure \
	       Makefile.in
