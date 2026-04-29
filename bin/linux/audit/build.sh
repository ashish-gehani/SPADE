#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

set -e

source "$( dirname "${BASH_SOURCE[0]}" )/../../env.sh"

KERNEL_MODULES=false
KERNEL_MODULES_DEBUG=false

function parse_args(){
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --kernel-modules)
                KERNEL_MODULES="$2"
                shift 2
                ;;
            --kernel-modules-debug)
                KERNEL_MODULES_DEBUG="$2"
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

    if [[ "${KERNEL_MODULES_DEBUG}" == "true" ]]; then
        KERNEL_MODULES_BUILD_TARGET="debug"
    else
        KERNEL_MODULES_BUILD_TARGET="release"
    fi

    gcc -o "${SPADE_AUDIT_BRIDGE}" "${SPADE_SRC}/spade/reporter/spadeAuditBridge.c"

    if [[ "${KERNEL_MODULES}" == "true" ]]; then
        make --no-print-directory -C "${AUDIT_KERNEL_MODULES_SRC}" \
            INSTALL_DIR="${AUDIT_KERNEL_MODULES_INSTALL}" \
            "${KERNEL_MODULES_BUILD_TARGET}" install
    else
        echo 'Skipping kernel modules build. KERNEL_MODULES is not set to true'
    fi

    echo ''
    echo '-----> IMPORTANT: To use the LinuxAudit reporter, please run the following commands to allow SPADE access to the audit stream:'
    echo '----->             sudo chown root '"${SPADE_AUDIT_BRIDGE}"
    echo '----->             sudo chmod ug+s '"${SPADE_AUDIT_BRIDGE}"
    echo ''
}

main "$@"
