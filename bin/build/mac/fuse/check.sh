#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


source "$(dirname "$0")/../../util/check/os.sh"
source "$(dirname "$0")/../../util/check/compiler.sh"
source "$(dirname "$0")/../../util/check/pkg.sh"

function check_fuse() {
    if pkg-config fuse &>/dev/null 2>&1; then
        return 0
    fi
    util_check_checking "fuse" "not found"
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

    util_check_pkg_has_pkg_config
    if (( $? != 0 )); then
        check_skip=1
    fi

    check_fuse
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
