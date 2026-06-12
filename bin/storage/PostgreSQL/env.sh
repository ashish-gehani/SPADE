#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.
#
# Sourced by PostgreSQL storage scripts. Do not execute directly.


# constants
ENV_SPADE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"

# globals
ENV_CONFIG_FILE=""
ENV_DATABASE=""
ENV_USERNAME=""
ENV_PASSWORD=""

# unused, kept for future use
# ENV_DRIVER=""
# ENV_PROTOCOL=""
# ENV_HOST=""
# ENV_PORT=""
# ENV_BUFFER=""
# ENV_RESET=""
# ENV_SECONDARY_INDEXES=""
# ENV_FETCH=""


function env_get_config_value() {
    local key="$1"
    local value
    value="$(grep "^[[:space:]]*${key}[[:space:]]*=" "${ENV_CONFIG_FILE}" | tail -1 | cut -d '=' -f 2- | xargs)"
    echo "${value}"
}

function env_print_config() {
    echo "Config file: ${ENV_CONFIG_FILE}"
    echo "Database:    ${ENV_DATABASE}"
    echo "Username:    ${ENV_USERNAME}"
}

function env_load_config() {
    local config_file="$1"

    if [[ -z "${config_file}" ]]; then
        echo "Error: configuration file path is required"
        exit 1
    fi

    if [[ ! -f "${config_file}" ]]; then
        echo "Error: configuration file not found: ${config_file}"
        exit 1
    fi

    ENV_CONFIG_FILE="${config_file}"

    ENV_DATABASE="$(env_get_config_value "database")"
    ENV_USERNAME="$(env_get_config_value "username")"
    ENV_PASSWORD="$(env_get_config_value "password")"

    # unused, kept for future use
    # ENV_DRIVER="$(env_get_config_value "driver")"
    # ENV_PROTOCOL="$(env_get_config_value "protocol")"
    # ENV_HOST="$(env_get_config_value "host")"
    # ENV_PORT="$(env_get_config_value "port")"
    # ENV_BUFFER="$(env_get_config_value "buffer")"
    # ENV_RESET="$(env_get_config_value "reset")"
    # ENV_SECONDARY_INDEXES="$(env_get_config_value "secondaryIndexes")"
    # ENV_FETCH="$(env_get_config_value "fetch")"
}
