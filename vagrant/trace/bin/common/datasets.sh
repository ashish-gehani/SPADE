#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# Generic Google Drive download helper, plus the TC-trace-specific
# download+decompress-once logic used by the cdm-to-* scenarios.

# Local aliases for the env/ constants this file actually uses.
DATASETS_TC_TRACE_FILE_ID="${ENV_DATASETS_TC_TRACE_FILE_ID}"
DATASETS_TC_TRACE_HOME="${ENV_DATASETS_TC_TRACE_HOME}"
DATASETS_TC_TRACE_ARCHIVE_FILE="${ENV_DATASETS_TC_TRACE_ARCHIVE_FILE}"
DATASETS_TC_TRACE_BINARY_FILE="${ENV_DATASETS_TC_TRACE_BINARY_FILE}"


# $1: file to check
function datasets_is_html_response() {
    local file_path="$1"

    LC_ALL=C grep -qi -m1 '<html' "${file_path}" 2>/dev/null
}

# $1: Google Drive file id
# $2: destination file path
function datasets_google_drive_download() {
    local file_id="$1"
    local dest_path="$2"
    local cookie_jar
    local page_file
    local confirm_token

    mkdir -p "$(dirname "${dest_path}")"

    curl -fsSL "https://drive.usercontent.google.com/download?id=${file_id}&export=download&confirm=t" -o "${dest_path}"

    # TODO: the direct request above is the only path actually exercised
    # against the real TC trace file so far (worked first try, no
    # interstitial page). Everything below -- the HTML sniff and the
    # cookie/confirm-token fallback -- is written defensively for a Drive
    # antivirus-warning page this file never actually triggered. Figure out
    # what Drive's interstitial page looks like today and verify this
    # fallback actually handles it before relying on it for a different/
    # larger file.
    if datasets_is_html_response "${dest_path}"; then
        cookie_jar="$(mktemp)"
        page_file="$(mktemp)"

        curl -fsSL -c "${cookie_jar}" "https://drive.google.com/uc?export=download&id=${file_id}" -o "${page_file}"
        confirm_token="$(grep -o 'confirm=[0-9A-Za-z_-]*' "${page_file}" | head -n1 | cut -d= -f2)"

        if [[ -n "${confirm_token}" ]]; then
            curl -fsSL -b "${cookie_jar}" "https://drive.google.com/uc?export=download&confirm=${confirm_token}&id=${file_id}" -o "${dest_path}"
        else
            cp "${page_file}" "${dest_path}"
        fi

        rm -f "${cookie_jar}" "${page_file}"
    fi

    if [[ ! -s "${dest_path}" ]] || datasets_is_html_response "${dest_path}"; then
        echo "Failed to download from Google Drive (file id ${file_id}) to ${dest_path}"
        exit 1
    fi
}

# Downloads and decompresses the DARPA TC trace once, into
# ENV_DATASETS_TC_TRACE_HOME. Skips whichever step already has output, so
# re-provisioning doesn't re-download/re-decompress ~1.6 GB every time.
# Shared by both cdm-to-* scenarios -- see PLAN.md's "Isolation guarantees"
# for why one shared, read-only copy is safe.
function datasets_prepare_tc_trace() {
    mkdir -p "${DATASETS_TC_TRACE_HOME}"

    if [[ ! -s "${DATASETS_TC_TRACE_ARCHIVE_FILE}" ]]; then
        datasets_google_drive_download "${DATASETS_TC_TRACE_FILE_ID}" "${DATASETS_TC_TRACE_ARCHIVE_FILE}"
    fi

    if [[ ! -s "${DATASETS_TC_TRACE_BINARY_FILE}" ]]; then
        gunzip -k -c "${DATASETS_TC_TRACE_ARCHIVE_FILE}" > "${DATASETS_TC_TRACE_BINARY_FILE}"
    fi
}
