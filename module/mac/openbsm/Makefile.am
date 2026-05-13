# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

OPENBSM_MAC_OUTPUT = build/spadeOpenBSM
OPENBSM_MAC_C_SRC = $(SPADE_SRC_DIR)/spade/reporter/spadeOpenBSM.c

if HAVE_LIBBSM
all-local:
	$(MKDIR_P) build
	$(CC) -o $(OPENBSM_MAC_OUTPUT) -lbsm $(OPENBSM_MAC_C_SRC)

install-exec-local:
	$(MKDIR_P) $(DESTDIR)$(prefix)
	$(INSTALL_PROGRAM) $(OPENBSM_MAC_OUTPUT) $(DESTDIR)$(prefix)/spadeOpenBSM
else
all-local install-exec-local:
	@echo "Skipping OpenBSM: libbsm not found"
endif

uninstall-local:
	rm -f "$(DESTDIR)$(prefix)/spadeOpenBSM"

clean-local:
	rm -f $(OPENBSM_MAC_OUTPUT)

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf configure configure~ Makefile.in aclocal.m4 build-aux autom4te.cache
