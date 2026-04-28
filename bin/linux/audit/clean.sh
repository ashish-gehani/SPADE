#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

set -e

source "$( dirname "${BASH_SOURCE[0]}" )/../../env.sh"

KERNEL_MODULES="${KERNEL_MODULES:-false}"

rm -f "${SPADE_BIN}/spadeAuditBridge"

if [[ "${KERNEL_MODULES}" == "true" ]]; then
    make --no-print-directory -C "${AUDIT_KERNEL_MODULES_SRC}" \
        INSTALL_DIR="${AUDIT_KERNEL_MODULES_INSTALL}" \
        clean
fi
