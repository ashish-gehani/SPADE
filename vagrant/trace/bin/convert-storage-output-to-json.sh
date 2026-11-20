#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# Standalone, NOT called by provision.sh. Converts a storage file-writer
# output file (raw Avro binary, self-describing -- schema embedded) into
# JSON, using the avro-tools jar SPADE's own build already pulls in.
#
# Usage: bin/convert-storage-output-to-json.sh <input>.bin

# Disable HotSpot Base64 intrinsics to mitigate Java4Shell. https://calif.io/java4shell
JAVA_BASE64_INTRINSICS_FLAG="-XX:-UseBASE64Intrinsics"

BIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TRACE_HOME="$(dirname "${BIN_DIR}")"

source "${TRACE_HOME}/env/env.sh"
source "${BIN_DIR}/common/util.sh"


function main() {
    local input_file="$1"
    local avro_tools_jar
    local output_file

    if [[ -z "${input_file}" ]]; then
        echo "Usage: $(basename "$0") <input>.bin"
        exit 1
    fi

    if [[ ! -f "${input_file}" ]]; then
        echo "No such file: ${input_file}"
        exit 1
    fi

    avro_tools_jar="$(util_require_avro_tools_jar)"
    output_file="${input_file%.bin}.json"

    java "${JAVA_BASE64_INTRINSICS_FLAG}" -jar "${avro_tools_jar}" tojson "${input_file}" > "${output_file}"

    echo "Wrote: ${output_file}"
}

main "$@"
