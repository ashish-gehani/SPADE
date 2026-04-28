#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

set -e

SPADE_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )"/../../ && pwd )"

rm -rf "${SPADE_ROOT}/android-build"
rm -rf "${SPADE_ROOT}/android-lib"
