#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# Scenario file contract -- reference/template only. Not sourced by
# bin/provision.sh (it only sources scenarios/<name>/scenario.sh for the
# name in ENV_SCENARIO_SELECTED). Every real scenario file implements the
# same functions/variable below; see this directory's README.md for the
# full narrative. The bodies here are no-ops -- copy this file's shape when
# adding a new scenario.

# How long (in seconds) scenario_execute's spade_wait_for_log_marker call
# should wait before giving up -- depends on this scenario's own input
# size, so it's set here rather than shared across scenarios.
SCENARIO_DONE_MARKER_TIMEOUT_SECONDS=0

# Hook: echoes one line, printed in banners.
function scenario_description() {
    :
}

# Hook: prepare this scenario's input (download+decompress the shared TC
# trace, or just verify the assumed audit log file exists) -- runs before
# SPADE is started.
function scenario_prepare_input() {
    :
}

# Hook: installs this scenario's own cfg/ files (storage config, and
# reporter config for audit-to-*) into ${ENV_SPADE_HOME}/cfg/.
function scenario_install_config() {
    :
}

# Hook: runs while SPADE is up and the control port is ready. Owns its own
# full sequence -- calls bin/common/spade.sh's spade_run_control_command
# once per command (add reporter, add storage, later remove reporter,
# remove storage -- never batched, one command per call so each one's own
# success/failure is checked before moving on; spade_run_control_command
# itself exits non-zero if SPADE's response doesn't contain the "... done"
# success text), and spade_wait_for_log_marker with its own marker string
# and SCENARIO_DONE_MARKER_TIMEOUT_SECONDS in between to confirm the
# reporter actually finished (a timeout there only warns, it doesn't stop
# this sequence). Nothing in bin/provision.sh inspects or drives this
# sequence -- it's entirely this scenario's own.
function scenario_execute() {
    :
}

# Hook: reports this scenario's output after it's stopped -- greps its own
# installed cfg/spade.storage.<Class>.config for which writer(s) were
# active, consumes the topic if the server writer was, prints the output
# path if the file writer was.
function scenario_post_execution_work() {
    :
}
