# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

SRC_DIR = src

BUILD_DIR = build
BUILD_LLVM_TRACE_LIB_NAME = llvmTracer.dylib
BUILD_LLVM_BRIDGE_OBJ_NAME = llvmBridge.o
BUILD_LLVM_CLOSE_OBJ_NAME = llvmClose.o

OUTPUT_SPADE_PACKAGE_LLVM_TRACE_LIB_FILE = $(SPADE_PACKAGE_LIB_DIR)/$(SPADE_PACKAGE_MAC_LLVM_TRACE_LIB_NAME)

LLVM_CXXFLAGS_ALL = \
	@LLVM_CXXFLAGS@

LLVM_DYLIB_FLAGS = \
	-dynamiclib \
	-Wl,-flat_namespace \
	-Wl,-undefined,suppress

all-local:
	@$(MKDIR_P) "$(BUILD_DIR)"
	$(CLANGXX) $(LLVM_CXXFLAGS_ALL) -c \
		"$(SRC_DIR)/llvmTracer.cpp" \
		-o "$(BUILD_DIR)/llvmTracer.o"
	$(CLANGXX) $(LLVM_DYLIB_FLAGS) \
		-o "$(BUILD_DIR)/$(BUILD_LLVM_TRACE_LIB_NAME)" \
		"$(BUILD_DIR)/llvmTracer.o"
	$(CLANG) -D_LLVMREPORTER_MACOSX -c \
		"$(SRC_DIR)/llvmBridge.c" \
		-o "$(BUILD_DIR)/$(BUILD_LLVM_BRIDGE_OBJ_NAME)"
	$(CLANG) -c \
		"$(SRC_DIR)/llvmClose.c" \
		-o "$(BUILD_DIR)/$(BUILD_LLVM_CLOSE_OBJ_NAME)"
	cp "$(BUILD_DIR)/$(BUILD_LLVM_TRACE_LIB_NAME)" \
		"$(OUTPUT_SPADE_PACKAGE_LLVM_TRACE_LIB_FILE)"

clean-local:
	rm -f "$(BUILD_DIR)/llvmTracer.o"
	rm -f "$(BUILD_DIR)/$(BUILD_LLVM_TRACE_LIB_NAME)"
	rm -f "$(BUILD_DIR)/$(BUILD_LLVM_BRIDGE_OBJ_NAME)"
	rm -f "$(BUILD_DIR)/$(BUILD_LLVM_CLOSE_OBJ_NAME)"
	rm -f "$(OUTPUT_SPADE_PACKAGE_LLVM_TRACE_LIB_FILE)"

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf aclocal.m4 \
	       autom4te.cache \
	       build-aux \
	       configure \
	       configure~ \
	       Makefile.in
