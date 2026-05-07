#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


# constants
SPADE_KERNEL_CLASS="spade.core.Kernel"

# globals
ANDROID_BUILD=""
ANDROID_SDK_TOOLS=""
ANDROID_DEVICE_SDCARD=""
ANDROID_DEVICE_SPADE_DIR=""
DEVICE_LOG_DIR=""
DEVICE_CFG_DIR=""
DEVICE_CONFIG_FILE=""
DEVICE_SPADE_JAR=""
DEVICE_CONTROL_SH=""
SPADE_JAR=""


print_help() {
    echo "Usage: $(basename "$0") --android_build <path> --device_sdcard <path> --device_spade_dir <path> --spade_jar <name>"
    echo ""
    echo "Options:"
    echo "    --android_build <path>      Path to the Android build output directory"
    echo "    --device_sdcard <path>      Device sdcard path"
    echo "    --device_spade_dir <path>   Device SPADE directory path"
    echo "    --spade_jar <name>          SPADE jar filename"
    echo "    --help                      Show this message and exit"
    exit 0
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --android_build)    ANDROID_BUILD="$2";    shift 2 ;;
            --device_sdcard)    ANDROID_DEVICE_SDCARD="$2";    shift 2 ;;
            --device_spade_dir) ANDROID_DEVICE_SPADE_DIR="$2"; shift 2 ;;
            --spade_jar)        SPADE_JAR="$2";        shift 2 ;;
            --help)             print_help ;;
            *) echo "Unknown argument: $1"; print_help ;;
        esac
    done
}

validate_args() {
    if [[ -z "${ANDROID_BUILD}" ]]; then
        echo "Error: --android_build is required"
        exit 1
    fi
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

setup_paths() {
    DEVICE_LOG_DIR="${ANDROID_DEVICE_SPADE_DIR}/log"
    DEVICE_CFG_DIR="${ANDROID_DEVICE_SPADE_DIR}/cfg"
    DEVICE_CONFIG_FILE="${DEVICE_CFG_DIR}/spade.client.Control.config"
    DEVICE_SPADE_JAR="${ANDROID_DEVICE_SPADE_DIR}/${SPADE_JAR}"
    DEVICE_CONTROL_SH="${ANDROID_DEVICE_SPADE_DIR}/control"
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
add filter IORuns 0
add storage Graphviz output=${ANDROID_DEVICE_SPADE_DIR}/audit.dot
add reporter Strace name=zygote user=radio user=system !name=/system/bin/surfaceflinger
EOF
}

setup_device() {
    local spade_config
    spade_config="$(build_spade_config)"
    ${ANDROID_SDK_TOOLS}/adb shell start
    ${ANDROID_SDK_TOOLS}/adb shell "rm -r ${ANDROID_DEVICE_SPADE_DIR}"
    ${ANDROID_SDK_TOOLS}/adb shell "mkdir ${ANDROID_DEVICE_SPADE_DIR}"
    ${ANDROID_SDK_TOOLS}/adb shell "mkdir ${DEVICE_LOG_DIR}"
    ${ANDROID_SDK_TOOLS}/adb shell "mkdir ${DEVICE_CFG_DIR}"
    # ${ANDROID_SDK_TOOLS}/adb shell "mkdir ${ANDROID_DEVICE_SPADE_DIR}/android-lib"
    # ${ANDROID_SDK_TOOLS}/adb shell "mkdir ${ANDROID_DEVICE_SPADE_DIR}/android-build"
    ${ANDROID_SDK_TOOLS}/adb shell "echo \"${spade_config}\" > ${DEVICE_CONFIG_FILE}"
    # for f in "android-build" "android-lib"; do ${ANDROID_SDK_TOOLS}/adb push $f ${ANDROID_DEVICE_SPADE_DIR}/$f; done
    ${ANDROID_SDK_TOOLS}/adb push "${ANDROID_BUILD}/android-spade.jar" "${DEVICE_SPADE_JAR}"
    ${ANDROID_SDK_TOOLS}/adb push "${ANDROID_BUILD}/control.sh" "${DEVICE_CONTROL_SH}"
    # ${ANDROID_SDK_TOOLS}/adb shell "cd ${ANDROID_DEVICE_SPADE_DIR}/android-build; dalvikvm -Xmx512M -cp android-spade.jar ${SPADE_KERNEL_CLASS} android"
    ${ANDROID_SDK_TOOLS}/adb shell "cd ${ANDROID_DEVICE_SPADE_DIR}; dalvikvm -Xmx512M -cp android-spade.jar ${SPADE_KERNEL_CLASS} android"
}

main() {
    parse_args "$@"
    validate_args
    setup_paths
    find_adb
    setup_device
}

main "$@"
