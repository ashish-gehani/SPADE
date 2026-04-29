#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


# globals
AUDIT_BRIDGE=""
SPADE_SRC=""


print_help() {
    echo "Usage: $(basename "$0") --audit_bridge <path> --spade_src <path>"
    echo ""
    echo "Options:"
    echo "    --audit_bridge <path>   Output path for the compiled audit bridge binary"
    echo "    --spade_src <path>      Path to the SPADE source directory"
    echo "    --help                  Show this message and exit"
    exit 0
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --audit_bridge) AUDIT_BRIDGE="$2"; shift 2 ;;
            --spade_src)    SPADE_SRC="$2";    shift 2 ;;
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
    if [[ -z "${SPADE_SRC}" ]]; then
        echo "Error: --spade_src is required"
        exit 1
    fi
}

print_post_build_instructions() {
    echo ''
    echo '-----> IMPORTANT: To use the LinuxAudit reporter, please run the following commands to allow SPADE access to the audit stream:'
    echo '----->             sudo chown root '"${AUDIT_BRIDGE}"
    echo '----->             sudo chmod ug+s '"${AUDIT_BRIDGE}"
    echo ''
}

build() {
    gcc -o "${AUDIT_BRIDGE}" "${SPADE_SRC}/spade/reporter/spadeAuditBridge.c"
    print_post_build_instructions
}

main() {
    parse_args "$@"
    validate_args
    build
}

main "$@"
