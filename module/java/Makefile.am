# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# 'make all' compiles the Java module and places the resulting jar in the expected location.

all-local:
	@$(MVN) $(MAVEN_DEBUG_FLAGS) -Dspade.root="$(SPADE_ROOT)" \
		package

install-exec-local:
	@$(MVN) -Dspade.root="$(SPADE_ROOT)" \
		install

uninstall-local:
	@$(MVN) -Dspade.root="$(SPADE_ROOT)" \
		clean

clean-local:
	@$(MVN) -Dspade.root="$(SPADE_ROOT)" \
		clean

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf configure configure~ Makefile.in aclocal.m4 build-aux autom4te.cache
