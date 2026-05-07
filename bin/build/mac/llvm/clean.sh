#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


# globals
LLVM_TARGET=
LLVM_TRACER=
LLVM_BRIDGE=
LLVM_CLOSE=
LLVM_TRACER_DYLIB=


function print_help() {
    echo "Usage: $(basename "$0") [--llvm-target <path>] --llvm-tracer <path> --llvm-bridge <path> --llvm-close <path> --llvm-tracer-dylib <path>"
    echo ""
    echo "Options:"
    echo "    --llvm-target        Path to LLVM bitcode target (without extension); optional"
    echo "    --llvm-tracer        Path to llvmTracer (without extension)"
    echo "    --llvm-bridge        Path to llvmBridge (without extension)"
    echo "    --llvm-close         Path to llvmClose (without extension)"
    echo "    --llvm-tracer-dylib  Path to the llvmTracer dylib"
    echo "    --help               Show this message and exit"
    exit 0
}

function parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --llvm-target)       LLVM_TARGET="$2";       shift 2 ;;
            --llvm-tracer)       LLVM_TRACER="$2";       shift 2 ;;
            --llvm-bridge)       LLVM_BRIDGE="$2";       shift 2 ;;
            --llvm-close)        LLVM_CLOSE="$2";        shift 2 ;;
            --llvm-tracer-dylib) LLVM_TRACER_DYLIB="$2"; shift 2 ;;
            --help) print_help ;;
            *) echo "Unknown argument: $1"; exit 1 ;;
        esac
    done
}

function validate_args() {
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

function clean() {
    [[ -n "${LLVM_TARGET}" ]] && rm -f "${LLVM_TARGET}" "${LLVM_TARGET}".*
    rm -f "${LLVM_TRACER}.o"
    rm -f "${LLVM_BRIDGE}.o"
    rm -f "${LLVM_CLOSE}.o"
    rm -f "${LLVM_TRACER_DYLIB}"
}

function main() {
    parse_args "$@"
    validate_args
    clean
}

main "$@"
