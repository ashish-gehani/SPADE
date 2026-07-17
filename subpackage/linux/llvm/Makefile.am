# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

LLVM_SRC_DIR        = $(SPADE_JAVA_SRC_DIR)/spade/reporter/llvm
LLVM_INSTALL_LIBDIR = $(DESTDIR)$(prefix)/lib

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
	@$(MKDIR_P) "$(SPADE_LLVM_LIB_DIR)"
	$(CLANGXX) $(LLVM_CXXFLAGS_ALL) -c -MMD -MP \
		-MF "$(SPADE_LLVM_LIB_DIR)/llvmTracer.d" \
		-MT "$(SPADE_LLVM_LIB_DIR)/llvmTracer.o" \
		"$(LLVM_SRC_DIR)/llvmTracer.cpp" \
		-o "$(SPADE_LLVM_LIB_DIR)/llvmTracer.o"
	$(CLANGXX) $(LLVM_LINK_FLAGS) \
		-o "$(SPADE_LLVM_LIB_DIR)/LLVMTrace.so" \
		"$(SPADE_LLVM_LIB_DIR)/llvmTracer.o"
	$(CLANG) -emit-llvm -c \
		"$(LLVM_SRC_DIR)/flushModule.c" \
		-o "$(SPADE_LLVM_LIB_DIR)/flush.bc"
	$(CLANGXX) $(LLVM_CXXFLAGS_ALL) -c -MMD -MP \
		-MF "$(SPADE_LLVM_LIB_DIR)/LibcWrapper.d" \
		-MT "$(SPADE_LLVM_LIB_DIR)/LibcWrapper.o" \
		"$(LLVM_SRC_DIR)/LibcWrapper.cpp" \
		-o "$(SPADE_LLVM_LIB_DIR)/LibcWrapper.o"
	$(CLANGXX) $(LLVM_LINK_FLAGS) \
		-o "$(SPADE_LLVM_LIB_DIR)/LibcWrapper.so" \
		"$(SPADE_LLVM_LIB_DIR)/LibcWrapper.o"

install-exec-local:
	$(MKDIR_P) "$(LLVM_INSTALL_LIBDIR)"
	$(INSTALL_PROGRAM) "$(SPADE_LLVM_LIB_DIR)/LLVMTrace.so" "$(LLVM_INSTALL_LIBDIR)/LLVMTrace.so"
	$(INSTALL_DATA) "$(SPADE_LLVM_LIB_DIR)/flush.bc" "$(LLVM_INSTALL_LIBDIR)/flush.bc"
	$(INSTALL_PROGRAM) "$(SPADE_LLVM_LIB_DIR)/LibcWrapper.so" "$(LLVM_INSTALL_LIBDIR)/LibcWrapper.so"

uninstall-local:
	rm -f "$(LLVM_INSTALL_LIBDIR)/LLVMTrace.so"
	rm -f "$(LLVM_INSTALL_LIBDIR)/flush.bc"
	rm -f "$(LLVM_INSTALL_LIBDIR)/LibcWrapper.so"

clean-local:
	rm -f "$(SPADE_LLVM_LIB_DIR)/llvmTracer.o" "$(SPADE_LLVM_LIB_DIR)/llvmTracer.d"
	rm -f "$(SPADE_LLVM_LIB_DIR)/LLVMTrace.so"
	rm -f "$(SPADE_LLVM_LIB_DIR)/flush.bc"
	rm -f "$(SPADE_LLVM_LIB_DIR)/LibcWrapper.o" "$(SPADE_LLVM_LIB_DIR)/LibcWrapper.d"
	rm -f "$(SPADE_LLVM_LIB_DIR)/LibcWrapper.so"

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf aclocal.m4 \
	       autom4te.cache \
	       build-aux \
	       configure \
	       Makefile.in
