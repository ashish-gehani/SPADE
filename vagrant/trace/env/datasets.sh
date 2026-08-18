#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# External datasets used as scenario input: the DARPA TC CDM trace (hosted
# on Google Drive) used by the cdm-to-* scenarios, and a real auditd log
# used by the audit-to-* scenarios. Only the TC trace's share URL is
# hand-maintained; the file id used by the actual download request is
# parsed out of it so there's one source of truth instead of two constants
# that could drift apart.

ENV_DATASETS_TC_TRACE_URL="https://drive.google.com/file/d/1cbs3XAcvuVtGVvxKpdiIeE05_GQD8wYa/view?usp=sharing"
ENV_DATASETS_TC_TRACE_FILE_ID="$(echo "${ENV_DATASETS_TC_TRACE_URL}" | sed -E 's#.*/d/([^/]+)/.*#\1#')"

# Where downloaded datasets land, under the VM's own working directory. Each
# dataset gets its own subdirectory below this -- there may be more than
# the TC trace here eventually.
ENV_DATASETS_HOME="${ENV_VM_WORK_HOME}/datasets"

ENV_DATASETS_TC_TRACE_HOME="${ENV_DATASETS_HOME}/tc-trace"

ENV_DATASETS_TC_TRACE_ARCHIVE_NAME="ta1-trace-1-e5-official-1.bin.gz"
ENV_DATASETS_TC_TRACE_BINARY_NAME="ta1-trace-1-e5-official-1.bin"

ENV_DATASETS_TC_TRACE_ARCHIVE_FILE="${ENV_DATASETS_TC_TRACE_HOME}/${ENV_DATASETS_TC_TRACE_ARCHIVE_NAME}"
ENV_DATASETS_TC_TRACE_BINARY_FILE="${ENV_DATASETS_TC_TRACE_HOME}/${ENV_DATASETS_TC_TRACE_BINARY_NAME}"

# Not downloaded by anything -- assumed location for now. Drop a real
# auditd log at ENV_DATASETS_AUDIT_LOG_FILE (or repoint this constant)
# before running an audit-to-* scenario. Shared by both audit-to-cdm and
# audit-to-kafka, same as the TC trace is shared by both cdm-to-* scenarios.
ENV_DATASETS_AUDIT_LOG_HOME="${ENV_DATASETS_HOME}/audit-log"
ENV_DATASETS_AUDIT_LOG_FILE="${ENV_DATASETS_AUDIT_LOG_HOME}/audit.log"
