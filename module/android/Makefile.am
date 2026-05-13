# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

ANDROID_BUILD_DIR     = build
ANDROID_SPADE_JAR     = android-spade.jar
ANDROID_INSTALL_LIBDIR = $(DESTDIR)$(prefix)/lib

all-local:
	@$(MKDIR_P) "$(ANDROID_BUILD_DIR)"; \
	$(DX) --dex --verbose --no-strict \
		--output="$(ANDROID_BUILD_DIR)/$(ANDROID_SPADE_JAR)" \
		"$(SPADE_BUILD_DIR)/spade"

install-exec-local:
	$(MKDIR_P) "$(ANDROID_INSTALL_LIBDIR)"
	$(INSTALL_DATA) "$(ANDROID_BUILD_DIR)/$(ANDROID_SPADE_JAR)" \
		"$(ANDROID_INSTALL_LIBDIR)/$(ANDROID_SPADE_JAR)"

uninstall-local:
	rm -f "$(ANDROID_INSTALL_LIBDIR)/$(ANDROID_SPADE_JAR)"

clean-local:
	rm -rf "$(ANDROID_BUILD_DIR)"
