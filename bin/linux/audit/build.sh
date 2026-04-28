#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

set -e

source "$( dirname "${BASH_SOURCE[0]}" )/../../env.sh"

KERNEL_MODULES="${KERNEL_MODULES:-false}"
KERNEL_MODULES_DEBUG="${KERNEL_MODULES_DEBUG:-false}"

if [[ "${KERNEL_MODULES_DEBUG}" == "true" ]]; then
    KERNEL_MODULES_BUILD_TARGET="debug"
else
    KERNEL_MODULES_BUILD_TARGET="release"
fi

gcc -o "${SPADE_BIN}/spadeAuditBridge" "${SPADE_SRC}/spade/reporter/spadeAuditBridge.c"

if [[ "${KERNEL_MODULES}" == "true" ]]; then
    make --no-print-directory -C "${AUDIT_KERNEL_MODULES_SRC}" \
        INSTALL_DIR="${AUDIT_KERNEL_MODULES_INSTALL}" \
        "${KERNEL_MODULES_BUILD_TARGET}" install
else
    echo 'Skipping kernel modules build. KERNEL_MODULES is not set to true'
fi

echo ''
echo '-----> IMPORTANT: To use the LinuxAudit reporter, please run the following commands to allow SPADE access to the audit stream:'
echo '----->             sudo chown root '"${SPADE_BIN}/spadeAuditBridge"
echo '----->             sudo chmod ug+s '"${SPADE_BIN}/spadeAuditBridge"
echo ''
