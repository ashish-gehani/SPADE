#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


# globals
KERNEL_MODULES_SRC=""
KERNEL_MODULES_INSTALL=""


print_help() {
    echo "Usage: $(basename "$0") --kernel_modules_src <path> --kernel_modules_install <path>"
    echo ""
    echo "Options:"
    echo "    --kernel_modules_src <path>       Path to kernel modules source directory"
    echo "    --kernel_modules_install <path>   Path to kernel modules install directory"
    echo "    --help                            Show this message and exit"
    exit 0
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --kernel_modules_src)     KERNEL_MODULES_SRC="$2";     shift 2 ;;
            --kernel_modules_install) KERNEL_MODULES_INSTALL="$2"; shift 2 ;;
            --help)                   print_help ;;
            *) echo "Unknown argument: $1"; print_help ;;
        esac
    done
}

validate_args() {
    if [[ -z "${KERNEL_MODULES_SRC}" ]]; then
        echo "Error: --kernel_modules_src is required"
        exit 1
    fi
    if [[ -z "${KERNEL_MODULES_INSTALL}" ]]; then
        echo "Error: --kernel_modules_install is required"
        exit 1
    fi
}

clean() {
    make --no-print-directory -C "${KERNEL_MODULES_SRC}" \
        INSTALL_DIR="${KERNEL_MODULES_INSTALL}" \
        clean
}

main() {
    parse_args "$@"
    validate_args
    clean
}

main "$@"
