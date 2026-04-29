#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

set -e

source "$( dirname "${BASH_SOURCE[0]}" )/../../env.sh"

KERNEL_MODULES=false

function parse_args(){
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --kernel-modules)
                KERNEL_MODULES="$2"
                shift 2
                ;;
            *)
                echo "Unknown argument: $1"
                exit 1
                ;;
        esac
    done
}

function main(){
    parse_args "$@"

    rm -f "${SPADE_AUDIT_BRIDGE}"

    if [[ "${KERNEL_MODULES}" == "true" ]]; then
        make --no-print-directory -C "${AUDIT_KERNEL_MODULES_SRC}" \
            INSTALL_DIR="${AUDIT_KERNEL_MODULES_INSTALL}" \
            clean
    fi
}

main "$@"
