# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


OUTPUT_SPADE_PACKAGE_KERNEL_MODULE_DIR = $(SPADE_PACKAGE_LIB_DIR)/$(SPADE_PACKAGE_KERNEL_MODULE_DIR_NAME)

KERNEL_MODULES = @KERNEL_MODULES@

all-local:
	@if test "$(KERNEL_MODULES)" = "true"; then \
		$(MAKE) -f Kbuild \
			PACKAGE_DIR=$(OUTPUT_SPADE_PACKAGE_KERNEL_MODULE_DIR) \
			@KERNEL_MODULE_TARGET@; \
		$(MAKE) -f Kbuild \
			PACKAGE_DIR=$(OUTPUT_SPADE_PACKAGE_KERNEL_MODULE_DIR) \
			package; \
	else \
		echo "INFO: kernel module build skipped (KERNEL_MODULES != true)"; \
	fi

clean-local:
	$(MAKE) \
		-f Kbuild \
		PACKAGE_DIR=$(OUTPUT_SPADE_PACKAGE_KERNEL_MODULE_DIR) \
		clean

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf aclocal.m4 \
	       autom4te.cache \
	       build-aux \
	       configure \
	       Makefile.in
