#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


# constants
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOWNLOAD_URL="https://dist.neo4j.org/neo4j-community-5.26.25-unix.tar.gz"

# globals
COMMAND=""
EXTRA_ARGS=()
NEO4J_CONFIG_FILE="${PROJECT_ROOT}/cfg/spade.storage.Neo4j.config"
NEO4J_DIR_PATH=""
DOWNLOAD_DIR_PATH=""


function read_config_and_set_globals() {
    if [[ ! -f "${NEO4J_CONFIG_FILE}" ]]; then
        echo "Error: config file not found: ${NEO4J_CONFIG_FILE}"
        exit 1
    fi
    local neo4j_dir_relative
    neo4j_dir_relative="$(awk -F' *= *' '/^dbms\.directories\.neo4j_home/{print $2}' "${NEO4J_CONFIG_FILE}")"
    if [[ -z "${neo4j_dir_relative}" ]]; then
        echo "Error: key 'dbms.directories.neo4j_home' not found in: ${NEO4J_CONFIG_FILE}"
        exit 1
    fi
    NEO4J_DIR_PATH="${PROJECT_ROOT}/${neo4j_dir_relative}"
    DOWNLOAD_DIR_PATH="$(dirname "${NEO4J_DIR_PATH}")"
}

function print_help() {
    echo "Usage: $(basename "$0") <command>"
    echo ""
    echo "Commands:"
    echo "    info        Show the currently installed Neo4j version"
    echo "    clean       Remove the downloaded Neo4j installation"
    echo "    install     Install Neo4j"
    echo "    start       Start Neo4j"
    echo "    stop        Stop Neo4j"
    echo ""
    echo "Options:"
    echo "    --config <path>    Path to Neo4j config file (default: ${NEO4J_CONFIG_FILE})"
    echo "    --help             Show this message and exit"
    exit 0
}

function parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --help) print_help ;;
            --config) NEO4J_CONFIG_FILE="$2"; shift 2 ;;
            info|clean|install) COMMAND="$1"; shift ;;
            start|stop) COMMAND="$1"; shift; EXTRA_ARGS=("$@"); break ;;
            *) echo "Unknown argument: $1"; exit 1 ;;
        esac
    done
}

function validate_args() {
    if [[ -z "${COMMAND}" ]]; then
        echo "Error: command required"
        echo ""
        print_help
    fi
}

function cmd_info() {
    if [[ ! -d "${NEO4J_DIR_PATH}" ]]; then
        echo "Neo4j is not installed"
        return
    fi
    local version
    local packaging_info_path
    packaging_info_path="${NEO4J_DIR_PATH}/packaging_info"
    version=""
    if [[ -f "${packaging_info_path}" ]]; then
        version="$(grep -i -m1 '^version:' "${packaging_info_path}" | cut -d: -f2)"
    fi
    if [[ -z "${version}" ]]; then
        echo "Neo4j installed at: ${NEO4J_DIR_PATH} (version unknown)"
    else
        echo "Neo4j ${version} installed at: ${NEO4J_DIR_PATH}"
    fi
}

function cmd_clean() {
    if [[ ! -d "${NEO4J_DIR_PATH}" ]]; then
        echo "Neo4j is not installed"
        return
    fi
    echo "Warning: this will permanently delete Neo4j and all its data at: ${NEO4J_DIR_PATH}"
    local response
    read -r -p "Proceed? [y/N] " response
    if [[ "${response}" != "y" ]] && [[ "${response}" != "Y" ]]; then
        echo "Aborted"
        exit 0
    fi
    rm -rf "${NEO4J_DIR_PATH}"
    echo "Removed: ${NEO4J_DIR_PATH}"
}

function cmd_start() {
    "${NEO4J_DIR_PATH}/bin/neo4j" start "${EXTRA_ARGS[@]}"
}

function cmd_stop() {
    "${NEO4J_DIR_PATH}/bin/neo4j" stop "${EXTRA_ARGS[@]}"
}

function cmd_install() {
    if [[ -d "${NEO4J_DIR_PATH}" ]]; then
        echo "Neo4j is already installed at: ${NEO4J_DIR_PATH}"
        return
    fi
    "${PROJECT_ROOT}/bin/downloadNeo4j" \
        --url "${DOWNLOAD_URL}" \
        --download-dir "${DOWNLOAD_DIR_PATH}" \
        --neo4j-dir "${NEO4J_DIR_PATH}"
}

function main() {
    parse_args "$@"
    read_config_and_set_globals
    validate_args

    if [[ "${COMMAND}" = "info" ]]; then
        cmd_info
    elif [[ "${COMMAND}" = "clean" ]]; then
        cmd_clean
    elif [[ "${COMMAND}" = "install" ]]; then
        cmd_install
    elif [[ "${COMMAND}" = "start" ]]; then
        cmd_start
    elif [[ "${COMMAND}" = "stop" ]]; then
        cmd_stop
    fi
}

main "$@"
