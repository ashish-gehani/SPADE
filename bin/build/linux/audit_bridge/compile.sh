#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


# globals
OUTPUT=""
SPADE_SRC=""


print_help() {
    echo "Usage: $(basename "$0") --output <path> --spade_src <path>"
    echo ""
    echo "Options:"
    echo "    --output <path>    Output path for the compiled audit bridge binary"
    echo "    --spade_src <path> Path to the SPADE source directory"
    echo "    --help             Show this message and exit"
    exit 0
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --output)    OUTPUT="$2";    shift 2 ;;
            --spade_src) SPADE_SRC="$2"; shift 2 ;;
            --help)      print_help ;;
            *) echo "Unknown argument: $1"; print_help ;;
        esac
    done
}

validate_args() {
    if [[ -z "${OUTPUT}" ]]; then
        echo "Error: --output is required"
        exit 1
    fi
    if [[ -z "${SPADE_SRC}" ]]; then
        echo "Error: --spade_src is required"
        exit 1
    fi
}

build() {
    gcc -o "${OUTPUT}" "${SPADE_SRC}/spade/reporter/spadeAuditBridge.c"
}

main() {
    parse_args "$@"
    validate_args
    build
}

main "$@"
