# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# 'make all' compiles the Java module and places the resulting jar
# in the java lib directory as well.

MVN_SPADE_VARS = -Dspade.src.dir="$(SPADE_JAVA_SRC_DIR)" \
	-Dspade.src.resource.dir="$(SPADE_JAVA_SRC_RESOURCE_DIR)" \
	-Dspade.build.dir="$(SPADE_JAVA_BUILD_DIR)" \
	-Dspade.build.native.include.dir="$(SPADE_JAVA_BUILD_NATIVE_INCLUDE_DIR)" \
	-Dspade.lib.dir="$(SPADE_JAVA_LIB_DIR)" \
	-Dspade.local.repository.url="$(SPADE_JAVA_LOCAL_REPOSITORY_URL)"

all-local:
	@$(MVN) $(MAVEN_DEBUG_FLAGS) $(MVN_SPADE_VARS) \
		package

install-exec-local:
	@$(MVN) $(MVN_SPADE_VARS) \
		install

uninstall-local:
	@$(MVN) $(MVN_SPADE_VARS) \
		clean

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
