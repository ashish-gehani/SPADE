# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.
#
# Sourced by check scripts. Do not execute directly.


source "$(dirname "${BASH_SOURCE[0]}")/check.sh"


function util_check_pkg_has_pkg_config() {
    if command -v pkg-config &>/dev/null; then
        util_check_checking "pkg-config" "found"
        return 0
    fi
    util_check_checking "pkg-config" "not found"
    return 1
}

function util_check_pkg_has_make() {
    if command -v make &>/dev/null; then
        util_check_checking "make" "found"
        return 0
    fi
    util_check_checking "make" "not found"
    return 1
}

function util_check_pkg_has_dx() {
    if command -v dx &>/dev/null; then
        util_check_checking "dx" "found"
        return 0
    fi
    util_check_checking "dx" "not found"
    return 1
}
