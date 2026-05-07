#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


source "$(dirname "$0")/../../util/check/os.sh"
source "$(dirname "$0")/../../util/check/compiler.sh"


function check_bsm() {
    if [[ -f "/usr/lib/libbsm.dylib" ]]; then
        util_check_checking "libbsm" "found"
        return 0
    fi
    util_check_checking "libbsm" "not found"
    return 1
}

function check_prereqs() {
    local check_skip=0
    util_check_init_status_file

    util_check_os_is_darwin
    if (( $? != 0 )); then
        check_skip=1
    fi

    util_check_compiler_has_gcc
    if (( $? != 0 )); then
        check_skip=1
    fi

    check_bsm
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
