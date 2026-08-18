#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# SPADE checkout/process constants. One checkout, shared by every scenario.
#
# WARNING: common/spade.sh rm -rf's ENV_SPADE_HOME on uninstall -- that path
# must stay exactly what get_spade() itself creates via git clone.

ENV_SPADE_HOME="${ENV_VM_WORK_HOME}/SPADE"
ENV_SPADE_BIN="${ENV_SPADE_HOME}/bin/spade"
ENV_SPADE_REPO_BRANCH="pidsmaker-dev"
ENV_SPADE_CONTROL_CLIENT_CONFIG="${ENV_SPADE_HOME}/cfg/spade.client.Control.config"
ENV_SPADE_CURRENT_LOG_FILE="${ENV_SPADE_HOME}/log/current.log"
# 30 x 2s = 1 minute
ENV_SPADE_CONTROL_PORT_WAIT_ATTEMPTS="30"
ENV_SPADE_DONE_MARKER_WAIT_SLEEP_SECONDS="5"
# 30 x 2s = 1 minute -- how long to wait for a graceful `bin/spade stop`
# (buffers clear, process exits on its own) before falling back to
# `bin/spade kill`.
ENV_SPADE_STOP_WAIT_ATTEMPTS="30"
ENV_SPADE_STOP_WAIT_SLEEP_SECONDS="2"
