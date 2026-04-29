#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# globals
NATIVE_HEADER_FILE=""
LIB_PATH=""


print_help() {
    echo "Usage: $(basename "$0") --native-header-file <path> --lib-path <path>"
    echo ""
    echo "Options:"
    echo "    --native-header-file <path>  Path to the JNI header file to remove"
    echo "    --lib-path <path>           Path to the shared library to remove"
    echo "    --help                      Show this message and exit"
    exit 0
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --native-header-dir)
                NATIVE_HEADER_FILE="$2"
                shift 2
                ;;
            --lib-path)
                LIB_PATH="$2"
                shift 2
                ;;
            --help)
                print_help
                ;;
            *)
                echo "Unknown argument: $1"
                exit 1
                ;;
        esac
    done
}

validate_args() {
    if [[ -z "${NATIVE_HEADER_FILE}" ]]; then
        echo "Error: --native-header-file is required"
        exit 1
    fi
    if [[ -z "${LIB_PATH}" ]]; then
        echo "Error: --lib-path is required"
        exit 1
    fi
}

clean() {
    rm -f "${NATIVE_HEADER_FILE}"
    rm -f "${LIB_PATH%.*}".*
}

main() {
    parse_args "$@"
    validate_args
    clean
}

main "$@"
