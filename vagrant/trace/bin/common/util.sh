#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# Helpers for user-facing utility scripts (bin/convert-storage-output-to-json.sh
# and anything similar added later) -- not part of the provisioning pipeline.

# Local alias for the env/ constant this file actually uses.
UTIL_AVRO_TOOLS_JAR="${ENV_UTIL_AVRO_TOOLS_JAR}"


# Prints the avro-tools jar path, or fails loudly with build-step guidance
# if it isn't there. Not downloaded separately by this script -- it's
# expected to already exist because SPADE's own pkg/java/pom.xml declares
# it as a build dependency and common/spade.sh's build step will have
# fetched it into ENV_MAVEN_REPO_HOME.
function util_require_avro_tools_jar() {
    if [[ ! -f "${UTIL_AVRO_TOOLS_JAR}" ]]; then
        echo "avro-tools jar not found at: ${UTIL_AVRO_TOOLS_JAR}"
        echo "Build SPADE first (bin/provision.sh) -- it's a build dependency, not something this script downloads."
        exit 1
    fi
    echo "${UTIL_AVRO_TOOLS_JAR}"
}
