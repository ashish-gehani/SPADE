#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


# constants
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"


function print_help() {
    echo "Usage: $(basename "$0")"
    echo ""
    echo "Runs autoreconf -fi in every configure.ac directory, bottom-up."
    echo ""
    echo "Options:"
    echo "    --help    Show this message and exit"
    exit 0
}

function parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --help) print_help ;;
            *) echo "Unknown argument: $1"; exit 1 ;;
        esac
    done
}

function validate_args() {
    :
}

function run_autoreconf() {
    local dirs=(
        "${REPO_ROOT}/module/android"
        "${REPO_ROOT}/module/java"
        "${REPO_ROOT}/module/linux/audit_bridge"
        "${REPO_ROOT}/module/linux/fuse"
        "${REPO_ROOT}/module/linux/kernel_module"
        "${REPO_ROOT}/module/linux/llvm"
        "${REPO_ROOT}/module/linux"
        "${REPO_ROOT}/module/mac/fuse"
        "${REPO_ROOT}/module/mac/llvm"
        "${REPO_ROOT}/module/mac/openbsm"
        "${REPO_ROOT}/module/mac"
        "${REPO_ROOT}"
    )

    local dir
    for dir in "${dirs[@]}"; do
        echo "==> autoreconf -fi: ${dir}"
        pushd "${dir}" > /dev/null
        autoreconf -fi
        popd > /dev/null
    done
}

function main() {
    parse_args "$@"
    validate_args
    run_autoreconf
}

main "$@"
