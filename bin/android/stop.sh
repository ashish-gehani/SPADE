#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


# constants
DEVICE_SDCARD="/sdcard"
DEVICE_SPADE_DIR="${DEVICE_SDCARD}/spade"
SPADE_CLIENT_JAR="android-spade.jar"
SPADE_CLIENT_CLASS="spade.client.Android"

# globals
ANDROID_SDK_TOOLS=""


print_help() {
    echo "Usage: $(basename "$0")"
    echo ""
    echo "Options:"
    echo "    --help    Show this message and exit"
    exit 0
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --help) print_help ;;
            *) echo "Unknown argument: $1"; print_help ;;
        esac
    done
}

validate_args() {
    :
}

find_adb() {
    local adb
    adb="$(which adb 2>/dev/null)"
    if [[ -z "${adb}" ]]; then
        echo "Error: adb not found. Please install Android SDK platform tools."
        exit 1
    fi
    ANDROID_SDK_TOOLS="$(dirname "${adb}")/"
}

stop_spade() {
    # ${ANDROID_SDK_TOOLS}/adb shell "cd ${DEVICE_SPADE_DIR}/android-build; dalvikvm -cp ${SPADE_CLIENT_JAR} ${SPADE_CLIENT_CLASS} shutdown"
    ${ANDROID_SDK_TOOLS}/adb shell "cd ${DEVICE_SPADE_DIR}; dalvikvm -cp ${SPADE_CLIENT_JAR} ${SPADE_CLIENT_CLASS} shutdown"
}

main() {
    parse_args "$@"
    validate_args
    find_adb
    stop_spade
}

main "$@"
