#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


source "$(dirname "$0")/../../util/check/os"
source "$(dirname "$0")/../../util/check/distro"
source "$(dirname "$0")/../../util/check/pkg"

# constants
MIN_KERNEL_VERSION="5.4"
MAX_KERNEL_VERSION="6.8"


function check_not_wsl() {
    local kernel_release
    kernel_release="$(uname -r)"
    if [[ "${kernel_release}" != *"Microsoft"* ]]; then
        util_check_checking "wsl" "not detected"
        return 0
    fi
    util_check_checking "wsl" "detected (not supported)"
    return 1
}

function check_arch() {
    local arch
    arch="$(uname -m)"
    if [[ "${arch}" == "x86_64" ]]; then
        util_check_checking "arch" "${arch}"
        return 0
    fi
    util_check_checking "arch" "${arch} (only x86_64 supported)"
    return 1
}

function version_lte() {
    printf '%s\n' "$1" "$2" | sort -V --check=silent 2>/dev/null
}

function check_kernel_version() {
    local kernel_release kernel_version
    kernel_release="$(uname -r)"
    kernel_version="$(echo "${kernel_release}" | cut -d '.' -f 1,2)"

    if [[ -z "${kernel_version}" ]]; then
        util_check_checking "kernel version" "failed to parse"
        return 1
    fi

    if version_lte "${MIN_KERNEL_VERSION}" "${kernel_version}" && \
       version_lte "${kernel_version}" "${MAX_KERNEL_VERSION}"; then
        util_check_checking "kernel version" "${kernel_release}"
        return 0
    fi
    util_check_checking "kernel version" "${kernel_release} (must be between ${MIN_KERNEL_VERSION} and ${MAX_KERNEL_VERSION})"
    return 1
}

function check_kernel_headers() {
    local headers_path
    headers_path="/lib/modules/$(uname -r)/build"
    if [[ -d "${headers_path}" ]]; then
        util_check_checking "kernel headers" "${headers_path}"
        return 0
    fi
    util_check_checking "kernel headers" "${headers_path} not found"
    return 1
}

function check_prereqs() {
    local check_skip=0
    util_check_init_status_file

    util_check_distro_is_ubuntu
    if (( $? != 0 )); then
        check_skip=1
    fi

    check_not_wsl
    if (( $? != 0 )); then
        check_skip=1
    fi

    check_arch
    if (( $? != 0 )); then
        check_skip=1
    fi

    check_kernel_version
    if (( $? != 0 )); then
        check_skip=1
    fi

    check_kernel_headers
    if (( $? != 0 )); then
        check_skip=1
    fi

    util_check_pkg_has_make
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
