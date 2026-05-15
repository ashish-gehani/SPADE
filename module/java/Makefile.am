# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

JAVA_BUILD_DIR      = build
JAVA_JAR            = $(JAVA_BUILD_DIR)/spade.jar
JAVA_INSTALL_LIBDIR = $(DESTDIR)$(prefix)/lib

all-local:
	@$(MKDIR_P) "$(JAVA_BUILD_DIR)"
	$(MVN) $(MAVEN_DEBUG_FLAGS) -Dspade.root="$(SPADE_ROOT)" \
		-Dspade.lib.dir="$(abs_builddir)/$(JAVA_BUILD_DIR)" \
		compile

install-exec-local:
	$(MKDIR_P) "$(JAVA_INSTALL_LIBDIR)"
	$(INSTALL_DATA) "$(JAVA_JAR)" "$(JAVA_INSTALL_LIBDIR)/spade.jar"

uninstall-local:
	rm -f "$(JAVA_INSTALL_LIBDIR)/spade.jar"

clean-local:
	$(MVN) -Dspade.root="$(SPADE_ROOT)" clean
	rm -f "$(JAVA_JAR)"

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf configure configure~ Makefile.in aclocal.m4 build-aux autom4te.cache
