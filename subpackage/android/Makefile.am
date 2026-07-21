# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

BUILD_DIR                               = "build"
BUILD_JAR_NAME                          = "android-spade.jar"
OUTPUT_BUILD_JAR_PATH                   = "$(BUILD_DIR)/$(BUILD_JAR_NAME)"
INPUT_SPADE_PACKAGE_JAR_PATH            = "$(SPADE_PACKAGE_JAR_PATH)"
OUTPUT_SPADE_PACKAGE_ANDROID_JAR_PATH   = "$(SPADE_PACKAGE_ANDROID_JAR_PATH)"

all-local:
	@$(MKDIR_P) "$(BUILD_DIR)"; \
	$(DX) --dex --verbose --no-strict \
		--output="$(OUTPUT_BUILD_JAR_PATH)" \
		"$(INPUT_SPADE_PACKAGE_JAR_PATH)"; \
	cp "$(OUTPUT_BUILD_JAR_PATH)" \
		"$(OUTPUT_SPADE_PACKAGE_ANDROID_JAR_PATH)"

clean-local:
	rm -rf "$(BUILD_DIR)"
	rm -f "$(OUTPUT_SPADE_PACKAGE_ANDROID_JAR_PATH)"

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf aclocal.m4 \
	       autom4te.cache \
	       build-aux \
	       configure \
	       Makefile.in
