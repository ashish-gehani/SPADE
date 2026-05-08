# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.
#
# Sourced by check scripts. Do not execute directly.


# globals
UTIL_CHECK_SILENT=0
UTIL_CHECK_STATUS_FILE=""


function util_check_validate_args() {
    :
}

function util_check_parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --silent)      UTIL_CHECK_SILENT=1;         shift ;;
            --status-file) UTIL_CHECK_STATUS_FILE="$2"; shift 2 ;;
            --help)        util_check_print_help ;;
            *) echo "Unknown argument: $1"; util_check_print_help ;;
        esac
    done
}

function util_check_print_help() {
    echo "Usage: $(basename "$0") [--silent] [--status-file <path>]"
    echo ""
    echo "Options:"
    echo "    --silent               Suppress messages for unmet prerequisites"
    echo "    --status-file <path>   Write result (0 or 1) to file instead of stdout"
    echo "    --help                 Show this message and exit"
    exit 0
}

function util_check_checking() {
    if [[ -n "${UTIL_CHECK_STATUS_FILE}" ]]; then
        echo "checking $1... $2" >> "${UTIL_CHECK_STATUS_FILE}"
    elif (( UTIL_CHECK_SILENT == 0 )); then
        echo "checking $1... $2"
    fi
}

function util_check_init_status_file() {
    if [[ -n "${UTIL_CHECK_STATUS_FILE}" ]]; then
        mkdir -p "$(dirname "${UTIL_CHECK_STATUS_FILE}")"
        > "${UTIL_CHECK_STATUS_FILE}"
    fi
}

function util_check_write_status() {
    if [[ -n "${UTIL_CHECK_STATUS_FILE}" ]]; then
        echo "$1" >> "${UTIL_CHECK_STATUS_FILE}"
    fi
    echo "$1"
}

function util_check_write_status_continue() {
    util_check_write_status "continue"
}

function util_check_write_status_skip() {
    util_check_write_status "skip"
}

# util_check_finalize <skip>: writes continue or skip status and exits. <skip> is 1 to skip, 0 to continue.
function util_check_finalize() {
    if (( $1 == 1 )); then
        util_check_write_status_skip
        exit 0
    fi
    util_check_write_status_continue
    exit 0
}
