#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "${SCRIPT_DIR}/env.sh"
source "${SCRIPT_DIR}/helper.sh"


# globals
COMMAND=""


function print_help() {
    echo "Usage: $(basename "$0") --cmd <command>"
    echo ""
    echo "Manages the SPADE instance at ${ENV_SPADE_HOME}."
    echo ""
    echo "Commands:"
    echo "    setup                Install dependencies and build SPADE"
    echo "    publish_kafka_data   Start SPADE, add the Kafka storage and DSL reporter, feed"
    echo "                         it dummy provenance data, then stop SPADE"
    echo ""
    echo "Options:"
    echo "    --cmd <command>  Command to run"
    echo "    --help           Show this message and exit"
    exit 0
}

function parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --cmd) COMMAND="$2"; shift 2 ;;
            --help) print_help ;;
            *) echo "Unknown argument: $1"; exit 1 ;;
        esac
    done
}

function validate_args() {
    if [[ -z "${COMMAND}" ]]; then
        echo "Error: --cmd must not be empty"
        exit 1
    fi
    case "${COMMAND}" in
        setup|publish_kafka_data) ;;
        *) echo "Error: unknown command '${COMMAND}'"; exit 1 ;;
    esac
}

function install_java() {
    sudo apt-get update
    sudo apt-get install -y openjdk-21-jdk
}

function install_dependencies() {
    sudo apt-get install -y auditd autoconf automake bison clang cmake curl flex fuse git ifupdown libaudit-dev libfuse-dev linux-headers-`uname -r` lsof maven pkg-config unzip uthash-dev wget
}

function get_spade() {
    if [[ -d "${ENV_SPADE_HOME}" ]]; then
        return
    fi
    git clone --branch "${ENV_SPADE_REPO_BRANCH}" --depth 1 https://github.com/ashish-gehani/SPADE.git "${ENV_SPADE_HOME}"
}

function build_spade() {
    # ./configure and make are cwd-relative build tools; pushd/popd scopes the
    # directory change to just this function instead of leaking it to the rest
    # of the script.
    pushd "${ENV_SPADE_HOME}" > /dev/null
    ./configure
    make
    popd > /dev/null
}

function install_kafka_config() {
    cp "${ENV_SPADE_KAFKA_STORAGE_CONFIG_FILE}" "${ENV_SPADE_HOME}/cfg/"
}

function run_setup() {
    install_java
    install_dependencies
    get_spade
    build_spade
    install_kafka_config
}

function start_spade() {
    # No cd needed: bin/spade's own run() wrapper pushd's into SPADE_ROOT before
    # dispatching "start", so the JVM it forks already inherits the right cwd.
    "${ENV_SPADE_BIN}" start --mem-min 1g --mem-max 1g
}

function wait_for_control_port() {
    local i
    local ready

    ready=false
    for i in $(seq 1 "${ENV_SPADE_CONTROL_PORT_WAIT_ATTEMPTS}"); do
        if printf 'exit\n' | "${ENV_SPADE_BIN}" control; then
            ready=true
            break
        fi
        echo "Waiting for SPADE... (${i}/${ENV_SPADE_CONTROL_PORT_WAIT_ATTEMPTS})"
        sleep 2
    done

    if [[ "${ready}" != true ]]; then
        echo "SPADE control port never became ready"
        exit 1
    fi
    echo "SPADE control port ready"
}

function add_kafka_storage() {
    # Clear out any leftover output file from a previous run.
    if [[ -e "${ENV_SPADE_KAFKA_OUTPUT_FILE}" ]]; then
        rm -f "${ENV_SPADE_KAFKA_OUTPUT_FILE}"
    fi

    # run_setup installs cfg/spade.storage.Kafka.config with both writers enabled,
    # so no arguments are needed here; the storage picks it up as its default config.
    printf 'add storage Kafka\nexit\n' | "${ENV_SPADE_BIN}" control
}

function add_dsl_reporter() {
    # The DSL reporter refuses to start if a filesystem entry already exists at the pipe
    # path, so clear out any leftover from a previous run first.
    if [[ -e "${ENV_SPADE_DSL_PIPE}" ]]; then
        rm -f "${ENV_SPADE_DSL_PIPE}"
    fi

    printf 'add reporter DSL %s\nexit\n' "${ENV_SPADE_DSL_PIPE}" | "${ENV_SPADE_BIN}" control

    # Give the reporter a moment to create and open the pipe before writing to it
    sleep 2
}

function feed_dummy_data() {
    cat "${ENV_SPADE_DSL_INPUT_FILE}" > "${ENV_SPADE_DSL_PIPE}"
}

function wait_for_kafka_output() {
    local i
    local ready

    ready=false
    for i in $(seq 1 "${ENV_SPADE_KAFKA_OUTPUT_WAIT_ATTEMPTS}"); do
        if [[ -e "${ENV_SPADE_KAFKA_OUTPUT_FILE}" ]]; then
            if diff <(sort "${ENV_SPADE_KAFKA_EXPECTED_OUTPUT_FILE}") <(sort "${ENV_SPADE_KAFKA_OUTPUT_FILE}") > /dev/null; then
                ready=true
                break
            fi
        fi
        echo "Waiting for ${ENV_SPADE_KAFKA_OUTPUT_FILE} to match ${ENV_SPADE_KAFKA_EXPECTED_OUTPUT_FILE}... (${i}/${ENV_SPADE_KAFKA_OUTPUT_WAIT_ATTEMPTS})"
        sleep 2
    done

    if [[ "${ready}" != true ]]; then
        echo "${ENV_SPADE_KAFKA_OUTPUT_FILE} never matched ${ENV_SPADE_KAFKA_EXPECTED_OUTPUT_FILE}:"
        diff <(sort "${ENV_SPADE_KAFKA_EXPECTED_OUTPUT_FILE}") <(sort "${ENV_SPADE_KAFKA_OUTPUT_FILE}")
        exit 1
    fi
    echo "${ENV_SPADE_KAFKA_OUTPUT_FILE} matches ${ENV_SPADE_KAFKA_EXPECTED_OUTPUT_FILE}"
}

function remove_dsl_reporter() {
    printf 'remove reporter DSL\nexit\n' | "${ENV_SPADE_BIN}" control
}

function remove_kafka_storage() {
    printf 'remove storage Kafka\nexit\n' | "${ENV_SPADE_BIN}" control
}

function stop_spade() {
    "${ENV_SPADE_BIN}" stop
    sleep 2
}

function run_publish_kafka_data() {
    helper_print_banner "Starting SPADE"
    start_spade
    wait_for_control_port

    helper_print_banner "Adding Kafka storage"
    add_kafka_storage

    helper_print_banner "Adding DSL reporter"
    add_dsl_reporter

    helper_print_banner "Feeding dummy data"
    feed_dummy_data
    wait_for_kafka_output

    helper_print_banner "Removing DSL reporter"
    remove_dsl_reporter

    helper_print_banner "Removing Kafka storage"
    remove_kafka_storage

    helper_print_banner "Stopping SPADE"
    stop_spade
}

function handle_command() {
    case "${COMMAND}" in
        setup) run_setup ;;
        publish_kafka_data) run_publish_kafka_data ;;
    esac
}

function main() {
    parse_args "$@"
    validate_args
    handle_command
}

main "$@"
