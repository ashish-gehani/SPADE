#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

set -e

SPADE_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )"/../../.. && pwd )"
SPADE_BUILD="${SPADE_ROOT}/build"
SPADE_LIB="${SPADE_ROOT}/lib"
SPADE_SRC="${SPADE_ROOT}/src"

JAVA_HOME_DIR="$(java -classpath "${SPADE_BUILD}" spade.utility.JavaHome)"

javac -classpath "${SPADE_BUILD}:${SPADE_LIB}/*" -h "${SPADE_SRC}/spade/reporter" "${SPADE_SRC}/spade/reporter/MacFUSE.java"
gcc -dynamiclib \
    -I"${JAVA_HOME_DIR}" \
    -I"${JAVA_HOME_DIR}/darwin" \
    "${SPADE_SRC}/spade/reporter/libMacFUSE.c" \
    $(pkg-config fuse --cflags --libs) \
    -o "${SPADE_LIB}/libMacFUSE.jnilib"
