#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# Which scenario bin/provision.sh runs, and where scenario directories live.

# Root of every scenario's own directory (scenario.sh + cfg/).
ENV_SCENARIO_HOME="${ENV_VM_TRACE_HOME}/scenarios"

# Every valid scenario name -- one directory under ENV_SCENARIO_HOME per
# entry. bin/provision.sh validates ENV_SCENARIO_SELECTED against this list
# (and prints it on a typo) instead of listing scenarios/ directories
# itself, so this array is the single source of truth for "which scenarios
# exist."
ENV_SCENARIO_AVAILABLE=("cdm-to-json" "cdm-to-kafka" "audit-to-cdm" "audit-to-kafka")

# Edit this one line to switch scenarios (or re-run bin/provision.sh after
# editing it, to switch without a full re-provision) -- no other file needs
# to change. Must be one of ENV_SCENARIO_AVAILABLE above.
ENV_SCENARIO_SELECTED="cdm-to-kafka"

ENV_SCENARIO_SELECTED_HOME="${ENV_SCENARIO_HOME}/${ENV_SCENARIO_SELECTED}"
