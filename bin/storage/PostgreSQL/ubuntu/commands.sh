#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.
#
# Ubuntu-specific PostgreSQL command implementations. Sourced by manage-service.sh.


# constants
UBUNTU_PSQL_USER="postgres"
UBUNTU_PG_VERSION="13"
UBUNTU_PKG="postgresql-${UBUNTU_PG_VERSION}"
UBUNTU_PKG_CLIENT="postgresql-client-${UBUNTU_PG_VERSION}"
UBUNTU_SOURCES_FILE="/etc/apt/sources.list.d/pgdg.list"
UBUNTU_REPO_KEY_ID="ACCC4CF8"
UBUNTU_REPO_KEY_URL="https://www.postgresql.org/media/keys/${UBUNTU_REPO_KEY_ID}.asc"


function ubuntu_is_installed() {
    apt list --installed 2>/dev/null | grep -q "${UBUNTU_PKG}" && echo 1 || echo 0
}

# postgresql-common assigns each cluster the next free port, so our cluster
# only gets 5432 if no other PostgreSQL version's cluster (e.g. a distro-
# preinstalled one, as on GitHub Actions Ubuntu runners) already claimed it.
function ubuntu_pg_port() {
    pg_lsclusters --no-header 2>/dev/null | awk -v ver="${UBUNTU_PG_VERSION}" '$1 == ver { print $3; exit }'
}

function ubuntu_psql() {
    (cd /tmp && sudo -u "${UBUNTU_PSQL_USER}" psql -p "$(ubuntu_pg_port)" "$@")
}

function ubuntu_is_user_present() {
    local db_user="$1"
    local output
    output="$(ubuntu_psql --no-align --tuples-only --command "select 1 from pg_roles where rolname='${db_user}';")"
    [[ "${output}" == "1" ]] && echo 1 || echo 0
}

function ubuntu_is_db_present() {
    local db_name="$1"
    local output
    output="$(ubuntu_psql --no-align --tuples-only --command "select 1 from pg_database where datname='${db_name}';")"
    [[ "${output}" == "1" ]] && echo 1 || echo 0
}

function ubuntu_wait_for_server() {
    local i
    local port
    for ((i = 0; i < 90; i++)); do
        port="$(ubuntu_pg_port)"
        [[ -n "${port}" ]] && pg_isready -q -p "${port}" && return 0
        sleep 1
    done
    echo "Error: PostgreSQL did not become ready in time"
    return 1
}

function ubuntu_install() {
    if [[ "$(ubuntu_is_installed)" -eq 1 ]]; then
        echo "PostgreSQL package is already installed"
        return 0
    fi

    local release_name
    sudo apt-get update && \
        sudo apt-get install -y lsb-release gnupg && \
        release_name="$(lsb_release -cs)" && \
        sudo sh -c "echo 'deb http://apt.postgresql.org/pub/repos/apt ${release_name}-pgdg main' > ${UBUNTU_SOURCES_FILE}" && \
        wget --quiet -O - "${UBUNTU_REPO_KEY_URL}" | sudo apt-key add - && \
        sudo apt-get update && \
        sudo DEBIAN_FRONTEND=noninteractive apt-get -y install "${UBUNTU_PKG}" || return 1

    ubuntu_wait_for_server || return 1

    ubuntu_setup
}

function ubuntu_uninstall() {
    if [[ "$(ubuntu_is_installed)" -eq 0 ]]; then
        echo "PostgreSQL package is not installed"
        return 0
    fi

    local uninstall_cmd="remove"
    [[ "${PURGE}" -eq 1 ]] && uninstall_cmd="purge"

    sudo DEBIAN_FRONTEND=noninteractive apt-get "${uninstall_cmd}" -y "${UBUNTU_PKG}" "${UBUNTU_PKG_CLIENT}"
    sudo apt-key del "${UBUNTU_REPO_KEY_ID}"
    sudo rm -f "${UBUNTU_SOURCES_FILE}"
}

function ubuntu_setup() {
    if [[ "$(ubuntu_is_installed)" -eq 0 ]]; then
        echo "PostgreSQL package is not installed"
        return 0
    fi

    local db_name="${ENV_DATABASE}"
    local db_user="${ENV_USERNAME}"
    local db_pass="${ENV_PASSWORD}"

    # if inside a docker container
    if [[ -f /.dockerenv ]]; then
        printf '#!/bin/sh\nexit 0' > /usr/sbin/policy-rc.d
        echo "Updated policy-rc.d for container"
    fi

    # Create or update user
    if [[ "$(ubuntu_is_user_present "${db_user}")" -eq 0 ]]; then
        ubuntu_psql --command "create user ${db_user} with password '${db_pass}';" &>/dev/null \
            && echo "User ${db_user} created" \
            || { echo "Failed to setup user ${db_user}"; return 1; }
    else
        ubuntu_psql --command "alter user ${db_user} with password '${db_pass}';" &>/dev/null \
            && echo "User ${db_user} updated" \
            || { echo "Failed to setup user ${db_user}"; return 1; }
    fi

    ubuntu_psql --command "alter user ${db_user} with superuser;" &>/dev/null \
        && echo "User ${db_user} update successful" \
        || echo "User ${db_user} update failed"

    # Create database if not present
    if [[ "$(ubuntu_is_db_present "${db_name}")" -eq 0 ]]; then
        ubuntu_psql --command "create database ${db_name};" &>/dev/null \
            && echo "Database ${db_name} created" \
            || { echo "Failed to setup database ${db_name}"; return 1; }
    else
        echo "Database ${db_name} exists"
    fi

    ubuntu_psql --command "grant all privileges on database ${db_name} to ${db_user};" &>/dev/null \
        && echo "Database ${db_name} update successful" \
        || echo "Database ${db_name} update failed"
}

function ubuntu_info() {
    if [[ "$(ubuntu_is_installed)" -eq 0 ]]; then
        echo "PostgreSQL package is not installed"
        return 0
    fi

    apt list --installed 2>/dev/null | grep "${UBUNTU_PKG}"
    echo ""

    if [[ "$(ubuntu_is_user_present "${ENV_USERNAME}")" -eq 1 ]]; then
        echo "User exists: ${ENV_USERNAME}"
    else
        echo "User not found: ${ENV_USERNAME}"
    fi

    if [[ "$(ubuntu_is_db_present "${ENV_DATABASE}")" -eq 1 ]]; then
        echo "Database exists: ${ENV_DATABASE}"
    else
        echo "Database not found: ${ENV_DATABASE}"
    fi
}

function ubuntu_connect() {
    ubuntu_psql -d "${ENV_DATABASE}"
}

function ubuntu_drop() {
    ubuntu_psql --command "drop database if exists ${ENV_DATABASE};" &>/dev/null \
        && echo "Database ${ENV_DATABASE} dropped" \
        || { echo "Failed to drop database ${ENV_DATABASE}"; return 1; }
}

function ubuntu_clear() {
    ubuntu_drop && ubuntu_setup
}
