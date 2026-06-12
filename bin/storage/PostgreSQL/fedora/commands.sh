#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.
#
# Fedora-specific PostgreSQL command implementations. Sourced by manage-service.sh.


# constants
FEDORA_PSQL_USER="postgres"
FEDORA_PKG="postgresql-server-13.4"
FEDORA_DATA_DIR="/var/lib/pgsql"
FEDORA_HBA_FILE="${FEDORA_DATA_DIR}/data/pg_hba.conf"
FEDORA_HBA_LOCAL_ACCESS_LINE="host all all 127.0.0.1/32 md5"


function fedora_is_installed() {
    dnf list --installed "${FEDORA_PKG}" &>/dev/null && echo 1 || echo 0
}

function fedora_psql() {
    sudo -u "${FEDORA_PSQL_USER}" psql "$@"
}

function fedora_is_user_present() {
    local db_user="$1"
    local output
    output="$(fedora_psql --no-align --tuples-only --command "select 1 from pg_roles where rolname='${db_user}';")"
    [[ "${output}" == "1" ]] && echo 1 || echo 0
}

function fedora_is_db_present() {
    local db_name="$1"
    local output
    output="$(fedora_psql --no-align --tuples-only --command "select 1 from pg_database where datname='${db_name}';")"
    [[ "${output}" == "1" ]] && echo 1 || echo 0
}

function fedora_install() {
    if [[ "$(fedora_is_installed)" -eq 1 ]]; then
        echo "PostgreSQL package is already installed"
        return 0
    fi

    sudo dnf install -y "${FEDORA_PKG}" || return 1

    if sudo test ! -f "${FEDORA_HBA_FILE}"; then
        sudo postgresql-setup --initdb --unit postgresql || return 1
    fi

    sudo grep -q "${FEDORA_HBA_LOCAL_ACCESS_LINE}" "${FEDORA_HBA_FILE}" \
        || sudo sed -i -e "1i${FEDORA_HBA_LOCAL_ACCESS_LINE}" "${FEDORA_HBA_FILE}"

    sudo systemctl start postgresql || return 1

    fedora_setup
}

function fedora_uninstall() {
    if [[ "$(fedora_is_installed)" -eq 0 ]]; then
        echo "PostgreSQL package is not installed"
        return 0
    fi

    sudo dnf remove -y "${FEDORA_PKG}"

    if [[ "${PURGE}" -eq 1 ]]; then
        sudo rm -rf "${FEDORA_DATA_DIR}"
    fi
}

function fedora_setup() {
    if [[ "$(fedora_is_installed)" -eq 0 ]]; then
        echo "PostgreSQL package is not installed"
        return 0
    fi

    local db_name="${ENV_DATABASE}"
    local db_user="${ENV_USERNAME}"
    local db_pass="${ENV_PASSWORD}"

    # Create or update user
    if [[ "$(fedora_is_user_present "${db_user}")" -eq 0 ]]; then
        fedora_psql --command "create user ${db_user} with password '${db_pass}';" &>/dev/null \
            && echo "User ${db_user} created" \
            || { echo "Failed to setup user ${db_user}"; return 1; }
    else
        fedora_psql --command "alter user ${db_user} with password '${db_pass}';" &>/dev/null \
            && echo "User ${db_user} updated" \
            || { echo "Failed to setup user ${db_user}"; return 1; }
    fi

    fedora_psql --command "alter user ${db_user} with superuser;" &>/dev/null \
        && echo "User ${db_user} update successful" \
        || echo "User ${db_user} update failed"

    # Create database if not present
    if [[ "$(fedora_is_db_present "${db_name}")" -eq 0 ]]; then
        fedora_psql --command "create database ${db_name};" &>/dev/null \
            && echo "Database ${db_name} created" \
            || { echo "Failed to setup database ${db_name}"; return 1; }
    else
        echo "Database ${db_name} exists"
    fi

    fedora_psql --command "grant all privileges on database ${db_name} to ${db_user};" &>/dev/null \
        && echo "Database ${db_name} update successful" \
        || echo "Database ${db_name} update failed"
}

function fedora_info() {
    if [[ "$(fedora_is_installed)" -eq 0 ]]; then
        echo "PostgreSQL package is not installed"
        return 0
    fi

    dnf list --installed "${FEDORA_PKG}"
    echo ""

    if [[ "$(fedora_is_user_present "${ENV_USERNAME}")" -eq 1 ]]; then
        echo "User exists: ${ENV_USERNAME}"
    else
        echo "User not found: ${ENV_USERNAME}"
    fi

    if [[ "$(fedora_is_db_present "${ENV_DATABASE}")" -eq 1 ]]; then
        echo "Database exists: ${ENV_DATABASE}"
    else
        echo "Database not found: ${ENV_DATABASE}"
    fi
}

function fedora_connect() {
    fedora_psql -d "${ENV_DATABASE}"
}

function fedora_drop() {
    fedora_psql --command "drop database if exists ${ENV_DATABASE};" &>/dev/null \
        && echo "Database ${ENV_DATABASE} dropped" \
        || { echo "Failed to drop database ${ENV_DATABASE}"; return 1; }
}

function fedora_clear() {
    fedora_drop && fedora_setup
}
