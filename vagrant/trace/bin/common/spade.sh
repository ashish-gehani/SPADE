#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# Generic SPADE checkout/build/start/stop/add/remove helpers. Shared by
# every scenario -- knows nothing about which one is currently selected.

# Local aliases for the env/ constants this file actually uses, so the
# functions below read without an ENV_ prefix on every line.
SPADE_HOME="${ENV_SPADE_HOME}"
SPADE_BIN="${ENV_SPADE_BIN}"
SPADE_REPO_BRANCH="${ENV_SPADE_REPO_BRANCH}"
SPADE_CONTROL_CLIENT_CONFIG="${ENV_SPADE_CONTROL_CLIENT_CONFIG}"
SPADE_CURRENT_LOG_FILE="${ENV_SPADE_CURRENT_LOG_FILE}"
SPADE_CONTROL_PORT_WAIT_ATTEMPTS="${ENV_SPADE_CONTROL_PORT_WAIT_ATTEMPTS}"
SPADE_DONE_MARKER_WAIT_SLEEP_SECONDS="${ENV_SPADE_DONE_MARKER_WAIT_SLEEP_SECONDS}"
SPADE_STOP_WAIT_ATTEMPTS="${ENV_SPADE_STOP_WAIT_ATTEMPTS}"
SPADE_STOP_WAIT_SLEEP_SECONDS="${ENV_SPADE_STOP_WAIT_SLEEP_SECONDS}"
MAVEN_REPO_HOME="${ENV_MAVEN_REPO_HOME}"


function spade_install_java() {
    sudo apt-get update
    sudo apt-get install -y openjdk-21-jdk
}

function spade_install_requirements() {
    sudo apt-get install -y auditd autoconf automake bison clang cmake curl flex fuse git ifupdown libaudit-dev libfuse-dev linux-headers-`uname -r` lsof maven pkg-config unzip uthash-dev wget
}

function spade_get() {
    if [[ -d "${SPADE_HOME}" ]]; then
        return
    fi
    mkdir -p "$(dirname "${SPADE_HOME}")"
    git clone --branch "${SPADE_REPO_BRANCH}" --depth 1 https://github.com/ashish-gehani/SPADE.git "${SPADE_HOME}"
}

function spade_build() {
    # ./configure and make are cwd-relative build tools; pushd/popd scopes the
    # directory change to just this function instead of leaking it to the rest
    # of the script.
    pushd "${SPADE_HOME}" > /dev/null
    mkdir -p "${MAVEN_REPO_HOME}"
    ./configure
    make
    popd > /dev/null
}

function spade_setup() {
    spade_install_java
    spade_install_requirements
    spade_get
    spade_build
}

function spade_uninstall() {
    if [[ -d "${SPADE_HOME}" ]]; then
        rm -rf "${SPADE_HOME}"
    fi
}

# Truncates the control-client config SPADE auto-saves its running
# reporters/storages/etc into on every stop, and auto-replays on every
# start. Without this, whatever a previous scenario had running would get
# silently re-added here.
function spade_clear_control_client_config() {
    if [[ -e "${SPADE_CONTROL_CLIENT_CONFIG}" ]]; then
        : > "${SPADE_CONTROL_CLIENT_CONFIG}"
    fi
}

function spade_start() {
    spade_clear_control_client_config
    # No cd needed: bin/spade's own run() wrapper pushd's into SPADE_ROOT before
    # dispatching "start", so the JVM it forks already inherits the right cwd.
    "${SPADE_BIN}" start --mem-min 1g --mem-max 2g
}

function spade_is_up() {
    printf 'exit\n' | "${SPADE_BIN}" control > /dev/null 2>&1
}

function spade_wait_for_control_port() {
    local i
    local ready

    ready=false
    for i in $(seq 1 "${SPADE_CONTROL_PORT_WAIT_ATTEMPTS}"); do
        if spade_is_up; then
            ready=true
            break
        fi
        echo "Waiting for SPADE... (${i}/${SPADE_CONTROL_PORT_WAIT_ATTEMPTS})"
        sleep 2
    done

    if [[ "${ready}" != true ]]; then
        echo "SPADE control port never became ready"
        exit 1
    fi
    echo "SPADE control port ready"
}

function spade_is_stopped() {
    [[ "$("${SPADE_BIN}" status)" == "Stopped" ]]
}

# Sends a graceful stop (bin/spade stop -- SIGTERM, SPADE exits once its
# buffers clear) and polls bin/spade status for up to
# SPADE_STOP_WAIT_ATTEMPTS x SPADE_STOP_WAIT_SLEEP_SECONDS. A graceful stop
# can take an arbitrarily long time; if SPADE still hasn't exited by then,
# falls back to bin/spade kill (SIGKILL) so the process is never left
# behind.
function spade_stop() {
    local i
    local stopped

    "${SPADE_BIN}" stop

    stopped=false
    for i in $(seq 1 "${SPADE_STOP_WAIT_ATTEMPTS}"); do
        if spade_is_stopped; then
            stopped=true
            break
        fi
        echo "Waiting for SPADE to stop... (${i}/${SPADE_STOP_WAIT_ATTEMPTS})"
        sleep "${SPADE_STOP_WAIT_SLEEP_SECONDS}"
    done

    if [[ "${stopped}" != true ]]; then
        echo "SPADE did not stop within the timeout -- killing it"
        "${SPADE_BIN}" kill
    fi
}

# Runs exactly one control command and checks SPADE's own response for
# success. Kernel.java's add/remove handlers always print "<action>...
# done" on success (e.g. "Adding storage CDM... done.", "Shutting down
# reporter CDM... done"); failures print "error: ...", a bare "failed"/
# "failed. <msg>", or "<Class> not found" instead -- never that "... done"
# substring -- so checking for it is a reliable pass/fail signal without
# having to match every failure message SPADE could produce.
# $1: a single control command (e.g. "add storage CDM") -- "exit" is
#     appended automatically, callers don't include it
function spade_run_control_command() {
    local command="$1"
    local output

    output="$(printf '%s\nexit\n' "${command}" | "${SPADE_BIN}" control)"
    echo "${output}"

    if ! grep -qF '... done' <<< "${output}"; then
        echo "Command failed: ${command}"
        exit 1
    fi
}

# Polls log/current.log for a fixed marker string, e.g. the line a reporter
# logs right before its read loop exits. Used instead of a fixed sleep since
# how long a reporter takes to finish depends on its input size -- callers
# pass their own total timeout since that depends on their own input size
# too (e.g. a scenario replaying a large trace file needs much longer than
# one reading a small log). Attempts are derived from that timeout and the
# shared poll interval, rounded up so the actual wait is never shorter than
# the requested timeout. Times out with a warning rather than exiting -- the
# reporter may just be slower than expected, and the caller's own
# remove/stop cleanup still needs to run either way.
# $1: exact marker string to grep -F for
# $2: total timeout in seconds to wait for the marker
function spade_wait_for_log_marker() {
    local marker="$1"
    local timeout_seconds="$2"
    local attempts
    local i
    local ready

    attempts=$(( (timeout_seconds + SPADE_DONE_MARKER_WAIT_SLEEP_SECONDS - 1) / SPADE_DONE_MARKER_WAIT_SLEEP_SECONDS ))

    ready=false
    for i in $(seq 1 "${attempts}"); do
        if [[ -e "${SPADE_CURRENT_LOG_FILE}" ]] && grep -qF "${marker}" "${SPADE_CURRENT_LOG_FILE}"; then
            ready=true
            break
        fi
        echo "Waiting for: ${marker} (${i}/${attempts})"
        sleep "${SPADE_DONE_MARKER_WAIT_SLEEP_SECONDS}"
    done

    if [[ "${ready}" != true ]]; then
        echo "WARNING: never saw marker in ${SPADE_CURRENT_LOG_FILE}: ${marker} -- continuing anyway"
        return
    fi
    echo "Saw marker: ${marker}"
}
