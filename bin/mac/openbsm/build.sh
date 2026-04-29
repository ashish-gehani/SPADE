#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


# globals
C_SRC=
OUTPUT=


function print_help() {
    echo "Usage: $(basename "$0") --c-src <path> --output <path>"
    echo ""
    echo "Options:"
    echo "    --c-src   Path to the spadeOpenBSM C source file"
    echo "    --output  Output path for the spadeOpenBSM binary"
    echo "    --help    Show this message and exit"
    exit 0
}

function parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --c-src) C_SRC="$2"; shift 2 ;;
            --output) OUTPUT="$2"; shift 2 ;;
            --help) print_help ;;
            *) echo "Unknown argument: $1"; exit 1 ;;
        esac
    done
}

function validate_args() {
    if [[ -z "${C_SRC}" ]]; then
        echo "Error: --c-src is required"
        exit 1
    fi
    if [[ -z "${OUTPUT}" ]]; then
        echo "Error: --output is required"
        exit 1
    fi
}

function build() {
    gcc -o "${OUTPUT}" -lbsm "${C_SRC}"
}

function print_notice() {
    echo ''
    echo '-----> IMPORTANT: To use the OpenBSM reporter, please run the following commands to allow SPADE access to the audit stream:'
    echo '----->             sudo chown root '"${OUTPUT}"
    echo '----->             sudo chmod ug+s '"${OUTPUT}"
    echo ''
}

function main() {
    parse_args "$@"
    validate_args
    build
    print_notice
}

main "$@"
