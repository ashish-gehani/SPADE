# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

LLVM_BUILD_DIR      = build
LLVM_SRC_DIR        = $(SPADE_SRC_DIR)/spade/reporter/llvm
LLVM_INSTALL_LIBDIR = $(DESTDIR)$(prefix)/lib

LLVM_CXXFLAGS_ALL = \
	@LLVM_CXXFLAGS@

LLVM_DYLIB_FLAGS = \
	-dynamiclib \
	-Wl,-flat_namespace \
	-Wl,-undefined,suppress

all-local:
	@$(MKDIR_P) "$(LLVM_BUILD_DIR)"
	$(CLANGXX) $(LLVM_CXXFLAGS_ALL) -c \
		"$(LLVM_SRC_DIR)/llvmTracer.cpp" \
		-o "$(LLVM_BUILD_DIR)/llvmTracer.o"
	$(CLANGXX) $(LLVM_DYLIB_FLAGS) \
		-o "$(LLVM_BUILD_DIR)/llvmTracer.dylib" \
		"$(LLVM_BUILD_DIR)/llvmTracer.o"
	$(CLANG) -D_LLVMREPORTER_MACOSX -c \
		"$(LLVM_SRC_DIR)/llvmBridge.c" \
		-o "$(LLVM_BUILD_DIR)/llvmBridge.o"
	$(CLANG) -c \
		"$(LLVM_SRC_DIR)/llvmClose.c" \
		-o "$(LLVM_BUILD_DIR)/llvmClose.o"

install-exec-local:
	$(MKDIR_P) "$(LLVM_INSTALL_LIBDIR)"
	$(INSTALL_PROGRAM) "$(LLVM_BUILD_DIR)/llvmTracer.dylib" \
		"$(LLVM_INSTALL_LIBDIR)/llvmTracer.dylib"
	$(INSTALL_DATA) "$(LLVM_BUILD_DIR)/llvmBridge.o" \
		"$(LLVM_INSTALL_LIBDIR)/llvmBridge.o"
	$(INSTALL_DATA) "$(LLVM_BUILD_DIR)/llvmClose.o" \
		"$(LLVM_INSTALL_LIBDIR)/llvmClose.o"

uninstall-local:
	rm -f "$(LLVM_INSTALL_LIBDIR)/llvmTracer.dylib"
	rm -f "$(LLVM_INSTALL_LIBDIR)/llvmBridge.o"
	rm -f "$(LLVM_INSTALL_LIBDIR)/llvmClose.o"

clean-local:
	rm -f "$(LLVM_BUILD_DIR)/llvmTracer.o" "$(LLVM_BUILD_DIR)/llvmTracer.dylib"
	rm -f "$(LLVM_BUILD_DIR)/llvmBridge.o"
	rm -f "$(LLVM_BUILD_DIR)/llvmClose.o"

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf configure configure~ Makefile.in aclocal.m4 build-aux autom4te.cache
