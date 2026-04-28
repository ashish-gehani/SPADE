#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

set -e

source "$( dirname "${BASH_SOURCE[0]}" )/../env.sh"

rm -rf "${ANDROID_BUILD}"
rm -rf "${ANDROID_LIB}"
