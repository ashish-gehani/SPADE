#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"


# globals
HOST_ANDROID_SPADE_JAR=""
HOST_ANDROID_CONTROL_SH=""
DEVICE_SDCARD=""


function print_help() {
    echo "Usage: $(basename "$0") --android_spade_jar <path> --android_control_sh <path> --device_sdcard <path>"
    echo ""
    echo "Options:"
    echo "    --android_spade_jar <path>  Path to the android-spade.jar on the host"
    echo "    --android_control_sh <path> Path to the control script on the host"
    echo "    --device_sdcard <path>      Device sdcard path"
    echo "    --help                      Show this message and exit"
    exit 0
}

function parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --android_spade_jar)  HOST_ANDROID_SPADE_JAR="$2";      shift 2 ;;
            --android_control_sh) HOST_ANDROID_CONTROL_SH="$2";     shift 2 ;;
            --device_sdcard)      DEVICE_SDCARD="$2";  shift 2 ;;
            --help)               print_help ;;
            *) echo "Unknown argument: $1"; exit 1 ;;
        esac
    done
}

function validate_args() {
    if [[ -z "${HOST_ANDROID_SPADE_JAR}" ]]; then
        echo "Error: --android_spade_jar is required"
        exit 1
    fi
    if [[ -z "${HOST_ANDROID_CONTROL_SH}" ]]; then
        echo "Error: --android_control_sh is required"
        exit 1
    fi
    if [[ -z "${DEVICE_SDCARD}" ]]; then
        echo "Error: --device_sdcard is required"
        exit 1
    fi
}

function setup_paths() {
    env_setup_device_paths "${DEVICE_SDCARD}"
}

function build_spade_control_config() {
    cat <<EOF
add filter IORuns 0
add storage Graphviz output=${ENV_DEVICE_SPADE_DIR}/audit.dot
add reporter Strace name=zygote user=radio user=system !name=/system/bin/surfaceflinger
EOF
}

function setup_device() {
    local spade_control_config
    spade_control_config="$(build_spade_control_config)"
    ${ENV_ADB} shell "rm -r ${ENV_DEVICE_SPADE_DIR}"
    ${ENV_ADB} shell "mkdir ${ENV_DEVICE_SPADE_DIR}"
    ${ENV_ADB} shell "mkdir ${ENV_DEVICE_SPADE_LOG_DIR}"
    ${ENV_ADB} shell "mkdir ${ENV_DEVICE_SPADE_CFG_DIR}"
    ${ENV_ADB} shell "echo \"${spade_control_config}\" > ${ENV_DEVICE_SPADE_CFG_CONTROL_FILE}"
    ${ENV_ADB} push "${HOST_ANDROID_SPADE_JAR}" "${ENV_DEVICE_SPADE_JAR}"
    ${ENV_ADB} push "${HOST_ANDROID_CONTROL_SH}" "${ENV_DEVICE_CONTROL_SH}"
}

function main() {
    parse_args "$@"
    validate_args
    setup_paths
    env_find_adb
    setup_device
}

main "$@"
