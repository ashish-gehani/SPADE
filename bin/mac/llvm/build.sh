#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

SPADE_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )"/../../.. && pwd )"
SPADE_LIB="${SPADE_ROOT}/lib"
SPADE_SRC="${SPADE_ROOT}/src"

# TODO Meaning unknown
TARGET=
# TODO Meaning unknown
LLVM_TARGET=

function compile_and_build(){
    local LLVM_PATH="/var/clang+llvm-3.0-x86_64-apple-darwin11"
    local REPLIB_OSFLAG="-D_LLVMREPORTER_MACOSX"
    local CLANG="${LLVM_PATH}/bin/clang"
    local OPT="${LLVM_PATH}/bin/opt"
    local LLC="${LLVM_PATH}/bin/llc"
    local LLVM_TRACER="${SPADE_SRC}/spade/reporter/llvmTracer"
    local LLVM_BRIDGE="${SPADE_SRC}/spade/reporter/llvmBridge"
    local LLVM_CLOSE="${SPADE_SRC}/spade/reporter/llvmClose"
    local LLVM_TRACER_DYLIB="${SPADE_LIB}/llvmTracer.dylib"
    local CXX_FLAGS="$(${LLVM_PATH}/bin/llvm-config --cxxflags)"
    local C_FLAGS="$(${LLVM_PATH}/bin/llvm-config --cflags)"
    local CLANG_FLAGS="-cc1 -triple x86_64-apple-macosx10.6.8 -emit-obj -mrelax-all -disable-free ${C_FLAGS}"
    local CLANGXX_FLAGS="${CXX_FLAGS} -m64 -Wl,-flat_namespace -Wl,-undefined,suppress -dynamiclib -mmacosx-version-min=10.6"

    ${CLANG} ${CLANG_FLAGS} -g -o ${LLVM_TRACER}.o -x c++ ${LLVM_TRACER}.cpp
    ${CLANG} ${CLANGXX_FLAGS} -o ${LLVM_TRACER_DYLIB} ${LLVM_TRACER}.o

    ${CLANG} -static ${REPLIB_OSFLAG} ${LLVM_BRIDGE}.c -c -o ${LLVM_BRIDGE}.o
    ${CLANG} -c -emit-llvm ${TARGET}.c -o ${LLVM_TARGET}.bc
    ${OPT} -load ${LLVM_TRACER_DYLIB} -provenance ${LLVM_TARGET}.bc -o ${LLVM_TARGET}.bc
    ${LLC} ${LLVM_TARGET}.bc -o ${LLVM_TARGET}.s
    ${CLANG} ${LLVM_CLOSE}.c -c -o ${LLVM_CLOSE}.o
    ${CLANG} ${LLVM_TARGET}.s ${LLVM_CLOSE}.o -dynamiclib -Wl,-flat_namespace -Wl,-undefined,suppress -o ${LLVM_TARGET}.dylib
    ${CLANG} ${LLVM_BRIDGE}.o ${LLVM_TARGET}.dylib -o ${LLVM_TARGET}
}

function usage(){
    echo "Usage: $(basename "$0") --target <target> --llvm-target <llvm-target>"
    echo ""
    echo "Options:"
    echo "  --target       Source file target (without extension)"
    echo "  --llvm-target  LLVM bitcode target (without extension)"
    echo "  -h, --help     Show this help message"
}

function parse_args(){
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --target)
                TARGET="$2"
                shift 2
                ;;
            --llvm-target)
                LLVM_TARGET="$2"
                shift 2
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            *)
                echo "Unknown argument: $1"
                usage
                exit 1
                ;;
        esac
    done
}

function validate_args(){
    if [[ -z "${TARGET}" ]]; then
        echo "Error: --target is required"
        exit 1
    fi
    if [[ -z "${LLVM_TARGET}" ]]; then
        echo "Error: --llvm-target is required"
        exit 1
    fi
}

function main(){
    parse_args "$@"
    validate_args
    compile_and_build
}

main "$@"
