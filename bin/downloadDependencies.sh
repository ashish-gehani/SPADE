#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# Downloads SPADE's Java dependencies from a git repository and copies the
# jars listed in cfg/java.classpath into lib/. Existing jars are left in place.
# The repo is cloned once to lib/.deps and reused on subsequent runs.


# globals
DEPS_REPO_URL=""
DEPS_REPO_COMMIT=""
DEPS_REPO_SUBDIR=""
SPADE_ROOT=""
SPADE_LIB=""
DEPS_REPO_DIR=""
JAVA_CLASSPATH_FILE=""


function print_help() {
    echo "Usage: $(basename "$0") --url <repo-url> --commit <commit> --spade-root <path>"
    echo ""
    echo "Options:"
    echo "    --url <repo-url>    Git URL of the dependencies repository"
    echo "    --commit <commit>   Commit hash or tag to check out"
    echo "    --spade-root <path> Path to the SPADE root directory"
    echo "    --subdir <path>     Subdirectory within the cloned repo where jars live (optional)"
    echo "    --help              Show this message and exit"
    exit 0
}

function parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --url)        DEPS_REPO_URL="$2";    shift 2 ;;
            --commit)     DEPS_REPO_COMMIT="$2"; shift 2 ;;
            --spade-root) SPADE_ROOT="$2";       shift 2 ;;
            --subdir)     DEPS_REPO_SUBDIR="$2"; shift 2 ;;
            --help)       print_help ;;
            *) echo "Unknown argument: $1"; exit 1 ;;
        esac
    done
}

function validate_args() {
    if [[ -z "${DEPS_REPO_URL}" ]]; then
        echo "Error: --url is required"
        exit 1
    fi
    if [[ -z "${DEPS_REPO_COMMIT}" ]]; then
        echo "Error: --commit is required"
        exit 1
    fi
    if [[ -z "${SPADE_ROOT}" ]]; then
        echo "Error: --spade-root is required"
        exit 1
    fi
    SPADE_LIB="${SPADE_ROOT}/lib"
    DEPS_REPO_DIR="${SPADE_LIB}/.deps"
    JAVA_CLASSPATH_FILE="${SPADE_ROOT}/cfg/java.classpath"
}

function check_git() {
    if ! command -v git &>/dev/null; then
        echo "Error: git not found. Please install git."
        exit 1
    fi
}

function clone_deps_repo() {
    if [[ -d "${DEPS_REPO_DIR}" ]]; then
        echo "Deps repo exists, skipping clone: ${DEPS_REPO_DIR}"
        return
    fi
    echo "Cloning deps repo: ${DEPS_REPO_URL}"
    git clone "${DEPS_REPO_URL}" "${DEPS_REPO_DIR}"
    git -C "${DEPS_REPO_DIR}" checkout "${DEPS_REPO_COMMIT}"
}

function copy_jars() {
    while IFS= read -r line; do
        if [[ ! "${line}" =~ ^lib/[^/]+\.jar$ ]]; then
            continue
        fi

        local jar_name
        jar_name="$(basename "${line}")"
        local dest="${SPADE_LIB}/${jar_name}"

        if [[ -f "${dest}" ]]; then
            echo "Exists, skipping: ${jar_name}"
            continue
        fi

        local src="${DEPS_REPO_DIR}/${DEPS_REPO_SUBDIR:+${DEPS_REPO_SUBDIR}/}${jar_name}"
        if [[ ! -f "${src}" ]]; then
            echo "Warning: ${jar_name} not found in deps repo"
            continue
        fi

        echo "Copying: ${jar_name}"
        cp "${src}" "${dest}"
    done < "${JAVA_CLASSPATH_FILE}"
}

function main() {
    parse_args "$@"
    validate_args
    check_git
    clone_deps_repo
    copy_jars
}

main "$@"
