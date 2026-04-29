#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# Meant to be sourced by other scripts, not executed directly.

SPADE_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )"/../ && pwd )"
SPADE_BIN="${SPADE_ROOT}/bin"
SPADE_BUILD="${SPADE_ROOT}/build"
SPADE_LIB="${SPADE_ROOT}/lib"
SPADE_SRC="${SPADE_ROOT}/src"
SPADE_BIN_LLVM="${SPADE_BIN}/llvm"

ANDROID_BUILD="${SPADE_ROOT}/android-build"
ANDROID_LIB="${SPADE_ROOT}/android-lib"

ADB="$(which adb 2>/dev/null)"
ANDROID_SDK_TOOLS="$(dirname "${ADB}")/"

JAVAC_CP_PATH="${SPADE_BIN}/classpath.sh"
JAVAC_CP="$("${JAVAC_CP_PATH}")"

JAVAC="$(which javac)"
JAVAC_OPTIONS="${EXTRA_JAVAC_OPTIONS:-} -Xlint:none -proc:none"

JAVA_HOME_DIR="$(java -classpath "${SPADE_BUILD}" spade.utility.JavaHome)"

AUDIT_KERNEL_MODULES_SRC="${AUDIT_KERNEL_MODULES_SRC:-${SPADE_SRC}/spade/reporter/audit/kernel-modules/}"
AUDIT_KERNEL_MODULES_INSTALL="${AUDIT_KERNEL_MODULES_INSTALL:-${SPADE_LIB}/kernel-modules}"

SPADE_AUDIT_BRIDGE="${SPADE_BIN}/spadeAuditBridge"
