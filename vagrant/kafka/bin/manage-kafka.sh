#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "${SCRIPT_DIR}/env.sh"
source "${SCRIPT_DIR}/helper.sh"


# globals
COMMAND=""


function print_help() {
    echo "Usage: $(basename "$0") --cmd <command>"
    echo ""
    echo "Manages a bare-minimum, single-node Kafka broker (KRaft mode, no"
    echo "authentication or encryption) listening on PLAINTEXT port ${ENV_KAFKA_SERVER_PORT}."
    echo ""
    echo "Commands:"
    echo "    setup     Install and configure the Kafka broker"
    echo "    start     Start the Kafka broker"
    echo "    status    Check whether the Kafka broker is running"
    echo "    publish   Publish one dummy message to topic '${ENV_SPADE_KAFKA_TOPIC}'"
    echo "    consume   Print data published to topic '${ENV_SPADE_KAFKA_TOPIC}'"
    echo "    shutdown  Stop the Kafka broker"
    echo "    uninstall Remove the installed Kafka broker"
    echo ""
    echo "Options:"
    echo "    --cmd <command>  Command to run"
    echo "    --help           Show this message and exit"
    exit 0
}

function parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --cmd) COMMAND="$2"; shift 2 ;;
            --help) print_help ;;
            *) echo "Unknown argument: $1"; exit 1 ;;
        esac
    done
}

function validate_args() {
    if [[ -z "${COMMAND}" ]]; then
        echo "Error: --cmd must not be empty"
        exit 1
    fi
    case "${COMMAND}" in
        setup|start|status|publish|consume|shutdown|uninstall) ;;
        *) echo "Error: unknown command '${COMMAND}'"; exit 1 ;;
    esac
}

function install_java() {
    sudo apt-get update
    sudo apt-get install -y openjdk-21-jdk
}

function download_kafka() {
    mkdir -p "${ENV_KAFKA_HOME}"
    curl -fsSL -o "${ENV_KAFKA_SERVER_ARCHIVE}" "${ENV_KAFKA_SERVER_DOWNLOAD_URL}"
    tar xzf "${ENV_KAFKA_SERVER_ARCHIVE}" -C "${ENV_KAFKA_HOME}"
}

function configure_kafka() {
    local cluster_id

    # Clear any leftover metadata log from a previous setup; kafka-storage.sh format
    # generates a fresh cluster_id every run and refuses to format over a mismatched one.
    rm -rf "${ENV_KAFKA_SERVER_LOG_DIR}"

    cat > "${ENV_KAFKA_SERVER_CONFIG_FILE}" <<EOF
node.id=1
process.roles=broker,controller
controller.quorum.voters=1@localhost:9093
listeners=PLAINTEXT://0.0.0.0:${ENV_KAFKA_SERVER_PORT},CONTROLLER://0.0.0.0:9093
advertised.listeners=PLAINTEXT://localhost:${ENV_KAFKA_SERVER_PORT}
controller.listener.names=CONTROLLER
inter.broker.listener.name=PLAINTEXT
log.dirs=${ENV_KAFKA_SERVER_LOG_DIR}
num.partitions=1
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
EOF

    cluster_id="$("${ENV_KAFKA_SERVER_HOME}/bin/kafka-storage.sh" random-uuid)"
    # --standalone/--initial-controllers are for dynamic controller.quorum.bootstrap.servers
    # quorums; they conflict with the static controller.quorum.voters used above.
    "${ENV_KAFKA_SERVER_HOME}/bin/kafka-storage.sh" format --cluster-id "${cluster_id}" --config "${ENV_KAFKA_SERVER_CONFIG_FILE}"
}

function start_kafka() {
    "${ENV_KAFKA_SERVER_HOME}/bin/kafka-server-start.sh" -daemon "${ENV_KAFKA_SERVER_CONFIG_FILE}"
}

function is_kafka_up() {
    "${ENV_KAFKA_SERVER_HOME}/bin/kafka-broker-api-versions.sh" --bootstrap-server "localhost:${ENV_KAFKA_SERVER_PORT}" > /dev/null 2>&1
}

function wait_for_kafka_ready() {
    local i
    local ready

    ready=false
    for i in $(seq 1 "${ENV_KAFKA_SERVER_WAIT_ATTEMPTS}"); do
        if is_kafka_up; then
            ready=true
            break
        fi
        echo "Waiting for Kafka broker... (${i}/${ENV_KAFKA_SERVER_WAIT_ATTEMPTS})"
        sleep 2
    done

    if [[ "${ready}" != true ]]; then
        echo "Kafka broker never became ready"
        exit 1
    fi
    echo "Kafka broker ready on PLAINTEXT port ${ENV_KAFKA_SERVER_PORT}"
}

function run_setup() {
    install_java
    download_kafka
    configure_kafka
}

function run_start() {
    start_kafka
    wait_for_kafka_ready
}

function is_kafka_set_up() {
    [[ -x "${ENV_KAFKA_SERVER_HOME}/bin/kafka-broker-api-versions.sh" ]]
}

function require_kafka_set_up() {
    if ! is_kafka_set_up; then
        echo "Kafka has not been set up (run with --cmd setup first)"
        exit 1
    fi
}

function get_kafka_listener_protocol() {
    local listener_line

    listener_line="$(grep '^advertised.listeners=' "${ENV_KAFKA_SERVER_CONFIG_FILE}")"
    if [[ -z "${listener_line}" ]]; then
        echo "unknown"
        return
    fi
    echo "${listener_line#advertised.listeners=}" | cut -d: -f1
}

function run_status() {
    local protocol

    require_kafka_set_up

    if is_kafka_up; then
        protocol="$(get_kafka_listener_protocol)"
        echo "Kafka broker is running on port ${ENV_KAFKA_SERVER_PORT} (${protocol})"
        echo "Version: ${ENV_KAFKA_SERVER_VERSION}"
        echo "Home: ${ENV_KAFKA_SERVER_HOME}"
        echo "Config: ${ENV_KAFKA_SERVER_CONFIG_FILE}"
    else
        echo "Kafka broker is not running (run with --cmd start first)"
        exit 1
    fi
}

function run_publish() {
    require_kafka_set_up

    if ! is_kafka_up; then
        echo "Kafka broker is not running (run with --cmd start first)"
        exit 1
    fi

    echo "dummy message $(date -u +%FT%TZ)" | "${ENV_KAFKA_SERVER_HOME}/bin/kafka-console-producer.sh" --bootstrap-server "localhost:${ENV_KAFKA_SERVER_PORT}" --topic "${ENV_SPADE_KAFKA_TOPIC}"
    echo "Published dummy message to topic '${ENV_SPADE_KAFKA_TOPIC}'"
}

function run_consume() {
    require_kafka_set_up

    if ! is_kafka_up; then
        echo "Kafka broker is not running (run with --cmd start first)"
        exit 1
    fi

    helper_print_banner "Data consumed from topic '${ENV_SPADE_KAFKA_TOPIC}'"
    "${ENV_KAFKA_SERVER_HOME}/bin/kafka-console-consumer.sh" \
        --bootstrap-server "localhost:${ENV_KAFKA_SERVER_PORT}" \
        --topic "${ENV_SPADE_KAFKA_TOPIC}" \
        --from-beginning \
        --timeout-ms "${ENV_KAFKA_SERVER_CONSUME_TIMEOUT_MS}"
    # kafka-console-consumer exits non-zero on --timeout-ms expiry; that's the
    # expected way this command ends, not a failure.
    return 0
}

function stop_kafka() {
    "${ENV_KAFKA_SERVER_HOME}/bin/kafka-server-stop.sh"
}

function wait_for_kafka_stopped() {
    local i
    local stopped

    stopped=false
    for i in $(seq 1 "${ENV_KAFKA_SERVER_WAIT_ATTEMPTS}"); do
        if ! is_kafka_up; then
            stopped=true
            break
        fi
        echo "Waiting for Kafka broker to stop... (${i}/${ENV_KAFKA_SERVER_WAIT_ATTEMPTS})"
        sleep 2
    done

    if [[ "${stopped}" != true ]]; then
        echo "Kafka broker did not stop in time"
        exit 1
    fi
    echo "Kafka broker stopped"
}

function run_shutdown() {
    stop_kafka
    wait_for_kafka_stopped
}

function run_uninstall() {
    if is_kafka_up; then
        echo "Kafka broker is running (run with --cmd shutdown first)"
        exit 1
    fi
    rm -rf "${ENV_KAFKA_SERVER_HOME}"
    rm -f "${ENV_KAFKA_SERVER_ARCHIVE}"
    rm -rf "${ENV_KAFKA_SERVER_LOG_DIR}"
    echo "Kafka uninstalled"
}

function handle_command() {
    case "${COMMAND}" in
        setup) run_setup ;;
        start) run_start ;;
        status) run_status ;;
        publish) run_publish ;;
        consume) run_consume ;;
        shutdown) run_shutdown ;;
        uninstall) run_uninstall ;;
    esac
}

function main() {
    parse_args "$@"
    validate_args
    handle_command
}

main "$@"
