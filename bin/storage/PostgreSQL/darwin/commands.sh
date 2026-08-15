#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.
#
# Darwin-specific PostgreSQL command implementations. Sourced by manage-service.sh.


# constants
DARWIN_PSQL_USER="postgres"
# Check https://formulae.brew.sh/api/formula/postgresql@<major>.json for
# "disabled"/"disable_date" before picking a replacement.
DARWIN_PKG="postgresql@15"
# Versioned formulae are keg-only (not symlinked onto PATH), hence the
# "brew link --force" in darwin_install() below.
DARWIN_DATA_DIR="$(brew --prefix 2>/dev/null)/var/${DARWIN_PKG}"


function darwin_is_installed() {
    brew list "${DARWIN_PKG}" &>/dev/null && echo 1 || echo 0
}

function darwin_psql() {
    psql -U "${DARWIN_PSQL_USER}" "$@"
}

function darwin_is_user_present() {
    local db_user="$1"
    local output
    output="$(darwin_psql --no-align --tuples-only --command "select 1 from pg_roles where rolname='${db_user}';")"
    [[ "${output}" == "1" ]] && echo 1 || echo 0
}

function darwin_is_db_present() {
    local db_name="$1"
    local output
    output="$(darwin_psql --no-align --tuples-only --command "select 1 from pg_database where datname='${db_name}';")"
    [[ "${output}" == "1" ]] && echo 1 || echo 0
}

function darwin_install() {
    if [[ "$(darwin_is_installed)" -eq 1 ]]; then
        echo "PostgreSQL package is already installed"
        return 0
    fi

    brew install "${DARWIN_PKG}" && \
        brew link --force "${DARWIN_PKG}" && \
        brew pin "${DARWIN_PKG}" && \
        brew services start "${DARWIN_PKG}" || return 1

    createuser -s "${DARWIN_PSQL_USER}"

    darwin_setup
}

function darwin_uninstall() {
    if [[ "$(darwin_is_installed)" -eq 0 ]]; then
        echo "PostgreSQL package is not installed"
        return 0
    fi

    brew unpin "${DARWIN_PKG}"
    brew services stop "${DARWIN_PKG}"
    brew remove "${DARWIN_PKG}"

    if [[ "${PURGE}" -eq 1 ]]; then
        rm -rf "${DARWIN_DATA_DIR}"
    fi
}

function darwin_setup() {
    if [[ "$(darwin_is_installed)" -eq 0 ]]; then
        echo "PostgreSQL package is not installed"
        return 0
    fi

    local db_name="${ENV_DATABASE}"
    local db_user="${ENV_USERNAME}"
    local db_pass="${ENV_PASSWORD}"

    # Create or update user
    if [[ "$(darwin_is_user_present "${db_user}")" -eq 0 ]]; then
        darwin_psql --command "create user ${db_user} with password '${db_pass}';" &>/dev/null \
            && echo "User ${db_user} created" \
            || { echo "Failed to setup user ${db_user}"; return 1; }
    else
        darwin_psql --command "alter user ${db_user} with password '${db_pass}';" &>/dev/null \
            && echo "User ${db_user} updated" \
            || { echo "Failed to setup user ${db_user}"; return 1; }
    fi

    darwin_psql --command "alter user ${db_user} with superuser;" &>/dev/null \
        && echo "User ${db_user} update successful" \
        || echo "User ${db_user} update failed"

    # Create database if not present
    if [[ "$(darwin_is_db_present "${db_name}")" -eq 0 ]]; then
        darwin_psql --command "create database ${db_name};" &>/dev/null \
            && echo "Database ${db_name} created" \
            || { echo "Failed to setup database ${db_name}"; return 1; }
    else
        echo "Database ${db_name} exists"
    fi

    darwin_psql --command "grant all privileges on database ${db_name} to ${db_user};" &>/dev/null \
        && echo "Database ${db_name} update successful" \
        || echo "Database ${db_name} update failed"
}

function darwin_info() {
    if [[ "$(darwin_is_installed)" -eq 0 ]]; then
        echo "PostgreSQL package is not installed"
        return 0
    fi

    brew info "${DARWIN_PKG}"
    echo ""

    if [[ "$(darwin_is_user_present "${ENV_USERNAME}")" -eq 1 ]]; then
        echo "User exists: ${ENV_USERNAME}"
    else
        echo "User not found: ${ENV_USERNAME}"
    fi

    if [[ "$(darwin_is_db_present "${ENV_DATABASE}")" -eq 1 ]]; then
        echo "Database exists: ${ENV_DATABASE}"
    else
        echo "Database not found: ${ENV_DATABASE}"
    fi
}

function darwin_connect() {
    darwin_psql -d "${ENV_DATABASE}"
}

function darwin_drop() {
    darwin_psql --command "drop database if exists ${ENV_DATABASE};" &>/dev/null \
        && echo "Database ${ENV_DATABASE} dropped" \
        || { echo "Failed to drop database ${ENV_DATABASE}"; return 1; }
}

function darwin_clear() {
    darwin_drop && darwin_setup
}
