# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


SRC_DIR = src

BUILD_DIR = build
BUILD_LLVM_TRACE_LIB_NAME = LLVMTrace.so
BUILD_LLVM_FLUSH_BC_NAME = flush.bc
BUILD_LLVM_LIBC_WRAPPER_LIB_NAME = LibcWrapper.so

OUTPUT_SPADE_PACKAGE_LLVM_TRACE_LIB_FILE = $(SPADE_PACKAGE_LIB_DIR)/$(SPADE_PACKAGE_LINUX_LLVM_TRACE_LIB_NAME)
OUTPUT_SPADE_PACKAGE_LLVM_FLUSH_BC_FILE = $(SPADE_PACKAGE_LIB_DIR)/$(SPADE_PACKAGE_LLVM_FLUSH_BC_NAME)
OUTPUT_SPADE_PACKAGE_LLVM_LIBC_WRAPPER_LIB_FILE = $(SPADE_PACKAGE_LIB_DIR)/$(SPADE_PACKAGE_LLVM_LIBC_WRAPPER_LIB_NAME)


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
	@$(MKDIR_P) "$(BUILD_DIR)"
	$(CLANGXX) $(LLVM_CXXFLAGS_ALL) -c -MMD -MP \
		-MF "$(BUILD_DIR)/llvmTracer.d" \
		-MT "$(BUILD_DIR)/llvmTracer.o" \
		"$(SRC_DIR)/llvmTracer.cpp" \
		-o "$(BUILD_DIR)/llvmTracer.o"
	$(CLANGXX) $(LLVM_LINK_FLAGS) \
		-o "$(BUILD_DIR)/$(BUILD_LLVM_TRACE_LIB_NAME)" \
		"$(BUILD_DIR)/llvmTracer.o"
	$(CLANG) -emit-llvm -c \
		"$(SRC_DIR)/flushModule.c" \
		-o "$(BUILD_DIR)/$(BUILD_LLVM_FLUSH_BC_NAME)"
	$(CLANGXX) $(LLVM_CXXFLAGS_ALL) -c -MMD -MP \
		-MF "$(BUILD_DIR)/LibcWrapper.d" \
		-MT "$(BUILD_DIR)/LibcWrapper.o" \
		"$(SRC_DIR)/LibcWrapper.cpp" \
		-o "$(BUILD_DIR)/LibcWrapper.o"
	$(CLANGXX) $(LLVM_LINK_FLAGS) \
		-o "$(BUILD_DIR)/$(BUILD_LLVM_LIBC_WRAPPER_LIB_NAME)" \
		"$(BUILD_DIR)/LibcWrapper.o"
	cp "$(BUILD_DIR)/$(BUILD_LLVM_TRACE_LIB_NAME)" \
		"$(OUTPUT_SPADE_PACKAGE_LLVM_TRACE_LIB_FILE)"
	cp "$(BUILD_DIR)/$(BUILD_LLVM_FLUSH_BC_NAME)" \
		"$(OUTPUT_SPADE_PACKAGE_LLVM_FLUSH_BC_FILE)"
	cp "$(BUILD_DIR)/$(BUILD_LLVM_LIBC_WRAPPER_LIB_NAME)" \
		"$(OUTPUT_SPADE_PACKAGE_LLVM_LIBC_WRAPPER_LIB_FILE)"

clean-local:
	rm -f "$(BUILD_DIR)/llvmTracer.o" "$(BUILD_DIR)/llvmTracer.d"
	rm -f "$(BUILD_DIR)/$(BUILD_LLVM_TRACE_LIB_NAME)"
	rm -f "$(BUILD_DIR)/$(BUILD_LLVM_FLUSH_BC_NAME)"
	rm -f "$(BUILD_DIR)/LibcWrapper.o" "$(BUILD_DIR)/LibcWrapper.d"
	rm -f "$(BUILD_DIR)/$(BUILD_LLVM_LIBC_WRAPPER_LIB_NAME)"
	rm -f "$(OUTPUT_SPADE_PACKAGE_LLVM_TRACE_LIB_FILE)"
	rm -f "$(OUTPUT_SPADE_PACKAGE_LLVM_FLUSH_BC_FILE)"
	rm -f "$(OUTPUT_SPADE_PACKAGE_LLVM_LIBC_WRAPPER_LIB_FILE)"

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf aclocal.m4 \
	       autom4te.cache \
	       build-aux \
	       configure \
	       Makefile.in
