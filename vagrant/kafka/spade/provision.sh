#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


# constants
KAFKA_OUTPUT_FILE="/tmp/kafka-output.json"
DSL_PIPE="/tmp/spade_pipe"
# 2 Process vertices + 1 Artifact vertex + 2 edges written by feed_dummy_data
EXPECTED_RECORD_COUNT="5"
# 120 x 1s = 2 minutes
KAFKA_OUTPUT_WAIT_ATTEMPTS="120"

# globals
REPO_BRANCH="kafka-dev"


function print_help() {
    echo "Usage: $(basename "$0") [--repo-branch <branch>]"
    echo ""
    echo "Provisions this VM: builds SPADE, adds the Kafka storage in file-only mode,"
    echo "adds the DSL reporter, and feeds it a small amount of dummy provenance data."
    echo ""
    echo "Options:"
    echo "    --repo-branch <branch>  SPADE git branch to clone (default: ${REPO_BRANCH})"
    echo "    --help                  Show this message and exit"
    exit 0
}

function parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --repo-branch) REPO_BRANCH="$2"; shift 2 ;;
            --help) print_help ;;
            *) echo "Unknown argument: $1"; exit 1 ;;
        esac
    done
}

function validate_args() {
    if [[ -z "${REPO_BRANCH}" ]]; then
        echo "Error: --repo-branch must not be empty"
        exit 1
    fi
}

function install_java() {
    sudo apt-get update
    sudo apt-get install -y openjdk-21-jdk
}

function install_dependencies() {
    sudo apt-get install -y auditd autoconf automake bison clang cmake curl flex fuse git ifupdown libaudit-dev libfuse-dev linux-headers-`uname -r` lsof maven pkg-config unzip uthash-dev wget
}

function build_spade() {
    git clone --branch "${REPO_BRANCH}" --depth 1 https://github.com/ashish-gehani/SPADE.git
    cd SPADE
    ./configure
    make
}

function start_spade() {
    bin/spade start --mem-min 1g --mem-max 1g
}

function wait_for_control_port() {
    local i
    local ready

    ready=false
    for i in $(seq 1 30); do
        if printf 'exit\n' | bin/spade control; then
            ready=true
            break
        fi
        echo "Waiting for SPADE... (${i}/30)"
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
    if [[ -e "${KAFKA_OUTPUT_FILE}" ]]; then
        rm -f "${KAFKA_OUTPUT_FILE}"
    fi

    # kafka.output.file alone selects file-only mode; kafka.schema is always required.
    printf 'add storage Kafka kafka.output.file=%s kafka.schema=cfg/spade.storage.Kafka.avsc\nexit\n' "${KAFKA_OUTPUT_FILE}" | bin/spade control
}

function add_dsl_reporter() {
    # The DSL reporter refuses to start if a filesystem entry already exists at the pipe
    # path, so clear out any leftover from a previous run first.
    if [[ -e "${DSL_PIPE}" ]]; then
        rm -f "${DSL_PIPE}"
    fi

    printf 'add reporter DSL %s\nexit\n' "${DSL_PIPE}" | bin/spade control

    # Give the reporter a moment to create and open the pipe before writing to it
    sleep 2
}

function feed_dummy_data() {
    # Two processes, a file one of them reads, and the edges linking them.
    echo 'type:Process id:1 pid:100 name:bash' > "${DSL_PIPE}"
    echo 'type:Process id:2 pid:200 name:cat' >> "${DSL_PIPE}"
    echo 'type:Artifact subtype:file id:3 path:/etc/hostname' >> "${DSL_PIPE}"
    echo 'type:WasTriggeredBy operation:fork from:1 to:2 time:1' >> "${DSL_PIPE}"
    echo 'type:Used operation:read from:2 to:3 time:2' >> "${DSL_PIPE}"
}

function wait_for_kafka_output() {
    local i
    local count
    local ready

    ready=false
    for i in $(seq 1 "${KAFKA_OUTPUT_WAIT_ATTEMPTS}"); do
        count=0
        if [[ -e "${KAFKA_OUTPUT_FILE}" ]]; then
            count="$(grep -o '"hash"' "${KAFKA_OUTPUT_FILE}" | wc -l)"
        fi

        if [[ "${count}" -ge "${EXPECTED_RECORD_COUNT}" ]]; then
            ready=true
            break
        fi
        echo "Waiting for records in ${KAFKA_OUTPUT_FILE} (${count}/${EXPECTED_RECORD_COUNT})... (${i}/${KAFKA_OUTPUT_WAIT_ATTEMPTS})"
        sleep 1
    done

    if [[ "${ready}" != true ]]; then
        echo "Not all records appeared in ${KAFKA_OUTPUT_FILE} in time"
        exit 1
    fi
    echo "All ${EXPECTED_RECORD_COUNT} records present in ${KAFKA_OUTPUT_FILE}"
}

function remove_dsl_reporter() {
    printf 'remove reporter DSL\nexit\n' | bin/spade control
}

function remove_kafka_storage() {
    printf 'remove storage Kafka\nexit\n' | bin/spade control
}

function stop_spade() {
    bin/spade stop
    sleep 2
}

function show_output() {
    echo "==== Kafka file-writer output (${KAFKA_OUTPUT_FILE}) ===="
    cat "${KAFKA_OUTPUT_FILE}"
}

function main() {
    parse_args "$@"
    validate_args
    install_java
    install_dependencies
    build_spade
    start_spade
    wait_for_control_port
    add_kafka_storage
    add_dsl_reporter
    feed_dummy_data
    wait_for_kafka_output
    remove_dsl_reporter
    remove_kafka_storage
    stop_spade
    show_output
}

main "$@"
