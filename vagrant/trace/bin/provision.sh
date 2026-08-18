#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# The one driver for this VM. Scenario-agnostic -- reads which scenario is
# selected (env/scenario.sh's ENV_SCENARIO_SELECTED), sources the generic
# env/common libraries plus that scenario's own scenario.sh, then runs the
# same pipeline regardless of which scenario it ended up being. See
# README.md for the narrative version of the steps below.

BIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TRACE_HOME="$(dirname "${BIN_DIR}")"


function load_libraries() {
    source "${TRACE_HOME}/env/env.sh"

    for f in "${BIN_DIR}"/common/*.sh; do
        source "${f}"
    done
}

function load_selected_scenario() {
    local valid=false
    local name

    for name in "${ENV_SCENARIO_AVAILABLE[@]}"; do
        if [[ "${name}" == "${ENV_SCENARIO_SELECTED}" ]]; then
            valid=true
            break
        fi
    done

    if [[ "${valid}" != true ]]; then
        echo "No such scenario: ${ENV_SCENARIO_SELECTED}"
        echo "Valid scenarios:"
        for name in "${ENV_SCENARIO_AVAILABLE[@]}"; do
            echo "  ${name}"
        done
        exit 1
    fi

    source "${ENV_SCENARIO_SELECTED_HOME}/scenario.sh"
}

function setup_spade() {
    spade_setup
}

function setup_kafka() {
    kafka_broker_setup
}

function start_kafka() {
    kafka_broker_start
}

function prepare_scenario_input() {
    scenario_prepare_input
}

function install_scenario_config() {
    scenario_install_config
}

function run_scenario() {
    spade_start
    spade_wait_for_control_port

    scenario_execute

    spade_stop
}

function report_scenario_output() {
    scenario_post_execution_work
}

function shutdown_kafka() {
    kafka_broker_shutdown
}

function scenario_banner_text() {
    scenario_description
}

# Runs on every exit from main -- success, an explicit `exit 1` from deep
# inside a common/*.sh or scenario.sh function, or a signal. Stops
# whichever of SPADE/the Kafka broker is still actually up, so a failure
# partway through a run (e.g. a failed control command, a scenario's input
# check) never leaves either running in the background. Cheap no-op on the
# happy path, where run_scenario/shutdown_kafka have already stopped both.
function cleanup() {
    if declare -F spade_is_up > /dev/null && spade_is_up; then
        helper_print_banner "Cleanup: stopping SPADE"
        spade_stop
    fi

    if declare -F kafka_broker_is_up > /dev/null && kafka_broker_is_up; then
        helper_print_banner "Cleanup: shutting down Kafka broker"
        kafka_broker_shutdown
    fi
}

function main() {
    trap cleanup EXIT

    load_libraries
    load_selected_scenario

    helper_print_banner "Scenario: ${ENV_SCENARIO_SELECTED} -- $(scenario_banner_text)"

    helper_print_banner "Setting up SPADE"
    setup_spade

    helper_print_banner "Setting up Kafka broker"
    setup_kafka

    helper_print_banner "Starting Kafka broker"
    start_kafka

    helper_print_banner "Preparing scenario input"
    prepare_scenario_input

    helper_print_banner "Installing scenario config"
    install_scenario_config

    helper_print_banner "Running scenario"
    run_scenario

    helper_print_banner "Scenario output"
    report_scenario_output

    helper_print_banner "Shutting down Kafka broker"
    shutdown_kafka

    helper_print_banner "Provisioning complete"
}

main "$@"
