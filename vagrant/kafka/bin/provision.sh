#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "${SCRIPT_DIR}/env.sh"
source "${SCRIPT_DIR}/helper.sh"


# constants
BIN_DIR="${SCRIPT_DIR}"


function print_help() {
    echo "Usage: $(basename "$0")"
    echo ""
    echo "Provisions this VM: builds SPADE, sets up and starts a Kafka broker, publishes"
    echo "dummy provenance data to it via SPADE, prints the consumed data, then shuts"
    echo "down the Kafka broker."
    echo ""
    echo "Options:"
    echo "    --help  Show this message and exit"
    exit 0
}

function parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --help) print_help ;;
            *) echo "Unknown argument: $1"; exit 1 ;;
        esac
    done
}

function validate_args() {
    :
}

function setup_spade() {
    "${BIN_DIR}/manage-spade.sh" --cmd setup
}

function setup_kafka() {
    "${BIN_DIR}/manage-kafka.sh" --cmd setup
}

function start_kafka() {
    "${BIN_DIR}/manage-kafka.sh" --cmd start
}

function publish_kafka_data() {
    "${BIN_DIR}/manage-spade.sh" --cmd publish_kafka_data
}

function consume_kafka_data() {
    "${BIN_DIR}/manage-kafka.sh" --cmd consume
}

function shutdown_kafka() {
    "${BIN_DIR}/manage-kafka.sh" --cmd shutdown
}

function main() {
    parse_args "$@"
    validate_args

    helper_print_banner "Setting up SPADE"
    setup_spade

    helper_print_banner "Setting up Kafka broker"
    setup_kafka

    helper_print_banner "Starting Kafka broker"
    start_kafka

    helper_print_banner "Publishing SPADE provenance data to Kafka"
    publish_kafka_data

    helper_print_banner "Consuming data from Kafka"
    consume_kafka_data

    helper_print_banner "Shutting down Kafka broker"
    shutdown_kafka

    helper_print_banner "Provisioning complete"
}

main "$@"
