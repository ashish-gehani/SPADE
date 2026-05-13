# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

LLVM_BUILD_DIR      = build
LLVM_SRC_DIR        = $(SPADE_SRC_DIR)/spade/reporter/llvm
LLVM_INSTALL_LIBDIR = $(DESTDIR)$(prefix)/lib/llvm

LLVM_CXXFLAGS_ALL = \
	@LLVM_CXXFLAGS@ \
	-I./ \
	-Wcast-qual -pedantic -Wno-long-long -Wall -W -Wno-unused-parameter \
	-Wwrite-strings -Wcovered-switch-default \
	-Wno-uninitialized -Wno-missing-field-initializers -Wno-comment

LLVM_LINK_FLAGS = \
	-O3 -Wl,-R \
	-Wl,'$$ORIGIN' \
	-Wl,--gc-sections \
	-rdynamic -L./ -shared \
	-lpthread -ltinfo -ldl -lm

all-local:
	@$(MKDIR_P) "$(LLVM_BUILD_DIR)"
	$(CLANGXX) $(LLVM_CXXFLAGS_ALL) -c -MMD -MP \
		-MF "$(LLVM_BUILD_DIR)/llvmTracer.d" \
		-MT "$(LLVM_BUILD_DIR)/llvmTracer.o" \
		"$(LLVM_SRC_DIR)/llvmTracer.cpp" \
		-o "$(LLVM_BUILD_DIR)/llvmTracer.o"
	$(CLANGXX) $(LLVM_LINK_FLAGS) \
		-o "$(LLVM_BUILD_DIR)/LLVMTrace.so" \
		"$(LLVM_BUILD_DIR)/llvmTracer.o"
	$(CLANG) -emit-llvm -c \
		"$(LLVM_SRC_DIR)/flushModule.c" \
		-o "$(LLVM_BUILD_DIR)/flush.bc"
	$(CLANGXX) $(LLVM_CXXFLAGS_ALL) -c -MMD -MP \
		-MT "$(LLVM_BUILD_DIR)/LibcWrapper.o" \
		"$(LLVM_SRC_DIR)/LibcWrapper.cpp" \
		-o "$(LLVM_BUILD_DIR)/LibcWrapper.o"
	$(CLANGXX) $(LLVM_LINK_FLAGS) \
		-o "$(LLVM_BUILD_DIR)/LibcWrapper.so" \
		"$(LLVM_BUILD_DIR)/LibcWrapper.o"

install-exec-local:
	$(MKDIR_P) "$(LLVM_INSTALL_LIBDIR)"
	$(INSTALL_PROGRAM) "$(LLVM_BUILD_DIR)/LLVMTrace.so" "$(LLVM_INSTALL_LIBDIR)/LLVMTrace.so"
	$(INSTALL_DATA) "$(LLVM_BUILD_DIR)/flush.bc" "$(LLVM_INSTALL_LIBDIR)/flush.bc"
	$(INSTALL_PROGRAM) "$(LLVM_BUILD_DIR)/LibcWrapper.so" "$(LLVM_INSTALL_LIBDIR)/LibcWrapper.so"

uninstall-local:
	rm -f "$(LLVM_INSTALL_LIBDIR)/LLVMTrace.so"
	rm -f "$(LLVM_INSTALL_LIBDIR)/flush.bc"
	rm -f "$(LLVM_INSTALL_LIBDIR)/LibcWrapper.so"

clean-local:
	rm -rf "$(LLVM_BUILD_DIR)"

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf configure Makefile.in aclocal.m4 build-aux autom4te.cache
