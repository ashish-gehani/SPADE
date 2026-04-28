#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


SPADE_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )"/../../ && pwd )"
ANDROID_BUILD="${SPADE_ROOT}/android-build"

ADB="$(which adb 2>/dev/null)"
if [[ -n "${ADB}" ]]; then
    ANDROID_SDK_TOOLS="$(dirname "${ADB}")/"
else
    ANDROID_SDK_TOOLS=""
fi

${ANDROID_SDK_TOOLS}/adb shell start
${ANDROID_SDK_TOOLS}/adb shell "rm -r /sdcard/spade"
${ANDROID_SDK_TOOLS}/adb shell "mkdir /sdcard/spade"
${ANDROID_SDK_TOOLS}/adb shell "mkdir /sdcard/spade/log"
${ANDROID_SDK_TOOLS}/adb shell "mkdir /sdcard/spade/cfg"
# ${ANDROID_SDK_TOOLS}/adb shell "mkdir /sdcard/spade/android-lib"
# ${ANDROID_SDK_TOOLS}/adb shell "mkdir /sdcard/spade/android-build"
SPADE_CONFIG="filter IORuns 0\nstorage Graphviz /sdcard/spade/audit.dot\nreporter Strace name=zygote user=radio user=system !name=/system/bin/surfaceflinger"
${ANDROID_SDK_TOOLS}/adb shell "echo -e \"${SPADE_CONFIG}\" > /sdcard/spade/cfg/spade.client.Control.config"
# for f in "android-build" "android-lib"; do ${ANDROID_SDK_TOOLS}/adb push $f /sdcard/spade/$f; done
${ANDROID_SDK_TOOLS}/adb push "${ANDROID_BUILD}/android-spade.jar" /sdcard/spade/android-spade.jar
${ANDROID_SDK_TOOLS}/adb push "${ANDROID_BUILD}/control.sh" /sdcard/spade/control.sh
# ${ANDROID_SDK_TOOLS}/adb shell "cd /sdcard/spade/android-build; dalvikvm -Xmx512M -cp android-spade.jar spade.core.Kernel android"
${ANDROID_SDK_TOOLS}/adb shell "cd /sdcard/spade; dalvikvm -Xmx512M -cp android-spade.jar spade.core.Kernel android"
