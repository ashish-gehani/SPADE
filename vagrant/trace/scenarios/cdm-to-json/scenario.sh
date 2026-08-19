#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# Scenario: replay the downloaded DARPA TC CDM trace through
# spade.reporter.CDM into spade.storage.JSON. Round-tripping CDM output
# back into CDM storage (a "cdm-to-cdm" scenario, over the Kafka broker)
# isn't viable, so this scenario writes the graph straight to JSON
# instead -- no broker involved.

# 450s (7.5 min) -- generous headroom for streaming the ~1.3 GiB CDM trace
# through the reporter.
SCENARIO_DONE_MARKER_TIMEOUT_SECONDS=450

function scenario_description() {
    echo "Replay the DARPA TC CDM trace into JSON storage"
}

function scenario_prepare_input() {
    datasets_prepare_tc_trace
}

function scenario_install_config() {
    local output_file

    cp "${ENV_SCENARIO_SELECTED_HOME}/cfg/spade.storage.JSON.config" "${ENV_SPADE_HOME}/cfg/"

    output_file="$(grep -oP '^output=\K.*' "${ENV_SPADE_HOME}/cfg/spade.storage.JSON.config")"
    if [[ -n "${output_file}" ]]; then
        mkdir -p "$(dirname "${output_file}")"
    fi
}

function scenario_execute() {
    spade_run_control_command "add storage JSON"
    spade_run_control_command "add reporter CDM inputFile=${ENV_DATASETS_TC_TRACE_BINARY_FILE}"

    spade_wait_for_log_marker "Exiting data reader thread" "${SCENARIO_DONE_MARKER_TIMEOUT_SECONDS}"

    spade_run_control_command "remove reporter CDM"
    spade_run_control_command "remove storage JSON"
}

function scenario_post_execution_work() {
    local installed_storage_config="${ENV_SPADE_HOME}/cfg/spade.storage.JSON.config"
    local output_file

    output_file="$(grep -oP '^output=\K.*' "${installed_storage_config}")"
    if [[ -n "${output_file}" ]]; then
        helper_print_banner "JSON storage output"
        echo "Written to: ${output_file}"
    fi
}
