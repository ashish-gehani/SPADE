#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


# constants
SPADE_CLIENT_CLASS="spade.client.Android"

# globals
ANDROID_SDK_TOOLS=""
ANDROID_DEVICE_SDCARD=""
ANDROID_DEVICE_SPADE_DIR=""
SPADE_JAR=""


print_help() {
    echo "Usage: $(basename "$0") --device_sdcard <path> --device_spade_dir <path> --spade_jar <name>"
    echo ""
    echo "Options:"
    echo "    --device_sdcard <path>      Device sdcard path"
    echo "    --device_spade_dir <path>   Device SPADE directory path"
    echo "    --spade_jar <name>          SPADE jar filename"
    echo "    --help                      Show this message and exit"
    exit 0
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --device_sdcard)    ANDROID_DEVICE_SDCARD="$2";    shift 2 ;;
            --device_spade_dir) ANDROID_DEVICE_SPADE_DIR="$2"; shift 2 ;;
            --spade_jar)        SPADE_JAR="$2";        shift 2 ;;
            --help)             print_help ;;
            *) echo "Unknown argument: $1"; print_help ;;
        esac
    done
}

validate_args() {
    if [[ -z "${ANDROID_DEVICE_SDCARD}" ]]; then
        echo "Error: --device_sdcard is required"
        exit 1
    fi
    if [[ -z "${ANDROID_DEVICE_SPADE_DIR}" ]]; then
        echo "Error: --device_spade_dir is required"
        exit 1
    fi
    if [[ -z "${SPADE_JAR}" ]]; then
        echo "Error: --spade_jar is required"
        exit 1
    fi
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
    # ${ANDROID_SDK_TOOLS}/adb shell "cd ${ANDROID_DEVICE_SPADE_DIR}/android-build; dalvikvm -cp ${SPADE_JAR} ${SPADE_CLIENT_CLASS} shutdown"
    ${ANDROID_SDK_TOOLS}/adb shell "cd ${ANDROID_DEVICE_SPADE_DIR}; dalvikvm -cp ${SPADE_JAR} ${SPADE_CLIENT_CLASS} shutdown"
}

main() {
    parse_args "$@"
    validate_args
    find_adb
    stop_spade
}

main "$@"
