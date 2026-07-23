# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

SRC_DIR                                        = src
SRC_C_MAIN_PATH                                = $(SRC_DIR)/spadeAuditBridge.c
BUILD_DIR                                      = build
BUILD_BIN_NAME                                 = spadeAuditBridge
BUILD_BIN_PATH                                 = $(BUILD_DIR)/$(BUILD_BIN_NAME)
OUTPUT_SPADE_PACKAGE_AUDIT_BRIDGE_BIN_PATH     = $(SPADE_PACKAGE_BIN_DIR)/$(SPADE_PACKAGE_AUDIT_BRIDGE_BIN_NAME)

all-local:
	@$(MKDIR_P) "$(BUILD_DIR)"
	$(CC) -o "$(BUILD_BIN_PATH)" "$(SRC_C_MAIN_PATH)"
	cp "$(BUILD_BIN_PATH)" "$(OUTPUT_SPADE_PACKAGE_AUDIT_BRIDGE_BIN_PATH)"

clean-local:
	rm -rf "$(BUILD_DIR)"
	rm -f "$(OUTPUT_SPADE_PACKAGE_AUDIT_BRIDGE_BIN_PATH)"

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf aclocal.m4 \
	       autom4te.cache \
	       build-aux \
	       configure \
	       Makefile.in
