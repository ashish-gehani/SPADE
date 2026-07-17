# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

ANDROID_SPADE_JAR	= android-spade.jar

all-local:
	@$(MKDIR_P) "$(SPADE_ANDROID_BUILD_DIR)"; \
	$(DX) --dex --verbose --no-strict \
		--output="$(SPADE_ANDROID_BUILD_DIR)/$(ANDROID_SPADE_JAR)" \
		"$(SPADE_JAVA_BUILD_DIR)"

install-exec-local:
	$(MKDIR_P) "$(SPADE_ANDROID_LIB_DIR)"
	$(INSTALL_DATA) "$(SPADE_ANDROID_BUILD_DIR)/$(ANDROID_SPADE_JAR)" \
		"$(SPADE_ANDROID_LIB_DIR)/$(ANDROID_SPADE_JAR)"

uninstall-local:
	rm -f "$(SPADE_ANDROID_LIB_DIR)/$(ANDROID_SPADE_JAR)"

clean-local:
	rm -f "$(SPADE_ANDROID_BUILD_DIR)/$(ANDROID_SPADE_JAR)"

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf aclocal.m4 \
	       autom4te.cache \
	       build-aux \
	       configure \
	       Makefile.in
