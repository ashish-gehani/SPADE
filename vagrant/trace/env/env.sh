#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# Aggregator: sources every other category file in this same directory.

ENV_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Order matters here: each file may reference constants from files sourced
# before it (e.g. maven.sh/spade.sh/datasets.sh all use vm.sh's
# ENV_VM_WORK_HOME; util.sh uses maven.sh's ENV_MAVEN_REPO_HOME; scenario.sh
# uses vm.sh's ENV_VM_TRACE_HOME). vm.sh must stay first, and a file must
# never be moved above one it depends on.
source "${ENV_DIR}/vm.sh"
source "${ENV_DIR}/maven.sh"
source "${ENV_DIR}/spade.sh"
source "${ENV_DIR}/kafka.sh"
source "${ENV_DIR}/datasets.sh"
source "${ENV_DIR}/util.sh"
source "${ENV_DIR}/scenario.sh"
