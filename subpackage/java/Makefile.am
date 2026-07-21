# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# 'make all' compiles the Java module and places the resulting jar
# in the java lib directory as well.

MVN_SPADE_VARS = -Dspade.package.lib.dir="$(SPADE_PACKAGE_LIB_DIR)" \
	-Dspade.jar.name="$(SPADE_JAR_NAME)" \
	-Dspade.package.jar.path="$(SPADE_PACKAGE_JAR_PATH)" \
	-Dspade.package.header.include.java.dir="$(SPADE_PACKAGE_HEADER_INCLUDE_JAVA_DIR)"

all-local:
	@$(MVN) $(MAVEN_DEBUG_FLAGS) $(MVN_SPADE_VARS) \
		package

clean-local:
	@$(MVN) $(MVN_SPADE_VARS) \
		clean

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf aclocal.m4 \
	       autom4te.cache \
	       build-aux \
	       configure \
	       configure~ \
	       Makefile.in
