#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# Scenario: replay the downloaded DARPA TC CDM trace through
# spade.reporter.CDM into spade.storage.Kafka.

# 450s (7.5 min) -- generous headroom for streaming the ~1.3 GiB CDM trace
# through the reporter.
SCENARIO_DONE_MARKER_TIMEOUT_SECONDS=450

function scenario_description() {
    echo "Replay the DARPA TC CDM trace into Kafka storage"
}

function scenario_prepare_input() {
    datasets_prepare_tc_trace
}

function scenario_install_config() {
    local output_file

    cp "${ENV_SCENARIO_SELECTED_HOME}/cfg/spade.storage.Kafka.config" "${ENV_SPADE_HOME}/cfg/"

    output_file="$(grep -oP '^kafka\.output\.file=\K.*' "${ENV_SPADE_HOME}/cfg/spade.storage.Kafka.config")"
    if [[ -n "${output_file}" ]]; then
        mkdir -p "$(dirname "${output_file}")"
    fi
}

function scenario_execute() {
    spade_run_control_command "add storage Kafka"
    spade_run_control_command "add reporter CDM inputFile=${ENV_DATASETS_TC_TRACE_BINARY_FILE}"

    spade_wait_for_log_marker "Exiting data reader thread" "${SCENARIO_DONE_MARKER_TIMEOUT_SECONDS}"

    spade_run_control_command "remove reporter CDM"
    spade_run_control_command "remove storage Kafka"
}

function scenario_post_execution_work() {
    local installed_storage_config="${ENV_SPADE_HOME}/cfg/spade.storage.Kafka.config"
    local topic
    local consumed_file="/tmp/trace/cdm-to-kafka/consumed-topic.txt"
    local output_file

    if grep -qP '^kafka\.output\.(server|topic|producer\.id)=' "${installed_storage_config}"; then
        topic="$(grep -oP '^kafka\.output\.topic=\K.*' "${installed_storage_config}")"
        helper_print_banner "Consuming from topic: ${topic}"
        kafka_broker_consume "${topic}" "${consumed_file}"
        echo "Written to: ${consumed_file}"
    fi

    output_file="$(grep -oP '^kafka\.output\.file=\K.*' "${installed_storage_config}")"
    if [[ -n "${output_file}" ]]; then
        helper_print_banner "File writer output"
        echo "Written to: ${output_file}"
        echo "Convert to JSON with: bin/convert-storage-output-to-json.sh ${output_file}"
    fi
}
