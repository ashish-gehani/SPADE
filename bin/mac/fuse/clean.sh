#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

set -e

source "$( dirname "${BASH_SOURCE[0]}" )/../../env.sh"

rm -f "${SPADE_SRC}/spade/reporter/spade_reporter_MacFUSE.h"
rm -f "${SPADE_LIB}"/libMacFUSE.*
