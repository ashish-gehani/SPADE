#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


# globals
OUTPUT=


function print_help() {
    echo "Usage: $(basename "$0") --output <path>"
    echo ""
    echo "Options:"
    echo "    --output  Path to the spadeOpenBSM binary"
    echo "    --help    Show this message and exit"
    exit 0
}

function parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --output) OUTPUT="$2"; shift 2 ;;
            --help) print_help ;;
            *) echo "Unknown argument: $1"; exit 1 ;;
        esac
    done
}

function validate_args() {
    if [[ -z "${OUTPUT}" ]]; then
        echo "Error: --output is required"
        exit 1
    fi
}

function clean() {
    rm -f "${OUTPUT}"
}

function main() {
    parse_args "$@"
    validate_args
    clean
}

main "$@"
