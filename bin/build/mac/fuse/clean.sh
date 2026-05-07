#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# globals
LIB_PATH=""


print_help() {
    echo "Usage: $(basename "$0") --lib-path <path>"
    echo ""
    echo "Options:"
    echo "    --lib-path <path>  Path to the shared library to remove"
    echo "    --help             Show this message and exit"
    exit 0
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
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
    if [[ -z "${LIB_PATH}" ]]; then
        echo "Error: --lib-path is required"
        exit 1
    fi
}

clean() {
    rm -f "${LIB_PATH%.*}".*
}

main() {
    parse_args "$@"
    validate_args
    clean
}

main "$@"
