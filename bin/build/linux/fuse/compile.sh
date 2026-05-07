#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# globals
JAVAC=""
JAVAC_OPTIONS=""
SPADE_BUILD=""
LIB_JAVA_SRC=""
LIB_C_SRC=""
NATIVE_HEADER_DIR=""
LIB_PATH=""


print_help() {
    echo "Usage: $(basename "$0") --javac <path> --spade-build <path> --lib-java-src <path> --lib-c-src <path> --native-header-dir <path> --lib-path <path> [--javac-options <opts>]"
    echo ""
    echo "Options:"
    echo "    --javac <path>              Path to the javac executable"
    echo "    --javac-options <opts>      Additional options passed to javac"
    echo "    --spade-build <path>        Path to the SPADE build directory"
    echo "    --lib-java-src <path>       Path to the Java source file"
    echo "    --lib-c-src <path>          Path to the C source file"
    echo "    --native-header-dir <path>  Path for JNI header output"
    echo "    --lib-path <path>           Output path for the shared library"
    echo "    --help                      Show this message and exit"
    exit 0
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --javac)
                JAVAC="$2"
                shift 2
                ;;
            --javac-options)
                JAVAC_OPTIONS="$2"
                shift 2
                ;;
            --spade-build)
                SPADE_BUILD="$2"
                shift 2
                ;;
            --lib-java-src)
                LIB_JAVA_SRC="$2"
                shift 2
                ;;
            --lib-c-src)
                LIB_C_SRC="$2"
                shift 2
                ;;
            --native-header-dir)
                NATIVE_HEADER_DIR="$2"
                shift 2
                ;;
            --lib-path)
                LIB_PATH="$2"
                shift 2
                ;;
            --help)
                print_help
                ;;
            *)
                echo "Unknown argument: $1"
                exit 1
                ;;
        esac
    done
}

validate_args() {
    if [[ -z "${JAVAC}" ]]; then
        echo "Error: --javac is required"
        exit 1
    fi
    if [[ -z "${SPADE_BUILD}" ]]; then
        echo "Error: --spade-build is required"
        exit 1
    fi
    if [[ ! -d "${SPADE_BUILD}" ]]; then
        echo "Error: --spade-build '${SPADE_BUILD}' is not a directory"
        exit 1
    fi
    if [[ -z "${LIB_JAVA_SRC}" ]]; then
        echo "Error: --lib-java-src is required"
        exit 1
    fi
    if [[ -z "${LIB_C_SRC}" ]]; then
        echo "Error: --lib-c-src is required"
        exit 1
    fi
    if [[ -z "${NATIVE_HEADER_DIR}" ]]; then
        echo "Error: --native-header-dir is required"
        exit 1
    fi
    if [[ -z "${LIB_PATH}" ]]; then
        echo "Error: --lib-path is required"
        exit 1
    fi
}

compile_java() {
    "${JAVAC}" ${JAVAC_OPTIONS} -h "${NATIVE_HEADER_DIR}" "${LIB_JAVA_SRC}"
}

build_native() {
    # TODO NOT GOOD!
    local java_home_dir
    java_home_dir="$(java -classpath "${SPADE_BUILD}" spade.utility.JavaHome)"
    export PKG_CONFIG_PATH=/usr/lib/pkgconfig
    gcc -fPIC -shared \
        -Wl,-soname,$(basename "${LIB_PATH}") \
        -I"${java_home_dir}" \
        -I"${java_home_dir}/linux" \
        -I"${NATIVE_HEADER_DIR}" \
        -Wall \
        "${LIB_C_SRC}" \
        $(pkg-config fuse --cflags --libs) \
        -o "${LIB_PATH}"
}

main() {
    parse_args "$@"
    validate_args
    compile_java
    build_native
}

main "$@"
