#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

set -e

source "$( dirname "${BASH_SOURCE[0]}" )/../../env.sh"

javac -classpath "${SPADE_BUILD}:${SPADE_LIB}/*" -h "${SPADE_SRC}/spade/reporter" "${SPADE_SRC}/spade/reporter/LinuxFUSE.java"

export PKG_CONFIG_PATH=/usr/lib/pkgconfig
gcc -fPIC -shared \
    -Wl,-soname,libLinuxFUSE.so \
    -I"${JAVA_HOME_DIR}" \
    -I"${JAVA_HOME_DIR}/linux" \
    -Wall \
    "${SPADE_SRC}/spade/reporter/libLinuxFUSE.c" \
    $(pkg-config fuse --cflags --libs) \
    -o "${SPADE_LIB}/libLinuxFUSE.so"

echo ''
echo '-----> IMPORTANT: To use the LinuxFUSE reporter, please enable "user_allow_other" in /etc/fuse.conf'
echo ''
