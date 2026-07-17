# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

KERNEL_MODULE_SRC_DIR         = $(SPADE_JAVA_SRC_DIR)/spade/reporter/audit/kernel-modules
KERNEL_MODULE_INSTALL_DIR     = $(DESTDIR)$(prefix)/lib/kernel-modules

KERNEL_MODULES = @KERNEL_MODULES@

all-local:
	@if test "$(KERNEL_MODULES)" = "true"; then \
		$(MKDIR_P) "$(SPADE_KERNEL_MODULE_LIB_DIR)"; \
		$(MAKE) --no-print-directory -C "$(KERNEL_MODULE_SRC_DIR)" \
			BUILD_DIR="$(SPADE_KERNEL_MODULE_LIB_DIR)" \
			KDIR="$(KHEADERS_PATH)" \
			$(if $(MOD_DEFINES),MOD_DEFINES="$(MOD_DEFINES)") \
			@KERNEL_MODULE_TARGET@; \
	else \
		echo "INFO: kernel module build skipped (KERNEL_MODULES != true)"; \
	fi

install-exec-local:
	@if test "$(KERNEL_MODULES)" = "true"; then \
		$(MKDIR_P) "$(KERNEL_MODULE_INSTALL_DIR)"; \
		$(MAKE) --no-print-directory -C "$(KERNEL_MODULE_SRC_DIR)" \
			BUILD_DIR="$(SPADE_KERNEL_MODULE_LIB_DIR)" \
			INSTALL_DIR="$(KERNEL_MODULE_INSTALL_DIR)" \
			KDIR="$(KHEADERS_PATH)" \
			install; \
	else \
		echo "INFO: kernel module install skipped (KERNEL_MODULES != true)"; \
	fi

uninstall-local:
	@if test "$(KERNEL_MODULES)" = "true"; then \
		rm -rf "$(KERNEL_MODULE_INSTALL_DIR)"; \
	else \
		echo "INFO: kernel module uninstall skipped (KERNEL_MODULES != true)"; \
	fi

clean-local:
	@if test "$(KERNEL_MODULES)" = "true"; then \
		if test -d "$(SPADE_KERNEL_MODULE_LIB_DIR)"; then \
			$(MAKE) --no-print-directory -C "$(KERNEL_MODULE_SRC_DIR)" \
				BUILD_DIR="$(SPADE_KERNEL_MODULE_LIB_DIR)" \
				INSTALL_DIR="$(KERNEL_MODULE_INSTALL_DIR)" \
				KDIR="$(KHEADERS_PATH)" \
				clean; \
		fi; \
	else \
		echo "INFO: kernel module clean skipped (KERNEL_MODULES != true)"; \
	fi

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf aclocal.m4 \
	       autom4te.cache \
	       build-aux \
	       configure \
	       Makefile.in
