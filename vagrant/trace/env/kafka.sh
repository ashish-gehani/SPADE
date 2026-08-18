#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# Kafka broker constants. One broker process, shared by every scenario --
# only one scenario's storage/reporter is ever attached at a time, so
# there's no port conflict; per-scenario topics are what keep scenarios
# apart, not separate brokers.
#
# WARNING: common/kafka.sh rm -rf's ENV_KAFKA_SERVER_HOME,
# ENV_KAFKA_SERVER_ARCHIVE, and ENV_KAFKA_SERVER_LOG_DIR on
# uninstall/reconfigure -- those paths must stay exactly what
# common/kafka.sh itself creates (curl download, tar extraction,
# mkdir -p).

ENV_KAFKA_HOME="${ENV_VM_WORK_HOME}/kafka"
ENV_KAFKA_SERVER_VERSION="4.3.1"
ENV_KAFKA_SERVER_SCALA_VERSION="2.13"
ENV_KAFKA_SERVER_ARCHIVE="${ENV_KAFKA_HOME}/kafka_${ENV_KAFKA_SERVER_SCALA_VERSION}-${ENV_KAFKA_SERVER_VERSION}.tgz"
ENV_KAFKA_SERVER_DOWNLOAD_URL="https://downloads.apache.org/kafka/${ENV_KAFKA_SERVER_VERSION}/kafka_${ENV_KAFKA_SERVER_SCALA_VERSION}-${ENV_KAFKA_SERVER_VERSION}.tgz"
ENV_KAFKA_SERVER_HOME="${ENV_KAFKA_HOME}/kafka_${ENV_KAFKA_SERVER_SCALA_VERSION}-${ENV_KAFKA_SERVER_VERSION}"
ENV_KAFKA_SERVER_CONFIG_FILE="${ENV_KAFKA_SERVER_HOME}/config/server.properties"
ENV_KAFKA_SERVER_LOG_DIR="/tmp/kraft-combined-logs"
ENV_KAFKA_SERVER_PORT="9092"
# ms to wait for new messages before the console consumer exits
ENV_KAFKA_SERVER_CONSUME_TIMEOUT_MS="5000"
# 30 x 2s = 1 minute
ENV_KAFKA_SERVER_WAIT_ATTEMPTS="30"
