# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AUDIT_BRIDGE_BINARY     = spadeAuditBridge
AUDIT_BRIDGE_C_SRC      = $(SPADE_JAVA_SRC_DIR)/spade/reporter/spadeAuditBridge.c
AUDIT_BRIDGE_INSTALL_BINDIR = $(DESTDIR)$(prefix)/bin

all-local:
	@$(MKDIR_P) "$(SPADE_AUDIT_BRIDGE_BIN_DIR)"
	$(CC) -o "$(SPADE_AUDIT_BRIDGE_BIN_DIR)/$(AUDIT_BRIDGE_BINARY)" "$(AUDIT_BRIDGE_C_SRC)"

install-exec-local:
	$(MKDIR_P) "$(AUDIT_BRIDGE_INSTALL_BINDIR)"
	$(INSTALL_PROGRAM) "$(SPADE_AUDIT_BRIDGE_BIN_DIR)/$(AUDIT_BRIDGE_BINARY)" \
		"$(AUDIT_BRIDGE_INSTALL_BINDIR)/$(AUDIT_BRIDGE_BINARY)"

uninstall-local:
	rm -f "$(AUDIT_BRIDGE_INSTALL_BINDIR)/$(AUDIT_BRIDGE_BINARY)"

clean-local:
	rm -f "$(SPADE_AUDIT_BRIDGE_BIN_DIR)/$(AUDIT_BRIDGE_BINARY)"

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf aclocal.m4 \
	       autom4te.cache \
	       build-aux \
	       configure \
	       Makefile.in
