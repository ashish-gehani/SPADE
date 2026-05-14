#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"


# constants
SPADE_KERNEL_CLASS="spade.core.Kernel"

# globals
DEVICE_SDCARD=""


function print_help() {
    echo "Usage: $(basename "$0") --device_sdcard <path>"
    echo ""
    echo "Options:"
    echo "    --device_sdcard <path>  Device sdcard path"
    echo "    --help                  Show this message and exit"
    exit 0
}

function parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --device_sdcard) DEVICE_SDCARD="$2"; shift 2 ;;
            --help)          print_help ;;
            *) echo "Unknown argument: $1"; exit 1 ;;
        esac
    done
}

function validate_args() {
    if [[ -z "${DEVICE_SDCARD}" ]]; then
        echo "Error: --device_sdcard is required"
        exit 1
    fi
}

function start_spade() {
    ${ENV_ADB} shell "cd ${ENV_DEVICE_SPADE_DIR}; dalvikvm -Xmx512M -cp ${ENV_DEVICE_SPADE_JAR} ${SPADE_KERNEL_CLASS} android"
}

function main() {
    parse_args "$@"
    validate_args
    env_setup_device_paths "${DEVICE_SDCARD}"
    env_find_adb
    start_spade
}

main "$@"
