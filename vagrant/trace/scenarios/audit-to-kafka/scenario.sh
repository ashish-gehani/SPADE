#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# Scenario: replay a real auditd log through spade.reporter.Audit (FILE
# mode) into spade.storage.Kafka.

# 300s (5 min) -- a user-supplied audit log's size isn't known ahead of
# time, but is expected to be far smaller than the downloaded CDM trace.
SCENARIO_DONE_MARKER_TIMEOUT_SECONDS=300

function scenario_description() {
    echo "Replay an auditd log into Kafka storage"
}

function scenario_prepare_input() {
    mkdir -p "${ENV_DATASETS_AUDIT_LOG_HOME}"

    if [[ ! -s "${ENV_DATASETS_AUDIT_LOG_FILE}" ]]; then
        echo "No audit log at: ${ENV_DATASETS_AUDIT_LOG_FILE}"
        echo "Place a real auditd log there, or repoint ENV_DATASETS_AUDIT_LOG_FILE in env/datasets.sh, before running this scenario."
        exit 1
    fi
}

function scenario_install_config() {
    local output_file

    cp "${ENV_SCENARIO_SELECTED_HOME}/cfg/spade.storage.Kafka.config" "${ENV_SPADE_HOME}/cfg/"
    cp "${ENV_SCENARIO_SELECTED_HOME}/cfg/spade.reporter.Audit.config" "${ENV_SPADE_HOME}/cfg/"

    output_file="$(grep -oP '^kafka\.output\.file=\K.*' "${ENV_SPADE_HOME}/cfg/spade.storage.Kafka.config")"
    if [[ -n "${output_file}" ]]; then
        mkdir -p "$(dirname "${output_file}")"
    fi
}

function scenario_execute() {
    spade_run_control_command "add storage Kafka"
    spade_run_control_command "add reporter Audit inputLog=${ENV_DATASETS_AUDIT_LOG_FILE}"

    spade_wait_for_log_marker "Exiting event reader thread for SPADE audit bridge" "${SCENARIO_DONE_MARKER_TIMEOUT_SECONDS}"

    spade_run_control_command "remove reporter Audit"
    spade_run_control_command "remove storage Kafka"
}

function scenario_post_execution_work() {
    local installed_storage_config="${ENV_SPADE_HOME}/cfg/spade.storage.Kafka.config"
    local topic
    local output_file

    if grep -qP '^kafka\.output\.(server|topic|producer\.id)=' "${installed_storage_config}"; then
        topic="$(grep -oP '^kafka\.output\.topic=\K.*' "${installed_storage_config}")"
        helper_print_banner "Consuming from topic: ${topic}"
        kafka_broker_consume "${topic}"
    fi

    output_file="$(grep -oP '^kafka\.output\.file=\K.*' "${installed_storage_config}")"
    if [[ -n "${output_file}" ]]; then
        helper_print_banner "File writer output"
        echo "Written to: ${output_file}"
        echo "Convert to JSON with: bin/convert-storage-output-to-json.sh ${output_file}"
    fi
}
