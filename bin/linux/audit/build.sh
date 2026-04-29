#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

set -e

source "$( dirname "${BASH_SOURCE[0]}" )/../../env.sh"

function main(){
    gcc -o "${SPADE_AUDIT_BRIDGE}" "${SPADE_SRC}/spade/reporter/spadeAuditBridge.c"

    echo ''
    echo '-----> IMPORTANT: To use the LinuxAudit reporter, please run the following commands to allow SPADE access to the audit stream:'
    echo '----->             sudo chown root '"${SPADE_AUDIT_BRIDGE}"
    echo '----->             sudo chmod ug+s '"${SPADE_AUDIT_BRIDGE}"
    echo ''
}

main "$@"
