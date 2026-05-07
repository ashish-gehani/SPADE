#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


source "$(dirname "$0")/../../util/check/distro.sh"
source "$(dirname "$0")/../../util/check/compiler.sh"


function check_prereqs() {
    local check_skip=0
    util_check_init_status_file

    util_check_distro_is_ubuntu
    if (( $? != 0 )); then
        check_skip=1
    fi

    util_check_compiler_has_gcc
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
