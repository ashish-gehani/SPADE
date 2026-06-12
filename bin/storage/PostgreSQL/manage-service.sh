#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"


# constants
ENV_CONFIG_FILE="${ENV_SPADE_ROOT}/cfg/spade.storage.PostgreSQL.config"

# globals
COMMAND=""
CONFIG_PATH="${ENV_CONFIG_FILE}"
PURGE=0
OS_ID=""


function print_help() {
    echo "Usage: $(basename "$0") <command>"
    echo ""
    echo "Commands:"
    echo "    install     Install PostgreSQL"
    echo "    uninstall   Uninstall PostgreSQL and delete ALL existing databases"
    echo "    setup       Setup PostgreSQL user and database for SPADE"
    echo "    info        Display PostgreSQL info"
    echo "    connect     Connect to the PostgreSQL database"
    echo "    clear       Clear the database"
    echo "    drop        Drop the database"
    echo ""
    echo "Options:"
    echo "    -c, --config <path>  Path to SPADE PostgreSQL storage configuration. Default: '${ENV_CONFIG_FILE}'"
    echo "    -p, --purge          Delete data on uninstall"
    echo "    --help               Show this message and exit"
    exit 0
}

function parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --help) print_help ;;
            install|uninstall|setup|info|connect|clear|drop) COMMAND="$1"; shift ;;
            -c|--config) CONFIG_PATH="$2"; shift 2 ;;
            -p|--purge) PURGE=1; shift ;;
            *) echo "Unknown argument: $1"; exit 1 ;;
        esac
    done
}

function validate_args() {
    if [[ -z "${COMMAND}" ]]; then
        echo "Error: command is required"
        print_help
    fi
}

function detect_os() {
    local kernel
    kernel="$(uname)"

    if [[ "${kernel}" == "Darwin" ]]; then
        OS_ID="darwin"
    elif [[ "${kernel}" == "Linux" ]] && [[ -f /etc/os-release ]]; then
        OS_ID="$(grep "^ID=" /etc/os-release | cut -d '=' -f 2 | tr -d '"')"
    fi

    case "${OS_ID}" in
        ubuntu|fedora|darwin) ;;
        *) echo "Error: unsupported platform: '${OS_ID}'. Allowed: ubuntu, fedora, darwin"; exit 1 ;;
    esac
}

function load_os_commands() {
    source "$(dirname "${BASH_SOURCE[0]}")/${OS_ID}/commands.sh"
}

# OS command API
#
# Each <os>/commands.sh (ubuntu, fedora, darwin) must define the following
# functions, named "<os>_<action>" and invoked via dispatch() with no
# positional arguments. Each function reads the globals noted below.
#
#   <os>_install    Install PostgreSQL.
#   <os>_uninstall  Uninstall PostgreSQL.
#                    Reads: PURGE (1 also deletes data)
#   <os>_setup      Create/update the SPADE db user and database.
#                    Reads: ENV_DATABASE, ENV_USERNAME, ENV_PASSWORD
#   <os>_info       Print install, user, and database status.
#                    Reads: ENV_USERNAME, ENV_DATABASE
#   <os>_connect    Open an interactive psql session to the SPADE database.
#                    Reads: ENV_DATABASE
#   <os>_clear      Drop and recreate the SPADE database.
#                    Reads: ENV_DATABASE, ENV_USERNAME, ENV_PASSWORD
#   <os>_drop       Drop the SPADE database.
#                    Reads: ENV_DATABASE
function dispatch() {
    local action="$1"
    "${OS_ID}_${action}"
}

function handle_install()   { dispatch install; }
function handle_uninstall() { dispatch uninstall; }
function handle_setup()     { dispatch setup; }
function handle_info()      { env_print_config; echo ""; dispatch info; }
function handle_connect()   { dispatch connect; }
function handle_clear()     { dispatch clear; }
function handle_drop()      { dispatch drop; }

function main() {
    parse_args "$@"
    validate_args
    detect_os
    load_os_commands
    env_load_config "${CONFIG_PATH}"

    case "${COMMAND}" in
        install)   handle_install ;;
        uninstall) handle_uninstall ;;
        setup)     handle_setup ;;
        info)      handle_info ;;
        connect)   handle_connect ;;
        clear)     handle_clear ;;
        drop)      handle_drop ;;
    esac
}

main "$@"
