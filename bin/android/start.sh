#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


# constants
DEVICE_SDCARD="/sdcard"
DEVICE_SPADE_DIR="${DEVICE_SDCARD}/spade"
DEVICE_LOG_DIR="${DEVICE_SPADE_DIR}/log"
DEVICE_CFG_DIR="${DEVICE_SPADE_DIR}/cfg"
DEVICE_CONFIG_FILE="${DEVICE_CFG_DIR}/spade.client.Control.config"
DEVICE_SPADE_JAR="${DEVICE_SPADE_DIR}/android-spade.jar"
DEVICE_CONTROL_SH="${DEVICE_SPADE_DIR}/control.sh"
SPADE_KERNEL_CLASS="spade.core.Kernel"

# globals
ANDROID_BUILD=""
ANDROID_SDK_TOOLS=""


print_help() {
    echo "Usage: $(basename "$0") --android_build <path>"
    echo ""
    echo "Options:"
    echo "    --android_build <path>    Path to the Android build output directory"
    echo "    --help                    Show this message and exit"
    exit 0
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --android_build) ANDROID_BUILD="$2"; shift 2 ;;
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

build_spade_config() {
    cat <<EOF
filter IORuns 0
storage Graphviz ${DEVICE_SPADE_DIR}/audit.dot
reporter Strace name=zygote user=radio user=system !name=/system/bin/surfaceflinger
EOF
}

setup_device() {
    local spade_config
    spade_config="$(build_spade_config)"
    ${ANDROID_SDK_TOOLS}/adb shell start
    ${ANDROID_SDK_TOOLS}/adb shell "rm -r ${DEVICE_SPADE_DIR}"
    ${ANDROID_SDK_TOOLS}/adb shell "mkdir ${DEVICE_SPADE_DIR}"
    ${ANDROID_SDK_TOOLS}/adb shell "mkdir ${DEVICE_LOG_DIR}"
    ${ANDROID_SDK_TOOLS}/adb shell "mkdir ${DEVICE_CFG_DIR}"
    # ${ANDROID_SDK_TOOLS}/adb shell "mkdir ${DEVICE_SPADE_DIR}/android-lib"
    # ${ANDROID_SDK_TOOLS}/adb shell "mkdir ${DEVICE_SPADE_DIR}/android-build"
    ${ANDROID_SDK_TOOLS}/adb shell "echo \"${spade_config}\" > ${DEVICE_CONFIG_FILE}"
    # for f in "android-build" "android-lib"; do ${ANDROID_SDK_TOOLS}/adb push $f ${DEVICE_SPADE_DIR}/$f; done
    ${ANDROID_SDK_TOOLS}/adb push "${ANDROID_BUILD}/android-spade.jar" "${DEVICE_SPADE_JAR}"
    ${ANDROID_SDK_TOOLS}/adb push "${ANDROID_BUILD}/control.sh" "${DEVICE_CONTROL_SH}"
    # ${ANDROID_SDK_TOOLS}/adb shell "cd ${DEVICE_SPADE_DIR}/android-build; dalvikvm -Xmx512M -cp android-spade.jar ${SPADE_KERNEL_CLASS} android"
    ${ANDROID_SDK_TOOLS}/adb shell "cd ${DEVICE_SPADE_DIR}; dalvikvm -Xmx512M -cp android-spade.jar ${SPADE_KERNEL_CLASS} android"
}

main() {
    parse_args "$@"
    validate_args
    find_adb
    setup_device
}

main "$@"
