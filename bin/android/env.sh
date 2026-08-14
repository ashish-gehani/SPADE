#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.
#
# Sourced by android scripts. Do not execute directly.


# globals
ENV_ADB=""

ENV_DEVICE_SPADE_DIR=""
ENV_DEVICE_SPADE_LOG_DIR=""
ENV_DEVICE_SPADE_CFG_DIR=""
ENV_DEVICE_SPADE_CFG_CONTROL_FILE=""
ENV_DEVICE_SPADE_JAR=""
ENV_DEVICE_CONTROL_SH=""


function env_find_adb() {
    local adb
    adb="$(which adb 2>/dev/null)"
    if [[ -z "${adb}" ]]; then
        echo "Error: adb not found. Please install Android SDK platform tools."
        exit 1
    fi
    ENV_ADB="${adb}"
}

function env_setup_device_paths() {
    local device_sdcard="$1"
    if [[ -z "${device_sdcard}" ]]; then
        echo "Error: setup_device_paths requires device_sdcard argument"
        exit 1
    fi
    ENV_DEVICE_SPADE_DIR="${device_sdcard}/spade"
    ENV_DEVICE_SPADE_LOG_DIR="${ENV_DEVICE_SPADE_DIR}/log"
    ENV_DEVICE_SPADE_CFG_DIR="${ENV_DEVICE_SPADE_DIR}/cfg"
    ENV_DEVICE_SPADE_CFG_CONTROL_FILE="${ENV_DEVICE_SPADE_CFG_DIR}/spade.client.Control.config"
    ENV_DEVICE_SPADE_JAR="${ENV_DEVICE_SPADE_DIR}/android-spade.jar"
    ENV_DEVICE_CONTROL_SH="${ENV_DEVICE_SPADE_DIR}/control.sh"
}
