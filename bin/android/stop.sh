#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


source "$( dirname "${BASH_SOURCE[0]}" )/../env.sh"

ADB="$(which adb 2>/dev/null)"
ANDROID_SDK_TOOLS="$(dirname "${ADB}")/"

if [[ -z "${ADB}" ]]; then
    echo "Error: adb not found. Please install Android SDK platform tools."
    exit 1
fi

# ${ANDROID_SDK_TOOLS}/adb shell "cd /sdcard/spade/android-build; dalvikvm -cp android-spade.jar spade.client.Android shutdown"
${ANDROID_SDK_TOOLS}/adb shell "cd /sdcard/spade; dalvikvm -cp android-spade.jar spade.client.Android shutdown"
