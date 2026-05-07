#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


# globals
SPADE_SRC=""
SPADE_BIN_LLVM=""


print_help() {
    echo "Usage: $(basename "$0") [--spade-src <path>] [--spade-bin-llvm <path>]"
    echo ""
    echo "Options:"
    echo "    --spade-src <path>       Path to the SPADE source directory (optional)"
    echo "    --spade-bin-llvm <path>  Path to the LLVM output directory (optional)"
    echo "    --help                   Show this message and exit"
    exit 0
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --spade-src)      SPADE_SRC="$2";      shift 2 ;;
            --spade-bin-llvm) SPADE_BIN_LLVM="$2"; shift 2 ;;
            --help)           print_help ;;
            *) echo "Unknown argument: $1"; print_help ;;
        esac
    done
}

validate_args() {
    :
}

clean() {
    if [[ -n "${SPADE_SRC}" ]]; then
        rm -f "${SPADE_SRC}/spade/reporter/llvm/llvmTracer.o"
        rm -f "${SPADE_SRC}/spade/reporter/llvm/llvmTracer.d.tmp"
        rm -f "${SPADE_SRC}/spade/reporter/llvm/LibcWrapper.o"
    fi
    if [[ -n "${SPADE_BIN_LLVM}" ]]; then
        rm -f "${SPADE_BIN_LLVM}/LLVMTrace.so"
        rm -f "${SPADE_BIN_LLVM}/flush.bc"
        rm -f "${SPADE_BIN_LLVM}/LibcWrapper.so"
    fi
}

main() {
    parse_args "$@"
    validate_args
    clean
}

main "$@"
