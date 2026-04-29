#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# Downloads SPADE's Java dependencies from a git repository and copies the
# jars listed in cfg/java.classpath into lib/. Existing jars are left in place.
# The repo is cloned once to lib/.deps and reused on subsequent runs.
#
# Usage:
#   downloadDependencies.sh --url <repo-url> --commit <commit>
#
# Options:
#   --url     Git URL of the dependencies repository (required)
#   --commit  Commit hash or tag to check out (required)
#
# Globals (set before calling or modify in this file):
#   DEPS_REPO_SUBDIR  Subdirectory within the cloned repo where jars live.
#                     Leave empty if jars are at the repo root.

set -e

source "$( dirname "${BASH_SOURCE[0]}" )/env.sh"

DEPS_REPO_URL=""
DEPS_REPO_COMMIT=""
DEPS_REPO_SUBDIR=""
DEPS_REPO_DIR="${SPADE_LIB}/.deps"
JAVA_CLASSPATH_FILE="${SPADE_ROOT}/cfg/java.classpath"

function usage(){
    echo "Usage: $(basename "$0") --url <repo-url> --commit <commit>"
    echo ""
    echo "Options:"
    echo "  --url     Git URL of the dependencies repository (required)"
    echo "  --commit  Commit hash or tag to check out (required)"
    echo "  -h, --help  Show this help message"
}

function parse_args(){
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --url)
                DEPS_REPO_URL="$2"
                shift 2
                ;;
            --commit)
                DEPS_REPO_COMMIT="$2"
                shift 2
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            *)
                echo "Unknown argument: $1"
                usage
                exit 1
                ;;
        esac
    done

    if [[ -z "${DEPS_REPO_URL}" ]]; then
        echo "Error: --url is required"
        exit 1
    fi
    if [[ -z "${DEPS_REPO_COMMIT}" ]]; then
        echo "Error: --commit is required"
        exit 1
    fi
}

function check_git(){
    if ! command -v git &>/dev/null; then
        echo "Error: git not found. Please install git."
        exit 1
    fi
}

function clone_deps_repo(){
    if [[ -d "${DEPS_REPO_DIR}" ]]; then
        echo "Deps repo exists, skipping clone: ${DEPS_REPO_DIR}"
        return
    fi
    echo "Cloning deps repo: ${DEPS_REPO_URL}"
    git clone "${DEPS_REPO_URL}" "${DEPS_REPO_DIR}"
    git -C "${DEPS_REPO_DIR}" checkout "${DEPS_REPO_COMMIT}"
}

function copy_jars(){
    while IFS= read -r line; do
        # only process direct lib/*.jar entries
        [[ "${line}" =~ ^lib/[^/]+\.jar$ ]] || continue

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

function main(){
    parse_args "$@"
    check_git
    clone_deps_repo
    copy_jars
}

main "$@"
