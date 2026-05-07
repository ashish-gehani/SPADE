#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


source "$(dirname "$0")/../../util/check/distro.sh"
source "$(dirname "$0")/../../util/check/compiler.sh"

# constants
LLVM_INCLUDE_PATH="/usr/include/llvm-3.6"


function check_llvm_include() {
    if [[ -d "${LLVM_INCLUDE_PATH}" ]]; then
        util_check_checking "llvm include" "${LLVM_INCLUDE_PATH}"
        return 0
    fi
    util_check_checking "llvm include" "${LLVM_INCLUDE_PATH} not found"
    return 1
}

function check_prereqs() {
    local check_skip=0
    util_check_init_status_file

    util_check_distro_is_ubuntu
    if (( $? != 0 )); then
        check_skip=1
    fi

    util_check_compiler_has_clang
    if (( $? != 0 )); then
        check_skip=1
    fi

    util_check_compiler_has_clang_plus_plus
    if (( $? != 0 )); then
        check_skip=1
    fi

    check_llvm_include
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
