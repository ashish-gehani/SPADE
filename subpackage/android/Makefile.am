# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

BUILD_DIR                               = "build"
BUILD_JAR_NAME                          = "android-spade.jar"
BUILD_JAR_FILE                          = "$(BUILD_DIR)/$(BUILD_JAR_NAME)"
INPUT_SPADE_PACKAGE_JAR_FILE            = "$(SPADE_PACKAGE_SPADE_JAR_FILE)"
OUTPUT_SPADE_PACKAGE_ANDROID_JAR_FILE   = "$(SPADE_PACKAGE_LIB_DIR)/$(SPADE_PACKAGE_ANDROID_JAR_NAME)"

all-local:
	@$(MKDIR_P) "$(BUILD_DIR)"; \
	$(DX) --dex --verbose --no-strict \
		--output="$(BUILD_JAR_FILE)" \
		"$(INPUT_SPADE_PACKAGE_JAR_FILE)"; \
	cp "$(BUILD_JAR_FILE)" \
		"$(OUTPUT_SPADE_PACKAGE_ANDROID_JAR_FILE)"

clean-local:
	rm -rf "$(BUILD_DIR)"
	rm -f "$(OUTPUT_SPADE_PACKAGE_ANDROID_JAR_FILE)"

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf aclocal.m4 \
	       autom4te.cache \
	       build-aux \
	       configure \
	       Makefile.in
