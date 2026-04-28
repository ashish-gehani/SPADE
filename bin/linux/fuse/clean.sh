#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

set -e

SPADE_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )"/../../.. && pwd )"
SPADE_LIB="${SPADE_ROOT}/lib"
SPADE_SRC="${SPADE_ROOT}/src"

rm -f "${SPADE_SRC}/spade/reporter/spade_reporter_LinuxFUSE.h"
rm -f "${SPADE_LIB}"/libLinuxFUSE.*
