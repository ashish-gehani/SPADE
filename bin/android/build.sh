#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


SPADE_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )"/../../ && pwd )"
SPADE_BUILD="${SPADE_ROOT}/build"
ANDROID_BUILD="${SPADE_ROOT}/android-build"

EXTRA_JAVAC_OPTIONS=

JAVAC="$(which javac)"
JAVAC_CP="$("${SPADE_ROOT}/bin/classpath.sh")"
JAVAC_OPTIONS="${EXTRA_JAVAC_OPTIONS} -Xlint:none -proc:none"

DX="$(which dx 2>/dev/null)"
if [[ -n "${DX}" ]]; then
    ANDROID_BUILD_TOOLS="$(dirname "${DX}")/"
else
    ANDROID_BUILD_TOOLS=""
fi

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
