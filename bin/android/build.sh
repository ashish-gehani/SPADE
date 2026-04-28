#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


source "$( dirname "${BASH_SOURCE[0]}" )/../env.sh"

DX="$(which dx 2>/dev/null)"
if [[ -z "${DX}" ]]; then
    echo "Error: dx not found. Please install Android SDK build tools."
    exit 1
fi
ANDROID_BUILD_TOOLS="$(dirname "${DX}")/"

mkdir -p "${SPADE_BUILD}"
${JAVAC} ${JAVAC_OPTIONS} -cp "${JAVAC_CP}" -sourcepath src -d "${SPADE_BUILD}" src/spade/client/Android.java
${JAVAC} ${JAVAC_OPTIONS} -cp "${JAVAC_CP}" -sourcepath src -d "${SPADE_BUILD}" src/spade/core/*.java
${JAVAC} ${JAVAC_OPTIONS} -cp "${JAVAC_CP}" -sourcepath src -d "${SPADE_BUILD}" src/spade/utility/*.java
${JAVAC} ${JAVAC_OPTIONS} -cp "${JAVAC_CP}" -sourcepath src -d "${SPADE_BUILD}" src/spade/filter/IORuns.java
${JAVAC} ${JAVAC_OPTIONS} -cp "${JAVAC_CP}" -sourcepath src -d "${SPADE_BUILD}" src/spade/storage/Graphviz.java
${JAVAC} ${JAVAC_OPTIONS} -cp "${JAVAC_CP}" -sourcepath src -d "${SPADE_BUILD}" src/spade/reporter/Strace.java
# mkdir -p android-lib
# ${ANDROID_BUILD_TOOLS}/dx --dex --output=android-lib/lucene-core-3.5.0.jar lib/lucene-core-3.5.0.jar
mkdir -p "${ANDROID_BUILD}"
echo "dalvikvm -cp android-spade.jar spade.client.Android" > "${ANDROID_BUILD}/control.sh"
cd "${SPADE_BUILD}"
${ANDROID_BUILD_TOOLS}/dx --dex --verbose --no-strict --output="${ANDROID_BUILD}/android-spade.jar" spade
