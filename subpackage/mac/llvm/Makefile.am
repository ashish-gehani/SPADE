# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

LLVM_SRC_DIR        = $(SPADE_JAVA_SRC_DIR)/spade/reporter/llvm
LLVM_INSTALL_LIBDIR = $(DESTDIR)$(prefix)/lib

LLVM_CXXFLAGS_ALL = \
	@LLVM_CXXFLAGS@

LLVM_DYLIB_FLAGS = \
	-dynamiclib \
	-Wl,-flat_namespace \
	-Wl,-undefined,suppress

all-local:
	@$(MKDIR_P) "$(SPADE_LLVM_LIB_DIR)"
	$(CLANGXX) $(LLVM_CXXFLAGS_ALL) -c \
		"$(LLVM_SRC_DIR)/llvmTracer.cpp" \
		-o "$(SPADE_LLVM_LIB_DIR)/llvmTracer.o"
	$(CLANGXX) $(LLVM_DYLIB_FLAGS) \
		-o "$(SPADE_LLVM_LIB_DIR)/llvmTracer.dylib" \
		"$(SPADE_LLVM_LIB_DIR)/llvmTracer.o"
	$(CLANG) -D_LLVMREPORTER_MACOSX -c \
		"$(LLVM_SRC_DIR)/llvmBridge.c" \
		-o "$(SPADE_LLVM_LIB_DIR)/llvmBridge.o"
	$(CLANG) -c \
		"$(LLVM_SRC_DIR)/llvmClose.c" \
		-o "$(SPADE_LLVM_LIB_DIR)/llvmClose.o"

install-exec-local:
	$(MKDIR_P) "$(LLVM_INSTALL_LIBDIR)"
	$(INSTALL_PROGRAM) "$(SPADE_LLVM_LIB_DIR)/llvmTracer.dylib" \
		"$(LLVM_INSTALL_LIBDIR)/llvmTracer.dylib"
	$(INSTALL_DATA) "$(SPADE_LLVM_LIB_DIR)/llvmBridge.o" \
		"$(LLVM_INSTALL_LIBDIR)/llvmBridge.o"
	$(INSTALL_DATA) "$(SPADE_LLVM_LIB_DIR)/llvmClose.o" \
		"$(LLVM_INSTALL_LIBDIR)/llvmClose.o"

uninstall-local:
	rm -f "$(LLVM_INSTALL_LIBDIR)/llvmTracer.dylib"
	rm -f "$(LLVM_INSTALL_LIBDIR)/llvmBridge.o"
	rm -f "$(LLVM_INSTALL_LIBDIR)/llvmClose.o"

clean-local:
	rm -f "$(SPADE_LLVM_LIB_DIR)/llvmTracer.o" "$(SPADE_LLVM_LIB_DIR)/llvmTracer.dylib"
	rm -f "$(SPADE_LLVM_LIB_DIR)/llvmBridge.o"
	rm -f "$(SPADE_LLVM_LIB_DIR)/llvmClose.o"

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf aclocal.m4 \
	       autom4te.cache \
	       build-aux \
	       configure \
	       configure~ \
	       Makefile.in
