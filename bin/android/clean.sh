#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


ANDROID_BUILD=""
ANDROID_LIB=""


print_help() {
    echo "Usage: $(basename "$0") --android_build <path> --android_lib <path>"
    echo ""
    echo "Options:"
    echo "    --android_build <path>    Path to the Android build output directory"
    echo "    --android_lib <path>      Path to the Android lib directory"
    echo "    --help                    Show this message and exit"
    exit 0
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --android_build) ANDROID_BUILD="$2"; shift 2 ;;
            --android_lib)   ANDROID_LIB="$2";   shift 2 ;;
            --help)          print_help ;;
            *) echo "Unknown argument: $1"; print_help ;;
        esac
    done
}

validate_args() {
    if [[ -z "${ANDROID_BUILD}" ]]; then
        echo "Error: --android_build is required"
        exit 1
    fi
    if [[ -z "${ANDROID_LIB}" ]]; then
        echo "Error: --android_lib is required"
        exit 1
    fi
}

clean() {
    rm -rf "${ANDROID_BUILD}"
    rm -rf "${ANDROID_LIB}"
}

main() {
    parse_args "$@"
    validate_args
    clean
}

main "$@"
