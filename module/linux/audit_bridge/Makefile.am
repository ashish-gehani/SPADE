# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AUDIT_BRIDGE_BUILD_DIR  = build
AUDIT_BRIDGE_BINARY     = spadeAuditBridge
AUDIT_BRIDGE_C_SRC      = $(SPADE_SRC_DIR)/spade/reporter/spadeAuditBridge.c
AUDIT_BRIDGE_INSTALL_BINDIR = $(DESTDIR)$(prefix)/bin

all-local:
	@$(MKDIR_P) "$(AUDIT_BRIDGE_BUILD_DIR)"
	$(CC) -o "$(AUDIT_BRIDGE_BUILD_DIR)/$(AUDIT_BRIDGE_BINARY)" "$(AUDIT_BRIDGE_C_SRC)"

install-exec-local:
	$(MKDIR_P) "$(AUDIT_BRIDGE_INSTALL_BINDIR)"
	$(INSTALL_PROGRAM) "$(AUDIT_BRIDGE_BUILD_DIR)/$(AUDIT_BRIDGE_BINARY)" \
		"$(AUDIT_BRIDGE_INSTALL_BINDIR)/$(AUDIT_BRIDGE_BINARY)"

uninstall-local:
	rm -f "$(AUDIT_BRIDGE_INSTALL_BINDIR)/$(AUDIT_BRIDGE_BINARY)"

clean-local:
	rm -rf "$(AUDIT_BRIDGE_BUILD_DIR)"

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf configure Makefile.in aclocal.m4 build-aux autom4te.cache
