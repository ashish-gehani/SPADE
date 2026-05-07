#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


source "$(dirname "$0")/../../util/check/os.sh"

# constants
LLVM_PATH="/var/clang+llvm-3.0-x86_64-apple-darwin11"


function check_llvm_dir() {
    if [[ -d "${LLVM_PATH}" ]]; then
        util_check_checking "llvm" "${LLVM_PATH}"
        return 0
    fi
    util_check_checking "llvm" "${LLVM_PATH} not found"
    return 1
}

function check_llvm_tool() {
    local tool="$1"
    local path="${LLVM_PATH}/bin/${tool}"
    if [[ -x "${path}" ]]; then
        util_check_checking "${tool}" "found"
        return 0
    fi
    util_check_checking "${tool}" "not found"
    return 1
}

function check_prereqs() {
    local check_skip=0
    util_check_init_status_file

    util_check_os_is_darwin
    if (( $? != 0 )); then
        check_skip=1
    fi

    check_llvm_dir
    if (( $? != 0 )); then
        util_check_finalize 1
    fi

    check_llvm_tool "clang"
    if (( $? != 0 )); then
        check_skip=1
    fi

    check_llvm_tool "opt"
    if (( $? != 0 )); then
        check_skip=1
    fi

    check_llvm_tool "llc"
    if (( $? != 0 )); then
        check_skip=1
    fi

    check_llvm_tool "llvm-config"
    if (( $? != 0 )); then
        check_skip=1
    fi

    util_check_finalize "${check_skip}"
}

function main() {
    util_check_parse_args "$@"
    util_check_validate_args
    check_prereqs
}

main "$@"
