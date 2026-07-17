# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

OPENBSM_MAC_OUTPUT = $(SPADE_OPENBSM_BIN_DIR)/spadeOpenBSM
OPENBSM_MAC_C_SRC  = $(SPADE_JAVA_SRC_DIR)/spade/reporter/spadeOpenBSM.c

OPENBSM_MAC_INSTALL_BINDIR = $(DESTDIR)$(prefix)/bin

all-local:
	@$(MKDIR_P) "$(SPADE_OPENBSM_BIN_DIR)"
	$(CC) -o "$(OPENBSM_MAC_OUTPUT)" $(LIBBSM_LIBS) "$(OPENBSM_MAC_C_SRC)"

install-exec-local:
	$(MKDIR_P) "$(OPENBSM_MAC_INSTALL_BINDIR)"
	$(INSTALL_PROGRAM) $(OPENBSM_MAC_OUTPUT) "$(OPENBSM_MAC_INSTALL_BINDIR)/spadeOpenBSM"

uninstall-local:
	rm -f "$(OPENBSM_MAC_INSTALL_BINDIR)/spadeOpenBSM"

clean-local:
	rm -f $(OPENBSM_MAC_OUTPUT)

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf aclocal.m4 \
	       autom4te.cache \
	       build-aux \
	       configure \
	       configure~ \
	       Makefile.in
