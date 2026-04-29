#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# TODO
# 1. mac/llvm
# 2. mac/openbsm
# 3. remove env unless needed for things relative to spade project.


# constants
LLVM_PATH="/var/clang+llvm-3.0-x86_64-apple-darwin11"

# globals
TARGET=
LLVM_TARGET=
SPADE_SRC=
SPADE_LIB=
LLVM_TRACER=
LLVM_BRIDGE=
LLVM_CLOSE=
LLVM_TRACER_DYLIB=


function print_help() {
    echo "Usage: $(basename "$0") --target <target> --llvm-target <llvm-target> --spade-src <path> --spade-lib <path> --llvm-tracer <path> --llvm-bridge <path> --llvm-close <path> --llvm-tracer-dylib <path>"
    echo ""
    echo "Options:"
    echo "    --target             Source file target (without extension)"
    echo "    --llvm-target        LLVM bitcode target (without extension)"
    echo "    --spade-src          Path to the SPADE source directory"
    echo "    --spade-lib          Path to the SPADE lib directory"
    echo "    --llvm-tracer        Path to llvmTracer (without extension)"
    echo "    --llvm-bridge        Path to llvmBridge (without extension)"
    echo "    --llvm-close         Path to llvmClose (without extension)"
    echo "    --llvm-tracer-dylib  Output path for the llvmTracer dylib"
    echo "    --help               Show this message and exit"
    exit 0
}

function parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --target) TARGET="$2"; shift 2 ;;
            --llvm-target) LLVM_TARGET="$2"; shift 2 ;;
            --spade-src) SPADE_SRC="$2"; shift 2 ;;
            --spade-lib) SPADE_LIB="$2"; shift 2 ;;
            --llvm-tracer) LLVM_TRACER="$2"; shift 2 ;;
            --llvm-bridge) LLVM_BRIDGE="$2"; shift 2 ;;
            --llvm-close) LLVM_CLOSE="$2"; shift 2 ;;
            --llvm-tracer-dylib) LLVM_TRACER_DYLIB="$2"; shift 2 ;;
            --help) print_help ;;
            *) echo "Unknown argument: $1"; exit 1 ;;
        esac
    done
}

function validate_args() {
    if [[ -z "${TARGET}" ]]; then
        echo "Error: --target is required"
        exit 1
    fi
    if [[ -z "${LLVM_TARGET}" ]]; then
        echo "Error: --llvm-target is required"
        exit 1
    fi
    if [[ -z "${SPADE_SRC}" ]]; then
        echo "Error: --spade-src is required"
        exit 1
    fi
    if [[ ! -d "${SPADE_SRC}" ]]; then
        echo "Error: --spade-src '${SPADE_SRC}' is not a directory"
        exit 1
    fi
    if [[ -z "${SPADE_LIB}" ]]; then
        echo "Error: --spade-lib is required"
        exit 1
    fi
    if [[ ! -d "${SPADE_LIB}" ]]; then
        echo "Error: --spade-lib '${SPADE_LIB}' is not a directory"
        exit 1
    fi
    if [[ -z "${LLVM_TRACER}" ]]; then
        echo "Error: --llvm-tracer is required"
        exit 1
    fi
    if [[ -z "${LLVM_BRIDGE}" ]]; then
        echo "Error: --llvm-bridge is required"
        exit 1
    fi
    if [[ -z "${LLVM_CLOSE}" ]]; then
        echo "Error: --llvm-close is required"
        exit 1
    fi
    if [[ -z "${LLVM_TRACER_DYLIB}" ]]; then
        echo "Error: --llvm-tracer-dylib is required"
        exit 1
    fi
}

function compile_and_build() {
    local REPLIB_OSFLAG="-D_LLVMREPORTER_MACOSX"
    local CLANG="${LLVM_PATH}/bin/clang"
    local OPT="${LLVM_PATH}/bin/opt"
    local LLC="${LLVM_PATH}/bin/llc"
    local CXX_FLAGS
    CXX_FLAGS="$("${LLVM_PATH}/bin/llvm-config" --cxxflags)"
    local C_FLAGS
    C_FLAGS="$("${LLVM_PATH}/bin/llvm-config" --cflags)"
    local CLANG_FLAGS="-cc1 -triple x86_64-apple-macosx10.6.8 -emit-obj -mrelax-all -disable-free ${C_FLAGS}"
    local CLANGXX_FLAGS="${CXX_FLAGS} -m64 -Wl,-flat_namespace -Wl,-undefined,suppress -dynamiclib -mmacosx-version-min=10.6"

    ${CLANG} ${CLANG_FLAGS} -g -o "${LLVM_TRACER}.o" -x c++ "${LLVM_TRACER}.cpp"
    ${CLANG} ${CLANGXX_FLAGS} -o "${LLVM_TRACER_DYLIB}" "${LLVM_TRACER}.o"

    ${CLANG} -static ${REPLIB_OSFLAG} "${LLVM_BRIDGE}.c" -c -o "${LLVM_BRIDGE}.o"
    ${CLANG} -c -emit-llvm "${TARGET}.c" -o "${LLVM_TARGET}.bc"
    ${OPT} -load "${LLVM_TRACER_DYLIB}" -provenance "${LLVM_TARGET}.bc" -o "${LLVM_TARGET}.bc"
    ${LLC} "${LLVM_TARGET}.bc" -o "${LLVM_TARGET}.s"
    ${CLANG} "${LLVM_CLOSE}.c" -c -o "${LLVM_CLOSE}.o"
    ${CLANG} "${LLVM_TARGET}.s" "${LLVM_CLOSE}.o" -dynamiclib -Wl,-flat_namespace -Wl,-undefined,suppress -o "${LLVM_TARGET}.dylib"
    ${CLANG} "${LLVM_BRIDGE}.o" "${LLVM_TARGET}.dylib" -o "${LLVM_TARGET}"
}

function main() {
    parse_args "$@"
    validate_args
    compile_and_build
}

main "$@"
