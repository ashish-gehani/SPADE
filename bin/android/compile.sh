#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.



JAVAC=""
SPADE_BUILD=""
ANDROID_BUILD=""
JAVAC_OPTIONS=""
JAVA_SRC=""


print_help() {
    echo "Usage: $(basename "$0") --javac <path> --spade_build <path> --android_build <path> --javac_options <opts> --java_src <path>"
    echo ""
    echo "Options:"
    echo "    --javac <path>            Path to the javac compiler"
    echo "    --spade_build <path>      Path to the SPADE build output directory"
    echo "    --android_build <path>    Path to the Android build output directory"
    echo "    --javac_options <opts>    Additional javac options"
    echo "    --java_src <path>         Path to the Java source directory"
    echo "    --help                    Show this message and exit"
    exit 0
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --javac)         JAVAC="$2";          shift 2 ;;
            --spade_build)   SPADE_BUILD="$2";    shift 2 ;;
            --android_build) ANDROID_BUILD="$2";  shift 2 ;;
            --javac_options) JAVAC_OPTIONS="$2";  shift 2 ;;
            --java_src)      JAVA_SRC="$2";       shift 2 ;;
            --help)          print_help ;;
            *) echo "Unknown argument: $1"; print_help ;;
        esac
    done
}

validate_args() {
    if [[ -z "${JAVAC}" ]]; then
        echo "Error: --javac '${JAVAC}' is required"
        exit 1
    fi
    if [[ -z "${SPADE_BUILD}" ]]; then
        echo "Error: --spade_build is required"
        exit 1
    fi
    if [[ -z "${ANDROID_BUILD}" ]]; then
        echo "Error: --android_build is required"
        exit 1
    fi
    if [[ -z "${JAVAC_OPTIONS}" ]]; then
        echo "Error: --javac_options is required"
        exit 1
    fi
    if [[ -z "${JAVA_SRC}" ]]; then
        echo "Error: --java_src is required"
        exit 1
    fi
}

javac_compile() {
    ${JAVAC} ${JAVAC_OPTIONS} -sourcepath "${JAVA_SRC}" -d "${SPADE_BUILD}" "$@"
}

# TODO: compilation should not be done here
compile() {
    mkdir -p "${SPADE_BUILD}"
    javac_compile "${JAVA_SRC}"/spade/client/Android.java
    javac_compile "${JAVA_SRC}"/spade/core/*.java
    javac_compile "${JAVA_SRC}"/spade/utility/*.java
    javac_compile "${JAVA_SRC}"/spade/filter/IORuns.java
    javac_compile "${JAVA_SRC}"/spade/storage/Graphviz.java
    javac_compile "${JAVA_SRC}"/spade/reporter/Strace.java
}

package() {
    local dx
    local android_build_tools
    dx="$(which dx 2>/dev/null)"
    if [[ -z "${dx}" ]]; then
        echo "Error: dx not found. Please install Android SDK build tools."
        exit 1
    fi
    android_build_tools="$(dirname "${dx}")/"
    # mkdir -p android-lib
    # ${android_build_tools}/dx --dex --output=android-lib/lucene-core-3.5.0.jar lib/lucene-core-3.5.0.jar
    mkdir -p "${ANDROID_BUILD}"
    echo "dalvikvm -cp android-spade.jar spade.client.Android" > "${ANDROID_BUILD}/control.sh"
    cd "${SPADE_BUILD}"
    ${android_build_tools}/dx --dex --verbose --no-strict --output="${ANDROID_BUILD}/android-spade.jar" spade
}

main() {
    parse_args "$@"
    validate_args
    compile
    package
}

main "$@"
