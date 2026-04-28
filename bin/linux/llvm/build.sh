#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

source "$( dirname "${BASH_SOURCE[0]}" )/../../env.sh"

# TODO This script will conflict with https://github.com/ashish-gehani/SPADE/blob/master/bin/llvm/llvmTrace.sh
# TODO This script will not work!
# TODO Above-mentioned script is an improved version.


REPLIB_OSFLAG="-D_LLVMREPORTER_LINUX"
LLVM_INCLUDE_PATH="/usr/include/llvm-3.6/"


echo llvm[0]: "Compiling llvmTracer.cpp for Release+Asserts build" "(PIC)"
clang++ \
    -I${LLVM_INCLUDE_PATH} \
    -I./ \
    -D_DEBUG \
    -D_GNU_SOURCE \
    -D__STDC_CONSTANT_MACROS \
    -D__STDC_FORMAT_MACROS \
    -D__STDC_LIMIT_MACROS \
    -O3 \
    -fomit-frame-pointer \
    -std=c++11 \
    -fvisibility-inlines-hidden \
    -fno-exceptions \
    -fno-rtti \
    -fPIC \
    -ffunction-sections \
    -fdata-sections \
    -Wcast-qual \
    -pedantic \
    -Wno-long-long \
    -Wall \
    -W \
    -Wno-unused-parameter \
    -Wwrite-strings \
    -Wcovered-switch-default \
    -Wno-uninitialized \
    -Wno-missing-field-initializers \
    -Wno-comment \
    -c \
    -MMD \
    -MP \
    -MF "llvmTracer.d.tmp" \
    -MT "llvmTracer.o" \
    -MT "llvmTracer.d" \
    "${SPADE_SRC}/spade/reporter/llvm/llvmTracer.cpp" \
    -o "${SPADE_SRC}/spade/reporter/llvm/llvmTracer.o"


echo 'llvm[0]: Linking "Loadable Module" LLVMTrace.so'
clang++ \
    -O3 \
    -Wl,-R \
    -Wl,'$ORIGIN' \
    -Wl,--gc-sections \
    -rdynamic \
    -L./ \
    -L./ \
    -shared \
    -o "${SPADE_BIN_LLVM}/LLVMTrace.so" \
    "${SPADE_SRC}/spade/reporter/llvm/llvmTracer.o" \
    -lpthread \
    -ltinfo \
    -ldl \
    -lm
clang -emit-llvm -c "${SPADE_SRC}/spade/reporter/llvm/flushModule.c" -o "${SPADE_BIN_LLVM}/flush.bc"


echo llvm[0]: "Compiling WrapperPass.cpp for Release+Asserts build" "(PIC)"
clang++ \
    -I${LLVM_INCLUDE_PATH} \
    -I./ \
    -D_DEBUG \
    -D_GNU_SOURCE \
    -D__STDC_CONSTANT_MACROS \
    -D__STDC_FORMAT_MACROS \
    -D__STDC_LIMIT_MACROS \
    -O3 \
    -fomit-frame-pointer \
    -std=c++11 \
    -fvisibility-inlines-hidden \
    -fno-exceptions \
    -fno-rtti \
    -fPIC \
    -ffunction-sections \
    -fdata-sections \
    -Wcast-qual \
    -pedantic \
    -Wno-long-long \
    -Wall \
    -W \
    -Wno-unused-parameter \
    -Wwrite-strings \
    -Wcovered-switch-default \
    -Wno-uninitialized \
    -Wno-missing-field-initializers \
    -Wno-comment \
    -c \
    -MMD \
    -MP \
    -MT "WrapperPass.o" \
    "${SPADE_SRC}/spade/reporter/llvm/LibcWrapper.cpp" \
    -o "${SPADE_SRC}/spade/reporter/llvm/LibcWrapper.o"


echo llvm[0]: Linking "Loadable Module build/WrapperPass.so"
clang++ \
    -O3 \
    -Wl,-R \
    -Wl,'$ORIGIN' \
    -Wl,--gc-sections \
    -rdynamic \
    -L./ \
    -Lm./ \
    -shared \
    -o "${SPADE_BIN_LLVM}/LibcWrapper.so" \
    "${SPADE_SRC}/spade/reporter/llvm/LibcWrapper.o" \
    -lpthread \
    -ltinfo \
    -ldl \
    -lm
