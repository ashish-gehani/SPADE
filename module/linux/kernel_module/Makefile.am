# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

KERNEL_MODULE_SRC_DIR         = $(SPADE_SRC_DIR)/spade/reporter/audit/kernel-modules
KERNEL_MODULE_BUILD_DIR       = build
KERNEL_MODULE_INSTALL_STAGING = $(KERNEL_MODULE_BUILD_DIR)/install
KERNEL_MODULE_INSTALL_DIR     = $(DESTDIR)$(prefix)/lib/kernel-modules

all-local:
	@$(MKDIR_P) "$(KERNEL_MODULE_BUILD_DIR)"
	$(MAKE) --no-print-directory -C "$(KERNEL_MODULE_SRC_DIR)" \
		BUILD_DIR="$(CURDIR)/$(KERNEL_MODULE_BUILD_DIR)" \
		INSTALL_DIR="$(CURDIR)/$(KERNEL_MODULE_INSTALL_STAGING)" \
		$(if $(KDIR),KDIR="$(KDIR)") \
		$(if $(MOD_DEFINES),MOD_DEFINES="$(MOD_DEFINES)") \
		@KERNEL_MODULE_TARGET@ install

install-exec-local:
	$(MKDIR_P) "$(KERNEL_MODULE_INSTALL_DIR)"
	cp -rp "$(KERNEL_MODULE_INSTALL_STAGING)/." "$(KERNEL_MODULE_INSTALL_DIR)/"

uninstall-local:
	rm -rf "$(KERNEL_MODULE_INSTALL_DIR)"

clean-local:
	$(MAKE) --no-print-directory -C "$(KERNEL_MODULE_SRC_DIR)" \
		BUILD_DIR="$(CURDIR)/$(KERNEL_MODULE_BUILD_DIR)" \
		INSTALL_DIR="$(CURDIR)/$(KERNEL_MODULE_INSTALL_STAGING)" \
		$(if $(KDIR),KDIR="$(KDIR)") \
		clean
	rm -rf "$(KERNEL_MODULE_BUILD_DIR)"

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf configure Makefile.in aclocal.m4 build-aux autom4te.cache
