#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# Bare-minimum, single-node Kafka broker (KRaft mode, no authentication or
# encryption). One broker process, shared by every scenario -- only one
# scenario's storage/reporter is ever attached at a time, so there's no
# port conflict; per-scenario topics are what keep scenarios apart, not
# separate brokers.

# Local aliases for the env/ constants this file actually uses.
KAFKA_HOME="${ENV_KAFKA_HOME}"
KAFKA_SERVER_VERSION="${ENV_KAFKA_SERVER_VERSION}"
KAFKA_SERVER_ARCHIVE="${ENV_KAFKA_SERVER_ARCHIVE}"
KAFKA_SERVER_DOWNLOAD_URL="${ENV_KAFKA_SERVER_DOWNLOAD_URL}"
KAFKA_SERVER_HOME="${ENV_KAFKA_SERVER_HOME}"
KAFKA_SERVER_CONFIG_FILE="${ENV_KAFKA_SERVER_CONFIG_FILE}"
KAFKA_SERVER_LOG_DIR="${ENV_KAFKA_SERVER_LOG_DIR}"
KAFKA_SERVER_PORT="${ENV_KAFKA_SERVER_PORT}"
KAFKA_SERVER_CONSUME_TIMEOUT_MS="${ENV_KAFKA_SERVER_CONSUME_TIMEOUT_MS}"
KAFKA_SERVER_WAIT_ATTEMPTS="${ENV_KAFKA_SERVER_WAIT_ATTEMPTS}"


function kafka_broker_install_java() {
    sudo apt-get update
    sudo apt-get install -y openjdk-21-jdk
}

function kafka_broker_download() {
    if [[ -x "${KAFKA_SERVER_HOME}/bin/kafka-broker-api-versions.sh" ]]; then
        return
    fi
    mkdir -p "${KAFKA_HOME}"
    curl -fsSL -o "${KAFKA_SERVER_ARCHIVE}" "${KAFKA_SERVER_DOWNLOAD_URL}"
    tar xzf "${KAFKA_SERVER_ARCHIVE}" -C "${KAFKA_HOME}"
}

function kafka_broker_configure() {
    local cluster_id

    # Clear any leftover metadata log from a previous setup; kafka-storage.sh
    # format generates a fresh cluster_id every run and refuses to format over
    # a mismatched one. Recreate it immediately so this script -- not just the
    # Kafka tooling it invokes -- is the explicit owner of the directory it
    # deletes here.
    rm -rf "${KAFKA_SERVER_LOG_DIR}"
    mkdir -p "${KAFKA_SERVER_LOG_DIR}"

    cat > "${KAFKA_SERVER_CONFIG_FILE}" <<EOF
node.id=1
process.roles=broker,controller
controller.quorum.voters=1@localhost:9093
listeners=PLAINTEXT://0.0.0.0:${KAFKA_SERVER_PORT},CONTROLLER://0.0.0.0:9093
advertised.listeners=PLAINTEXT://localhost:${KAFKA_SERVER_PORT}
controller.listener.names=CONTROLLER
inter.broker.listener.name=PLAINTEXT
log.dirs=${KAFKA_SERVER_LOG_DIR}
num.partitions=1
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
EOF

    cluster_id="$("${KAFKA_SERVER_HOME}/bin/kafka-storage.sh" random-uuid)"
    # --standalone/--initial-controllers are for dynamic controller.quorum.bootstrap.servers
    # quorums; they conflict with the static controller.quorum.voters used above.
    "${KAFKA_SERVER_HOME}/bin/kafka-storage.sh" format --cluster-id "${cluster_id}" --config "${KAFKA_SERVER_CONFIG_FILE}"
}

function kafka_broker_setup() {
    kafka_broker_install_java
    kafka_broker_download
    kafka_broker_configure
}

function kafka_broker_is_up() {
    "${KAFKA_SERVER_HOME}/bin/kafka-broker-api-versions.sh" --bootstrap-server "localhost:${KAFKA_SERVER_PORT}" > /dev/null 2>&1
}

function kafka_broker_wait_for_ready() {
    local i
    local ready

    ready=false
    for i in $(seq 1 "${KAFKA_SERVER_WAIT_ATTEMPTS}"); do
        if kafka_broker_is_up; then
            ready=true
            break
        fi
        echo "Waiting for Kafka broker... (${i}/${KAFKA_SERVER_WAIT_ATTEMPTS})"
        sleep 2
    done

    if [[ "${ready}" != true ]]; then
        echo "Kafka broker never became ready"
        exit 1
    fi
    echo "Kafka broker ready on PLAINTEXT port ${KAFKA_SERVER_PORT}"
}

function kafka_broker_start() {
    "${KAFKA_SERVER_HOME}/bin/kafka-server-start.sh" -daemon "${KAFKA_SERVER_CONFIG_FILE}"
    kafka_broker_wait_for_ready
}

# $1: topic to read from
function kafka_broker_consume() {
    local topic="$1"

    "${KAFKA_SERVER_HOME}/bin/kafka-console-consumer.sh" \
        --bootstrap-server "localhost:${KAFKA_SERVER_PORT}" \
        --topic "${topic}" \
        --from-beginning \
        --timeout-ms "${KAFKA_SERVER_CONSUME_TIMEOUT_MS}"
    # kafka-console-consumer exits non-zero on --timeout-ms expiry; that's the
    # expected way this command ends, not a failure.
    return 0
}

function kafka_broker_stop() {
    "${KAFKA_SERVER_HOME}/bin/kafka-server-stop.sh"
}

function kafka_broker_wait_for_stopped() {
    local i
    local stopped

    stopped=false
    for i in $(seq 1 "${KAFKA_SERVER_WAIT_ATTEMPTS}"); do
        if ! kafka_broker_is_up; then
            stopped=true
            break
        fi
        echo "Waiting for Kafka broker to stop... (${i}/${KAFKA_SERVER_WAIT_ATTEMPTS})"
        sleep 2
    done

    if [[ "${stopped}" != true ]]; then
        echo "Kafka broker did not stop in time"
        exit 1
    fi
    echo "Kafka broker stopped"
}

function kafka_broker_shutdown() {
    kafka_broker_stop
    kafka_broker_wait_for_stopped
}

function kafka_broker_uninstall() {
    if kafka_broker_is_up; then
        echo "Kafka broker is running (call kafka_broker_shutdown first)"
        exit 1
    fi
    rm -rf "${KAFKA_SERVER_HOME}"
    rm -f "${KAFKA_SERVER_ARCHIVE}"
    rm -rf "${KAFKA_SERVER_LOG_DIR}"
    echo "Kafka uninstalled"
}
