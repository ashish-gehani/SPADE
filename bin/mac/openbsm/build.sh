#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

set -e

source "$( dirname "${BASH_SOURCE[0]}" )/../../env.sh"

gcc -o "${SPADE_LIB}/spadeOpenBSM" -lbsm "${SPADE_SRC}/spade/reporter/spadeOpenBSM.c"

echo ''
echo '-----> IMPORTANT: To use the OpenBSM reporter, please run the following commands to allow SPADE access to the audit stream:'
echo '----->             sudo chown root '"${SPADE_LIB}/spadeOpenBSM"
echo '----->             sudo chmod ug+s '"${SPADE_LIB}/spadeOpenBSM"
echo ''
