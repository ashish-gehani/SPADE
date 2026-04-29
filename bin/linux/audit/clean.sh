#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


# globals
AUDIT_BRIDGE=""


print_help() {
    echo "Usage: $(basename "$0") --audit_bridge <path>"
    echo ""
    echo "Options:"
    echo "    --audit_bridge <path>   Path to the audit bridge binary"
    echo "    --help                  Show this message and exit"
    exit 0
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --audit_bridge) AUDIT_BRIDGE="$2"; shift 2 ;;
            --help)         print_help ;;
            *) echo "Unknown argument: $1"; print_help ;;
        esac
    done
}

validate_args() {
    if [[ -z "${AUDIT_BRIDGE}" ]]; then
        echo "Error: --audit_bridge is required"
        exit 1
    fi
}

clean() {
    rm -f "${AUDIT_BRIDGE}"
}

main() {
    parse_args "$@"
    validate_args
    clean
}

main "$@"
