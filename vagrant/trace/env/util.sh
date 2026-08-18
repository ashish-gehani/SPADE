#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# Constants for user-facing utility scripts (bin/convert-storage-output-to-json.sh
# and anything similar added later) -- not part of the provisioning pipeline
# itself.

# Location of the avro-tools jar used to deserialize a raw-Avro-binary
# storage output file into JSON. Not downloaded separately -- SPADE's own
# pkg/java/pom.xml already declares org.apache.avro:avro-tools:1.8.1 as a
# build dependency, so it's already sitting in the Maven local repo after
# `./configure && make` runs.
ENV_UTIL_AVRO_TOOLS_JAR="${ENV_MAVEN_REPO_HOME}/org/apache/avro/avro-tools/1.8.1/avro-tools-1.8.1.jar"
