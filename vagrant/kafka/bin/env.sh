#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.


# constants

# vagrant constants
ENV_VAGRANT_ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_SPADE_DSL_INPUT_FILE="${ENV_VAGRANT_ROOT_DIR}/data/dsl-input.txt"
ENV_SPADE_KAFKA_STORAGE_CONFIG_FILE="${ENV_VAGRANT_ROOT_DIR}/cfg/spade.storage.Kafka.config"
ENV_SPADE_KAFKA_EXPECTED_OUTPUT_FILE="${ENV_VAGRANT_ROOT_DIR}/data/kafka-expected-output.json"

# spade server constants
ENV_SPADE_HOME="${HOME}/SPADE"
ENV_SPADE_BIN="${ENV_SPADE_HOME}/bin/spade"
ENV_SPADE_REPO_BRANCH="master"
ENV_SPADE_KAFKA_TOPIC="$(grep '^kafka.output.topic=' "${ENV_SPADE_KAFKA_STORAGE_CONFIG_FILE}" | cut -d= -f2-)"
ENV_SPADE_DSL_PIPE="/tmp/spade_pipe"
ENV_SPADE_KAFKA_OUTPUT_FILE="$(grep '^kafka.output.file=' "${ENV_SPADE_KAFKA_STORAGE_CONFIG_FILE}" | cut -d= -f2-)"
# 30 x 2s = 1 minute
ENV_SPADE_KAFKA_OUTPUT_WAIT_ATTEMPTS="30"
# 30 x 2s = 1 minute
ENV_SPADE_CONTROL_PORT_WAIT_ATTEMPTS="30"

# kafka server constants
ENV_KAFKA_HOME="${HOME}/kafka"
ENV_KAFKA_SERVER_VERSION="4.3.1"
ENV_KAFKA_SERVER_SCALA_VERSION="2.13"
ENV_KAFKA_SERVER_ARCHIVE="${ENV_KAFKA_HOME}/kafka_${ENV_KAFKA_SERVER_SCALA_VERSION}-${ENV_KAFKA_SERVER_VERSION}.tgz"
ENV_KAFKA_SERVER_DOWNLOAD_URL="https://downloads.apache.org/kafka/${ENV_KAFKA_SERVER_VERSION}/kafka_${ENV_KAFKA_SERVER_SCALA_VERSION}-${ENV_KAFKA_SERVER_VERSION}.tgz"
ENV_KAFKA_SERVER_HOME="${ENV_KAFKA_HOME}/kafka_${ENV_KAFKA_SERVER_SCALA_VERSION}-${ENV_KAFKA_SERVER_VERSION}"
ENV_KAFKA_SERVER_CONFIG_FILE="${ENV_KAFKA_SERVER_HOME}/config/server.properties"
ENV_KAFKA_SERVER_LOG_DIR="/tmp/kraft-combined-logs"
ENV_KAFKA_SERVER_PORT="$(grep '^kafka.output.server=' "${ENV_SPADE_KAFKA_STORAGE_CONFIG_FILE}" | cut -d= -f2- | cut -d: -f2)"
# ms to wait for new messages before the console consumer exits
ENV_KAFKA_SERVER_CONSUME_TIMEOUT_MS="5000"
# 30 x 2s = 1 minute
ENV_KAFKA_SERVER_WAIT_ATTEMPTS="30"