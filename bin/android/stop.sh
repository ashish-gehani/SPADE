#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


ADB="$(which adb 2>/dev/null)"
if [[ -n "${ADB}" ]]; then
    ANDROID_SDK_TOOLS="$(dirname "${ADB}")/"
else
    ANDROID_SDK_TOOLS=""
fi

# ${ANDROID_SDK_TOOLS}/adb shell "cd /sdcard/spade/android-build; dalvikvm -cp android-spade.jar spade.client.Android shutdown"
${ANDROID_SDK_TOOLS}/adb shell "cd /sdcard/spade; dalvikvm -cp android-spade.jar spade.client.Android shutdown"
