#!/bin/sh

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.
#
# Runs on the Android device. Expects android-spade.jar to be in the same
# directory as this script (see ENV_DEVICE_SPADE_JAR in env.sh).


SPADE_CLIENT_CLASS="spade.client.Android"
SPADE_JAR="$(dirname "$0")/android-spade.jar"

dalvikvm -cp "${SPADE_JAR}" "${SPADE_CLIENT_CLASS}"
